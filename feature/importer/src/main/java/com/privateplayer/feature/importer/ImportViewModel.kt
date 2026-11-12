package com.privateplayer.feature.importer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.domain.model.ImportPreferences
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.repository.ImportRepository
import com.privateplayer.domain.repository.StorageRepository
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.repository.WebUploadController
import com.privateplayer.domain.repository.WebUploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val activeTasks: List<ImportTask> = emptyList(),
    val finishedTasks: List<ImportTask> = emptyList(),
    val usage: StorageUsage = StorageUsage(0, 0, 0, 0, 0),
    val notices: List<String> = emptyList(),
    val enqueuing: Boolean = false,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importRepository: ImportRepository,
    storageRepository: StorageRepository,
    private val settingsRepository: SettingsRepository,
    private val webUploadController: WebUploadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private data class Local(
        val notices: List<String> = emptyList(),
        val enqueuing: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val webUploadState: StateFlow<WebUploadState> = webUploadController.state

    val importPreferences: StateFlow<ImportPreferences> = settingsRepository.observeSettings()
        .map { it.import }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportPreferences())

    init {
        viewModelScope.launch {
            // 应用启动后补挂丢失的后台任务，保证进程被杀后的导入能继续。
            importRepository.reconcilePendingTasks()
        }
    }

    val uiState: StateFlow<ImportUiState> = combine(
        importRepository.observeTasks(),
        storageRepository.observeUsage(),
        local,
    ) { tasks, usage, localState ->
        ImportUiState(
            activeTasks = tasks.filter { it.state.isActive },
            finishedTasks = tasks.filter { !it.state.isActive },
            usage = usage,
            notices = localState.notices,
            enqueuing = localState.enqueuing,
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())


    fun startWebUpload(directory: String = "") {
        webUploadController.start(directory)
    }

    fun stopWebUpload() {
        webUploadController.stop()
    }

    fun enqueue(uris: List<String>, directory: String = "") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(enqueuing = true) }
            try {
                val result = importRepository.enqueue(uris, targetDirectory = directory)
                val notices = buildList {
                    if (result.acceptedTaskIds.isNotEmpty()) {
                        add(context.getString(R.string.import_notice_enqueued, result.acceptedTaskIds.size))
                    }
                    if (result.duplicates.isNotEmpty()) {
                        add(context.getString(R.string.import_notice_duplicates, result.duplicates.joinToString("、")))
                    }
                    result.rejected.forEach { rejected ->
                        add(context.getString(R.string.import_notice_rejected, rejected.displayName, rejected.reason))
                    }
                }
                if (notices.isNotEmpty()) local.update { it.copy(notices = it.notices + notices) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val reason = error.message ?: context.getString(R.string.import_unknown_error)
                local.update { it.copy(notices = it.notices + context.getString(R.string.import_notice_enqueue_failed, reason)) }
            } finally {
                local.update { it.copy(enqueuing = false) }
            }
        }
    }

    fun setGenerateThumbnails(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGenerateThumbnails(enabled) }
    }

    fun setDuplicateDetection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDuplicateDetection(enabled) }
    }

    fun dismissNotices() {
        local.update { it.copy(notices = emptyList()) }
    }

    fun cancel(taskId: String) {
        viewModelScope.launch { importRepository.cancel(taskId) }
    }

    fun retry(taskId: String) {
        viewModelScope.launch { importRepository.retry(taskId) }
    }

    fun remove(taskId: String) {
        viewModelScope.launch { importRepository.removeTask(taskId) }
    }
}
