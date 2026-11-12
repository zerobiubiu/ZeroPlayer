package com.privateplayer.media

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
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
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.usecase.SavePlaybackProgressUseCase
import com.privateplayer.feature.player.PlayMode
import com.privateplayer.feature.player.PlaybackCoordinator
import com.privateplayer.feature.player.PlaybackServiceStarter
import com.privateplayer.feature.player.PlaybackQueueSheet
import com.privateplayer.feature.player.PlayerRouteArgs
import com.privateplayer.feature.player.PlayerUiState
import com.privateplayer.feature.player.PlayerViewModel
import com.privateplayer.feature.player.QueueItem
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 播放队列面板（F-20）：真实队列渲染、当前项标记、受控跳转与空队列。
 *
 * 跳转用例使用真实 libmpv、私有文件与内存数据库（沿用 MpvPlaybackInstrumentedTest
 * 的沙箱方式），验证“UI 只表达意图、会话执行切换”的完整链路；
 * 渲染用例直接驱动生产的 [PlaybackQueueSheet]，不复制平行实现。
 * MediaSession 生命周期契约不变：关闭停服、重开新会话。
 */
@RunWith(AndroidJUnit4::class)
class PlayerQueueInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<RotationTestActivity>()

    private fun selectedNodes() = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
    )

    private fun queueState(
        names: List<String>,
        currentIndex: Int,
        playMode: PlayMode = PlayMode.SEQUENTIAL,
    ) = PlayerUiState(
        isLoading = false,
        mediaId = names.getOrNull(currentIndex)?.hashCode()?.toLong() ?: 0L,
        title = names.getOrNull(currentIndex) ?: "",
        queue = names.mapIndexed { index, name -> QueueItem(mediaId = (index + 1).toLong(), path = "目录/$name") },
        queueIndex = currentIndex,
        playMode = playMode,
    )

    @Test
    fun queueSheetShowsEntriesMarksOnlyCurrentAndRequestsSwitch() {
        val names = listOf("第一集.mp4", "第二集.mp4", "第三集.mp4")
        val state = mutableStateOf(queueState(names, currentIndex = 1))
        val requested = mutableListOf<Int>()
        val previousClicks = AtomicInteger()
        val nextClicks = AtomicInteger()
        val modes = mutableListOf<PlayMode>()
        compose.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    state = state.value,
                    onSelectItem = { requested += it },
                    onPlayPrevious = { previousClicks.incrementAndGet() },
                    onPlayNext = { nextClicks.incrementAndGet() },
                    onSelectMode = { modes += it },
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("第二集.mp4").fetchSemanticsNodes().isNotEmpty()
        }

        // 标题改为“播放队列”，位置计数正确；条目按队列顺序完整显示
        compose.onNodeWithText("播放队列 · 2/3").assertExists()
        names.forEach { name -> compose.onNodeWithText(name).assertExists() }

        // F-16 语义：仅当前项 + 当前播放模式两个单选态，其余条目无勾选
        selectedNodes().assertCountEquals(2)
        val current = compose.onNodeWithText("第二集.mp4").fetchSemanticsNode()
        assertEquals(Role.RadioButton, current.config[SemanticsProperties.Role])
        assertTrue(current.config[SemanticsProperties.Selected])
        val other = compose.onNodeWithText("第一集.mp4").fetchSemanticsNode()
        assertEquals(false, other.config[SemanticsProperties.Selected])

        // 点击非当前项只表达意图；点击当前项同样只发出请求，受理判定在会话侧
        compose.onNodeWithText("第三集.mp4").performClick()
        compose.waitUntil(10_000) { requested == listOf(2) }
        compose.onNodeWithText("第二集.mp4").performClick()
        compose.waitUntil(10_000) { requested == listOf(2, 1) }

        // 上一集/下一集与播放模式保留且可用
        compose.onNodeWithText("上一集").assertIsEnabled()
        compose.onNodeWithText("下一集").assertIsEnabled()
        compose.onNodeWithText("上一集").performClick()
        compose.waitUntil(10_000) { previousClicks.get() == 1 }
        compose.onNodeWithText("下一集").performClick()
        compose.waitUntil(10_000) { nextClicks.get() == 1 }
        compose.onNodeWithText("随机播放").performClick()
        compose.waitUntil(10_000) { modes == listOf(PlayMode.SHUFFLE) }

        // 播放状态变化后当前项标记自动跟随（UI 读取 state.queueIndex）
        compose.runOnIdle { state.value = queueState(names, currentIndex = 2, playMode = PlayMode.SHUFFLE) }
        compose.waitUntil(10_000) {
            compose.onNodeWithText("第三集.mp4").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        selectedNodes().assertCountEquals(2)
        compose.onNodeWithText("播放队列 · 3/3").assertExists()
        compose.onNodeWithText("下一集").assertIsNotEnabled()
    }

    @Test
    fun emptyQueueShowsEmptyFeedbackWithoutCrash() {
        compose.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    state = PlayerUiState(isLoading = false),
                    onSelectItem = { throw AssertionError("空队列不应发出跳转请求") },
                    onPlayPrevious = {},
                    onPlayNext = {},
                    onSelectMode = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("队列为空").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("当前没有可播放的队列条目。").assertExists()
        compose.onNodeWithText("播放队列 · 1/1").assertExists()
        compose.onNodeWithText("上一集").assertIsNotEnabled()
        compose.onNodeWithText("下一集").assertIsNotEnabled()
        // 模式选择仍然可用
        compose.onNodeWithText("顺序播放").assertExists()
        selectedNodes().assertCountEquals(1)
    }

    @Test
    fun longQueueScrollsAndPositionsCurrentItem() {
        val names = (1..24).map { "长队列第%02d集.mp4".format(it) }
        val state = mutableStateOf(queueState(names, currentIndex = 17))
        compose.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    state = state.value,
                    onSelectItem = {},
                    onPlayPrevious = {},
                    onPlayNext = {},
                    onSelectMode = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放队列 · 18/24").fetchSemanticsNodes().isNotEmpty()
        }

        // 打开面板即定位到当前项（第 18 项在限高列表内可见）
        compose.onNodeWithText("长队列第18集.mp4").assertExists()
        // 列表可滚动到队首与队尾，长队列不卡顿、不崩溃
        compose.onNodeWithTag("播放队列列表").performScrollToNode(hasText("长队列第01集.mp4"))
        compose.onNodeWithText("长队列第01集.mp4").assertExists()
        compose.onNodeWithTag("播放队列列表").performScrollToNode(hasText("长队列第24集.mp4"))
        compose.onNodeWithText("长队列第24集.mp4").assertExists()

        // 当前项变化后重新定位
        compose.runOnIdle { state.value = queueState(names, currentIndex = 2) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("长队列第03集.mp4").fetchSemanticsNodes().isNotEmpty()
        }
        selectedNodes().assertCountEquals(2)
    }

    @Test
    fun clickingQueueEntrySwitchesRealPlaybackAndKeepsOrder() = runBlocking {
        // 在独立线程驱动真实播放与断言，避免阻塞仪器化线程引起 Compose 帧时钟重入布局。
        val testDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
        kotlinx.coroutines.withContext(testDispatcher) {
        withRealQueuePlayer { player ->
            val model = player.model
            val started = awaitPlayerState(model, "首个视频开始播放") {
                it.playback.status == PlaybackStatus.PLAYING && it.queue.size == 3
            }
            // 队列顺序与目录列举完全一致（排序规则属于文件树实现，含本地化排序）；
            // 当前项为打开的视频。
            val expectedOrder = player.tree.list("队列目录")
                .filter { !it.isDirectory && it.isVideo }
                .map { it.path }
            assertEquals(expectedOrder, started.queue.map { it.path })
            val orderNames = expectedOrder.map { it.substringAfterLast('/') }
            assertEquals(3, orderNames.size)
            // 标题是媒体显示名（去扩展名），队列行展示文件名（含扩展名）。
            val orderTitles = orderNames.map { it.substringBeforeLast('.') }
            val openIndex = orderNames.indexOf("队列甲.mp4")
            assertEquals(openIndex, started.queueIndex)
            assertEquals("队列甲", started.title)
            val targetName = orderNames.first { it != "队列甲.mp4" }
            val targetIndex = orderNames.indexOf(targetName)
            val otherName = orderNames.first { it != "队列甲.mp4" && it != targetName }
            val otherIndex = orderNames.indexOf(otherName)

            // 打开队列面板：真实状态渲染，当前项唯一高亮。
            // 面板渲染在承载 Surface 的同一宿主上（对话框窗口覆盖其上），
            // 避免引入第二个测试宿主 Activity 导致沙箱宿主被系统销毁。
            val sheetVisible = mutableStateOf(true)
            compose.runOnIdle {
                compose.activity.setContent {
                    MaterialTheme {
                        if (sheetVisible.value) {
                            val state by model.uiState.collectAsState()
                            PlaybackQueueSheet(
                                state = state,
                                onSelectItem = model::playQueueItem,
                                onPlayPrevious = model::playPrevious,
                                onPlayNext = model::playNext,
                                onSelectMode = model::setPlayMode,
                                onDismiss = { sheetVisible.value = false },
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(targetName).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("播放队列 · ${openIndex + 1}/3").assertExists()
            selectedNodes().assertCountEquals(2)

            // 点击非当前项：UI 只表达意图，会话完成真实切源
            compose.onNodeWithText(targetName).performClick()
            val switched = awaitPlayerState(model, "跳转到队列目标项") {
                it.title == orderTitles[targetIndex] && it.queueIndex == targetIndex &&
                    it.playback.status == PlaybackStatus.PLAYING
            }
            assertEquals(player.mediaIdsByName.getValue(targetName), switched.mediaId)
            // 队列顺序不因跳转改变；面板自动同步当前项
            assertEquals(expectedOrder, switched.queue.map { it.path })
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("播放队列 · ${targetIndex + 1}/3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(10_000) {
                compose.onNodeWithText(targetName).fetchSemanticsNode().config[SemanticsProperties.Selected]
            }

            // 快速连点：持有切源门使第一次跳转保持“切换中”，期间再点另一项必须被忽略。
            // （测试框架的两次点击间隔可能超过真实切换耗时，故用门保证并发确定性。）
            val gateAcquired = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseGate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val gateHolder = launch(Dispatchers.IO) {
                player.withTransitionGate {
                    gateAcquired.complete(Unit)
                    releaseGate.await()
                }
            }
            gateAcquired.await()
            try {
                compose.onNodeWithText(otherName).performClick()
                compose.onNodeWithText(targetName).performClick()
            } finally {
                releaseGate.complete(Unit)
            }
            gateHolder.join()
            val afterRapid = awaitPlayerState(model, "快速连点只执行第一次跳转") {
                it.title == orderTitles[otherIndex] && it.queueIndex == otherIndex &&
                    it.playback.status == PlaybackStatus.PLAYING
            }
            assertEquals(player.mediaIdsByName.getValue(otherName), afterRapid.mediaId)

            // 面板保持打开时当前项跟随；上一集/下一集仍走既有命令路径
            compose.waitUntil(10_000) {
                compose.onNodeWithText(otherName).fetchSemanticsNode().config[SemanticsProperties.Selected]
            }
            // 上一集/下一集仍走既有命令路径：断言真实播放结果（面板收起回调由纯 UI 用例覆盖）。
            if (otherIndex > 0) {
                compose.onNodeWithText("上一集").performClick()
                awaitPlayerState(model, "上一集回到前一项") {
                    it.queueIndex == otherIndex - 1 &&
                        it.title == orderTitles[otherIndex - 1] &&
                        it.playback.status == PlaybackStatus.PLAYING
                }
            } else {
                compose.onNodeWithText("下一集").performClick()
                awaitPlayerState(model, "下一集进入后一项") {
                    it.queueIndex == 1 &&
                        it.title == orderTitles[1] &&
                        it.playback.status == PlaybackStatus.PLAYING
                }
            }
            // 先收起面板再交还沙箱清理，避免 Compose 测试宿主与沙箱宿主互相销毁。
            compose.runOnIdle { sheetVisible.value = false }
            compose.waitForIdle()
        }
        }
        } finally {
            testDispatcher.close()
        }
    }

    // ------------------------------------------------------------ 真实播放沙箱

    private data class RealQueuePlayer(
        val model: PlayerViewModel,
        val tree: FileTreeRepositoryImpl,
        val mediaIdsByName: Map<String, Long>,
        val withTransitionGate: suspend (suspend () -> Unit) -> Unit,
    )

    /** 与 MpvPlaybackInstrumentedTest 相同：真实文件树、内存数据库、真实 libmpv、独立临时目录。 */
    private suspend fun withRealQueuePlayer(block: suspend (RealQueuePlayer) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val mediaRoot = File(context.filesDir, "media")
        check(!Files.isSymbolicLink(mediaRoot.toPath())) { "媒体根目录不能是符号链接" }
        check(mediaRoot.isDirectory || mediaRoot.mkdirs()) { "无法创建媒体根目录" }
        val sandbox = Files.createTempDirectory(mediaRoot.toPath(), "队列回归-").toFile()
        val sandboxContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = sandbox
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
        }
        val engine = MpvPlaybackEngine(context)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var database: PrivatePlayerDatabase? = null
        var viewModelStore: ViewModelStore? = null
        var coordinator: PlaybackCoordinator? = null
        val serviceOwner = Any()
        try {
            val storage = PrivateStorage(sandboxContext)
            check(storage.ensureLayout()) { "无法创建测试专用存储" }
            val directory = File(storage.mediaRoot, "队列目录").apply { check(mkdirs()) }
            val names = listOf("队列甲.mp4", "队列乙.mp4", "队列丙.mp4")
            names.forEach { name ->
                instrumentation.context.assets.open("playback-regression.mkv").use { input ->
                    File(directory, name).outputStream().use { output -> input.copyTo(output) }
                }
            }

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
            val settings = object : SettingsRepository by PreferencesSettingsRepository(context) {
                override fun observeSettings() = flowOf(AppSettings())
            }
            val provider = object : PlaybackEngineProvider {
                override val backendLabel: String = engine.backendLabel
                override val backendDetail: String = "真实 libmpv 队列测试实例"
                override fun create(): PlaybackEngine = engine
            }
            val mediaIdsByName = names.associateWith { tree.ensureMedia("队列目录/$it") }
            val playback = PlaybackCoordinator(
                sandboxContext,
                mediaRepository,
                tree,
                StorageMediaFileLocator(storage),
                LocalSubtitleStore(storage, SafDocuments(context)),
                settings,
                provider,
                SavePlaybackProgressUseCase(mediaRepository, history, settings),
                object : PlaybackServiceStarter {
                    override fun start(): String? = null
                    override fun stop() = Unit
                },
                applicationScope,
                Dispatchers.IO,
            )
            coordinator = playback
            val surfaceOwner = Any()

            // 直接使用 Compose 规则的宿主 Activity：Surface 与面板共用一个宿主，
            // 不引入第二个测试 Activity（会触发系统销毁沙箱宿主）。
            val surfaceReady = CountDownLatch(1)
            lateinit var surface: SurfaceView
            compose.runOnIdle {
                val activity = compose.activity
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
            lateinit var leaseGate: suspend (suspend () -> Unit) -> Unit
            compose.runOnIdle {
                leaseGate = playback.acquire(serviceOwner).withTransitionGate
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        require(modelClass == PlayerViewModel::class.java)
                        return modelClass.cast(
                            PlayerViewModel(
                                SavedStateHandle(mapOf(PlayerRouteArgs.ARG_MEDIA_ID to mediaIdsByName.getValue("队列甲.mp4").toString())),
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
            block(RealQueuePlayer(model, tree, mediaIdsByName, leaseGate))
        } finally {
            try {
                viewModelStore?.let { store -> instrumentation.runOnMainSync { store.clear() } }
            } finally {
                try {
                    val owner = coordinator
                    if (owner != null) {
                        val released = java.util.concurrent.atomic.AtomicReference<Deferred<Unit>?>()
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
                        database?.close()
                    } finally {
                        sandbox.deleteRecursively()
                    }
                }
            }
        }
    }

    private suspend fun awaitPlayerState(
        model: PlayerViewModel,
        stage: String,
        predicate: (PlayerUiState) -> Boolean,
    ): PlayerUiState {
        try {
            return withTimeout(20_000) {
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
}

