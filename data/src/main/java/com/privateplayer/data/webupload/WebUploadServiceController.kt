package com.privateplayer.data.webupload

import android.content.Context
import android.content.Intent
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val startupGate = Mutex()
    private var recordSession = 0L
    private val observedTasks = mutableSetOf<String>()
    private var server: WebUploadListeners? = null
    private var startup: Job? = null

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
        synchronized(lock) {
            if (stateMutable.value.status == WebUploadStatus.STARTING ||
                stateMutable.value.status == WebUploadStatus.RUNNING) return
            generation++
            val current = generation
            recordSession = current
            observedTasks.clear()
            stateMutable.value = WebUploadState(status = WebUploadStatus.STARTING, initialPath = directory)
            try {
                val intent = Intent(context, WebUploadService::class.java)
                    .putExtra(WebUploadService.EXTRA_GENERATION, current)
                    .putExtra(WebUploadService.EXTRA_DIRECTORY, directory)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                failLocked("无法启动网页文件服务，请检查系统权限与后台限制")
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            stopLocked()
            context.stopService(Intent(context, WebUploadService::class.java))
        }
    }

    internal fun stopFromNotification(expected: Long): Boolean = synchronized(lock) {
        if (!isActiveLocked(expected)) return@synchronized false
        stopLocked()
        true
    }

    internal fun withoutActiveSession(action: () -> Unit) {
        synchronized(lock) {
            if (!isActiveLocked(generation)) action()
        }
    }

    internal fun isActive(expected: Long): Boolean = synchronized(lock) { isActiveLocked(expected) }

    private fun isActiveLocked(expected: Long): Boolean = expected == generation &&
        (stateMutable.value.status == WebUploadStatus.STARTING || stateMutable.value.status == WebUploadStatus.RUNNING)

    internal fun startListening(expected: Long, directory: String, stopService: () -> Unit) {
        synchronized(lock) {
            if (!isActiveLocked(expected) || server != null) return
            val handler = WebUploadRequestHandler(context, directory, storage, tasks, tree, imports, scheduler) {
                updateTransfer(expected, it)
            }
            val listeners = WebUploadListeners(handler::recover, handler::handle, {
                if (publishEndpoints(expected)) stopService()
            })
            server = listeners // 先交给 stop：发现、绑定或恢复中均可撤回监听。
            startup = scope.launch {
                try {
                    startupGate.withLock {
                        tree.requireDirectory(directory)
                        listeners.start(discoverLanAddresses(context))
                    }
                    if (publishEndpoints(expected)) stopService()
                } catch (error: CancellationException) {
                    if (fail(expected, "网页文件服务启动已取消")) stopService()
                    throw error
                } catch (error: Exception) {
                    val reason = when (error) {
                        is WebUploadListenException -> error.message
                        is SecurityException -> "请检查局域网访问权限"
                        else -> "请检查网络、传输目录和存储空间后重试"
                    }
                    if (fail(expected, "网页文件服务启动失败：$reason")) stopService()
                }
            }
        }
    }

    /** 初次启动与接口断开都读取此刻的监听快照，旧通知不能重新发布已丢失的地址。 */
    private fun publishEndpoints(expected: Long): Boolean = synchronized(lock) {
        if (!isActiveLocked(expected)) return@synchronized false
        val endpoints = server?.endpoints().orEmpty()
        if (endpoints.isEmpty()) {
            failLocked("所有局域网监听均已断开，请检查网络后重新启动")
            true
        } else {
            stateMutable.value = stateMutable.value.copy(status = WebUploadStatus.RUNNING, endpoints = endpoints)
            false
        }
    }

    private fun updateTransfer(expected: Long, record: WebTransferRecord) {
        synchronized(lock) {
            if (expected != recordSession) return
            val current = stateMutable.value
            val index = current.transfers.indexOfFirst { it.id == record.id }
            if (!isActiveLocked(expected)) {
                // 已关闭网络端点；只允许已知接收记录收尾，不接受旧连接创建记录或刷新进度。
                if (index < 0 || record.state == WebTransferState.RECEIVING) return
            }
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

    internal fun fail(expected: Long, message: String): Boolean = synchronized(lock) {
        if (!isActiveLocked(expected)) return@synchronized false
        failLocked(message)
        true
    }

    internal fun onServiceStopped(expected: Long) {
        synchronized(lock) {
            if (isActiveLocked(expected)) stopLocked()
        }
    }

    private fun stopLocked() {
        finishLocked(WebUploadStatus.STOPPED, null)
    }

    private fun failLocked(message: String) {
        finishLocked(WebUploadStatus.ERROR, message)
    }

    private fun finishLocked(status: WebUploadStatus, message: String?) {
        generation++
        startup?.cancel()
        startup = null
        server?.close()
        server = null
        val current = stateMutable.value
        val transfers = current.transfers.map {
            if (it.state == WebTransferState.RECEIVING && it.receivedBytes < it.sizeBytes) {
                it.copy(state = WebTransferState.CANCELED, message = "服务已停止，文件未接收完成")
            } else it
        }
        stateMutable.value = current.copy(status = status, endpoints = emptyList(), message = message, transfers = transfers)
    }
}
