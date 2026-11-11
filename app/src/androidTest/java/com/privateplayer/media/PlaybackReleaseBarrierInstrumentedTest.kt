package com.privateplayer.media

import android.content.ContextWrapper
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.core.playback.EngineMediaInfo
import com.privateplayer.core.playback.MediaTrack
import com.privateplayer.core.playback.PlaybackDiagnostics
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.core.playback.PlaybackEvent
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackSource
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.session.MpvMedia3Player
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
import com.privateplayer.feature.player.PlaybackLease
import com.privateplayer.feature.player.PlaybackServiceStarter
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 可控阻塞只用于证明销毁和初始化的时序；真实解码及系统服务另有仪器测试。 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PlaybackReleaseBarrierInstrumentedTest {
    @Test
    fun blockedOldLoadDoesNotBlockDestroyAndNewNativeWaitsForRelease() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val sandbox = Files.createTempDirectory(context.cacheDir.toPath(), "release-barrier-").toFile()
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = sandbox
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val oldOwner = Any()
        val newOwner = Any()
        val thirdOwner = Any()
        var coordinator: PlaybackCoordinator? = null
        var firstCleanup: Deferred<Unit>? = null
        var secondCleanup: Deferred<Unit>? = null
        var thirdCleanup: Deferred<Unit>? = null
        var bridge: MpvMedia3Player? = null
        var session: MediaSession? = null
        var controller: MediaController? = null
        val nativeCount = AtomicInteger()
        val maxNativeCount = AtomicInteger()
        val unblockOldLoad = CountDownLatch(1)
        val engines = mutableListOf<BlockingEngine>()
        try {
            val storage = PrivateStorage(isolated)
            check(storage.ensureLayout())
            File(storage.mediaRoot, "fixture.mkv").writeBytes(byteArrayOf(1, 2, 3, 4))
            val tree = FileTreeRepositoryImpl(
                database, database.mediaDao(), database.playbackHistoryDao(), database.playlistDao(),
                storage, MediaProbe(), Dispatchers.IO,
            )
            val repository = MediaRepositoryImpl(
                database.mediaDao(), database.folderDao(), storage, tree, Dispatchers.IO,
            )
            val history = PlaybackHistoryRepositoryImpl(database.playbackHistoryDao(), Dispatchers.IO)
            val settings = object : SettingsRepository by PreferencesSettingsRepository(context) {
                override fun observeSettings() = flowOf(AppSettings())
            }
            val provider = object : PlaybackEngineProvider {
                override val backendLabel = "受控测试引擎"
                override val backendDetail = "仅用于释放屏障时序测试"
                override fun create(): PlaybackEngine = BlockingEngine(
                    nativeCount, maxNativeCount, if (engines.isEmpty()) unblockOldLoad else null,
                ).also { engines.add(it) }
            }
            val mediaId = tree.ensureMedia("fixture.mkv")
            val playback = PlaybackCoordinator(
                isolated, repository, tree, StorageMediaFileLocator(storage),
                LocalSubtitleStore(storage, SafDocuments(context)), settings, provider,
                SavePlaybackProgressUseCase(repository, history, settings),
                PlaybackServiceStarter { null }, applicationScope, Dispatchers.IO,
            )
            coordinator = playback
            onMain {
                playback.acquire(oldOwner)
                // 使用暂停装载，不申请设备音频焦点。
                check(playback.openFromVisibleUi(mediaId, requestMedia = false))
            }
            val oldEngine = engines.single()
            assertTrue("旧引擎没有进入受控 load", oldEngine.loadEntered.await(15, TimeUnit.SECONDS))
            assertEquals(1, nativeCount.get())

            val start = SystemClock.elapsedRealtime()
            onMain { firstCleanup = playback.release(oldOwner) }
            assertTrue("服务销毁不应等待阻塞的 load/JNI", SystemClock.elapsedRealtime() - start < 1_000L)
            val oldRelease = requireNotNull(firstCleanup)
            assertFalse(oldRelease.isCompleted)

            val newLease = AtomicReference<PlaybackLease>()
            val connection = AtomicReference<com.google.common.util.concurrent.ListenableFuture<MediaController>>()
            onMain {
                val lease = playback.acquire(newOwner)
                newLease.set(lease)
                val adapter = MpvMedia3Player(
                    lease.engine, lease.media, lease.executeForMedia, lease.registerCommand, lease.isCommandCurrent,
                    lease.registerSpeedRequest, lease.applySpeedRequest, lease.discardSpeedRequest,
                )
                bridge = adapter
                val mediaSession = MediaSession.Builder(context, adapter).setId("barrier-${UUID.randomUUID()}").build()
                session = mediaSession
                connection.set(MediaController.Builder(context, mediaSession.token).buildAsync())
            }
            controller = connection.get().get(15, TimeUnit.SECONDS)
            val secondEngine = engines.last()
            val attempted = CountDownLatch(1)
            val newInitialization = async(Dispatchers.IO) {
                attempted.countDown()
                newLease.get().engine.initialize()
            }
            assertTrue(attempted.await(5, TimeUnit.SECONDS))
            assertFalse("旧 native 未释放前不能初始化新 native", secondEngine.initialized.await(500, TimeUnit.MILLISECONDS))
            assertEquals(1, nativeCount.get())

            // 新服务在等待屏障时也可能销毁；它的清理必须继续排在旧服务之后。
            newInitialization.cancelAndJoin()
            onMain {
                controller?.release()
                controller = null
                session?.release()
                session = null
                bridge?.release()
                bridge = null
                secondCleanup = playback.release(newOwner)
            }
            assertFalse(requireNotNull(secondCleanup).isCompleted)
            val thirdLease = AtomicReference<PlaybackLease>()
            onMain { thirdLease.set(playback.acquire(thirdOwner)) }
            val thirdEngine = engines.last()
            val thirdAttempted = CountDownLatch(1)
            val thirdInitialization = async(Dispatchers.IO) {
                thirdAttempted.countDown()
                thirdLease.get().engine.initialize()
            }
            assertTrue(thirdAttempted.await(5, TimeUnit.SECONDS))
            assertFalse(thirdEngine.initialized.await(500, TimeUnit.MILLISECONDS))

            unblockOldLoad.countDown()
            withTimeout(15_000) { oldRelease.await() }
            withTimeout(15_000) { requireNotNull(secondCleanup).await() }
            withTimeout(15_000) { thirdInitialization.await() }
            assertEquals("等待时已销毁的服务不能初始化 native", 1L, secondEngine.initialized.count)
            assertTrue(thirdEngine.initialized.await(5, TimeUnit.SECONDS))
            assertEquals(1, nativeCount.get())
            assertEquals("任何时刻只能有一个有效 native 实例", 1, maxNativeCount.get())

            onMain { thirdCleanup = playback.release(thirdOwner) }
            withTimeout(15_000) { requireNotNull(thirdCleanup).await() }
            assertEquals(0, nativeCount.get())
        } finally {
            unblockOldLoad.countDown()
            onMain {
                controller?.release()
                session?.release()
                bridge?.release()
                coordinator?.release(oldOwner)?.let { firstCleanup = it }
                coordinator?.release(newOwner)?.let { secondCleanup = it }
                coordinator?.release(thirdOwner)?.let { thirdCleanup = it }
            }
            firstCleanup?.await()
            secondCleanup?.await()
            thirdCleanup?.await()
            applicationScope.cancel()
            database.close()
            sandbox.deleteRecursively()
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    private class BlockingEngine(
        private val active: AtomicInteger,
        private val maximum: AtomicInteger,
        private val unblockLoad: CountDownLatch?,
    ) : PlaybackEngine {
        val initialized = CountDownLatch(1)
        val loadEntered = CountDownLatch(1)
        private val live = AtomicBoolean(false)
        override val snapshot = MutableStateFlow(PlaybackSnapshot())
        override val audioTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        override val subtitleTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        override val events = emptyFlow<PlaybackEvent>()
        override val backendLabel = "受控测试引擎"

        override suspend fun initialize() {
            check(live.compareAndSet(false, true))
            val count = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, count) }
            initialized.countDown()
        }

        override suspend fun load(source: PlaybackSource, startPositionMs: Long, autoPlay: Boolean) {
            snapshot.value = PlaybackSnapshot(status = PlaybackStatus.LOADING, mediaId = source.mediaId, playWhenReady = autoPlay)
            loadEntered.countDown()
            if (unblockLoad != null) check(unblockLoad.await(30, TimeUnit.SECONDS))
        }

        override suspend fun release() {
            if (live.compareAndSet(true, false)) active.decrementAndGet()
        }

        override fun attachSurface(surface: Surface?) = Unit
        override fun resizeSurface(width: Int, height: Int) = Unit
        override fun diagnostics() = PlaybackDiagnostics()
        override fun play() = Unit
        override fun pause() = Unit
        override suspend fun stop() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun selectAudioTrack(trackId: String?) = Unit
        override fun selectSubtitleTrack(trackId: String?) = Unit
        override fun setSubtitleDelay(delayMs: Long) = Unit
        override fun setSubtitleStyle(preferences: SubtitlePreferences) = Unit
        override fun setAspect(aspect: VideoAspect) = Unit
        override suspend fun mediaInfo(): EngineMediaInfo? = null
    }
}
