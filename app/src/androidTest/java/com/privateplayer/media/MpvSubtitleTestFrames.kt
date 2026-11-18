package com.privateplayer.media

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.playback.PlaybackSource
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.mpv.MpvPlaybackEngine
import com.privateplayer.domain.model.SubtitlePreferences
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 固定同一暂停帧、同一块 Surface，只从视频输出缓冲取像素，不截页面控件。 */
internal class MpvSubtitleTestFrames(
    val engine: MpvPlaybackEngine,
    private val surface: SurfaceView,
    private val bitmap: Bitmap,
    val srt: String,
    val ass: String,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val previous = IntArray(WIDTH * HEIGHT)

    suspend fun select(id: String) {
        engine.selectSubtitleTrack(id)
        withTimeout(15_000) { engine.subtitleTracks.first { tracks -> tracks.any { it.id == id && it.isDefault } } }
    }

    suspend fun frame(description: String, matches: (IntArray) -> Boolean): IntArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        val deadline = SystemClock.elapsedRealtime() + 12_000
        var matched = false
        do {
            val state = engine.snapshot.value
            check(state.status != PlaybackStatus.ERROR) { "$description：${state.errorMessage}" }
            val result = suspendCoroutine<Int> { reply ->
                PixelCopy.request(surface.holder.surface, bitmap, { reply.resume(it) }, handler)
            }
            when (result) {
                PixelCopy.SUCCESS -> {
                    bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                    if (matches(pixels)) {
                        if (matched && difference(pixels, previous) <= 4) return pixels
                        pixels.copyInto(previous)
                        matched = true
                    } else matched = false
                }
                PixelCopy.ERROR_SOURCE_NO_DATA, PixelCopy.ERROR_TIMEOUT -> matched = false
                else -> throw AssertionError("$description：PixelCopy 错误 $result")
            }
            delay(80)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("未在真实 Surface 的连续两帧观察到：$description；${engine.snapshot.value}")
    }

    companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val HIDDEN_DELAY_MS = 60_000L

        fun near(a: Int, b: Int, tolerance: Int): Boolean =
            abs((a shr 16 and 255) - (b shr 16 and 255)) <= tolerance &&
                abs((a shr 8 and 255) - (b shr 8 and 255)) <= tolerance && abs((a and 255) - (b and 255)) <= tolerance

        fun difference(a: IntArray, b: IntArray): Int = a.indices.count { !near(a[it], b[it], 16) }

        fun brightness(color: Int) = (color shr 16 and 255) + (color shr 8 and 255) + (color and 255)

        /** 只计相对于无字帧新增的目标颜色，视频彩条本身不能冒充字幕。 */
        fun ink(clean: IntArray, frame: IntArray, color: Int): Ink {
            var count = 0
            var left = WIDTH
            var right = -1
            var top = HEIGHT
            var bottom = -1
            for (i in frame.indices) {
                if (!near(frame[i], color, 32) || near(frame[i], clean[i], 32)) continue
                count++
                val x = i % WIDTH
                val y = i / WIDTH
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
            return Ink(count, left, top, right - left + 1, bottom - top + 1)
        }
    }

    data class Ink(val count: Int, val left: Int, val top: Int, val width: Int, val height: Int)
}

internal suspend fun withMpvSubtitleFrame(
    initial: SubtitlePreferences,
    block: suspend MpvSubtitleTestFrames.(clean: IntArray) -> Unit,
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext.applicationContext
    val mediaRoot = File(context.filesDir, "media")
    check(!Files.isSymbolicLink(mediaRoot.toPath()) && (mediaRoot.isDirectory || mediaRoot.mkdirs()))
    val dir = Files.createTempDirectory(mediaRoot.toPath(), "字幕渲染-").toFile()
    val video = File(dir, "playback-regression.mkv")
    val script = File(dir, "authored-layout.ass")
    try {
        for (file in listOf(video, script)) {
            instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        ActivityScenario.launch(RotationTestActivity::class.java).use { scenario ->
            val ready = CountDownLatch(1)
            lateinit var view: SurfaceView
            scenario.onActivity { host ->
                view = SurfaceView(host)
                view.holder.setFixedSize(MpvSubtitleTestFrames.WIDTH, MpvSubtitleTestFrames.HEIGHT)
                view.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (width == MpvSubtitleTestFrames.WIDTH && height == MpvSubtitleTestFrames.HEIGHT) ready.countDown()
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
                host.setContentView(view)
            }
            assertTrue("测试 Surface 未就绪", ready.await(15, TimeUnit.SECONDS))
            val bitmap = Bitmap.createBitmap(MpvSubtitleTestFrames.WIDTH, MpvSubtitleTestFrames.HEIGHT, Bitmap.Config.ARGB_8888)
            val engine = MpvPlaybackEngine(context)
            try {
                engine.setSubtitleStyle(initial)
                // 初始化前便把两个轨道的首条字幕推到片尾之后，基线不可能截到旧字幕。
                engine.setSubtitleDelay(MpvSubtitleTestFrames.HIDDEN_DELAY_MS)
                engine.setVolume(0f)
                engine.initialize()
                engine.attachSurface(view.holder.surface)
                engine.resizeSurface(MpvSubtitleTestFrames.WIDTH, MpvSubtitleTestFrames.HEIGHT)
                engine.load(PlaybackSource(-1L, video.name, video.path, 16_000L, listOf(script.path)), 4_000L, false)
                val state = withTimeout(35_000) {
                    engine.snapshot.first { it.status == PlaybackStatus.ERROR || (it.status == PlaybackStatus.PAUSED && it.positionMs in 3_800L..4_200L) }
                }
                assertEquals(state.errorMessage, PlaybackStatus.PAUSED, state.status)
                assertTrue("非随包 mpv 0.41", engine.diagnostics().engineVersion?.contains("0.41.0") == true)
                val tracks = withTimeout(15_000) { engine.subtitleTracks.first { list -> list.any { it.externalFileName == script.name } } }
                val srt = tracks.single { it.externalFileName == null }
                val ass = tracks.single { it.externalFileName == script.name }
                assertTrue("不是内嵌 SRT：$srt", srt.codec == "subrip" || srt.codec == "srt")
                assertTrue("不是原生 ASS：$ass", ass.codec == "ass" || ass.codec == "ssa")
                val probe = MpvSubtitleTestFrames(engine, view, bitmap, srt.id, ass.id)
                val clean = probe.frame("解码出的无字幕彩色测试帧") { frame ->
                    frame.count { (it shr 16 and 255) - (it and 255) > 80 } > 500 &&
                        frame.count { (it and 255) - (it shr 16 and 255) > 80 } > 500
                }
                probe.block(clean)
            } finally {
                try { engine.release() } finally { bitmap.recycle() }
            }
        }
    } finally {
        script.delete()
        video.delete()
        dir.delete()
    }
}
