package com.privateplayer.media

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.core.playback.PlaybackSource
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.mpv.MpvPlaybackEngine
import com.privateplayer.core.storage.LocalSubtitleStore
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.core.storage.StorageMediaFileLocator
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.MediaRepositoryImpl
import com.privateplayer.data.repository.PlaybackHistoryRepositoryImpl
import com.privateplayer.domain.model.AppSettings
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.usecase.SavePlaybackProgressUseCase
import com.privateplayer.feature.player.PlaybackCoordinator
import com.privateplayer.feature.player.PlaybackServiceStarter
import com.privateplayer.feature.player.PlayerRouteArgs
import com.privateplayer.feature.player.PlayerUiState
import com.privateplayer.feature.player.PlayerViewModel
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 libmpv、Surface 和私有文件验证解码与轨道，不以模拟进度代替画面。 */
@RunWith(AndroidJUnit4::class)
class MpvPlaybackInstrumentedTest {
    @Test
    fun playsPrivateUnicodeFileRendersFramesAndSeeks() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(mediaDir, "回归🎬-${UUID.randomUUID()}.mkv")
        instrumentation.context.assets.open("playback-regression.mkv").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val surfaceReady = CountDownLatch(1)
        lateinit var view: SurfaceView
        scenario.onActivity { activity ->
            view = SurfaceView(activity)
            view.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    if (width > 0 && height > 0) surfaceReady.countDown()
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
            activity.setContentView(view)
        }
        val engine = MpvPlaybackEngine(context)
        try {
            assertTrue("Surface 未创建", surfaceReady.await(15, TimeUnit.SECONDS))
            engine.initialize()
            engine.attachSurface(view.holder.surface)
            engine.resizeSurface(view.holder.surfaceFrame.width(), view.holder.surfaceFrame.height())
            engine.load(
                PlaybackSource(
                    mediaId = -1L,
                    title = file.name,
                    absolutePath = file.absolutePath,
                    durationHintMs = 15_000L,
                ),
                startPositionMs = 0L,
                autoPlay = true,
            )

            val started = withTimeout(30_000) {
                engine.snapshot.first { it.status == PlaybackStatus.PLAYING || it.status == PlaybackStatus.ERROR }
            }
            assertEquals(started.errorMessage, PlaybackStatus.PLAYING, started.status)
            assertTrue("不是实际 libmpv", engine.diagnostics().engineVersion?.contains("mpv") == true)

            val audio = withTimeout(15_000) { engine.audioTracks.first { it.size == 2 } }
            val subtitles = withTimeout(15_000) { engine.subtitleTracks.first { it.isNotEmpty() } }
            engine.selectAudioTrack(audio[1].id)
            withTimeout(10_000) { engine.audioTracks.first { tracks -> tracks.any { it.id == audio[1].id && it.isDefault } } }
            engine.selectSubtitleTrack(subtitles[0].id)
            withTimeout(10_000) { engine.subtitleTracks.first { tracks -> tracks.any { it.id == subtitles[0].id && it.isDefault } } }

            val rendered = withTimeoutOrNull(25_000) {
                while (!containsVideoFrame(view)) delay(250)
                true
            } ?: false
            assertTrue("Surface 未输出真实视频帧", rendered)

            engine.pause()
            engine.seekTo(6_000L)
            val sought = withTimeout(15_000) {
                engine.snapshot.first { it.status == PlaybackStatus.PAUSED && it.positionMs in 5_500L..6_500L }
            }
            assertTrue("媒体时长不正确：${sought.durationMs}", sought.durationMs in 14_000L..16_000L)
        } finally {
            engine.attachSurface(null)
            engine.release()
            scenario.close()
            file.delete()
        }
    }

    @Test
    fun temporarySpeedChangesRemainResponsiveAndVideoKeepsRendering() = runBlocking {
        withRealPlayer { player ->
            val model = player.model
            awaitPlayerState(model) { it.playback.status == PlaybackStatus.PLAYING && it.subtitleTracks.isNotEmpty() }
            assertTrue("不是实际 libmpv", player.engine.diagnostics().engineVersion?.contains("mpv") == true)
            onMain { model.selectSubtitleTrack(null) }
            awaitPlayerState(model) { it.selectedSubtitleTrackId == null }
            awaitVideoFrame(player.surface)

            onMainPromptly("设为正常速度") { model.setSpeed(1f) }
            awaitPlayerState(model) { it.playback.status == PlaybackStatus.PLAYING && abs(it.playback.speed - 1f) < 0.01f }
            onMain { model.seekTo(1_000L) }
            awaitPlayerState(model) { it.playback.status == PlaybackStatus.PLAYING && it.playback.positionMs in 800L..2_000L }

            val beforeFast = awaitVideoFrame(player.surface)
            val fastPosition = model.uiState.value.playback.positionMs
            onMainPromptly("开始临时倍速") { model.beginTemporarySpeed(2f) }
            onMainPromptly("加速期间响应主线程") {}
            awaitPlayerState(model) {
                it.playback.status == PlaybackStatus.PLAYING && abs(it.playback.speed - 2f) < 0.01f &&
                    it.playback.positionMs >= fastPosition + 500L
            }
            val fastFrame = awaitChangedVideoFrame(player.surface, beforeFast)

            val normalPosition = model.uiState.value.playback.positionMs
            onMainPromptly("恢复正常速度") { model.endTemporarySpeed() }
            onMainPromptly("恢复未确认时再次长按") {
                model.beginTemporarySpeed(3f)
                model.endTemporarySpeed()
            }
            onMainPromptly("恢复期间响应主线程") {}
            awaitPlayerState(model) {
                it.playback.status == PlaybackStatus.PLAYING && abs(it.playback.speed - 1f) < 0.01f &&
                    it.playback.positionMs >= normalPosition + 500L
            }
            awaitChangedVideoFrame(player.surface, fastFrame)
        }
    }

    @Test
    fun replacementOwnersKeepOneEngineAndIgnoreStaleSurfaceDestruction() = runBlocking {
        withRealPlayer { player ->
            awaitPlayerState(player.model) { it.playback.status == PlaybackStatus.PLAYING }
            val before = awaitVideoFrame(player.surface)
            onMain {
                val oldOwner = Any()
                val newOwner = Any()
                val oldLease = player.coordinator.acquire(player.serviceOwner)
                val newServiceOwner = Any()
                val newLease = player.coordinator.acquire(newServiceOwner)
                assertSame(oldLease.engine, newLease.engine)
                player.coordinator.claim(newServiceOwner)
                player.coordinator.release(player.serviceOwner)
                player.coordinator.acquire(player.serviceOwner)
                player.coordinator.claim(player.serviceOwner)

                val surface = player.surface.holder.surface
                player.model.activateSurfaceOwner(oldOwner)
                player.model.attachSurface(oldOwner, surface)
                player.model.activateSurfaceOwner(newOwner)
                player.model.attachSurface(newOwner, surface)
                player.model.resizeSurface(newOwner, surface, 320, 320)
                player.model.detachSurface(oldOwner, surface)
                player.model.detachSurface(oldOwner)
            }
            awaitPlayerState(player.model) { it.playback.status == PlaybackStatus.PLAYING }
            awaitChangedVideoFrame(player.surface, before)
        }
    }

    @Test
    fun siblingSubtitleIsDiscoveredAndRendersInsideLetterboxedVideo() = runBlocking {
        withRealPlayer { player ->
            val matches = player.tree.matchingSubtitles(player.videoPath)
            assertEquals("只应匹配同目录、同基名的字幕", listOf(player.subtitle.canonicalPath), matches)
            val model = player.model
            val started = awaitPlayerState(model, "同目录字幕发现、加载及真实播放") {
                it.playback.status == PlaybackStatus.PLAYING &&
                    it.subtitleFileNames.contains(player.subtitle.name) &&
                    it.subtitleTracks.any { track -> track.externalFileName == player.subtitle.name }
            }
            val subtitleTrack = started.subtitleTracks.single { it.externalFileName == player.subtitle.name }
            val subtitleId = subtitleTrack.id
            assertTrue("外挂 SRT 未解析成真实文本轨道：$subtitleTrack", subtitleTrack.codec in setOf("subrip", "srt"))
            assertTrue("测试必须能区分内嵌与外挂字幕", started.subtitleTracks.any { it.externalFileName == null })
            assertEquals("必须验证用户默认字幕样式", SubtitlePreferences(), started.subtitlePrefs)
            val info = withContext(Dispatchers.IO) { requireNotNull(player.engine.mediaInfo()) }
            assertEquals(320, info.width)
            assertEquals(180, info.height)
            onMain { model.setAspect(VideoAspect.FIT) }

            onMain { model.playPause() }
            awaitPlayerState(model, "暂停字幕回归视频") { it.playback.status == PlaybackStatus.PAUSED }
            onMain { model.seekTo(4_000L) }
            awaitPlayerState(model, "暂停时跳转至字幕时间段") { it.playback.status == PlaybackStatus.PAUSED && it.playback.positionMs in 3_800L..4_200L }
            delay(750)

            onMain { model.selectSubtitleTrack(subtitleId) }
            awaitPlayerState(model, "选中真实同目录外挂轨道") { it.selectedSubtitleTrackId == subtitleId }
            onMain { model.selectSubtitleTrack(null) }
            awaitPlayerState(model, "关闭字幕取得无字画面") { it.selectedSubtitleTrackId == null }
            delay(400)
            val baseline = awaitVideoFrame(player.surface)
            delay(300)
            val stable = awaitVideoFrame(player.surface)
            assertTrue("暂停后的画面不稳定，无法进行字幕差分", changedPixels(baseline, stable) < 20)
            assertLetterboxIsBlack(stable)

            onMain { model.selectSubtitleTrack(subtitleId) }
            awaitPlayerState(model, "再次选中真实同目录外挂轨道") { it.selectedSubtitleTrackId == subtitleId }
            val subtitled = awaitSubtitleFrame(player.surface, stable)
            val inside = changedPixels(stable, subtitled, FRAME_SIZE / 2, VIDEO_BOTTOM)
            val upperMargin = changedPixels(stable, subtitled, 0, VIDEO_TOP - 6)
            val upperVideo = changedPixels(stable, subtitled, VIDEO_TOP, FRAME_SIZE / 2)
            val lowerMargin = changedPixels(stable, subtitled, VIDEO_BOTTOM + 6, FRAME_SIZE)
            assertTrue("视频下侧未观察到外挂字幕像素：$inside", inside >= 20)
            assertTrue("普通文本字幕没有保持在画面下侧：$upperVideo/$inside", upperVideo <= maxOf(4, inside / 30))
            assertTrue("字幕绘制到了视频上方的黑边：$upperMargin", upperMargin <= maxOf(4, inside / 30))
            assertTrue("字幕绘制到了视频下方的黑边：$lowerMargin", lowerMargin <= maxOf(4, inside / 30))
        }
    }

    private data class RealPlayer(
        val model: PlayerViewModel,
        val engine: MpvPlaybackEngine,
        val surface: SurfaceView,
        val tree: FileTreeRepositoryImpl,
        val videoPath: String,
        val subtitle: File,
        val coordinator: PlaybackCoordinator,
        val serviceOwner: Any,
    )

    /** 文件树、数据库、视图模型和解码器都是真实实现；数据限定在独立临时目录及内存数据库。 */
    private suspend fun withRealPlayer(block: suspend (RealPlayer) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val mediaRoot = File(context.filesDir, "media")
        check(!Files.isSymbolicLink(mediaRoot.toPath())) { "媒体根目录不能是符号链接" }
        check(mediaRoot.isDirectory || mediaRoot.mkdirs()) { "无法创建媒体根目录" }
        val sandbox = Files.createTempDirectory(mediaRoot.toPath(), "播放器回归-").toFile()
        val sandboxContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = sandbox
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
        }
        val engine = MpvPlaybackEngine(context)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var database: PrivatePlayerDatabase? = null
        var scenario: ActivityScenario<ComponentActivity>? = null
        var viewModelStore: ViewModelStore? = null
        var coordinator: PlaybackCoordinator? = null
        val serviceOwner = Any()
        try {
            val storage = PrivateStorage(sandboxContext)
            check(storage.ensureLayout()) { "无法创建测试专用存储" }
            val directory = File(storage.mediaRoot, "目录树/子目录").apply { check(mkdirs()) }
            val video = File(directory, "片名🎬.mkv")
            instrumentation.context.assets.open("playback-regression.mkv").use { input ->
                video.outputStream().use { output -> input.copyTo(output) }
            }
            val subtitle = File(directory, "片名🎬.zh.srt").apply {
                writeText("1\n00:00:00,000 --> 00:00:15,000\nEXTERNAL SUBTITLE TEST 123456\nSECOND LINE FOR PIXEL CHECK\n", Charsets.UTF_8)
            }
            File(directory, "片名🎬2.srt").writeText("不匹配", Charsets.UTF_8)
            File(directory, "下层").apply { check(mkdirs()) }
                .resolve("片名🎬.en.srt").writeText("不同目录", Charsets.UTF_8)

            val db = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
            database = db
            val tree = FileTreeRepositoryImpl(
                db, db.mediaDao(), db.playbackHistoryDao(), db.playlistDao(),
                storage, MediaProbe(), Dispatchers.IO,
            )
            val mediaRepository = MediaRepositoryImpl(
                db.mediaDao(), db.folderDao(), storage, tree, Dispatchers.IO,
            )
            val history = PlaybackHistoryRepositoryImpl(db.playbackHistoryDao(), Dispatchers.IO)
            // 仅为测试提供固定的只读设置，不读取或修改用户设置。
            val settings = object : SettingsRepository by PreferencesSettingsRepository(context) {
                override fun observeSettings() = flowOf(AppSettings())
            }
            val provider = object : PlaybackEngineProvider {
                override val backendLabel: String = engine.backendLabel
                override val backendDetail: String = "真实 libmpv 测试实例"
                override fun create(): PlaybackEngine = engine
            }
            val videoPath = "目录树/子目录/${video.name}"
            val mediaId = tree.ensureMedia(videoPath)
            val playback = PlaybackCoordinator(
                sandboxContext,
                mediaRepository,
                tree,
                StorageMediaFileLocator(storage),
                LocalSubtitleStore(storage, SafDocuments(context)),
                settings,
                provider,
                SavePlaybackProgressUseCase(mediaRepository, history, settings),
                PlaybackServiceStarter { null },
                applicationScope,
                Dispatchers.IO,
            )
            coordinator = playback
            val surfaceOwner = Any()

            // 调试测试清单注册的空白宿主不会启动首页或访问用户文件树。
            val activityScenario = ActivityScenario.launch(ComponentActivity::class.java)
            scenario = activityScenario
            val surfaceReady = CountDownLatch(1)
            lateinit var surface: SurfaceView
            activityScenario.onActivity { activity ->
                surface = SurfaceView(activity)
                surface.holder.setFixedSize(320, 320)
                surface.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (width == 320 && height == 320) surfaceReady.countDown()
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
                activity.setContentView(surface)
            }
            assertTrue("测试 Surface 未创建", surfaceReady.await(15, TimeUnit.SECONDS))

            val store = ViewModelStore()
            viewModelStore = store
            lateinit var model: PlayerViewModel
            activityScenario.onActivity {
                playback.acquire(serviceOwner)
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        require(modelClass == PlayerViewModel::class.java)
                        return modelClass.cast(
                            PlayerViewModel(
                                SavedStateHandle(mapOf(PlayerRouteArgs.ARG_MEDIA_ID to mediaId.toString())),
                                playback,
                            ),
                        )!!
                    }
                }
                model = ViewModelProvider(store, factory)[PlayerViewModel::class.java]
                model.activateSurfaceOwner(surfaceOwner)
                model.attachSurface(surfaceOwner, surface.holder.surface)
                model.resizeSurface(surfaceOwner, surface.holder.surface, surface.holder.surfaceFrame.width(), surface.holder.surfaceFrame.height())
                model.onStart()
            }
            block(RealPlayer(model, engine, surface, tree, videoPath, subtitle, playback, serviceOwner))
        } finally {
            try {
                viewModelStore?.let { store -> instrumentation.runOnMainSync { store.clear() } }
            } finally {
                try {
                    val owner = coordinator
                    if (owner != null) {
                        val released = AtomicReference<Deferred<Unit>?>()
                        instrumentation.runOnMainSync { released.set(owner.release(serviceOwner)) }
                        val cleanup = released.get()
                        if (cleanup != null) cleanup.await() else withContext(Dispatchers.IO) { engine.release() }
                    } else {
                        withContext(Dispatchers.IO) { engine.release() }
                    }
                    applicationScope.cancel()
                    applicationScope.coroutineContext[Job]?.children?.toList()?.joinAll()
                } finally {
                    try {
                        scenario?.close()
                    } finally {
                        try {
                            database?.close()
                        } finally {
                            sandbox.deleteRecursively()
                        }
                    }
                }
            }
        }
    }

    private suspend fun awaitPlayerState(
        model: PlayerViewModel,
        stage: String = "播放器状态",
        predicate: (PlayerUiState) -> Boolean,
    ): PlayerUiState {
        try {
            return withTimeout(12_000) {
                model.uiState.first { state ->
                    check(state.playback.status != PlaybackStatus.ERROR && state.error == null) {
                        "等待「$stage」时播放器出错：$state"
                    }
                    predicate(state)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("等待「$stage」超时；真实播放器状态：${model.uiState.value}", timeout)
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    /** 同时检查主线程排队和回调本身的耗时，避免把同步 JNI 阻塞误当成异步成功。 */
    private fun onMainPromptly(action: String, block: () -> Unit) {
        val done = CountDownLatch(1)
        val elapsed = AtomicLong()
        val failure = AtomicReference<Throwable?>()
        Handler(Looper.getMainLooper()).post {
            val start = SystemClock.elapsedRealtime()
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                elapsed.set(SystemClock.elapsedRealtime() - start)
                done.countDown()
            }
        }
        assertTrue("$action：主线程超过 2 秒未响应", done.await(2, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("$action 失败", it) }
        assertTrue("$action：主线程回调耗时 ${elapsed.get()}ms", elapsed.get() < 1_000L)
    }

    private suspend fun awaitVideoFrame(view: SurfaceView): IntArray = withTimeout(12_000) {
        var pixels: IntArray? = null
        while (pixels == null || pixels.count { (it and 0x00ff_ffff) != 0 } < 500) {
            pixels = copySurface(view)
            if (pixels == null || pixels.count { (it and 0x00ff_ffff) != 0 } < 500) delay(120)
        }
        requireNotNull(pixels)
    }

    private suspend fun awaitChangedVideoFrame(view: SurfaceView, before: IntArray): IntArray = withTimeout(4_000) {
        var pixels: IntArray? = null
        while (pixels == null || changedPixels(before, pixels) < 20) {
            delay(120)
            pixels = copySurface(view)
        }
        requireNotNull(pixels)
    }

    private suspend fun awaitSubtitleFrame(view: SurfaceView, before: IntArray): IntArray {
        var pixels: IntArray? = null
        try {
            return withTimeout(8_000) {
                while (pixels == null || changedPixels(before, requireNotNull(pixels), FRAME_SIZE / 2, VIDEO_BOTTOM) < 20) {
                    delay(120)
                    pixels = copySurface(view)
                }
                requireNotNull(pixels)
            }
        } catch (timeout: TimeoutCancellationException) {
            val actual = pixels?.let {
                "上黑边=${changedPixels(before, it, 0, VIDEO_TOP)}, " +
                    "上半视频=${changedPixels(before, it, VIDEO_TOP, FRAME_SIZE / 2)}, " +
                    "下半视频=${changedPixels(before, it, FRAME_SIZE / 2, VIDEO_BOTTOM)}, " +
                    "下黑边=${changedPixels(before, it, VIDEO_BOTTOM, FRAME_SIZE)}"
            } ?: "PixelCopy 未取得画面"
            throw AssertionError("选中外挂字幕后未在视频下侧观察到字形；实际差分：$actual", timeout)
        }
    }

    private fun copySurface(view: SurfaceView): IntArray? {
        val bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        val lock = Any()
        var abandoned = false
        var result = PixelCopy.ERROR_UNKNOWN
        try {
            PixelCopy.request(view, bitmap, { code ->
                synchronized(lock) {
                    result = code
                    if (abandoned) bitmap.recycle()
                    done.countDown()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            return null
        }
        if (!done.await(3, TimeUnit.SECONDS)) {
            synchronized(lock) {
                if (done.count == 0L) bitmap.recycle() else abandoned = true
            }
            return null
        }
        return try {
            if (result != PixelCopy.SUCCESS) null else IntArray(FRAME_SIZE * FRAME_SIZE).also {
                bitmap.getPixels(it, 0, FRAME_SIZE, 0, 0, FRAME_SIZE, FRAME_SIZE)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun changedPixels(before: IntArray, after: IntArray, top: Int = 0, bottom: Int = FRAME_SIZE): Int {
        var changed = 0
        for (index in top * FRAME_SIZE until bottom * FRAME_SIZE) {
            val a = before[index]
            val b = after[index]
            val difference = abs((a shr 16 and 255) - (b shr 16 and 255)) +
                abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))
            if (difference >= 60) changed++
        }
        return changed
    }

    private fun assertLetterboxIsBlack(pixels: IntArray) {
        var dark = 0
        var total = 0
        for (y in VIDEO_BOTTOM + 6 until FRAME_SIZE step 4) {
            for (x in 0 until FRAME_SIZE step 4) {
                val pixel = pixels[y * FRAME_SIZE + x]
                if ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255) < 30) dark++
                total++
            }
        }
        assertTrue("未形成可验证的下方黑边：$dark/$total", total > 0 && dark * 100 >= total * 98)
    }

    private companion object {
        const val FRAME_SIZE = 256
        // 16:9 视频适应正方形 Surface，画面占据中间 9/16 的高度。
        const val VIDEO_TOP = FRAME_SIZE * 7 / 32
        const val VIDEO_BOTTOM = FRAME_SIZE * 25 / 32
    }

    private fun containsVideoFrame(view: SurfaceView): Boolean {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
        val done = CountDownLatch(1)
        try {
            PixelCopy.request(view, bitmap, { code ->
                result.set(code)
                done.countDown()
            }, Handler(Looper.getMainLooper()))
            if (!done.await(3, TimeUnit.SECONDS) || result.get() != PixelCopy.SUCCESS) return false
            var colored = 0
            for (y in 0 until bitmap.height step 4) {
                for (x in 0 until bitmap.width step 4) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel and 0x00ff_ffff) != 0) colored++
                }
            }
            return colored > 10
        } finally {
            bitmap.recycle()
        }
    }
}
