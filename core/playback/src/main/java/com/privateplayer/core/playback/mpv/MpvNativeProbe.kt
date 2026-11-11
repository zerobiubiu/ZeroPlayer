package com.privateplayer.core.playback.mpv

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 探测随 APK 提供的 JNI 库是否能被系统加载。
 *
 * 库加载成功不代表 libmpv 初始化、画面输出或硬件解码已经验证。
 */
@Singleton
class MpvNativeProbe @Inject constructor() {
    data class Result(
        val available: Boolean,
        val libraryName: String?,
        val clientApiVersion: Long?,
        val detail: String,
    )

    fun probe(): Result = try {
        // 库可能直接从 APK 加载，不能仅凭 nativeLibraryDir 里的文件判断是否打包。
        System.loadLibrary("player")
        Result(
            available = true,
            libraryName = "libplayer.so",
            clientApiVersion = null,
            detail = "JNI 库已加载；仍需验证 libmpv 初始化、播放及硬件解码",
        )
    } catch (error: LinkageError) {
        Result(
            available = false,
            libraryName = "libplayer.so",
            clientApiVersion = null,
            detail = "JNI 库未打包或加载失败：${error.message ?: error.javaClass.simpleName}",
        )
    }
}

/**
 * 硬件解码能力探测：为 libmpv 的 `hwdec=mediacodec` 方案提供设备侧证据。
 */
@Singleton
class CodecCapabilityProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class DecoderInfo(
        val name: String,
        val mimeType: String,
        val hardwareAccelerated: Boolean?,
        val softwareOnly: Boolean?,
        val maxWidth: Int,
        val maxHeight: Int,
        val supported: Boolean,
    )

    private val videoMimes = listOf(
        "video/avc",
        "video/hevc",
        "video/av01",
        "video/x-vnd.on2.vp9",
    )

    fun probeVideoDecoders(): List<DecoderInfo> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val result = mutableListOf<DecoderInfo>()
        list.codecInfos.filter { !it.isEncoder }.forEach { info ->
            val mime = info.supportedTypes.firstOrNull { it in videoMimes } ?: return@forEach
            val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
            val videoCaps = caps?.videoCapabilities
            result += DecoderInfo(
                name = info.name,
                mimeType = mime,
                hardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    null
                },
                softwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isSoftwareOnly
                } else {
                    null
                },
                maxWidth = videoCaps?.supportedWidths?.upper ?: 0,
                maxHeight = videoCaps?.supportedHeights?.upper ?: 0,
                supported = caps != null && caps.isFormatSupported(
                    android.media.MediaFormat.createVideoFormat(
                        mime,
                        videoCaps?.supportedWidths?.lower?.coerceAtLeast(2) ?: 1280,
                        videoCaps?.supportedHeights?.lower?.coerceAtLeast(2) ?: 720,
                    ),
                ),
            )
        }
        return result.sortedWith(compareBy({ it.mimeType }, { it.name }))
    }

    /** 设备是否具备指定编码的硬解能力（API 29+ 可精确判断）。 */
    fun hasHardwareDecoder(mime: String): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return probeVideoDecoders().any { it.mimeType == mime && it.hardwareAccelerated == true }
    }

    fun hardwareSummary(): String {
        val probes = probeVideoDecoders()
        if (probes.isEmpty()) return "未发现视频解码器"
        val hardware = probes.filter { it.hardwareAccelerated == true }.map { it.mimeType }.distinct()
        return if (hardware.isEmpty()) {
            "未检测到硬件解码器（共 ${probes.size} 个解码器）"
        } else {
            "硬件解码支持：${hardware.joinToString("、")}"
        }
    }
}
