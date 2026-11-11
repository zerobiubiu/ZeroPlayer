package com.privateplayer.data.webupload

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.ImportRepository
import com.privateplayer.domain.repository.WebTransferRecord
import com.privateplayer.domain.repository.WebTransferState
import com.privateplayer.domain.repository.WebUploadController
import com.privateplayer.domain.repository.WebUploadState
import com.privateplayer.domain.repository.WebUploadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.BindException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 进程内共享服务和本次传输状态；停止监听不会停止已接收的导入任务。 */
@Singleton
class WebUploadServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: PrivateStorage,
    private val tasks: ImportTaskDao,
    private val tree: FileTreeRepository,
    private val imports: ImportRepository,
    private val scheduler: ImportWorkScheduler,
) : WebUploadController {
    private val stateMutable = MutableStateFlow(WebUploadState())
    override val state: StateFlow<WebUploadState> = stateMutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var generation = 0L
    private var session = 0L
    private val observedTasks = mutableSetOf<String>()
    private var server: WebUploadHttpServer? = null

    init {
        scope.launch {
            tasks.observeAll().collect { all ->
                val byId = all.associateBy { it.id }
                synchronized(lock) {
                    val current = stateMutable.value
                    val updated = current.transfers.map { record ->
                        val task = byId[record.id]
                        if (task == null) {
                            if (record.id in observedTasks && record.state == WebTransferState.IMPORTING) {
                                record.copy(state = WebTransferState.CANCELED, message = "导入任务已移除")
                            } else record
                        } else {
                            observedTasks += record.id
                            val status = when (task.state) {
                                ImportTaskState.QUEUED.name, ImportTaskState.RUNNING.name,
                                ImportTaskState.COMMITTING.name -> WebTransferState.IMPORTING
                                ImportTaskState.COMPLETED.name -> WebTransferState.COMPLETED
                                ImportTaskState.FAILED.name -> WebTransferState.FAILED
                                ImportTaskState.CANCELED.name -> WebTransferState.CANCELED
                                else -> record.state
                            }
                            record.copy(state = status, message = task.errorMessage, taskId = task.id)
                        }
                    }
                    if (updated != current.transfers) stateMutable.value = current.copy(transfers = updated)
                }
            }
        }
    }

    override fun start(directory: String) {
        val current: Long
        synchronized(lock) {
            if (stateMutable.value.status == WebUploadStatus.STARTING ||
                stateMutable.value.status == WebUploadStatus.RUNNING) return
            generation++
            current = generation
            session = current
            observedTasks.clear()
            stateMutable.value = WebUploadState(status = WebUploadStatus.STARTING, initialPath = directory)
        }
        try {
            val intent = Intent(context, WebUploadService::class.java)
                .putExtra(WebUploadService.EXTRA_GENERATION, current)
                .putExtra(WebUploadService.EXTRA_DIRECTORY, directory)
            ContextCompat.startForegroundService(context, intent)
        } catch (error: Exception) {
            fail(current, "无法启动网页文件服务：${error.message ?: "系统限制"}")
        }
    }

    override fun stop() {
        synchronized(lock) {
            generation++
            server?.stop()
            server = null
            stateMutable.value = stateMutable.value.copy(
                status = WebUploadStatus.STOPPED, address = null, message = null,
            )
        }
        context.stopService(Intent(context, WebUploadService::class.java))
    }

    internal fun startListening(expectedGeneration: Long, directory: String, stopService: () -> Unit) {
        scope.launch {
            try {
                tree.requireDirectory(directory)
                val address = localAddress()
                    ?: throw IllegalStateException("未找到局域网 IPv4 地址，请连接 Wi-Fi 后重试")
                val next = WebUploadHttpServer(
                    context = context,
                    address = address,
                    initialPath = directory,
                    storage = storage,
                    tasks = tasks,
                    tree = tree,
                    imports = imports,
                    scheduler = scheduler,
                    onTransfer = { updateTransfer(expectedGeneration, it) },
                    onFailure = { reason -> if (fail(expectedGeneration, reason)) stopService() },
                )
                next.start()
                synchronized(lock) {
                    if (expectedGeneration != generation || stateMutable.value.status != WebUploadStatus.STARTING) {
                        next.stop()
                        return@launch
                    }
                    server = next
                    stateMutable.value = stateMutable.value.copy(
                        status = WebUploadStatus.RUNNING,
                        address = "http://${address.hostAddress}:${next.port}/",
                    )
                }
            } catch (error: Exception) {
                val reason = if (error is BindException) "端口 8765 已被占用" else error.message ?: "未知错误"
                if (fail(expectedGeneration, "网页文件服务启动失败：$reason")) stopService()
            }
        }
    }

    private fun updateTransfer(expectedSession: Long, record: WebTransferRecord) {
        synchronized(lock) {
            if (session != expectedSession) return
            val current = stateMutable.value
            val index = current.transfers.indexOfFirst { it.id == record.id }
            if (index < 0) {
                stateMutable.value = current.copy(transfers = current.transfers + record)
            } else {
                val previous = current.transfers[index]
                // 数据库可能先于网络线程发出终态，不能被较晚的接收进度覆盖。
                if (record.state == WebTransferState.RECEIVING && previous.state != WebTransferState.RECEIVING) return
                if (record.state == WebTransferState.IMPORTING &&
                    previous.state in setOf(WebTransferState.COMPLETED, WebTransferState.FAILED, WebTransferState.CANCELED)) return
                val next = current.transfers.toMutableList()
                next[index] = record
                stateMutable.value = current.copy(transfers = next)
            }
        }
    }

    internal fun onStartFailure(expectedGeneration: Long, message: String) {
        fail(expectedGeneration, message)
    }

    internal fun onServiceStopped() {
        synchronized(lock) {
            generation++
            server?.stop()
            server = null
            if (stateMutable.value.status != WebUploadStatus.ERROR) {
                stateMutable.value = stateMutable.value.copy(status = WebUploadStatus.STOPPED, address = null)
            }
        }
    }

    private fun fail(expectedGeneration: Long, message: String): Boolean = synchronized(lock) {
        if (expectedGeneration != generation) return@synchronized false
        server?.stop()
        server = null
        stateMutable.value = stateMutable.value.copy(status = WebUploadStatus.ERROR, address = null, message = message)
        true
    }

    /** 优先当前非蜂窝网络；热点或以太网使用系统列出的私有 IPv4 地址。 */
    private fun localAddress(): Inet4Address? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val caps = network?.let(connectivity::getNetworkCapabilities)
        if (network != null && caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val active = connectivity.getLinkProperties(network)?.linkAddresses
                ?.mapNotNull { it.address as? Inet4Address }
                ?.firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            if (active != null) return active
        }
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .filter { networkInterface ->
                val name = networkInterface.name.lowercase()
                name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth") ||
                    name.startsWith("en") || name.startsWith("wifi")
            }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
    }
}
