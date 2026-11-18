package com.privateplayer.media

import android.content.ContextWrapper
import android.view.Surface
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
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
import com.privateplayer.core.storage.LocalSubtitleStore
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.core.storage.StorageMediaFileLocator
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.MediaRepositoryImpl
import com.privateplayer.data.repository.PlaybackHistoryRepositoryImpl
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.usecase.SavePlaybackProgressUseCase
import com.privateplayer.feature.player.PlaybackCoordinator
import com.privateplayer.feature.player.PlaybackServiceStarter
import com.privateplayer.feature.player.PlayerRouteArgs
import com.privateplayer.feature.player.PlayerUiState
import com.privateplayer.feature.player.PlayerViewModel
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

/** 真实播放会话、私有文件树、Room 和 DataStore；引擎只记录实际收到的命令，不验证渲染。 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionPreferencesInstrumentedTest {
    @Test
    fun rapidDelayEditsSurviveQueueSwitchAndImmediateSessionRecreation() = withFixture {
        database.mediaDao().setSubtitleDelayMs(firstId, 150L)
        val model = host.open(firstId)
        val first = model.loaded(firstId)
        assertEquals(150L, first.subtitleDelayMs)
        assertEquals(150L, host.engine.delayAtLoad)
        val secondIndex = first.queue.indexOfFirst { it.mediaId == secondId }
        check(secondIndex >= 0)

        val gate = media.holdNextWrite()
        withContext(Dispatchers.Main.immediate) {
            model.adjustSubtitleDelay(100L)
            model.adjustSubtitleDelay(100L)
            model.resetSubtitleDelay()
            model.adjustSubtitleDelay(-200L)
            assertEquals(-200L, host.engine.delayMs)
            model.playQueueItem(secondIndex)
        }
        gate.entered.await()
        assertEquals(150L, database.mediaDao().findById(firstId)!!.subtitleDelayMs)
        gate.resume.complete(Unit)

        val second = model.loaded(secondId)
        assertEquals(0L, second.subtitleDelayMs)
        assertEquals(0L, host.engine.delayAtLoad)
        assertEquals(0L, database.mediaDao().findById(secondId)!!.subtitleDelayMs)
        val firstIndex = second.queue.indexOfFirst { it.mediaId == firstId }
        check(firstIndex >= 0)
        withContext(Dispatchers.Main.immediate) {
            if (firstIndex < second.queueIndex) model.playPrevious() else model.playNext()
        }
        assertEquals(-200L, model.loaded(firstId).subtitleDelayMs)
        assertEquals(-200L, host.engine.delayAtLoad)
        assertEquals(-200L, database.mediaDao().findById(firstId)!!.subtitleDelayMs)

        val closingEngine = host.engine
        val beforeClose = media.holdNextWrite()
        withContext(Dispatchers.Main.immediate) {
            model.adjustSubtitleDelay(500L)
            model.closePlayback()
        }
        val reopened = host.open(firstId)
        beforeClose.entered.await()
        beforeClose.resume.complete(Unit)
        assertEquals(300L, reopened.loaded(firstId).subtitleDelayMs)
        assertNotSame(closingEngine, host.engine)
        assertEquals(300L, host.engine.delayAtLoad)
        assertEquals(300L, database.mediaDao().findById(firstId)!!.subtitleDelayMs)

        withContext(Dispatchers.Main.immediate) {
            reopened.resetSubtitleDelay()
            reopened.closePlayback()
        }
        assertEquals(0L, host.open(firstId).loaded(firstId).subtitleDelayMs)
        assertEquals(0L, host.engine.delayAtLoad)
        assertEquals(0L, database.mediaDao().findById(firstId)!!.subtitleDelayMs)
        assertEquals(0L, database.mediaDao().findById(secondId)!!.subtitleDelayMs)
        assertEquals(listOf(250L, 350L, 0L, -200L, 300L, 0L).map { firstId to it }, media.writes.toList())
    }

    @Test
    fun firstAndLiveStylesComeFromDataStoreWithoutWritingThemBack() = withFixture {
        settings.setSubtitleFont(SubtitleFont.SERIF)
        val initial = settings.observeSettings().first().subtitle
        val writesBeforeOpen = settingsWrites.get()
        val model = host.open(firstId)
        model.loaded(firstId)
        assertEquals(initial, host.engine.styleAtLoad)
        assertEquals(writesBeforeOpen, settingsWrites.get())

        withContext(Dispatchers.Main.immediate) {
            model.setSubtitleFontScale(1.5f)
            model.setSubtitleFont(SubtitleFont.MONOSPACE)
            model.setSubtitleFontStyle(SubtitleFontStyle.BOLD_ITALIC)
            model.setSubtitleTextColor(SubtitleTextColor.CYAN)
            model.setSubtitleBackground(SubtitleBackground.NAVY)
            model.setSubtitleVerticalOffset(25)
            model.setSubtitleOverrideAss(true)
        }
        val edited = initial.copy(
            fontSizeScale = 1.5f, font = SubtitleFont.MONOSPACE, fontStyle = SubtitleFontStyle.BOLD_ITALIC,
            textColor = SubtitleTextColor.CYAN, background = SubtitleBackground.NAVY,
            verticalOffsetPercent = 25, overrideAss = true,
        )
        model.uiState.first { it.subtitlePrefs == edited }
        assertEquals(edited, settings.observeSettings().first().subtitle)
        assertEquals(edited, host.engine.style)
        assertEquals(writesBeforeOpen + 7, settingsWrites.get())

        val styleCalls = host.engine.styleCalls.get()
        settings.setAutoPlayNext(false)
        model.uiState.first { !it.autoPlayNext }
        assertEquals(styleCalls, host.engine.styleCalls.get())
        settings.setSubtitleTextColor(SubtitleTextColor.YELLOW)
        val external = edited.copy(textColor = SubtitleTextColor.YELLOW)
        model.uiState.first { it.subtitlePrefs == external }
        assertEquals(external, host.engine.style)
        withContext(Dispatchers.Main.immediate) {
            model.setSubtitleFontStyle(SubtitleFontStyle.ITALIC)
            model.closePlayback()
        }
        val finalStyle = external.copy(fontStyle = SubtitleFontStyle.ITALIC)
        assertEquals(finalStyle, host.open(secondId).loaded(secondId).subtitlePrefs)
        assertEquals(finalStyle, host.engine.styleAtLoad)
        assertEquals(writesBeforeOpen + 10, settingsWrites.get())
    }

    @Test
    fun eightTimesDefaultAndTemporarySpeedRestoreThePreviousRate() = withFixture {
        settings.setDefaultSpeed(8f)
        val model = host.open(firstId)
        model.loaded(firstId)
        suspend fun awaitSpeed(speed: Float) {
            model.uiState.first { it.playback.mediaId == firstId && it.playback.speed == speed }
            assertEquals(speed, host.engine.snapshot.value.speed, 0f)
        }
        awaitSpeed(8f)
        withContext(Dispatchers.Main.immediate) { model.setSpeed(6f) }
        awaitSpeed(6f)
        withContext(Dispatchers.Main.immediate) { model.beginTemporarySpeed(10f) }
        awaitSpeed(8f)
        withContext(Dispatchers.Main.immediate) { model.endTemporarySpeed() }
        awaitSpeed(6f)
        withContext(Dispatchers.Main.immediate) { model.beginTemporarySpeed(0.25f) }
        awaitSpeed(1.5f)
        withContext(Dispatchers.Main.immediate) { model.endTemporarySpeed() }
        awaitSpeed(6f)
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
    }

    /** 门只控制落库时序，读写都委托给真实 Room 仓库。 */
    private class GatedMedia(private val repository: MediaRepository) : MediaRepository by repository {
        private val next = AtomicReference<Gate?>()
        val writes = CopyOnWriteArrayList<Pair<Long, Long>>()
        fun holdNextWrite() = Gate().also { check(next.compareAndSet(null, it)) }

        override suspend fun setSubtitleDelayMs(mediaId: Long, delayMs: Long) {
            next.getAndSet(null)?.let { gate ->
                gate.entered.complete(Unit)
                gate.resume.await()
            }
            repository.setSubtitleDelayMs(mediaId, delayMs)
            writes.add(mediaId to delayMs)
        }
    }

    private class Host : PlaybackServiceStarter, PlaybackEngineProvider {
        lateinit var coordinator: PlaybackCoordinator
        lateinit var engine: RecordingEngine
        private var owner: Any? = null
        var released: Deferred<Unit>? = null
        override val backendLabel = "命令记录引擎"
        override val backendDetail = "会话偏好回归，不解码媒体"
        override fun create(): PlaybackEngine = RecordingEngine().also { engine = it }
        override fun start(): String? {
            if (owner == null) {
                owner = Any().also { coordinator.acquire(it, prepareForPlayback = { false }) }
            }
            return null
        }
        override fun stop() {
            val current = owner ?: return
            released = coordinator.release(current)
            owner = null
        }
        suspend fun open(id: Long): PlayerViewModel = withContext(Dispatchers.Main.immediate) {
            PlayerViewModel(SavedStateHandle(mapOf(PlayerRouteArgs.ARG_MEDIA_ID to id.toString())), coordinator)
                .also { it.onStart() }
        }
    }

    private class RecordingEngine : PlaybackEngine {
        override val backendLabel = "命令记录引擎"
        override val snapshot = MutableStateFlow(PlaybackSnapshot())
        override val audioTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        override val subtitleTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        override val events = emptyFlow<PlaybackEvent>()
        @Volatile var style: SubtitlePreferences? = null
        @Volatile var styleAtLoad: SubtitlePreferences? = null
        @Volatile var delayAtLoad: Long? = null
        @Volatile var delayMs: Long? = null
            private set
        val styleCalls = AtomicInteger()

        override suspend fun initialize() = Unit
        override suspend fun load(source: PlaybackSource, startPositionMs: Long, autoPlay: Boolean) {
            styleAtLoad = style
            delayAtLoad = delayMs
            snapshot.update { it.copy(mediaId = source.mediaId, status = PlaybackStatus.PAUSED,
                positionMs = startPositionMs, durationMs = source.durationHintMs) }
        }
        override fun setSubtitleStyle(preferences: SubtitlePreferences) {
            style = preferences
            styleCalls.incrementAndGet()
        }
        override fun setSubtitleDelay(delayMs: Long) { this.delayMs = delayMs }
        override fun setSpeed(speed: Float) { snapshot.update { it.copy(speed = speed) } }
        override fun play() { snapshot.update { it.copy(status = PlaybackStatus.PLAYING) } }
        override fun pause() { snapshot.update { it.copy(status = PlaybackStatus.PAUSED) } }
        override fun seekTo(positionMs: Long) { snapshot.update { it.copy(positionMs = positionMs) } }
        override fun setAspect(aspect: VideoAspect) { snapshot.update { it.copy(aspect = aspect) } }
        override fun setVolume(volume: Float) { snapshot.update { it.copy(volume = volume) } }
        override suspend fun stop() { snapshot.value = PlaybackSnapshot() }
        override suspend fun release() = stop()
        override fun attachSurface(surface: Surface?) = Unit
        override fun resizeSurface(width: Int, height: Int) = Unit
        override fun selectAudioTrack(trackId: String?) = Unit
        override fun selectSubtitleTrack(trackId: String?) = Unit
        override fun diagnostics() = PlaybackDiagnostics()
        override suspend fun mediaInfo(): EngineMediaInfo? = null
    }

    private class Fixture(
        val host: Host, val database: PrivatePlayerDatabase, val media: GatedMedia,
        val settings: PreferencesSettingsRepository, val settingsWrites: AtomicInteger,
        val firstId: Long, val secondId: Long,
    ) {
        suspend fun PlayerViewModel.loaded(id: Long): PlayerUiState {
            host.engine.snapshot.first { it.mediaId == id }
            return uiState.first {
                check(it.error == null) { it.error.orEmpty() }
                it.mediaId == id && it.playback.mediaId == id && it.playback.status == PlaybackStatus.PAUSED
            }
        }
    }

    private fun withFixture(test: suspend Fixture.() -> Unit) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val sandbox = Files.createTempDirectory(context.cacheDir.toPath(), "session-preferences-").toFile()
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = sandbox
            override fun getCacheDir() = File(sandbox, "cache").apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
        val applicationJob = SupervisorJob()
        val storeJob = SupervisorJob()
        val host = Host()
        try {
            val storage = PrivateStorage(isolated)
            check(storage.ensureLayout())
            val tree = FileTreeRepositoryImpl(database, database.mediaDao(), database.playbackHistoryDao(),
                database.playlistDao(), storage, MediaProbe(), Dispatchers.IO)
            suspend fun mediaId(name: String): Long {
                instrumentation.context.assets.open("playback-regression.mkv").use { input ->
                    File(storage.mediaRoot, name).outputStream().use { output -> input.copyTo(output) }
                }
                return tree.ensureMedia(name)
            }
            val first = mediaId("a.mkv")
            val second = mediaId("b.mkv")
            val media = GatedMedia(MediaRepositoryImpl(database.mediaDao(), database.folderDao(), storage, tree, Dispatchers.IO))
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(storeJob + Dispatchers.IO)) {
                File(sandbox, "session.preferences_pb")
            }
            val writes = AtomicInteger()
            val settings = PreferencesSettingsRepository(object : DataStore<Preferences> by store {
                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                    writes.incrementAndGet()
                    return store.updateData(transform)
                }
            })
            val history = PlaybackHistoryRepositoryImpl(database.playbackHistoryDao(), Dispatchers.IO)
            host.coordinator = PlaybackCoordinator(isolated, media, tree, StorageMediaFileLocator(storage),
                LocalSubtitleStore(storage, SafDocuments(isolated)), settings, host,
                SavePlaybackProgressUseCase(media, history, settings), host,
                CoroutineScope(applicationJob + Dispatchers.IO), Dispatchers.IO)
            withTimeout(30_000) { Fixture(host, database, media, settings, writes, first, second).test() }
        } finally {
            try {
                withContext(Dispatchers.Main.immediate) { host.stop() }
                host.released?.await()
            } finally {
                applicationJob.cancelAndJoin()
                storeJob.cancelAndJoin()
                database.close()
                sandbox.deleteRecursively()
            }
        }
    }
}
