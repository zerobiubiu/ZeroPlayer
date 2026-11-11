package com.privateplayer.core.storage

import android.net.Uri
import com.privateplayer.domain.model.MediaFileInfo
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.SubtitleFile
import com.privateplayer.domain.repository.MediaFileLocator
import com.privateplayer.domain.repository.SubtitleStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 私有存储到领域接口的适配：UI 只看到路径/文件，不接触 [PrivateStorage] 的目录规则。 */
@Singleton
class StorageMediaFileLocator @Inject constructor(
    private val storage: PrivateStorage,
) : MediaFileLocator {

    override fun mediaFile(relativePath: String): MediaFileInfo {
        val file = storage.absoluteFile(relativePath)
        return MediaFileInfo(
            absolutePath = file.absolutePath,
            exists = file.isFile,
            sizeBytes = if (file.isFile) file.length() else 0L,
        )
    }

    override fun thumbnailFile(thumbnailPath: String?): File? {
        if (thumbnailPath.isNullOrBlank()) return null
        return storage.absoluteCacheFile(thumbnailPath).takeIf { it.isFile }
    }

    override fun subtitlesFor(mediaId: Long): List<SubtitleFile> =
        storage.listSubtitles(mediaId).map { file ->
            SubtitleFile(
                name = file.name,
                absolutePath = file.absolutePath,
                format = file.extension.lowercase(),
            )
        }
}

/** 外挂字幕的导入与删除。 */
@Singleton
class LocalSubtitleStore @Inject constructor(
    private val storage: PrivateStorage,
    private val safDocuments: SafDocuments,
) : SubtitleStore {

    override fun list(mediaId: Long): List<SubtitleFile> =
        storage.listSubtitles(mediaId).map { file ->
            SubtitleFile(
                name = file.name,
                absolutePath = file.absolutePath,
                format = file.extension.lowercase(),
            )
        }

    override suspend fun import(mediaId: Long, uri: String): OperationOutcome {
        val parsed = Uri.parse(uri)
        val info = safDocuments.query(parsed) ?: return OperationOutcome.Failure("无法读取字幕文件信息")
        val extension = storage.extensionOfMedia(info.displayName)
            ?: return OperationOutcome.Failure("无法识别字幕格式（支持 srt / ass / ssa / vtt）")
        if (extension !in PrivateStorage.SUBTITLE_EXTENSIONS) {
            return OperationOutcome.Failure("不支持的字幕格式：$extension")
        }
        return runCatching {
            safDocuments.openInput(parsed).use { input ->
                storage.saveSubtitle(mediaId, info.displayName, input)
            }
            OperationOutcome.Success
        }.getOrElse { error ->
            OperationOutcome.Failure("字幕导入失败：${error.message ?: "未知错误"}")
        }
    }

    override suspend fun delete(absolutePath: String): OperationOutcome =
        if (storage.deleteSubtitle(absolutePath)) {
            OperationOutcome.Success
        } else {
            OperationOutcome.Failure("字幕文件删除失败")
        }
}
