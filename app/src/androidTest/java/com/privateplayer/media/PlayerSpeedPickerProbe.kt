package com.privateplayer.media

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private val button = hasContentDescription("播放速度") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
internal val speedOptionRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
internal val selectedSpeedOption = speedOptionRole and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
private val preview = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在试播，松开选定")
internal fun speedButtonAt(label: String) = button and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)
internal fun speedOption(label: String) = speedOptionRole and hasText(label)
internal fun speedRateLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"

/** 选速只用系统触摸；自定义起速和切片用真实会话命令。断言独立于扇面绘图/命中函数。 */
internal class PlayerSpeedPickerProbe(
    private val compose: ComposeTestRule,
    private val host: RotationTestActivity,
    private val engine: PlaybackEngine,
) {
    private val snapshot get() = engine.snapshot.value
    private val sectors = listOf(listOf(0.75f, 0.5f, 0.25f), listOf(2f, 2.5f, 3f), listOf(3.5f, 4f, 5f), listOf(6f, 7f, 8f))
    private val video = hasContentDescription("视频画面")
    private val choice get() = speedOptionRole
    private val readings = PlayerSpeedFanReadings(compose, host)
    private val density get() = host.resources.displayMetrics.density

    suspend fun exercise(setRegularSpeed: (Float) -> Unit) {
        compose.waitUntil(12_000) { snapshot.isPlaying && snapshot.positionMs > 0 }
        val mediaId = snapshot.mediaId
        ensureControls()
        selectWhilePlayingPastControlsTimeout()
        tap(hasContentDescription("暂停"))
        compose.waitUntil(5_000) { snapshot.status == PlaybackStatus.PAUSED && !snapshot.playWhenReady }
        for (landscape in listOf(false, true)) {
            if (landscape) {
                tap(hasContentDescription("旋转屏幕"))
                compose.waitUntil(12_000) { bounds(video).let { it.width > it.height } }
            }
            val screen = bounds(video)
            assertEquals(landscape, screen.width > screen.height)
            val anchor = bounds(button)
            val side = if (landscape) "landscape" else "portrait"
            val outside = Offset(screen.left + screen.width * 0.92f, screen.top + screen.height * 0.12f)
            val start = snapshot.speed
            tapPlayerScreen(anchor.center)
            awaitPanel()
            val cells = checkLayout(screen, anchor, side)
            assertCurrent(start)
            val pane = bounds(readings.pane)
            assertImmersive()
            for (rate in sectors.flatten() + 1f) {
                tap(speedOption(speedRateLabel(rate)))
                awaitClosedAt(rate)
                assertEquals("${speedRateLabel(rate)} 按钮跳动", anchor, bounds(button))
                Log.i(TAG, "$side tap=${speedRateLabel(rate)} engine=${snapshot.speed} anchor=$anchor")
                tapPlayerScreen(anchor.center)
                awaitPanel()
                assertCurrent(rate)
            }
            // 系统返回和点外只关面板；真实 onBack 会 finish 宿主，不能被空回调掩盖。
            pressBack()
            awaitClosedAt(1f)
            tapPlayerScreen(anchor.center)
            awaitPanel()
            tapPlayerScreen(outside)
            awaitClosedAt(1f)
            assertFalse(host.isFinishing)
            assertEquals(mediaId, snapshot.mediaId)

            compose.runOnIdle { setRegularSpeed(CUSTOM) }
            awaitClosedAt(CUSTOM)
            val corner = PlayerSpeedFanPaths(pane, cells, density).outsideArc
            assertTrue("弧外样点必须仍在外接方框内：$corner $pane", pane.contains(corner))
            scrubThroughAll(anchor.center, cells, corner, side, screen)
            exerciseOrigin(anchor.center, cells, screen, corner, setRegularSpeed, side)
            compose.runOnIdle { setRegularSpeed(CUSTOM) }
            awaitClosedAt(CUSTOM)
            for (end in listOf("arc", "cancel", "back", "second")) {
                val before = snapshot
                PlayerSystemTouch(anchor.center).use { finger ->
                    awaitPanel()
                    move(finger, 5f, cells, side, screen)
                    when (end) {
                        "arc" -> {
                            finger.moveTo(corner)
                            awaitNeutral(CUSTOM)
                            finger.up()
                        }
                        "cancel" -> finger.close()
                        "second" -> {
                            finger.tapSecond(screen.center)
                            awaitClosedAt(CUSTOM)
                            finger.up()
                        }
                        else -> { pressBack(); awaitClosedAt(CUSTOM); finger.up() }
                    }
                }
                awaitClosedAt(CUSTOM)
                assertEquals(before.positionMs, snapshot.positionMs)
                assertEquals(mediaId, snapshot.mediaId)
                assertFalse(host.isFinishing)
                Log.i(TAG, "$side $end after=${snapshot.speed}@${snapshot.positionMs}")
            }
            if (landscape) {
                tap(hasContentDescription("播放"))
                compose.waitUntil(4_000) { snapshot.isPlaying }
                PlayerSystemTouch(anchor.center).use { finger ->
                    awaitPanel()
                    move(finger, 0.5f, cells, side, screen)
                    val at = snapshot.positionMs
                    compose.waitUntil(4_000) { snapshot.isPlaying && snapshot.speed == 0.5f && snapshot.positionMs > at }
                    readings.check(0.5f, cells.getValue(0.5f).center, screen, log = true)
                    Log.i(TAG, "landscape playing held beforeUP=0.5@$at→${snapshot.positionMs}")
                    finger.up()
                    awaitClosedAt(0.5f)
                    assertTrue(snapshot.isPlaying)
                    Log.i(TAG, "landscape playing afterUP=${snapshot.speed}@${snapshot.positionMs}")
                }
            }
        }
        // 画面的长按仍是临时倍速；播放中等待真正推进，松手须恢复刚从板中选定的常规档。
        tap(button)
        awaitPanel()
        tap(speedOption("0.75×"))
        awaitClosedAt(0.75f)
        val screen = bounds(video)
        PlayerSystemTouch(Offset(screen.center.x, screen.top + screen.height * 0.35f)).use { finger ->
            compose.waitUntil(5_000) { snapshot.speed == 2f && snapshot.isPlaying }
            val at = snapshot.positionMs
            compose.waitUntil(4_000) { snapshot.positionMs > at && snapshot.speed == 2f }
            readings.assertHud(2f, screen)
            compose.onAllNodes(choice).assertCountEquals(0)
            finger.up()
            compose.waitUntil(5_000) { snapshot.speed == 0.75f && snapshot.isPlaying }
            Log.i(TAG, "video long press 0.75 → 2 → ${snapshot.speed}; playing $at → ${snapshot.positionMs} ms")
        }
    }

    /** 只复验这次受影响的布局：小窗两向 0.25×，常规窗横向 2.5× 作为正对照。 */
    fun exerciseFeedbackClearance(setRegularSpeed: (Float) -> Unit) {
        compose.waitUntil(12_000) { snapshot.isPlaying && snapshot.positionMs > 0 }
        ensureControls()
        tap(hasContentDescription("暂停"))
        compose.waitUntil(4_000) { snapshot.status == PlaybackStatus.PAUSED && !snapshot.playWhenReady }
        compose.runOnIdle { setRegularSpeed(CUSTOM) }
        awaitClosedAt(CUSTOM)
        val initial = bounds(video)
        val tight = minOf(initial.width, initial.height) / density < 340f
        val baseline = hasText("已选 1.375×")
        for (landscape in if (tight) listOf(false, true) else listOf(true)) {
            if (landscape) {
                tap(hasContentDescription("旋转屏幕"))
                compose.waitUntil(12_000) { bounds(video).let { it.width > it.height } }
            }
            val screen = bounds(video)
            val anchor = bounds(button)
            val side = if (landscape) "landscape" else "portrait"
            tap(button)
            awaitPanel()
            compose.onNode(baseline).assertIsDisplayed()
            val cells = checkLayout(screen, anchor, side)
            pressBack()
            awaitClosedAt(CUSTOM)
            val frozen = snapshot
            PlayerSystemTouch(anchor.center).use { finger ->
                awaitPanel()
                awaitNeutral(CUSTOM)
                compose.onNode(baseline).assertIsDisplayed()
                val rate = if (tight) 0.25f else 2.5f
                move(finger, rate, cells, side, screen)
                compose.onAllNodes(baseline).assertCountEquals(0)
                readings.captureComposited(side, rate)
                readings.assertNumbersClear("$side ${speedRateLabel(rate)}")
                assertEquals(rate, snapshot.speed, 0f)
                assertEquals(frozen.positionMs, snapshot.positionMs)
                assertEquals(frozen.mediaId, snapshot.mediaId)
                assertFalse(snapshot.playWhenReady)
            }
            awaitClosedAt(CUSTOM)
            Log.i(TAG, "$side baseline visible on tap/still, hidden while previewing, CANCEL=${snapshot.speed}@${snapshot.positionMs}")
        }
    }

    /** 以 2× 作反例，分别区分不动、系统 slop 内颤动、有意在方块内移动和从另一档折返。 */
    private fun exerciseOrigin(anchor: Offset, cells: Map<Float, Rect>, screen: Rect, outside: Offset,
        setRegularSpeed: (Float) -> Unit, side: String) {
        val slop = ViewConfiguration.get(host).scaledTouchSlop.toFloat()
        val deliberate = anchor + Offset(slop + 2 * density, 0f)
        assertTrue("主动样点必须仍在方块内，slop=$slop at=$deliberate", cells.getValue(1f).contains(deliberate))
        val before = snapshot.positionMs
        for (path in listOf("原地", "阈内", "方块内", "折返方块", "弧外")) {
            compose.runOnIdle { setRegularSpeed(2f) }
            awaitClosedAt(2f)
            PlayerSystemTouch(anchor).use { finger ->
                awaitPanel()
                awaitNeutral(2f)
                when (path) {
                    "阈内" -> for (scale in listOf(0.6f, -0.7f, 0.8f)) {
                        finger.moveTo(anchor + Offset(scale * slop, -0.15f * slop))
                        awaitNeutral(2f)
                    }
                    "方块内" -> {
                        finger.moveTo(deliberate)
                        awaitPreview(1f)
                        readings.check(1f, deliberate, screen, log = true)
                    }
                    "折返方块" -> {
                        move(finger, 5f, cells, side, screen)
                        readings.capture("$side-before-return")
                        move(finger, 1f, cells, side, screen)
                    }
                    "弧外" -> {
                        move(finger, 5f, cells, side, screen)
                        finger.moveTo(outside)
                        awaitNeutral(2f)
                    }
                }
                assertCurrent(2f)
                val held = snapshot.speed
                finger.up()
                awaitClosedAt(if (path == "方块内" || path == "折返方块") 1f else 2f)
                assertEquals(PlaybackStatus.PAUSED, snapshot.status)
                assertEquals(before, snapshot.positionMs)
                Log.i(TAG, "$side $path slopPx=$slop anchor=$anchor deliberate=$deliberate beforeUP=$held afterUP=${snapshot.speed} position=$before")
            }
        }
    }

    /** 另一条独立设备轨迹：用可见半径/标签测量合成非中心点，检查每次输入及整段真实速度流。 */
    suspend fun exerciseBoundaries(setRegularSpeed: (Float) -> Unit) = coroutineScope {
        compose.waitUntil(12_000) { snapshot.isPlaying && snapshot.positionMs > 0 }
        ensureControls()
        tap(hasContentDescription("暂停"))
        compose.waitUntil(4_000) { snapshot.status == PlaybackStatus.PAUSED && !snapshot.playWhenReady }
        for (landscape in listOf(false, true)) {
            val side = if (landscape) "landscape" else "portrait"
            val screen = bounds(video)
            assertEquals(landscape, screen.width > screen.height)
            val anchor = bounds(button)
            compose.runOnIdle { setRegularSpeed(2f) }
            awaitClosedAt(2f)
            // 同一暂停帧，先真正隐藏控制栏取裸画面，再按住；不以常规控件的底部渐变冒充视频原色。
            tapPlayerScreen(Offset(screen.center.x, screen.top + screen.height * 0.35f))
            compose.waitUntil(5_000) { !exists(button) }
            readings.capture("$side-base")
            ensureControls()
            val frozen = snapshot
            val observed = CopyOnWriteArrayList<Float>()
            val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                engine.snapshot.map { it.speed }.distinctUntilChanged().collect { observed.add(it) }
            }
            try {
                PlayerSystemTouch(anchor.center).use { finger ->
                    awaitPanel()
                    val cells = checkLayout(screen, anchor, side)
                    val paths = PlayerSpeedFanPaths(bounds(readings.pane), cells, density)
                    val seed = if (landscape) 0x38B else 0x38A
                    val samples = if (landscape) 12 else 6
                    val points = paths.trace(seed, samples)
                    for ((i, point) in points.withIndex()) {
                        if (point.stage == "外弧再入") readings.moveAndSampleArrival(finger, point.at, 5f, side) { snapshot.speed }
                        else finger.moveTo(point.at)
                        // 注入后同步 UI；不可将本次观察到的速度反过来当作下一步的预期。
                        if (point.stage == "外弧外缘") awaitNeutral(2f)
                        else if (point.allowed.size == 1) awaitPreview(point.allowed.single())
                        else {
                            // 此时前后两档均合法。先同步当前候选，再等同一档的真实引擎；
                            // 不能在输入刚投递时取旧快照，稍后拿旧数字去找已经更新的键帽。
                            val marked = compose.onNode(choice and preview).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
                            val candidate = point.allowed.singleOrNull { speedRateLabel(it) == marked }
                                ?: error("seed=$seed #$i $point 意外候选=$marked，真实流=$observed")
                            awaitPreview(candidate)
                        }
                        val actual = snapshot.speed
                        assertTrue("seed=$seed #$i $point actual=$actual history=$observed", actual in point.allowed)
                        assertCurrent(2f)
                        if (point.stage != "外弧外缘") readings.check(actual, point.at, screen)
                        assertEquals(frozen.positionMs, snapshot.positionMs)
                        assertEquals(frozen.mediaId, snapshot.mediaId)
                        assertFalse(snapshot.playWhenReady)
                        Log.i(TAG, "trace=$side seed=$seed #$i ${point.stage} @${point.at} expect=${point.allowed} got=$actual")
                    }
                    val last = points.last().at
                    readings.check(5f, last, screen, log = true)
                    readings.capture("$side-held")
                    // 旧临时提示 1200ms 后消失；现在同指不动也必须保留 HUD。
                    val start = SystemClock.uptimeMillis()
                    SystemClock.sleep(1_450)
                    readings.check(5f, last, screen)
                    Log.i(TAG, "$side heldHUDms=${SystemClock.uptimeMillis() - start} rate=${snapshot.speed}@${snapshot.positionMs}")
                    finger.up()
                }
                awaitClosedAt(5f)
                assertEquals(listOf(2f, 2.5f, 4f, 5f, 2.5f, 8f, 2.5f, 5f, 2f, 5f), observed.toList())
                Log.i(TAG, "$side trace transitions=$observed")
            } finally { watcher.cancelAndJoin() }
            val burst = CopyOnWriteArrayList<Float>()
            val fastWatcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                engine.snapshot.map { it.speed }.distinctUntilChanged().collect { burst.add(it) }
            }
            try {
                PlayerSystemTouch(anchor.center).use { finger ->
                    awaitPanel()
                    val cells = sectors.flatten().associateWith { bounds(speedOption(speedRateLabel(it))) }
                    val points = PlayerSpeedFanPaths(bounds(readings.pane), cells, density).burst()
                    finger.moveTo(cells.getValue(0.5f).center)
                    awaitPreview(0.5f)
                    val times = finger.burstAndUp(points, if (landscape) 8L else 11L)
                    awaitClosedAt(4f)
                    assertTrue("不是快速连续轨迹：${times.toList()}", times.last() - times.first() < 400 && times.last() - times[times.lastIndex - 1] < 40)
                    Log.i(TAG, "$side burst points=$points MOVE…UP msSinceDOWN=${times.toList()} speeds=$burst final=${snapshot.speed}@${snapshot.positionMs}")
                }
                assertEquals(5f, burst.first(), 0f)
                assertEquals(4f, burst.last(), 0f)
                assertTrue("中环快速往返误入其它环/原速：$burst", burst.drop(1).all { it in setOf(0.5f, 2.5f, 4f, 7f) })
                assertEquals(frozen.positionMs, snapshot.positionMs)
                tap(button)
                awaitPanel()
                assertCurrent(4f)
                pressBack()
                awaitClosedAt(4f)
            } finally { fastWatcher.cancelAndJoin() }
            if (!landscape) {
                // 保持旧指针切换宿主配置，不能把旧 UP 提交到新布局。
                PlayerSystemTouch(anchor.center).use { finger ->
                    awaitPanel()
                    finger.moveTo(bounds(speedOption("0.25×")).center)
                    awaitPreview(0.25f)
                    compose.runOnIdle { host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    compose.waitUntil(12_000) { !exists(choice) && bounds(video).let { it.width > it.height } }
                    awaitClosedAt(4f)
                    finger.up()
                    awaitClosedAt(4f)
                    assertEquals(frozen.positionMs, snapshot.positionMs)
                    Log.i(TAG, "rotation while DOWN: 4→0.25→${snapshot.speed}, unchanged after old UP")
                }
            }
        }
    }

    private suspend fun selectWhilePlayingPastControlsTimeout() = coroutineScope {
        val speeds = CopyOnWriteArrayList<Float>()
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            engine.snapshot.map { it.speed }.distinctUntilChanged().collect { speeds.add(it) }
        }
        try {
            tap(button)
            awaitPanel()
            assertCurrent(1f)
            val opened = SystemClock.uptimeMillis()
            val before = snapshot.positionMs
            compose.waitUntil(10_000) { SystemClock.uptimeMillis() - opened >= 4_300 && snapshot.positionMs > before + 3_500 }
            assertCurrent(1f)
            tap(speedOption("0.5×"))
            awaitClosedAt(0.5f)
            compose.waitUntil(3_000) { speeds.lastOrNull() == 0.5f }
            assertEquals(listOf(1f, 0.5f), speeds.toList())
            assertTrue(snapshot.isPlaying)
            Log.i(TAG, "playing picker held ${SystemClock.uptimeMillis() - opened} ms; position=$before → ${snapshot.positionMs}, speed=$speeds")
        } finally { watcher.cancelAndJoin() }
    }

    private suspend fun scrubThroughAll(anchor: Offset, cells: Map<Float, Rect>, corner: Offset, side: String, screen: Rect) = coroutineScope {
        val before = snapshot
        val observed = CopyOnWriteArrayList<Float>()
        val expected = mutableListOf(CUSTOM)
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            engine.snapshot.collect {
                assertEquals("选速不应 seek", before.positionMs, it.positionMs)
                assertEquals(PlaybackStatus.PAUSED, it.status)
                assertFalse(it.playWhenReady)
                if (it.speed != observed.lastOrNull()) observed += it.speed
            }
        }
        try {
            PlayerSystemTouch(anchor).use { finger ->
                awaitPanel()
                assertCurrent(CUSTOM)
                Log.i(TAG, "$side DOWN=$anchor base=${snapshot.speed}@${snapshot.positionMs}")
                for (rate in sectors.flatten()) {
                    move(finger, rate, cells, side, screen)
                    expected += rate
                    assertCurrent(CUSTOM)
                    if (rate == 0.5f || rate == 5f) {
                        if (rate == 0.5f) {
                            move(finger, 1f, cells, side, screen)
                            expected += 1f
                        } else {
                            finger.moveTo(corner)
                            awaitNeutral(CUSTOM)
                            expected += CUSTOM
                        }
                    }
                }
                repeat(3) { finger.moveTo(cells.getValue(8f).center + Offset(it + 1f, 0f)) }
                Log.i(TAG, "$side beforeUP=${snapshot.speed}@${snapshot.positionMs}")
                finger.up()
            }
            awaitClosedAt(8f)
            kotlinx.coroutines.delay(350) // 跨过一次引擎采样；不能先恢复再重新设置。
            assertEquals(expected, observed.toList())
            assertEquals(before.mediaId, snapshot.mediaId)
            Log.i(TAG, "$side afterUP=${snapshot.speed}@${snapshot.positionMs} observed=$observed")
        } finally { watcher.cancelAndJoin() }
    }

    fun changeSourceWhileHeld(setCustom: () -> Unit, changeSource: () -> Unit) {
        compose.waitUntil(12_000) { snapshot.isPlaying && snapshot.positionMs > 0 }
        ensureControls()
        tap(hasContentDescription("暂停"))
        compose.waitUntil(4_000) { snapshot.status == PlaybackStatus.PAUSED }
        compose.runOnIdle(setCustom)
        awaitClosedAt(CUSTOM)
        val first = snapshot.mediaId
        PlayerSystemTouch(bounds(button).center).use { finger ->
            awaitPanel()
            finger.moveTo(bounds(speedOption("5×")).center)
            compose.waitUntil(4_000) { snapshot.speed == 5f }
            compose.runOnIdle(changeSource)
            compose.waitUntil(10_000) { snapshot.mediaId != null && snapshot.mediaId != first && snapshot.isPlaying }
            val second = snapshot.mediaId
            compose.waitUntil(4_000) { snapshot.speed == CUSTOM && !exists(choice) }
            val at = snapshot.positionMs
            finger.up()
            compose.waitUntil(4_000) { snapshot.positionMs > at }
            assertEquals(second, snapshot.mediaId)
            assertEquals(CUSTOM, snapshot.speed, 0f)
            Log.i(TAG, "source $first→$second held=5 new/afterUP=${snapshot.speed}@${snapshot.positionMs}")
        }
    }

    private fun move(finger: PlayerSystemTouch, rate: Float, cells: Map<Float, Rect>, side: String, screen: Rect) {
        val at = cells.getValue(rate).center
        finger.moveTo(at)
        awaitPreview(rate)
        readings.check(rate, at, screen, log = rate == 0.25f)
        Log.i(TAG, "$side MOVE=$at held=${snapshot.speed}@${snapshot.positionMs} ${snapshot.status}")
    }

    private fun awaitPreview(rate: Float) {
        compose.waitUntil(4_000) { snapshot.speed == rate && exists(speedOption(speedRateLabel(rate)) and preview) }
        compose.onAllNodes(choice and preview).assertCountEquals(1)
    }

    private fun awaitNeutral(base: Float) = compose.waitUntil(4_000) {
        snapshot.speed == base && exists(choice) && !exists(choice and preview) && !readings.hasFeedback()
    }

    private fun checkLayout(screen: Rect, anchor: Rect, side: String): Map<Float, Rect> {
        val minimum = 48 * host.resources.displayMetrics.density - 1
        assertTrue("倍速按钮触控目标不足 48dp：$anchor", minOf(anchor.width, anchor.height) >= minimum)
        val rates = sectors.flatten() + 1f
        compose.onAllNodes(choice).assertCountEquals(rates.size)
        val pane = bounds(readings.pane)
        assertEquals("扇面正方形外接框", pane.width, pane.height, 1f)
        assertTrue("紧凑扇面半径超过 224dp：$pane", pane.width <= 224 * density + 1)
        val cells = rates.associateWith { rate ->
            val option = speedOption(speedRateLabel(rate))
            compose.onNode(option).assertIsDisplayed()
            bounds(option).also { cell ->
                assertTrue("${speedRateLabel(rate)} 超出可用区域：$cell, $screen", cell.left >= screen.left && cell.right <= screen.right && cell.top >= screen.top && cell.bottom <= screen.bottom)
            }
        }
        assertTrue("扇面底边不足 16dp", screen.bottom - cells.getValue(1f).bottom >= 16 * host.resources.displayMetrics.density - 1)
        cells.forEach { (rate, box) ->
            assertTrue("$rate 的标签与邻档相交", cells.none { (other, b) -> rate != other && box.overlaps(b) })
        }
        val origin = cells.getValue(1f).center
        assertTrue("原点脱离左下入口：$origin / $anchor", anchor.contains(origin) && origin.x < screen.center.x && origin.y > screen.center.y)
        fun delta(rate: Float) = cells.getValue(rate).center - origin
        fun angle(rate: Float) = delta(rate).let { atan2(it.x, -it.y) }
        for (sector in sectors) {
            val radii = sector.map { delta(it).getDistance() }
            assertTrue("扇内由近至远不符：$sector $radii", radii.zipWithNext().all { (a, b) -> a < b })
        }
        for (ring in 0..2) {
            val angles = sectors.map { angle(it[ring]) }
            assertTrue("扇序未从正上转到右侧：$angles", angles.zipWithNext().all { (a, b) -> a < b } && angles.first() < 0.52f && angles.last() > 1.0f)
        }
        assertTrue("0.25× 应最高", cells.getValue(0.25f).center.y == cells.values.minOf { it.center.y })
        assertTrue("8× 应最右", cells.getValue(8f).center.x == cells.values.maxOf { it.center.x })
        assertTrue("包围框角未落在外弧之外", (Offset(cells.getValue(8f).center.x, cells.getValue(0.25f).center.y) - origin).getDistance() > rates.maxOf { delta(it).getDistance() } * 1.15f)
        readings.assertChoicesFit(rates, screen)
        val reach = rates.maxOf { (cells.getValue(it).center - anchor.center).getDistance() } / density
        Log.i(TAG, "$side measured: density=$density font=${host.resources.configuration.fontScale} pane=$pane radiusDp=${pane.width / density} shortSideFraction=${pane.width / minOf(screen.width, screen.height)} reachDp=$reach bottomDp=${(screen.bottom - cells.getValue(1f).bottom) / density}")
        Log.i(TAG, "$side viewport=$screen font=${host.resources.configuration.fontScale} origin=$origin anchor=$anchor cells=$cells")
        return cells
    }

    private fun assertImmersive() = compose.runOnIdle {
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(host.window.decorView))
        assertFalse("选速板不能显示状态栏", insets.isVisible(WindowInsetsCompat.Type.statusBars()))
        assertFalse("选速板不能显示导航栏", insets.isVisible(WindowInsetsCompat.Type.navigationBars()))
    }

    private fun ensureControls() {
        if (!exists(button)) {
            val screen = bounds(video)
            tapPlayerScreen(Offset(screen.center.x, screen.top + screen.height * 0.35f))
            compose.waitUntil(5_000) { exists(button) }
        }
    }

    private fun assertCurrent(rate: Float) {
        if (rate == 1f || sectors.any { rate in it }) {
            compose.onAllNodes(selectedSpeedOption).assertCountEquals(1)
            compose.onNode(hasText(speedRateLabel(rate)) and selectedSpeedOption).assertIsDisplayed()
        } else compose.onAllNodes(selectedSpeedOption).assertCountEquals(0)
    }

    private fun awaitPanel() = compose.waitUntil(5_000) { exists(speedOption("1×")) }
    private fun awaitClosedAt(rate: Float) = compose.waitUntil(6_000) {
        snapshot.speed == rate && !exists(choice) && exists(speedButtonAt(speedRateLabel(rate))) && !readings.hasFeedback()
    }
    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(matcher: SemanticsMatcher) = compose.playerBounds(compose.onNode(matcher))
    private fun tap(matcher: SemanticsMatcher) = tapPlayerScreen(bounds(matcher).center)


    private companion object {
        const val TAG = "ZeroPlayerSpeedProof"
        const val CUSTOM = 1.375f
    }
}
