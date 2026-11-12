package com.privateplayer.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.usecase.PlaybackStopper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 当前目录的文件树状态，路径均相对于私有文件树根目录。 */
data class HomeUiState(
    val currentDirectory: String = "",
    val entries: List<FileTreeEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
)

private val entryOrder = Comparator<FileTreeEntry> { left, right ->
    when {
        left.isDirectory != right.isDirectory -> if (left.isDirectory) -1 else 1
        else -> left.name.compareTo(right.name, ignoreCase = true)
            .takeIf { it != 0 } ?: left.name.compareTo(right.name)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileTreeRepository: FileTreeRepository,
    private val playbackStopper: PlaybackStopper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val currentDirectory = MutableStateFlow("")
    private val reload = MutableStateFlow(0L)
    private val working = MutableStateFlow(false)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val directoryState = combine(currentDirectory, reload) { path, generation -> path to generation }
        .flatMapLatest { (path, _) ->
            flow { emitAll(fileTreeRepository.observeDirectory(path)) }
                .map { entries -> HomeUiState(currentDirectory = path, entries = entries.sortedWith(entryOrder), isLoading = false) }
                .onStart { emit(HomeUiState(currentDirectory = path)) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    val message = if (path.isEmpty()) {
                        "无法读取文件目录，请稍后重试。"
                    } else {
                        "无法读取此目录，可能已被移动或删除。请返回上级目录或重试。"
                    }
                    emit(HomeUiState(currentDirectory = path, isLoading = false, error = message))
                }
        }
        .flowOn(ioDispatcher)

    val uiState: StateFlow<HomeUiState> = combine(directoryState, working) { state, isWorking ->
        state.copy(isWorking = isWorking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun openDirectory(path: String) {
        currentDirectory.value = path
    }

    fun navigateUp() {
        val path = currentDirectory.value
        if (path.isNotEmpty()) currentDirectory.value = path.substringBeforeLast('/', "")
    }

    fun retry() {
        reload.update { it + 1 }
    }

    fun showUnsupportedFileMessage() {
        messageChannel.trySend("此文件不支持播放，可通过操作菜单导出或分享。")
    }

    fun resolveVideo(entry: FileTreeEntry, onResolved: (Long) -> Unit) {
        if (entry.isDirectory || !entry.isVideo) {
            showUnsupportedFileMessage()
            return
        }
        perform(
            failureMessage = "无法打开视频，请确认文件仍然存在后重试。",
            action = { entry.mediaId ?: fileTreeRepository.ensureMedia(entry.path) },
            onSuccess = onResolved,
        )
    }

    fun createDirectory(parent: String, name: String) {
        perform(
            failureMessage = "新建目录失败，请检查名称是否有效或已存在。",
            successMessage = "目录已创建",
            action = { fileTreeRepository.createDirectory(parent, name) },
        )
    }

    fun rename(entry: FileTreeEntry, name: String) {
        perform(
            failureMessage = "重命名失败，请检查名称是否有效或已存在。",
            successMessage = "已重命名",
            action = {
                playbackStopper.stopIfAffected(entry.path)
                fileTreeRepository.rename(entry.path, name)
            },
        )
    }

    fun move(entry: FileTreeEntry, destination: String) {
        perform(
            failureMessage = "移动失败，请检查目标目录是否可用且没有同名项目。",
            successMessage = "已移动",
            action = {
                playbackStopper.stopIfAffected(entry.path)
                fileTreeRepository.move(entry.path, destination)
            },
        )
    }

    fun delete(entry: FileTreeEntry) {
        perform(
            failureMessage = "删除失败，请确认项目仍然存在后重试。",
            successMessage = "已删除",
            action = {
                playbackStopper.stopIfAffected(entry.path)
                fileTreeRepository.delete(entry.path)
            },
        )
    }

    /** 目标选择器只能浏览实际存在的目录，且不能进入正在移动的目录。 */
    suspend fun directoriesForMove(path: String, entry: FileTreeEntry): List<FileTreeEntry> =
        withContext(ioDispatcher) {
            fileTreeRepository.list(path)
                .filter { it.isDirectory && (!entry.isDirectory || (it.path != entry.path && !it.path.startsWith("${entry.path}/"))) }
                .sortedWith(entryOrder)
        }

    private fun <T> perform(
        failureMessage: String,
        successMessage: String? = null,
        action: suspend () -> T,
        onSuccess: (T) -> Unit = {},
    ) {
        if (!working.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { action() }
                onSuccess(result)
                if (successMessage != null) messageChannel.send(successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messageChannel.send(failureMessage)
            } finally {
                working.value = false
            }
        }
    }
}
