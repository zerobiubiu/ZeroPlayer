package com.privateplayer.data.webupload

import android.content.Context
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.storage.ImportCopier
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.importer.isWebUpload
import com.privateplayer.data.importer.webUploadFile
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.ImportRepository
import com.privateplayer.domain.repository.WebTransferRecord
import com.privateplayer.domain.repository.WebTransferState
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** 只在用户开启时监听局域网；文件与首页共用同一棵私有目录树。 */
internal class WebUploadHttpServer(
    private val context: Context,
    private val address: Inet4Address,
    private val initialPath: String,
    private val storage: PrivateStorage,
    private val tasks: ImportTaskDao,
    private val tree: FileTreeRepository,
    private val imports: ImportRepository,
    private val scheduler: ImportWorkScheduler,
    private val onTransfer: (WebTransferRecord) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val open = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val executor = ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8),
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val journal = WebUploadJournal(storage, tasks, scheduler)
    private var listener: ServerSocket? = null
    val port: Int get() = listener?.localPort ?: 0

    fun start() {
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(address, PORT))
            journal.recover()
            listener = socket
            open.set(true)
            Thread({ acceptLoop(socket) }, "web-upload-listener").apply {
                isDaemon = true
                start()
            }
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }

    fun stop() {
        open.set(false)
        runCatching { listener?.close() }
        clients.forEach { runCatching { it.close() } }
        executor.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        try {
            while (open.get()) {
                val client = server.accept()
                client.soTimeout = 60_000
                clients.add(client)
                try {
                    executor.execute {
                        try {
                            client.use(::handle)
                        } finally {
                            clients.remove(client)
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    clients.remove(client)
                    client.close()
                }
            }
        } catch (error: IOException) {
            if (open.get()) onFailure("网页文件服务连接失败：${error.message ?: "未知错误"}")
        }
    }

    private fun handle(socket: Socket) {
        try {
            val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val request = LocalHttpProtocol.readRequest(input)
            val parsed = try {
                URI(request.target)
            } catch (_: Exception) {
                throw BadHttpRequest("请求地址无效")
            }
            val path = parsed.path ?: throw BadHttpRequest("请求地址无效")
            val query = LocalHttpProtocol.queryParameters(parsed.rawQuery)
            when {
                request.method == "GET" && path == "/" -> asset(socket, "index.html", "text/html; charset=utf-8")
                request.method == "GET" && path == "/app.js" -> asset(socket, "app.js", "text/javascript; charset=utf-8")
                request.method == "GET" && path == "/style.css" -> asset(socket, "style.css", "text/css; charset=utf-8")
                request.method == "GET" && path == "/api/config" ->
                    respond(socket, 200, JSONObject().put("initialPath", initialPath).toString())
                request.method == "GET" && path == "/api/files" -> listFiles(socket, query["path"].orEmpty())
                request.method == "GET" && path == "/api/download" -> download(socket, required(query, "path"))
                request.method == "POST" && path == "/api/folders" -> {
                    val entry = runBlocking { tree.createDirectory(query["path"].orEmpty(), required(query, "name")) }
                    respond(socket, 201, entryJson(entry).toString())
                }
                request.method == "POST" && path == "/api/rename" -> {
                    val entry = runBlocking { tree.rename(required(query, "path"), required(query, "name")) }
                    respond(socket, 200, entryJson(entry).toString())
                }
                request.method == "POST" && path == "/api/move" -> {
                    val entry = runBlocking { tree.move(required(query, "path"), required(query, "destination", allowEmpty = true)) }
                    respond(socket, 200, entryJson(entry).toString())
                }
                request.method == "POST" && path == "/api/delete" -> {
                    runBlocking { tree.delete(required(query, "path")) }
                    respond(socket, 200, JSONObject().put("ok", true).toString())
                }
                request.method == "POST" && path == "/api/uploads" -> upload(socket, input, request, query)
                request.method == "POST" && path.startsWith("/api/uploads/") && path.endsWith("/retry") ->
                    retry(socket, path.removePrefix("/api/uploads/").removeSuffix("/retry"))
                request.method == "GET" && path.startsWith("/api/uploads/") && path.endsWith("/download") ->
                    downloadFailed(socket, path.removePrefix("/api/uploads/").removeSuffix("/download"))
                request.method == "GET" && path.startsWith("/api/uploads/") -> status(socket, path.removePrefix("/api/uploads/"))
                else -> respond(socket, 404, jsonError("请求不存在"))
            }
        } catch (error: BadHttpRequest) {
            runCatching { respond(socket, 400, jsonError(error.message ?: "请求无效")) }
        } catch (_: EOFException) {
            // 客户端已断开，接收未完成的暂存由 upload 清理。
        } catch (_: SocketException) {
            // 停止服务时会关闭所有连接。
        } catch (error: FileAlreadyExistsException) {
            runCatching { respond(socket, 409, jsonError(error.message ?: "目标已存在")) }
        } catch (error: FileNotFoundException) {
            runCatching { respond(socket, 404, jsonError(error.message ?: "文件不存在")) }
        } catch (error: IllegalArgumentException) {
            runCatching { respond(socket, 400, jsonError(error.message ?: "请求参数无效")) }
        } catch (error: IllegalStateException) {
            runCatching { respond(socket, 409, jsonError(error.message ?: "操作冲突")) }
        } catch (error: IOException) {
            runCatching { respond(socket, 500, jsonError(error.message ?: "文件传输失败")) }
        } catch (_: Exception) {
            runCatching { respond(socket, 500, jsonError("服务处理失败，请重试")) }
        }
    }

    private fun listFiles(socket: Socket, path: String) {
        val normalized = normalizedDirectory(path)
        val entries = runBlocking { tree.list(normalized) }
        val items = JSONArray()
        entries.forEach { items.put(entryJson(it)) }
        respond(socket, 200, JSONObject().put("path", normalized).put("entries", items).toString())
    }

    private fun entryJson(entry: FileTreeEntry): JSONObject = JSONObject()
        .put("name", entry.name)
        .put("path", entry.path)
        .put("type", if (entry.isDirectory) "directory" else "file")
        .put("size", entry.sizeBytes)
        .put("modifiedAt", entry.modifiedAt)

    private fun normalizedDirectory(path: String): String {
        val directory = runBlocking { tree.requireDirectory(path) }.canonicalFile
        val root = storage.mediaRoot.canonicalFile
        if (directory == root) return ""
        if (!directory.toPath().startsWith(root.toPath())) throw BadHttpRequest("目录超出文件树")
        return root.toPath().relativize(directory.toPath()).toString().replace(File.separatorChar, '/')
    }

    private fun upload(socket: Socket, input: BufferedInputStream, request: LocalHttpRequest, query: Map<String, String>) {
        if (!uploading.compareAndSet(false, true)) {
            respond(socket, 409, jsonError("已有文件正在上传，请稍后重试"))
            return
        }
        var temp: File? = null
        var entry: WebUploadJournal.Entry? = null
        var journalPrepared = false
        var complete = false
        var accepted = false
        var record: WebTransferRecord? = null
        try {
            val name = validateUploadName(required(query, "name"))
            val directory = normalizedDirectory(query["path"].orEmpty())
            val length = requestContentLength(request.headers["content-length"])
            if (length == null) {
                respond(socket, 411, jsonError("需要有效的文件大小"))
                return
            }
            if (!storage.ensureLayout()) throw IOException("无法创建应用存储目录")
            val available = storage.freeBytes()
            if (length > 0L && (available <= ImportCopier.SPACE_HEADROOM_BYTES ||
                    length > available - ImportCopier.SPACE_HEADROOM_BYTES)) {
                respond(socket, 507, jsonError("手机存储空间不足"))
                return
            }
            val id = UUID.randomUUID().toString()
            val targetName = uniqueUploadName(directory, name)
            val targetPath = if (directory.isEmpty()) targetName else "$directory/$targetName"
            val mimeType = request.headers["content-type"]?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            val journalEntry = WebUploadJournal.Entry(id, targetName, targetPath, length, mimeType)
            entry = journalEntry
            journal.begin(id)
            journal.prepare(journalEntry)
            journalPrepared = true
            val staging = storage.webUploadFile(id)
            if (!staging.createNewFile()) throw IOException("无法创建上传暂存文件")
            temp = staging
            record = WebTransferRecord(id, targetName, length, 0L, WebTransferState.RECEIVING)
            onTransfer(record!!)
            if (request.headers["expect"]?.equals("100-continue", ignoreCase = true) == true) {
                socket.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                socket.getOutputStream().flush()
            }
            FileOutputStream(staging).use { output ->
                val buffer = ByteArray(PrivateStorage.COPY_BUFFER_SIZE)
                var remaining = length
                var lastReport = 0L
                while (remaining > 0L) {
                    if (!open.get()) throw SocketException("服务已停止")
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw EOFException("上传未完成")
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    remaining -= read
                    val now = System.nanoTime()
                    if (remaining == 0L || now - lastReport >= PROGRESS_INTERVAL_NANOS) {
                        record = record!!.copy(receivedBytes = length - remaining)
                        onTransfer(record!!)
                        lastReport = now
                    }
                }
                complete = true
                output.flush()
                output.fd.sync()
            }
            // 收满以后即使服务关闭也完成入库；收到的文件不能因连接断开丢失。
            journal.insert(journalEntry)
            journal.remove(id)
            accepted = true
            record = record!!.copy(receivedBytes = length, state = WebTransferState.IMPORTING, taskId = id)
            onTransfer(record!!)
            runCatching { scheduler.schedule(id) }
            respond(socket, 202, JSONObject().put("taskId", id).toString())
        } catch (error: Exception) {
            if (!accepted && record != null) {
                val canceled = !complete && (error is EOFException || error is SocketException)
                val message = if (complete) "已接收的文件已保留，可重新启动服务恢复导入" else
                    (error.message ?: "接收文件失败")
                onTransfer(record!!.copy(
                    state = if (canceled) WebTransferState.CANCELED else WebTransferState.FAILED,
                    message = message,
                ))
            }
            throw error
        } finally {
            if (!complete) {
                temp?.delete()
                if (journalPrepared) entry?.let { journal.remove(it.id) }
            }
            entry?.let { journal.end(it.id) }
            uploading.set(false)
        }
    }

    /** 名称预先避让已提交文件和持久化任务；提交时仍必须原子拒绝重名。 */
    private fun uniqueUploadName(directory: String, name: String): String {
        val existing = runBlocking {
            val fileNames = tree.list(directory).flatMap { listOf(it.name, it.path.substringAfterLast('/')) }
            val taskNames = tasks.observeAll().first().asSequence()
                .filter { it.state != ImportTaskState.COMPLETED.name }
                .map { it.targetFileName }
                .filter { it.substringBeforeLast('/', "") == directory }
                .map { it.substringAfterLast('/') }
                .toList()
            (fileNames + taskNames).mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        }
        return availableUploadName(name, existing)
    }

    private fun status(socket: Socket, id: String) {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) {
            respond(socket, 404, jsonError("任务不存在"))
            return
        }
        val task = runBlocking { tasks.findById(id) }
        if (task == null || !task.isWebUpload()) {
            respond(socket, 404, jsonError("任务不存在"))
            return
        }
        val body = JSONObject().put("state", task.state)
            .put("errorMessage", task.errorMessage ?: JSONObject.NULL)
            .put("bytesCopied", task.bytesCopied)
            .put("sizeBytes", task.sizeBytes)
        respond(socket, 200, body.toString())
    }

    private fun retry(socket: Socket, id: String) {
        val task = findWebTask(id)
        if (task == null) {
            respond(socket, 404, jsonError("任务不存在"))
            return
        }
        if (task.state != ImportTaskState.FAILED.name) {
            respond(socket, 409, jsonError("只有失败的任务可以重试"))
            return
        }
        runBlocking { imports.retry(id) }
        val current = findWebTask(id)
        if (current == null) {
            respond(socket, 404, jsonError("任务已被移除"))
        } else if (current.state == ImportTaskState.FAILED.name || current.state == ImportTaskState.CANCELED.name) {
            respond(socket, 409, jsonError(current.errorMessage ?: "任务状态已变化，无法重试"))
        } else {
            respond(socket, 202, JSONObject().put("taskId", id).toString())
        }
    }

    private fun downloadFailed(socket: Socket, id: String) {
        val task = findWebTask(id)
        if (task == null) {
            respond(socket, 404, jsonError("任务不存在"))
            return
        }
        if (task.state != ImportTaskState.FAILED.name) {
            respond(socket, 409, jsonError("只有失败任务的暂存文件可以下载"))
            return
        }
        try {
            // 打开文件后持有只读句柄；并发导入完成时解除链接不会影响已开始的下载。
            streamFile(socket, storage.webUploadFile(id), task.displayName)
        } catch (_: FileNotFoundException) {
            respond(socket, 409, jsonError("暂存文件已不可用，任务可能已完成或正在重试"))
        }
    }

    private fun findWebTask(id: String): com.privateplayer.core.database.entity.ImportTaskEntity? {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) return null
        return runBlocking { tasks.findById(id) }?.takeIf { it.isWebUpload() }
    }

    private fun download(socket: Socket, path: String) {
        val displayName = runBlocking { tree.find(path) }?.name
        val file = runBlocking { tree.openForRead(path) }
        streamFile(socket, file, displayName ?: file.name)
    }

    private fun streamFile(socket: Socket, file: File, displayName: String) {
        val input = FileInputStream(file)
        try {
            val length = input.channel.size()
            val encodedName = URLEncoder.encode(displayName, "UTF-8").replace("+", "%20").replace("*", "%2A")
            val headers = responseHeaders(
                200, "application/octet-stream", length,
                "Content-Disposition: attachment; filename=\"download\"; filename*=UTF-8''$encodedName\r\n",
            )
            val output = socket.getOutputStream()
            try {
                output.write(headers.toByteArray(StandardCharsets.US_ASCII))
                val buffer = ByteArray(PrivateStorage.COPY_BUFFER_SIZE)
                var remaining = length
                while (remaining > 0L) {
                    if (!open.get()) throw SocketException("服务已停止")
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw EOFException("下载文件已变更")
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                output.flush()
            } catch (_: IOException) {
                // 已开始发送附件，不能再向同一响应追加 JSON 错误。
            }
        } finally {
            runCatching { input.close() }
        }
    }

    private fun asset(socket: Socket, file: String, type: String) {
        val content = context.assets.open("web-upload/$file").use { it.readBytes() }
        respond(socket, 200, content, type)
    }

    private fun required(query: Map<String, String>, key: String, allowEmpty: Boolean = false): String {
        val value = query[key] ?: throw BadHttpRequest("缺少参数：$key")
        if (!allowEmpty && value.isEmpty()) throw BadHttpRequest("参数不能为空：$key")
        return value
    }

    private fun jsonError(message: String): String = JSONObject().put("error", message).toString()

    private fun respond(socket: Socket, code: Int, body: String) =
        respond(socket, code, body.toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8")

    private fun respond(socket: Socket, code: Int, body: ByteArray, type: String) {
        val headers = responseHeaders(code, type, body.size.toLong())
        socket.getOutputStream().apply {
            write(headers.toByteArray(StandardCharsets.US_ASCII))
            write(body)
            flush()
        }
    }

    companion object {
        const val PORT = 8765
        private val uploading = AtomicBoolean(false)
        private const val PROGRESS_INTERVAL_NANOS = 200_000_000L
    }
}
