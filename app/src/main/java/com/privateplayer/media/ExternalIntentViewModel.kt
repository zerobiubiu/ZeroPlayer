package com.privateplayer.media
import android.content.Context

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.repository.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 系统打开和分享只负责入队；播放器只接收导入完成后的内部媒体 ID。 */
@HiltViewModel
class ExternalIntentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importRepository: ImportRepository,
    private val openStore: ExternalOpenStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val pendingOpenTask = savedState.getStateFlow<String?>(KEY_OPEN_TASK, null)
    val readyMediaId = savedState.getStateFlow<Long?>(KEY_READY_MEDIA, null)
    val showImports = savedState.getStateFlow(KEY_SHOW_IMPORTS, false)
    private val enqueuingCount = MutableStateFlow(0)
    private val acceptedAwaitingObservation = MutableStateFlow<Set<String>>(emptySet())
    private var latestRequest = 0L
    private val messages = MutableStateFlow<List<String>>(emptyList())
    val notices: StateFlow<List<String>> = messages
    val tasks: StateFlow<List<ImportTask>> = importRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 临时 URI 授权依赖接收 Activity 的生命周期，复制结束前不主动结束 Activity。 */
    fun mustKeepActivity(): Boolean = enqueuingCount.value > 0 ||
        acceptedAwaitingObservation.value.isNotEmpty() || tasks.value.any { it.state.isActive }

    init {
        val saved = openStore.read()
        if (saved.readyMediaId != null) savedState[KEY_READY_MEDIA] = saved.readyMediaId
        if (saved.taskId != null) savedState[KEY_OPEN_TASK] = saved.taskId
        viewModelScope.launch {
            combine(tasks, acceptedAwaitingObservation) { current, pending ->
                pending - current.filter { !it.state.isActive }.map { it.id }.toSet()
            }.collect { remaining -> acceptedAwaitingObservation.value = remaining }
        }
        viewModelScope.launch {
            combine(pendingOpenTask, tasks) { id, current -> id to current.firstOrNull { it.id == id } }
                .collect { (id, task) ->
                    if (id == null || pendingOpenTask.value != id) return@collect
                    val mediaId = task?.mediaId
                    if (task?.state == ImportTaskState.COMPLETED && mediaId != null) {
                        try {
                            openStore.ready(mediaId)
                        } catch (error: Exception) {
                            messages.value = listOf(error.message ?: context.getString(R.string.app_external_save_ready_failed))
                        }
                        savedState[KEY_READY_MEDIA] = mediaId
                        savedState.set<String?>(KEY_OPEN_TASK, null)
                    } else if (task?.state == ImportTaskState.CANCELED) {
                        runCatching { openStore.clear() }
                        savedState.set<String?>(KEY_OPEN_TASK, null)
                    }
                }
        }
    }

    /** 恢复可能在写入任务之前被中断的外部打开；已有任务绝不重复创建。 */
    fun restorePending() {
        val saved = openStore.read()
        val taskId = saved.taskId ?: return
        val uri = saved.uri ?: return
        savedState[KEY_SHOW_IMPORTS] = true
        val request = ++latestRequest
        enqueuingCount.value += 1
        viewModelScope.launch {
            try {
                if (importRepository.observeTasks().first().any { it.id == taskId }) return@launch
                val result = importRepository.enqueueExternal(listOf(uri), saved.mimeType, true, taskId)
                if (request != latestRequest) return@launch
                acceptResult(result.acceptedTaskIds, uri, saved.mimeType, true)
                messages.value = result.rejected.map { context.getString(R.string.app_external_rejected, it.displayName, it.reason) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.value = listOf(error.message ?: context.getString(R.string.app_external_restore_failed))
            } finally {
                enqueuingCount.value -= 1
            }
        }
    }

    fun receive(uris: List<String>, mimeType: String?, openForPlayback: Boolean) {
        val request = ++latestRequest
        savedState[KEY_SHOW_IMPORTS] = true
        if (uris.isEmpty()) {
            messages.value = listOf(context.getString(R.string.app_external_no_readable_file))
            return
        }
        val uri = uris.first()
        val previous = openStore.read()
        if (openForPlayback && previous.uri == uri && previous.readyMediaId != null) {
            savedState[KEY_READY_MEDIA] = previous.readyMediaId
            return
        }
        val requestTaskId = if (openForPlayback) {
            previous.taskId?.takeIf { previous.uri == uri } ?: UUID.randomUUID().toString()
        } else null
        try {
            if (requestTaskId != null) openStore.pending(requestTaskId, uri, mimeType) else openStore.clear()
        } catch (error: Exception) {
            messages.value = listOf(error.message ?: context.getString(R.string.app_external_save_request_failed))
            return
        }
        savedState.set<Long?>(KEY_READY_MEDIA, null)
        savedState[KEY_OPEN_TASK] = requestTaskId
        enqueuingCount.value += 1
        viewModelScope.launch {
            try {
                val result = importRepository.enqueueExternal(uris, mimeType, openForPlayback, requestTaskId)
                acceptedAwaitingObservation.value = acceptedAwaitingObservation.value + result.acceptedTaskIds
                if (request == latestRequest) {
                    acceptResult(result.acceptedTaskIds, uri, mimeType, openForPlayback)
                    messages.value = result.rejected.map { context.getString(R.string.app_external_rejected, it.displayName, it.reason) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == latestRequest) messages.value = listOf(error.message ?: context.getString(R.string.app_external_enqueue_failed))
            } finally {
                enqueuingCount.value -= 1
            }
        }
    }

    private fun acceptResult(ids: List<String>, uri: String, mimeType: String?, openForPlayback: Boolean) {
        if (!openForPlayback) return
        val persisted = openStore.read()
        if (persisted.uri == uri && persisted.readyMediaId != null) return
        val id = ids.firstOrNull()
        if (id == null) {
            openStore.clear()
            savedState.set<String?>(KEY_OPEN_TASK, null)
        } else {
            openStore.pending(id, uri, mimeType)
            savedState[KEY_OPEN_TASK] = id
            acceptedAwaitingObservation.value = acceptedAwaitingObservation.value + id
        }
    }

    fun forgetTask(taskId: String) {
        if (pendingOpenTask.value == taskId) {
            runCatching { openStore.clear() }
            savedState.set<String?>(KEY_OPEN_TASK, null)
        }
    }

    fun showImportTasks() { savedState[KEY_SHOW_IMPORTS] = true }
    fun dismissImportTasks() {
        savedState[KEY_SHOW_IMPORTS] = false
        messages.value = emptyList()
    }
    fun consumeReadyMedia() {
        runCatching { openStore.clear() }
        savedState.set<Long?>(KEY_READY_MEDIA, null)
    }

    private companion object {
        const val KEY_OPEN_TASK = "external_open_task"
        const val KEY_READY_MEDIA = "external_ready_media"
        const val KEY_SHOW_IMPORTS = "external_show_imports"
    }
}
