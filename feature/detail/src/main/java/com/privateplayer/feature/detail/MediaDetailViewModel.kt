package com.privateplayer.feature.detail

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.MediaFileInfo
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.SubtitleFile
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaFileLocator
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.SubtitleStore
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.usecase.PlaybackStopper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DetailRouteArgs {
    const val ARG_MEDIA_ID = "mediaId"
}

/** 详情页反馈消息：资源 + 参数，文案由界面解析。 */
data class DetailMessage(@StringRes val res: Int, val args: List<String> = emptyList())

data class MediaDetailUiState(
    val isLoading: Boolean = true,
    val media: Media? = null,
    val thumbnailFile: File? = null,
    val fileInfo: MediaFileInfo? = null,
    val subtitles: List<SubtitleFile> = emptyList(),
    val sameDirectorySubtitles: List<SubtitleFile> = emptyList(),
    @StringRes val sameDirectorySubtitleErrorRes: Int? = null,
    val messageRes: Int? = null,
    val messageArgs: Array<Any> = emptyArray(),
    val showFileInfo: Boolean = false,
    /** 文件操作进行中：禁用更多菜单，避免并发操作同一文件。 */
    val isWorking: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mediaRepository: MediaRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val subtitleStore: SubtitleStore,
    private val fileLocator: MediaFileLocator,
    private val playbackStopper: PlaybackStopper,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(DetailRouteArgs.ARG_MEDIA_ID)?.toLongOrNull() ?: -1L

    private data class Local(
        val message: DetailMessage? = null,
        val showFileInfo: Boolean = false,
        val subtitles: List<SubtitleFile> = emptyList(),
        val isWorking: Boolean = false,
    )

    private data class SameDirectorySubtitles(
        val mediaPath: String? = null,
        val subtitles: List<SubtitleFile> = emptyList(),
        @StringRes val errorRes: Int? = null,
    )

    private val local = MutableStateFlow(Local())
    private val subtitleMutex = Mutex()

    private val mediaFlow = mediaRepository.observeById(mediaId)
    private val sameDirectorySubtitlesFlow = mediaFlow
        .map { it?.relativePath }
        .distinctUntilChanged()
        .flatMapLatest { mediaPath ->
            if (mediaPath == null) {
                flowOf(SameDirectorySubtitles())
            } else {
                val treePath = mediaPath.removePrefix("media/")
                val parent = treePath.substringBeforeLast('/', "")
                fileTreeRepository.observeDirectory(parent)
                    .map {
                        try {
                            val subtitles = fileTreeRepository.matchingSubtitles(treePath).map { path ->
                                val file = File(path)
                                SubtitleFile(name = file.name, absolutePath = path, format = file.extension.lowercase())
                            }
                            SameDirectorySubtitles(mediaPath = mediaPath, subtitles = subtitles)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            SameDirectorySubtitles(mediaPath = mediaPath, errorRes = R.string.detail_same_dir_subtitle_error)
                        }
                    }
                    .catch {
                        emit(SameDirectorySubtitles(mediaPath = mediaPath, errorRes = R.string.detail_same_dir_subtitle_error))
                    }
            }
        }

    init {
        refreshSubtitles()
    }

    val uiState: StateFlow<MediaDetailUiState> = combine(
        mediaFlow,
        sameDirectorySubtitlesFlow,
        local,
    ) { media, sameDirectorySubtitles, localState ->
        val matches = sameDirectorySubtitles.takeIf { it.mediaPath == media?.relativePath }
        MediaDetailUiState(
            isLoading = false,
            media = media,
            thumbnailFile = fileLocator.thumbnailFile(media?.thumbnailPath),
            fileInfo = media?.let { fileLocator.mediaFile(it.relativePath) },
            subtitles = localState.subtitles,
            sameDirectorySubtitles = matches?.subtitles.orEmpty(),
            sameDirectorySubtitleErrorRes = matches?.errorRes,
            messageRes = localState.message?.res,
            messageArgs = localState.message?.args.orEmpty().toTypedArray(),
            showFileInfo = localState.showFileInfo,
            isWorking = localState.isWorking,
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaDetailUiState())

    fun consumeMessage() {
        local.update { it.copy(message = null) }
    }

    fun setShowFileInfo(show: Boolean) {
        local.update { it.copy(showFileInfo = show) }
    }

    /** 当前媒体在文件树中的条目；文件操作对话框与首页共用同一模型。 */
    fun treeEntry(): FileTreeEntry? {
        val media = uiState.value.media ?: return null
        return FileTreeEntry(
            name = media.fileName,
            path = media.relativePath.removePrefix("media/"),
            isDirectory = false,
            sizeBytes = media.sizeBytes,
            modifiedAt = media.addedAt,
            mediaId = media.id,
            isVideo = true,
            thumbnailPath = media.thumbnailPath,
            durationMs = media.durationMs,
            playbackPositionMs = media.playbackPositionMs,
        )
    }

    fun rename(name: String) {
        val entry = treeEntry() ?: return
        performFileOperation(
            failureMessage = DetailMessage(R.string.detail_rename_failed),
            successMessage = DetailMessage(R.string.detail_renamed),
        ) {
            playbackStopper.stopIfAffected(entry.path)
            fileTreeRepository.rename(entry.path, name)
        }
    }

    fun move(destination: String) {
        val entry = treeEntry() ?: return
        performFileOperation(
            failureMessage = DetailMessage(R.string.detail_move_failed),
            successMessage = DetailMessage(R.string.detail_moved),
        ) {
            playbackStopper.stopIfAffected(entry.path)
            fileTreeRepository.move(entry.path, destination)
        }
    }

    fun delete() {
        val entry = treeEntry() ?: return
        performFileOperation(
            failureMessage = DetailMessage(R.string.detail_delete_failed),
            successMessage = DetailMessage(R.string.detail_deleted),
        ) {
            playbackStopper.stopIfAffected(entry.path)
            fileTreeRepository.delete(entry.path)
        }
    }

    /** 移动目标选择器与首页同一规则：只列真实文件夹，排除自身及其子树。 */
    suspend fun directoriesForMove(path: String, entry: FileTreeEntry): List<FileTreeEntry> =
        withContext(ioDispatcher) {
            fileTreeRepository.list(path)
                .filter { it.isDirectory && (it.path != entry.path && !it.path.startsWith("${entry.path}/")) }
                .sortedWith(compareBy<FileTreeEntry> { it.name.lowercase() }.thenBy { it.name })
        }

    /** 文件操作统一入口：串行执行、先停播放再动文件；结果经消息反馈，媒体记录由观察流自动同步。 */
    private fun performFileOperation(
        failureMessage: DetailMessage,
        successMessage: DetailMessage,
        action: suspend () -> Unit,
    ) {
        if (local.value.isWorking) return
        local.update { it.copy(isWorking = true) }
        viewModelScope.launch(ioDispatcher) {
            val message = try {
                action()
                successMessage
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failureMessage
            }
            local.update { it.copy(isWorking = false, message = message) }
        }
    }

    private fun refreshSubtitles() {
        viewModelScope.launch(ioDispatcher) {
            subtitleMutex.withLock {
                val subtitles = subtitleStore.list(mediaId)
                local.update { it.copy(subtitles = subtitles) }
            }
        }
    }

    fun importSubtitle(uri: String) =
        changeSubtitles(DetailMessage(R.string.detail_subtitle_added)) { subtitleStore.import(mediaId, uri) }

    fun deleteSubtitle(subtitle: SubtitleFile) =
        changeSubtitles(DetailMessage(R.string.detail_subtitle_deleted)) { subtitleStore.delete(subtitle.absolutePath) }

    private fun changeSubtitles(success: DetailMessage, action: suspend () -> OperationOutcome) {
        viewModelScope.launch(ioDispatcher) {
            subtitleMutex.withLock {
                val outcome = try {
                    action()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    OperationOutcome.Failure(error.message ?: context.getString(R.string.detail_unknown_error))
                }
                val subtitles = subtitleStore.list(mediaId)
                local.update { it.copy(subtitles = subtitles, message = outcome.toMessage(success)) }
            }
        }
    }

    private fun OperationOutcome.toMessage(success: DetailMessage): DetailMessage = when (this) {
        is OperationOutcome.Success -> success
        is OperationOutcome.Failure -> DetailMessage(R.string.detail_subtitle_op_failed, listOf(message))
    }
}
