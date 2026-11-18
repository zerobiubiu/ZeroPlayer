package com.privateplayer.media

import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** 屏幕读数以实际渲染树和字形盒为准，不以源码中的偏移、字号或 alpha 代替。 */
internal class PlayerSpeedFanReadings(private val compose: ComposeTestRule, private val host: RotationTestActivity) {
    private val density get() = host.resources.displayMetrics.density
    private val bubble = hasTestTag("speedFanBubble")
    private val hud = hasTestTag("temporarySpeedHud")
    private val evidence = InstrumentationRegistry.getArguments().getString("speedEvidence") == "true"
    private val reportedLayout = mutableSetOf<String>()
    private val capturedDigits = mutableSetOf<String>()
    private var choiceGlyphs = emptyMap<Float, Rect>()
    val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "播放速度")

    fun assertChoicesFit(rates: List<Float>, viewport: Rect) {
        choiceGlyphs = rates.associateWith { rate ->
            val text = speedRateLabel(rate)
            val layout = glyphs(hasText(text) and hasAnyAncestor(speedOptionRole), text)
            assertTrue("$text 字形超出视频区域：$layout / $viewport", viewport.encloses(layout))
            layout
        }
    }

    fun hasFeedback() = listOf(bubble, hud).any { exists(it) }

    fun check(rate: Float, at: Offset, screen: Rect, log: Boolean = false) {
        val text = speedRateLabel(rate)
        val cap = compose.playerBounds(compose.onNode(bubble, useUnmergedTree = true))
        val hudBox = compose.playerBounds(compose.onNode(hud, useUnmergedTree = true))
        val side = if (screen.width > screen.height) "landscape" else "portrait"
        val captureKey = "$side-digits-${rate.toString().replace('.', 'p')}"
        val firstDecimal = (rate == 2.5f || rate == 0.75f || rate == 0.25f) && capturedDigits.add(captureKey)
        if (firstDecimal) capture(captureKey)
        val ink = glyphs((bubble or hasAnyAncestor(bubble)) and hasText(text), text)
        assertHud(rate, screen)
        assertTrue("键帽或实际数字越界：$cap / $ink / $screen", screen.encloses(cap) && cap.encloses(ink))
        val gap = hypot(maxOf(ink.left - at.x, 0f, at.x - ink.right), maxOf(ink.top - at.y, 0f, at.y - ink.bottom)) / density
        assertTrue("手指盖住键帽数字：$text $gap dp，指针=$at 数字=$ink", gap >= 16f)
        assertFalse("键帽和上方倍速反馈重叠：$cap / $hudBox", cap.overlaps(hudBox))
        if (firstDecimal && rate == 2.5f && minOf(screen.width, screen.height) >= 340 * density) {
            val obscured = choiceGlyphs.filter { (other, rect) -> other != rate && cap.overlaps(rect) }.keys
            assertTrue("常规窗口 $text 键帽压住邻档字形：$obscured，键帽=$cap，字形=$choiceGlyphs", obscured.isEmpty())
        }
        if (log || firstDecimal) Log.i(TAG, "feedback=$text pointer=$at bubble=$cap glyph=$ink gapDp=$gap hud=$hudBox hudY=${(hudBox.center.y - screen.top) / screen.height}")
    }

    /** 最紧窗口的验收不是仅让泡和 HUD 分开：两者都不得横穿任一实际倍率字形。 */
    fun assertNumbersClear(side: String) {
        val cap = compose.playerBounds(compose.onNode(bubble, useUnmergedTree = true))
        val feedback = compose.playerBounds(compose.onNode(hud, useUnmergedTree = true))
        assertEquals(13, choiceGlyphs.size)
        val underCap = choiceGlyphs.filterValues(cap::overlaps).keys
        val underHud = choiceGlyphs.filterValues(feedback::overlaps).keys
        assertTrue("$side 键帽压字=$underCap / HUD压字=$underHud；cap=$cap hud=$feedback 13字形=$choiceGlyphs",
            underCap.isEmpty() && underHud.isEmpty())
        Log.i(TAG, "$side 13 glyphs clear of both: cap=$cap hud=$feedback glyphs=$choiceGlyphs")
    }

    fun assertHud(rate: Float, screen: Rect) {
        val text = speedRateLabel(rate)
        val numeric = SemanticsMatcher("倍率 $text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.substringBefore(' ') == text } == true
        }
        assertTrue("HUD 未显示引擎的 $text", exists((hud or hasAnyAncestor(hud)) and numeric))
        val bounds = compose.playerBounds(compose.onNode(hud, useUnmergedTree = true))
        val fraction = (bounds.center.y - screen.top) / screen.height
        assertTrue("HUD 不在视频上四分之一：$fraction", abs(fraction - 0.25f) < 0.035f && screen.encloses(bounds))
    }

    /** 在生产 HUD 刚因 MOVE 入场时逐帧取真实窗口，避免自动推进测试时钟跳到 150ms 动画末尾。 */
    fun moveAndSampleArrival(finger: PlayerSystemTouch, at: Offset, rate: Float, side: String, speed: () -> Float) {
        if (!evidence) { finger.moveTo(at); return }
        val clock = compose.mainClock
        val auto = clock.autoAdvance
        clock.autoAdvance = false
        try {
            finger.moveTo(at)
            val matcher = (hud or hasAnyAncestor(hud)) and hasText("${speedRateLabel(rate)} 临时倍速")
            val deadline = SystemClock.uptimeMillis() + 4_000L
            do {
                clock.advanceTimeByFrame()
                if (speed() == rate && exists(matcher)) break
                check(SystemClock.uptimeMillis() < deadline) { "首个可见 $rate HUD 超时，真实引擎=${speed()}" }
                SystemClock.sleep(4)
            } while (true)
            val first = clock.currentTime
            val scale = Settings.Global.getFloat(host.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            for ((index, advance) in listOf(0L, 32L, 160L).withIndex()) {
                if (advance > 0) clock.advanceTimeBy(advance)
                compose.waitForIdle()
                assertEquals(rate, speed(), 0f)
                capture("$side-frame${index + 1}")
                Log.i(TAG, "$side motionFrame=${index + 1} composeMs=${clock.currentTime - first} animatorScale=$scale rate=$rate")
            }
        } finally { clock.autoAdvance = auto }
    }

    private fun glyphs(matcher: SemanticsMatcher, text: String): Rect {
        val node = compose.onNode(matcher and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(results)) }
        val layout = results.single()
        assertEquals(text, layout.layoutInput.text.text)
        val box = compose.playerBounds(node)
        val end = layout.getLineEnd(0, visibleEnd = true)
        assertTrue("文本未完整显示：$text 行=${layout.lineCount} 末字=$end", layout.lineCount == 1 && end == text.length && !layout.isLineEllipsized(0))
        // BasicText(String) 的语义操作用父 maxWidth 重排 MultiParagraph，却配实际 layoutSize。
        // 比较完整字符盒与真实 Text 节点；不能把段落可用宽大于文字宽误判成数字被裁。
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (i in text.indices) {
            val glyph = layout.getBoundingBox(i).translate(box.topLeft)
            assertTrue("$text 第 $i 字越界：$glyph / $box", box.encloses(glyph))
            left = minOf(left, glyph.left)
            top = minOf(top, glyph.top)
            right = maxOf(right, glyph.right)
            bottom = maxOf(bottom, glyph.bottom)
        }
        val ink = Rect(left, top, right, bottom)
        if (layout.hasVisualOverflow && reportedLayout.add(text)) {
            Log.i(TAG, "text=$text node=$box glyphs=$ink paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} layout=${layout.size} visibleEnd=$end")
        }
        return ink
    }

    /** 窗口合成可能比语义树晚一帧。只保存同一 DOWN 下两处文字和 HUD 箭头均已出现在屏幕的原图。 */
    fun captureComposited(side: String, rate: Float) {
        val label = speedRateLabel(rate)
        val text = "$label 临时倍速"
        val capText = glyphs((bubble or hasAnyAncestor(bubble)) and hasText(label), label)
        val hudText = glyphs((hud or hasAnyAncestor(hud)) and hasText(text), text)
        val hudBox = compose.playerBounds(compose.onNode(hud, useUnmergedTree = true))
        val arrow = Rect(hudBox.left, hudBox.top, hudText.left, hudBox.bottom)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val start = SystemClock.uptimeMillis()
        var attempts = 0
        while (true) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val shot = requireNotNull(automation.takeScreenshot())
            val image = if (shot.config == Bitmap.Config.HARDWARE) requireNotNull(shot.copy(Bitmap.Config.ARGB_8888, false)) else shot
            try {
                attempts++
                val capInk = image.hasInk(capText, blue = false)
                val hudInk = image.hasInk(hudText, blue = false)
                val arrowInk = image.hasInk(arrow, blue = true)
                if (capInk && hudInk && arrowInk) {
                    if (evidence) save("$side-confirmed", image, start)
                    Log.i(TAG, "$side composited=$label frames=$attempts capturedInMs=${SystemClock.uptimeMillis() - start}")
                    return
                }
                if (SystemClock.uptimeMillis() - start >= 1_500) {
                    save("$side-not-composited", image, start)
                    error("真实屏幕未出现同一候选的两份数字/箭头：$label cap=$capInk hud=$hudInk arrow=$arrowInk")
                }
            } finally {
                if (image !== shot) image.recycle()
                shot.recycle()
            }
        }
    }

    private fun Bitmap.hasInk(rect: Rect, blue: Boolean): Boolean {
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt() step 2) {
            for (x in rect.left.toInt() until rect.right.toInt() step 2) {
                val p = getPixel(x, y)
                val r = (p ushr 16) and 255
                val g = (p ushr 8) and 255
                val b = p and 255
                val ink = if (blue) b > 145 && b > r + 75 && b > g + 45 && g > r + 18 else minOf(r, g, b) > 240
                if (ink && ++count >= (if (blue) 8 else 40)) return true
            }
        }
        return false
    }

    fun capture(suffix: String) {
        if (!evidence) return
        val stamp = SystemClock.uptimeMillis()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { save(suffix, image, stamp) } finally { image.recycle() }
    }

    private fun save(suffix: String, image: Bitmap, stamp: Long) {
        val file = File(host.cacheDir, "speed-fan-$suffix.png")
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        Log.i(TAG, "screen=$suffix uptime=$stamp size=${image.width}x${image.height}")
    }

    private fun exists(m: SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun Rect.encloses(r: Rect) = r.left >= left - 1 && r.top >= top - 1 && r.right <= right + 1 && r.bottom <= bottom + 1
    private companion object { const val TAG = "ZeroPlayerSpeedProof" }
}
