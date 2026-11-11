package com.privateplayer.core.storage

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从媒体文件提取元数据。
 *
 * 只依赖 Android 平台 API（`MediaMetadataRetriever` / `MediaExtractor`），
 * 不解析容器格式本身；播放引擎接入后仍以这里的结果作为“导入时快照”，
 * 运行时的真实轨道信息由播放引擎提供。
 */
@Singleton
class MediaProbe @Inject constructor() {

    data class ProbedMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val videoCodec: String?,
        val audioCodec: String?,
        val bitrate: Long?,
        val audioTrackCount: Int,
        val subtitleTrackCount: Int,
        val mimeType: String?,
    )

    /** 探测失败不阻止导入：缺省值保证记录可用，并在字段上体现“未知”。 */
    fun probe(file: File, declaredMimeType: String?): ProbedMetadata {
        var durationMs = 0L
        var width = 0
        var height = 0
        var bitrate: Long? = null
        var mimeType = declaredMimeType

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val swap = width
                width = height
                height = swap
            }
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: declaredMimeType
        } catch (error: Exception) {
            // 平台解析器不支持该容器：保留调用方传入的 MIME，其余字段置未知。
            mimeType = declaredMimeType
        } finally {
            runCatching { retriever.release() }
        }

        var videoCodec: String? = null
        var audioCodec: String? = null
        var audioTracks = 0
        var subtitleTracks = 0

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") -> {
                        if (videoCodec == null) videoCodec = friendlyCodec(mime)
                        if (width <= 0 || height <= 0) {
                            width = format.intOrZero(MediaFormat.KEY_WIDTH)
                            height = format.intOrZero(MediaFormat.KEY_HEIGHT)
                        }
                        if (durationMs <= 0L) {
                            val trackDurationUs = format.longOrZero(MediaFormat.KEY_DURATION)
                            if (trackDurationUs > 0) durationMs = trackDurationUs / 1000
                        }
                    }

                    mime.startsWith("audio/") -> {
                        audioTracks++
                        if (audioCodec == null) audioCodec = friendlyCodec(mime)
                    }

                    mime.startsWith("text/") || mime.contains("subrip") || mime.contains("cea") -> {
                        subtitleTracks++
                    }
                }
            }
        } catch (error: Exception) {
            // 轨道枚举失败时保留已拿到的信息。
        } finally {
            runCatching { extractor.release() }
        }

        return ProbedMetadata(
            durationMs = durationMs,
            width = width,
            height = height,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            bitrate = bitrate,
            audioTrackCount = audioTracks,
            subtitleTrackCount = subtitleTracks,
            mimeType = mimeType,
        )
    }

    /**
     * 生成缩略图。默认取 15% 处的关键帧，避免片头黑帧。
     * 输出为 JPEG（最长边 720px），写入 `cache/thumbnails`。
     */
    fun generateThumbnail(video: File, output: File, positionRatio: Float = 0.15f): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val targetUs = (durationMs * 1000L * positionRatio.coerceIn(0f, 0.9f)).toLong()

            val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_MAX_WIDTH,
                    THUMBNAIL_MAX_HEIGHT,
                )
            } else {
                retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frame == null) return false

            val scaled = scaleDown(frame)
            output.parentFile?.mkdirs()
            output.outputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.flush()
            }
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
            output.isFile && output.length() > 0L
        } catch (error: Exception) {
            output.delete()
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= THUMBNAIL_MAX_WIDTH) return source
        val ratio = THUMBNAIL_MAX_WIDTH.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * 重复检测指纹：文件大小 + 首 256 KiB 的 SHA-256。
     * 大文件不整文件哈希（几十 GB 视频整文件哈希会拖慢导入），
     * 头部指纹配合大小足以识别复制同一文件的常见场景。
     */
    @Throws(IOException::class)
    fun fingerprint(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(FINGERPRINT_HEAD_BYTES)
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count <= 0) break
                read += count
            }
            digest.update(buffer, 0, read)
        }
        return "${file.length()}:${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0

    private fun MediaFormat.longOrZero(key: String): Long =
        if (containsKey(key)) getLong(key) else 0L

    companion object {
        private const val THUMBNAIL_MAX_WIDTH = 720
        private const val THUMBNAIL_MAX_HEIGHT = 720
        private const val FINGERPRINT_HEAD_BYTES = 256 * 1024

        /** MIME 到用户可读编码名的映射。未知编码返回 MIME 末段大写形式。 */
        fun friendlyCodec(mime: String?): String? {
            if (mime.isNullOrBlank()) return null
            return when (mime.lowercase()) {
                "video/avc" -> "H.264"
                "video/hevc" -> "H.265"
                "video/av01" -> "AV1"
                "video/x-vnd.on2.vp9" -> "VP9"
                "video/x-vnd.on2.vp8" -> "VP8"
                "video/mp4v-es" -> "MPEG-4"
                "video/x-msvideo" -> "MPEG-4"
                "video/dolby-vision" -> "Dolby Vision"
                "audio/mp4a-latm" -> "AAC"
                "audio/opus" -> "Opus"
                "audio/vorbis" -> "Vorbis"
                "audio/ac3" -> "AC-3"
                "audio/eac3" -> "E-AC-3"
                "audio/flac" -> "FLAC"
                "audio/mpeg" -> "MP3"
                "audio/raw" -> "PCM"
                "audio/x-ms-wma" -> "WMA"
                "audio/alac" -> "ALAC"
                "audio/dts" -> "DTS"
                "application/x-subrip" -> "SubRip"
                else -> mime.substringAfter('/').uppercase()
            }
        }
    }
}
