package com.privateplayer.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.domain.repository.FileTreeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileTransferState(
    val exporting: Boolean = false,
    val copied: Long = 0L,
    val total: Long = 0L,
    val message: String? = null,
    val shareIntent: Intent? = null,
)

/** 在展示层之外完成私有树读取与对外流式写入。 */
class FileTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileTree: FileTreeRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun share(path: String): Intent = withContext(ioDispatcher) {
        val file = fileTree.openForRead(path)
        val name = fileTree.find(path)?.name ?: file.name
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file, name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeForName(name)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("文件", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "分享文件").apply {
            clipData = send.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun export(path: String, destination: Uri, onProgress: (Long, Long) -> Unit) = withContext(ioDispatcher) {
        if (destination.scheme != "content" || destination.authority.isNullOrBlank()) {
            throw IOException("导出目标不是有效的文档地址")
        }
        val authority = requireNotNull(destination.authority)
        val owner = context.packageManager.resolveContentProvider(authority, 0)?.packageName
        if (authority == "${context.packageName}.files" || owner == context.packageName) {
            throw IOException("不能将文件导出到应用自己的私有文件")
        }
        val source = fileTree.openForRead(path)
        val total = source.length()
        source.inputStream().use { input ->
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("无法打开导出目标")
            output.use {
                val buffer = ByteArray(1024 * 1024)
                var copied = 0L
                var lastReported = 0L
                var lastReportedAt = android.os.SystemClock.elapsedRealtime()
                onProgress(0L, total)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (copied - lastReported >= 8L * 1024 * 1024 || now - lastReportedAt >= 250L) {
                        onProgress(copied, total)
                        lastReported = copied
                        lastReportedAt = now
                    }
                }
                output.flush()
                if (copied != total) throw IOException("源文件在导出期间发生变化")
                onProgress(copied, total)
            }
        }
    }
}

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val manager: FileTransferManager,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FileTransferState())
    val state: StateFlow<FileTransferState> = mutableState
    private var exportJob: Job? = null

    fun prepareExport(path: String) { savedState[KEY_EXPORT_PATH] = path }

    fun exportTo(destination: Uri?) {
        val path = savedState.get<String>(KEY_EXPORT_PATH)
        savedState.set<String?>(KEY_EXPORT_PATH, null)
        if (path == null || destination == null) return
        if (mutableState.value.exporting) {
            mutableState.update { it.copy(message = "已有文件正在导出，请等待完成") }
            return
        }
        mutableState.update { it.copy(exporting = true, copied = 0L, total = 0L, message = null) }
        exportJob = viewModelScope.launch {
            try {
                manager.export(path, destination) { copied, total ->
                    mutableState.update { it.copy(copied = copied, total = total) }
                }
                mutableState.update { it.copy(exporting = false, message = "导出完成") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(exporting = false, message = "已取消导出；目标位置可能保留未完成的文件") }
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(exporting = false, message = "导出失败：${error.message ?: "无法写入目标"}；目标位置可能保留未完成的文件")
                }
            }
        }
    }

    fun cancelExport() { exportJob?.cancel() }

    fun share(path: String) {
        viewModelScope.launch {
            try {
                val intent = manager.share(path)
                mutableState.update { it.copy(shareIntent = intent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = "分享失败：${error.message ?: "无法读取文件"}") }
            }
        }
    }

    fun consumeShare() { mutableState.update { it.copy(shareIntent = null) } }
    fun reportError(message: String) { mutableState.update { it.copy(message = message) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    private companion object { const val KEY_EXPORT_PATH = "pending_export_path" }
}

internal fun mimeForName(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}
