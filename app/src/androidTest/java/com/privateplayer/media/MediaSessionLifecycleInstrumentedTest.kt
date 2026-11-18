package com.privateplayer.media

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.domain.model.PlaybackProgressMode
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.MediaRepositoryImpl
import com.privateplayer.media.playback.PlayerMediaSessionService
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 只允许在可丢弃模拟器手动运行：真实服务、真实 libmpv、隔离临时媒体及可回收数据库记录。 */
@RunWith(AndroidJUnit4::class)
class MediaSessionLifecycleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.FINGERPRINT.contains("generic", ignoreCase = true),
        ) { "该测试会改写应用私有测试记录并操作锁屏，只能在可丢弃模拟器运行" }
        val context = instrumentation.targetContext.applicationContext
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val mediaRoot = File(context.filesDir, "media")
        check(!Files.isSymbolicLink(mediaRoot.toPath()))
        check(mediaRoot.isDirectory || mediaRoot.mkdirs())
        val directory = Files.createTempDirectory(mediaRoot.toPath(), "media3-回归-").toFile()
        val file = File(directory, "01-会话回归-${directory.name}.mkv")
        val secondFile = File(directory, "02-会话回归-${directory.name}.mkv")
        for (fixture in listOf(file, secondFile)) {
            instrumentation.context.assets.open("playback-regression.mkv").use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val brokenDirectory = File(directory, "损坏样本").apply { check(mkdir()) }
        val brokenFile = File(brokenDirectory, "03-损坏-${directory.name}.mkv").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val database = Room.databaseBuilder(context, PrivatePlayerDatabase::class.java, PrivatePlayerDatabase.NAME).build()
        val storage = PrivateStorage(context)
        check(storage.ensureLayout())
        val thumbnailPath = storage.thumbnailRelativePath(directory.name)
        val thumbnailFile = storage.absoluteCacheFile(thumbnailPath)
        val artwork = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.rgb(40, 90, 160))
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
                output.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        thumbnailFile.writeBytes(artwork)
        val tree = FileTreeRepositoryImpl(
            database, database.mediaDao(), database.playbackHistoryDao(), database.playlistDao(),
            storage, MediaProbe(), Dispatchers.IO,
        )
        val mediaRepository = MediaRepositoryImpl(
            database.mediaDao(), database.folderDao(), storage, tree, Dispatchers.IO,
        )
        var scenario: ActivityScenario<MainActivity>? = null
        var controller: MediaController? = null
        var primaryFailure: Throwable? = null
        val preferences = PreferencesSettingsRepository(context)
        val originalProgress = preferences.observeSettings().first().playback.progressMode
        val directoryIdentity = storage.fileIdentity(directory)
        try {
            preferences.setPlaybackProgressMode(PlaybackProgressMode.RESUME)
            val id = tree.ensureMedia("${directory.name}/${file.name}")
            val secondId = tree.ensureMedia("${directory.name}/${secondFile.name}")
            database.mediaDao().setThumbnail(id, thumbnailPath, System.currentTimeMillis())
            val durationHint = mediaRepository.findById(id)?.durationMs ?: 0L
            val activity = ActivityScenario.launch(MainActivity::class.java)
            scenario = activity
            compose.waitUntil(20_000) { compose.onAllNodesWithText(directory.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(directory.name).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText(file.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(file.name).performClick()

            var remote = connect(context)
            controller = remote
            awaitController(
                remote,
                "首次打开：真实播放、媒体 ID 与封面",
                diagnostics = { "真实数据库记录：${database.mediaDao().findById(id)}；封面文件长度=${thumbnailFile.length()}" },
            ) {
                currentMediaItem?.mediaId == id.toString() && isPlaying && mediaMetadata.artworkData != null
            }
            awaitWakeLock(context, true, "首次真实播放持有 CPU 锁")
            onMain {
                assertEquals(file.nameWithoutExtension, remote.mediaMetadata.title.toString())
                assertNull(remote.mediaMetadata.artist)
                assertArrayEquals(artwork, remote.mediaMetadata.artworkData)
                assertTrue(remote.duration > 0L)
                assertEquals(remote.duration, remote.mediaMetadata.durationMs ?: -1L)
                if (durationHint > 0L) assertTrue(abs(remote.duration - durationHint) < 1_000L)
                assertNull(remote.currentMediaItem?.localConfiguration?.uri)
                assertFalse(remote.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
                assertFalse(remote.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
                remote.pause()
            }
            awaitController(remote, "控制器暂停并清除播放意图") {
                currentMediaItem?.mediaId == id.toString() && playbackState == Player.STATE_READY && !isPlaying && !playWhenReady
            }
            awaitWakeLock(context, false, "控制器暂停释放 CPU 锁")
            showControls()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("播放").fetchSemanticsNodes().isNotEmpty() }

            // 外部控制的跳转、倍速必须反映到同一页面。
            onMain {
                remote.seekTo(1_500L)
                remote.setPlaybackSpeed(8f)
            }
            awaitController(remote, "控制器跳转与最高倍速") { abs(currentPosition - 1_500L) < 800L && playbackParameters.speed == 8f }
            compose.waitUntil(10_000) { compose.onAllNodes(speedButtonAt("8×")).fetchSemanticsNodes().isNotEmpty() }
            // 非预设速度超出两位时标明近似，语义与面板保留精确值，不勾选邻近档。
            onMain { remote.setPlaybackSpeed(1.375f) }
            compose.waitUntil(10_000) { compose.onAllNodes(speedButtonAt("1.375×")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(speedButtonAt("1.375×") and hasText("≈1.38×")).assertIsDisplayed()

            // 在真实 MainActivity 的同一根手指尚未抬起时退后台：保持暂停，恢复外部设定的精确速度。
            val defaultSpeed = preferences.observeSettings().first().playback.defaultSpeed
            val anchor = compose.playerBounds(compose.onNode(speedButtonAt("1.375×"))).center
            PlayerSystemTouch(anchor).use { finger ->
                compose.waitUntil(5_000) { compose.onAllNodes(speedOption("5×")).fetchSemanticsNodes().isNotEmpty() }
                finger.moveTo(compose.playerBounds(compose.onNode(speedOption("5×"))).center)
                awaitController(remote, "未抬手时媒体会话已按 5× 试播") { playbackParameters.speed == 5f && !playWhenReady }
                compose.onNode(hasTestTag("temporarySpeedHud") and hasText("5× 临时倍速")).assertIsDisplayed()
                compose.onNode(hasTestTag("speedFanBubble") and hasText("5×")).assertIsDisplayed()
                assertEquals(defaultSpeed, preferences.observeSettings().first().playback.defaultSpeed, 0f)
                activity.moveToState(Lifecycle.State.CREATED)
                awaitController(remote, "退后台撤销试播，不恢复播放") { playbackParameters.speed == 1.375f && !isPlaying && !playWhenReady }
                activity.moveToState(Lifecycle.State.RESUMED)
                finger.up()
            }
            showControls()
            compose.waitUntil(5_000) { compose.onAllNodes(speedButtonAt("1.375×")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasTestTag("temporarySpeedHud")).assertCountEquals(0)
            compose.onAllNodes(hasTestTag("speedFanBubble")).assertCountEquals(0)
            assertEquals(defaultSpeed, preferences.observeSettings().first().playback.defaultSpeed, 0f)
            Log.i("ZeroPlayerSpeedProof", "MainActivity paused Media3 1.375→held 5→background 1.375; default=$defaultSpeed")
            if (InstrumentationRegistry.getArguments().getString("speedEvidence") == "true") {
                checkSystemSpeedHaptics(preferences, remote)
            }

            // 页面反向修改倍速和进度，再由页面恢复播放。
            compose.onNode(speedButtonAt("1.375×")).performTouchInput { click() }
            compose.onNode(speedOption("1×")).assertIsDisplayed()
            compose.onNodeWithText("1.375×", substring = true).assertIsDisplayed()
            compose.onAllNodes(selectedSpeedOption).assertCountEquals(0)
            compose.onNode(speedOption("0.5×")).performTouchInput { click() }
            awaitController(remote, "页面反向设置 0.5 倍速") { abs(playbackParameters.speed - 0.5f) < 0.01f }
            compose.onNodeWithContentDescription("播放进度")
                .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.2f) }
            awaitController(remote, "页面反向跳转至 20%") { duration > 0 && abs(currentPosition - (duration * 0.2f).toLong()) < 1_000L }
            val pausedSeekTarget = onMain { (remote.duration * 0.2f).toLong() }
            suspend fun alignedPause(stage: String, target: Long): Long {
                var previous = -1L
                awaitSystemState(context, stage, diagnostics = {
                    "目标=$target，数据库：${mediaRepository.findById(id)}\n${controllerState(remote)}"
                }) {
                    val row = mediaRepository.findById(id) ?: return@awaitSystemState false
                    val position = onMain {
                        if (!remote.isPlaying && !remote.playWhenReady && remote.currentMediaItem?.mediaId == id.toString())
                            remote.currentPosition else -1L
                    }
                    val matches = row.playCount == 1 && abs(row.playbackPositionMs - target) < 150L &&
                        abs(position - target) < 150L && abs(position - row.playbackPositionMs) < 150L
                    val stable = matches && previous == position
                    previous = if (matches) position else -1L
                    if (!stable) return@awaitSystemState false
                    val native = serviceState(context).substringAfter("engine=PlaybackSnapshot(", "").substringBefore("), ui=")
                    val enginePosition = native.substringAfter("positionMs=", "").substringBefore(',').toLongOrNull()
                        ?: return@awaitSystemState false
                    val same = native.contains("status=PAUSED,") && native.endsWith("mediaId=$id") &&
                        abs(enginePosition - target) < 150L && abs(enginePosition - position) < 150L
                    if (same) Log.i("ZeroPlayerSessionTest", "$stage：目标=$target 控制器=$position 库=${row.playbackPositionMs} 引擎=$enginePosition 媒体=$id 次数=${row.playCount}")
                    same
                }
                return previous
            }
            alignedPause("页面 20% 跳转已持久化", pausedSeekTarget)
            // Material Slider 触区含两端留白；中点是与滑块宽度/密度无关的已知物理目标。
            val midpoint = onMain { remote.duration / 2 }
            val shortStart = midpoint - 700L
            onMain { remote.seekTo(shortStart) }
            alignedPause("准备小幅跳转", shortStart)
            tapPlayerScreen(compose.playerBounds(compose.onNodeWithContentDescription("播放进度")).center)
            alignedPause("触屏小幅前移", midpoint)
            onMain { remote.seekTo(shortStart) }
            alignedPause("外部控制器小幅后移", shortStart)
            onMain { remote.seekTo(pausedSeekTarget) }
            alignedPause("恢复到页面 20% 断点", pausedSeekTarget)
            compose.onNodeWithContentDescription("播放").performClick()
            awaitController(remote, "页面恢复真实播放") { isPlaying }
            awaitWakeLock(context, true, "页面恢复播放重新持有 CPU 锁")

            val beforeBackground = onMain { remote.currentPosition }
            activity.moveToState(Lifecycle.State.CREATED)
            shell("input keyevent 223")
            awaitSystemState(
                context,
                "后台播放器完成息屏",
                diagnostics = { "isInteractive=${context.getSystemService(PowerManager::class.java).isInteractive}" },
            ) { !context.getSystemService(PowerManager::class.java).isInteractive }
            delay(2_500)
            awaitController(remote, "息屏后台播放持续推进") {
                currentMediaItem?.mediaId == id.toString() && isPlaying && currentPosition > beforeBackground + 400L
            }
            // 无焦点的息屏窗口会丢弃 input keyevent；走系统全局媒体键分发，仍发送真实 DOWN/UP。
            dispatchSystemPlayPause()
            awaitController(remote, "锁屏媒体键暂停") {
                currentMediaItem?.mediaId == id.toString() && playbackState == Player.STATE_READY && !isPlaying && !playWhenReady
            }
            awaitWakeLock(context, false, "锁屏媒体键暂停释放 CPU 锁")
            dispatchSystemPlayPause()
            awaitController(remote, "锁屏媒体键恢复播放") { currentMediaItem?.mediaId == id.toString() && isPlaying }
            awaitWakeLock(context, true, "锁屏媒体键恢复播放持有 CPU 锁")
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            activity.moveToState(Lifecycle.State.RESUMED)
            val beforeRecreate = onMain { remote.currentPosition }
            activity.recreate()
            awaitController(remote, "Activity 重建保留同一媒体与进度") { isPlaying && currentMediaItem?.mediaId == id.toString() && currentPosition >= beforeRecreate }

            // 1.0.1 契约：返回文件树会停止服务；重开创建新会话并从断点续播。
            onMain { remote.pause() }
            awaitController(remote, "关闭前暂停") {
                currentMediaItem?.mediaId == id.toString() && playbackState == Player.STATE_READY && !isPlaying && !playWhenReady
            }
            onMain { remote.seekTo(4_500L) }
            awaitController(remote, "关闭前定位到不足 5 秒的断点") {
                currentMediaItem?.mediaId == id.toString() && abs(currentPosition - 4_500L) < 500L
            }
            val checkpoint = alignedPause("关闭前暂停断点", 4_500L)
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(file.name).fetchSemanticsNodes().isNotEmpty() }
            awaitSystemState(context, "返回文件树后播放服务停止", diagnostics = {
                "isServiceRunning=${isServiceRunning(context)}"
            }) { !isServiceRunning(context) }
            awaitSystemState(context, "旧控制器随会话断开", diagnostics = {
                controllerState(remote)
            }) { !onMain { remote.isConnected } }
            runCatching { onMain { remote.release() } }
            controller = null
            val afterClose = requireNotNull(mediaRepository.findById(id)).playbackPositionMs
            val onOpenMode = preferences.observeSettings().first().playback.progressMode
            assertEquals("新会话须按续播策略读取断点", PlaybackProgressMode.RESUME, onOpenMode)
            assertTrue("关闭丢失断点：控制器=$checkpoint 关闭后库=$afterClose", abs(afterClose - checkpoint) < 500L)
            assertTrue("关闭后断点偏离明确的 4500ms 目标：$afterClose", abs(afterClose - 4_500L) < 150L)

            compose.onNodeWithText(file.name).performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("视频画面").assertIsDisplayed()
            awaitSystemState(context, "重开后播放服务启动", diagnostics = {
                "isServiceRunning=${isServiceRunning(context)}"
            }) { isServiceRunning(context) }
            remote = connect(context)
            controller = remote
            var firstReadyPosition = -1L
            awaitController(remote, "重开创建新会话并真正出帧") {
                (isConnected && isPlaying && currentMediaItem?.mediaId == id.toString()).also { ready ->
                    if (ready) firstReadyPosition = currentPosition
                }
            }
            Log.i("ZeroPlayerSessionTest", "恢复短断点：关闭前=$checkpoint 关闭后库=$afterClose 首个READY=$firstReadyPosition 模式=$onOpenMode")
            assertTrue("首个就绪位置未续接短断点：关闭前=$checkpoint 关闭后库=$afterClose 首个READY=$firstReadyPosition；${serviceState(context)}",
                firstReadyPosition >= maxOf(checkpoint, 4_500L) - 300L && firstReadyPosition < 4_500L + 1_000L)
            awaitWakeLock(context, true, "重开会话真实播放持有 CPU 锁")
            onMain { remote.pause() }
            awaitController(remote, "重开后控制器暂停") {
                currentMediaItem?.mediaId == id.toString() && playbackState == Player.STATE_READY && !isPlaying && !playWhenReady
            }
            awaitWakeLock(context, false, "重开后暂停释放 CPU 锁")
            awaitSystemState(
                context,
                "重开后暂停持久化：播放次数为 2 且进度大于 0",
                diagnostics = { "真实媒体记录：${mediaRepository.findById(id)}\n${controllerState(remote)}" },
            ) {
                val saved = mediaRepository.findById(id)
                saved != null && saved.playCount == 2 && saved.playbackPositionMs > 0
            }
            // 在后台结束第一段：真实目录队列应自动切到第二段，并提交第一段最终进度。
            val firstDuration = onMain { remote.duration }
            onMain { remote.play() }
            awaitController(remote, "第一段恢复播放") { isPlaying }
            activity.moveToState(Lifecycle.State.CREATED)
            onMain { remote.seekTo((firstDuration - 100L).coerceAtLeast(0L)) }
            awaitController(remote, "后台自动切到第二段") { currentMediaItem?.mediaId == secondId.toString() && isPlaying }
            awaitWakeLock(context, true, "后台切到第二段保持 CPU 锁")
            awaitSystemState(
                context,
                "第一段结束持久化：播放次数为 2 且进度达到 98%",
                diagnostics = { "期望时长=$firstDuration，真实媒体记录：${mediaRepository.findById(id)}\n${controllerState(remote)}" },
            ) {
                val saved = mediaRepository.findById(id)
                saved != null && saved.playCount == 2 && saved.playbackPositionMs >= firstDuration * 0.98
            }
            val secondPositionBeforeForeground = onMain { remote.currentPosition }
            val secondSpeedBeforeForeground = onMain { remote.playbackParameters.speed }
            activity.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            onMain {
                assertEquals("恢复页面不能用旧路由重载第一段", secondId.toString(), remote.currentMediaItem?.mediaId)
                assertTrue("恢复页面不能重置当前倍速", abs(remote.playbackParameters.speed - secondSpeedBeforeForeground) < 0.01f)
                assertTrue("恢复页面不能重置第二段进度", remote.currentPosition >= secondPositionBeforeForeground)
            }
            // 结束第二段与真实损坏文件错误都必须放开 CPU 锁。
            onMain {
                assertEquals(secondId.toString(), remote.currentMediaItem?.mediaId)
                assertTrue("第二段真实时长尚未就绪", remote.duration > 0L)
                assertTrue("第二段尚不能跳转", remote.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
                remote.seekTo((remote.duration - 100L).coerceAtLeast(0L))
            }
            awaitController(remote, "第二段真实结束") {
                currentMediaItem?.mediaId == secondId.toString() && playbackState == Player.STATE_ENDED && !isPlaying && !playWhenReady
            }
            awaitWakeLock(context, false, "第二段结束释放 CPU 锁")
            onMain { remote.play() }
            awaitController(remote, "终播后系统控制器从头重播") {
                currentMediaItem?.mediaId == secondId.toString() && isPlaying && currentPosition < 2_000L
            }
            awaitWakeLock(context, true, "系统重播重新持有 CPU 锁")
            onMain { remote.pause() }
            awaitController(remote, "系统重播后暂停") {
                currentMediaItem?.mediaId == secondId.toString() && playbackState == Player.STATE_READY && !isPlaying && !playWhenReady
            }
            awaitWakeLock(context, false, "系统重播后暂停释放 CPU 锁")
            // “仅记录”仍落库，但下一次新会话不能从该位置开始。
            preferences.setPlaybackProgressMode(PlaybackProgressMode.RECORD_ONLY)
            onMain { remote.seekTo(9_000L) }
            awaitSystemState(context, "仅记录模式保存第二段断点", diagnostics = { controllerState(remote) }) {
                abs((mediaRepository.findById(secondId)?.playbackPositionMs ?: 0L) - 9_000L) < 500L
            }
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(secondFile.name).fetchSemanticsNodes().isNotEmpty() }
            awaitSystemState(context, "关闭第二段后播放服务停止", diagnostics = {
                "isServiceRunning=${isServiceRunning(context)}"
            }) { !isServiceRunning(context) }
            awaitSystemState(context, "第二段旧控制器随会话断开", diagnostics = {
                controllerState(remote)
            }) { !onMain { remote.isConnected } }
            runCatching { onMain { remote.release() } }
            controller = null
            compose.onNodeWithText(secondFile.name).performClick()
            remote = connect(context)
            controller = remote
            awaitController(remote, "仅记录模式新会话开始播放") { isPlaying && currentMediaItem?.mediaId == secondId.toString() }
            assertTrue("仅记录模式不能从 9 秒续播", onMain { remote.currentPosition } < 4_000L)
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(secondFile.name).fetchSemanticsNodes().isNotEmpty() }
            awaitSystemState(context, "关闭仅记录会话", diagnostics = { controllerState(remote) }) { !onMain { remote.isConnected } }
            onMain { remote.release() }
            controller = null
            compose.onNodeWithText(brokenDirectory.name).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(brokenFile.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(brokenFile.name).performClick()
            // 错误媒体可能很快释放前台服务；直接绑定会话控制器等待真实错误。
            remote = connect(context)
            controller = remote
            awaitController(remote, "损坏文件暴露真实错误") { playerError != null }
            awaitWakeLock(context, false, "损坏媒体错误释放 CPU 锁")


            onMain { remote.release() }
            controller = null
            activity.close()
            scenario = null
            context.stopService(Intent(context, PlayerMediaSessionService::class.java))
            awaitSystemState(context, "关闭页面和控制器后媒体服务销毁", diagnostics = {
                "isServiceRunning=${isServiceRunning(context)}"
            }) { !isServiceRunning(context) }
            awaitWakeLock(context, false, "媒体服务销毁后没有遗留 CPU 锁")
            // 重新绑定得到空会话，证明原服务及其会话已销毁，而不是偷偷保留旧实例。
            val fresh = connect(context)
            controller = fresh
            awaitController(fresh, "服务销毁后重新绑定为空闲会话") { mediaItemCount == 0 && playbackState == Player.STATE_IDLE }
            onMain { fresh.release() }
            controller = null
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            runCatching { shell("input keyevent 224") }
            runCatching { shell("wm dismiss-keyguard") }
            runCatching { controller?.let { onMain { it.release() } } }
            runCatching { scenario?.close() }
            context.stopService(Intent(context, PlayerMediaSessionService::class.java))
            try {
                val cleanupFailure = runCatching {
                    awaitSystemState(context, "最终清理等待媒体服务销毁", diagnostics = {
                        "isServiceRunning=${isServiceRunning(context)}"
                    }) { !isServiceRunning(context) }
                }.exceptionOrNull()
                if (cleanupFailure != null) {
                    val original = primaryFailure
                    if (original == null) throw cleanupFailure else original.addSuppressed(cleanupFailure)
                }
                tree.delete(directory.name, directoryIdentity, isDirectory = true)
            } finally {
                preferences.setPlaybackProgressMode(originalProgress)
                database.close()
                // 缩略图可能在登记元数据前已生成，单独清理本次文件。
                thumbnailFile.delete()
            }
        }
    }

    private fun connect(context: Context): MediaController {
        val future = onMain {
            MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlayerMediaSessionService::class.java)),
            ).buildAsync()
        }
        return future.get(15, TimeUnit.SECONDS)
    }

    private suspend fun awaitController(
        controller: MediaController,
        stage: String,
        diagnostics: suspend () -> String = { "" },
        condition: MediaController.() -> Boolean,
    ) {
        try {
            // 原生状态需要真实时间，导航和重组同时需要测试帧时钟，不能只轮询主 Looper。
            compose.waitUntil(20_000) { onMain { controller.condition() } }
        } catch (timeout: ComposeTimeoutException) {
            val actual = controllerState(controller)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val service = serviceState(context)
            val ui = runCatching { compose.onRoot(useUnmergedTree = true).printToString() }
                .getOrElse { "无法读取页面：${it.message}" }
            val fixture = runCatching { diagnostics() }.getOrElse { "读取夹具诊断失败：${it.message}" }
            throw AssertionError("等待「$stage」超时；真实控制器：$actual\n$fixture\n真实服务：\n$service\n真实页面（Compose 同步后）：\n$ui", timeout)
        }
    }

    private fun controllerState(controller: MediaController): String = onMain {
        "connected=${controller.isConnected}, mediaId=${controller.currentMediaItem?.mediaId}, " +
            "title=${controller.mediaMetadata.title}, artworkBytes=${controller.mediaMetadata.artworkData?.size}, " +
            "state=${controller.playbackState}, isPlaying=${controller.isPlaying}, " +
            "playWhenReady=${controller.playWhenReady}, loading=${controller.isLoading}, " +
            "position=${controller.currentPosition}, duration=${controller.duration}, " +
            "speed=${controller.playbackParameters.speed}, commands=${controller.availableCommands}, " +
            "error=${controller.playerError}"
    }

    private fun serviceState(context: Context): String =
        shell("dumpsys activity service ${context.packageName}/${PlayerMediaSessionService::class.java.name}")

    private suspend fun awaitSystemState(
        context: Context,
        stage: String,
        diagnostics: suspend () -> String,
        condition: suspend () -> Boolean,
    ) {
        Log.i("ZeroPlayerSessionTest", "开始等待：$stage")
        try {
            withTimeout(10_000) {
                while (!condition()) delay(100)
            }
        } catch (timeout: TimeoutCancellationException) {
            val actual = runCatching { diagnostics() }.getOrElse { "读取实际状态失败：${it.message}" }
            val service = serviceState(context)
            val message = "等待「$stage」超时；实际状态：$actual\n真实服务：\n$service"
            Log.e("ZeroPlayerSessionTest", message)
            throw AssertionError(message, timeout)
        }
        Log.i("ZeroPlayerSessionTest", "完成等待：$stage")
    }

    /** 只有显式取证的 API 37 模拟器使用系统振动历史；普通回归不依赖厂商 dumpsys 格式。 */
    private suspend fun checkSystemSpeedHaptics(settings: PreferencesSettingsRepository, remote: MediaController) {
        val original = settings.observeSettings().first().gesture.hapticsEnabled
        val event = Regex("""(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*?com\.privateplayer\.media \(uid=.*?performHapticFeedback\(constant=(0|4)\)""")
        fun events() = event.findAll(shell("dumpsys vibrator_manager").substringAfter("Recent vibrations:").substringBefore("Aggregated vibration history:"))
            .map { it.groupValues[1] to it.groupValues[2] }.toSet()
        try {
            for (enabled in listOf(true, false)) {
                settings.setHapticsEnabled(enabled)
                val before = events()
                PlayerSystemTouch(compose.playerBounds(compose.onNode(speedButtonAt("1.375×"))).center).use { finger ->
                    compose.waitUntil(5_000) { compose.onAllNodes(speedOption("0.5×")).fetchSemanticsNodes().isNotEmpty() }
                    val slow = compose.playerBounds(compose.onNode(speedOption("0.5×"))).center
                    finger.moveTo(slow)
                    awaitController(remote, "震动开关=$enabled：慢速试播") { playbackParameters.speed == 0.5f && !isPlaying }
                    repeat(3) { finger.moveTo(slow + androidx.compose.ui.geometry.Offset(it + 1f, 0f)) }
                    finger.moveTo(compose.playerBounds(compose.onNode(speedOption("5×"))).center)
                    awaitController(remote, "震动开关=$enabled：跨档试播") { playbackParameters.speed == 5f && !isPlaying }
                    val origin = compose.playerBounds(compose.onNode(speedOption("1×"))).center
                    finger.moveTo(origin)
                    awaitController(remote, "回方块是一次有效切档") { playbackParameters.speed == 1f && !isPlaying && !playWhenReady }
                    repeat(3) { finger.moveTo(origin + androidx.compose.ui.geometry.Offset(0f, it + 1f)) }
                }
                awaitController(remote, "震动检查取消恢复自定义速度") { playbackParameters.speed == 1.375f && !isPlaying }
                delay(250) // API 37 完成脉冲后才写入 recent history。
                val actual = (events() - before).sortedBy { it.first }.map { it.second }
                assertEquals("系统反馈，开关=$enabled", if (enabled) listOf("0", "4", "4", "4") else emptyList<String>(), actual)
                Log.i("ZeroPlayerSpeedProof", "haptics enabled=$enabled requested=$actual for hold/slow/same×3/fast/origin/same×3/cancel")
            }
        } finally { settings.setHapticsEnabled(original) }
    }

    private fun dispatchSystemPlayPause() {
        val result = shell("cmd media_session dispatch play-pause")
        check(result.isBlank()) { "系统媒体键分发失败：$result" }
    }

    private fun showControls() {
        if (compose.onAllNodesWithContentDescription("播放").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isEmpty()
        ) {
            compose.onNodeWithContentDescription("视频画面").performTouchInput { click() }
        }
    }

    private suspend fun awaitWakeLock(context: Context, held: Boolean, stage: String) {
        awaitSystemState(context, "$stage（held=$held）", diagnostics = {
            "isInteractive=${context.getSystemService(PowerManager::class.java).isInteractive}\n" +
                shell("dumpsys power")
        }) { hasPlaybackWakeLock(context) == held }
    }

    private fun hasPlaybackWakeLock(context: Context): Boolean {
        val dump = shell("dumpsys power")
        val locks = dump.substringAfter("Wake Locks:", missingDelimiterValue = "")
            .substringBefore("\n\n")
        check(locks.isNotBlank()) { "无法读取模拟器的 PowerManager 唤醒锁" }
        return locks.lineSequence().any { it.contains("PARTIAL_WAKE_LOCK") && it.contains("${context.packageName}:playback") }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PlayerMediaSessionService::class.java.name }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }
}
