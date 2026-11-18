package com.privateplayer.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.HEIGHT
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.HIDDEN_DELAY_MS
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.WIDTH
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.brightness
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.difference
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.ink
import com.privateplayer.media.MpvSubtitleTestFrames.Companion.near
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MpvSubtitleRenderingInstrumentedTest {
    @Test
    fun embeddedSrtChangesFaceWeightSlantAndSizeOnTheSamePausedFrame() = runBlocking {
        val normal = SubtitlePreferences(
            font = SubtitleFont.SERIF, fontSizeScale = 1.3f,
            textColor = SubtitleTextColor.CYAN, background = SubtitleBackground.NONE,
        )
        withMpvSubtitleFrame(normal) { clean ->
            select(srt)
            engine.setSubtitleDelay(0)
            val serif = frame("初始化前设置的青色 SRT 字形") { ink(clean, it, CYAN).count > 60 }
            val original = ink(clean, serif, CYAN)
            suspend fun variant(label: String, style: SubtitlePreferences, vararg differentFrom: IntArray): IntArray {
                engine.setSubtitleStyle(style)
                return frame(label) { next ->
                    ink(clean, next, CYAN).count > 60 && differentFrom.all { difference(it, next) > 60 }
                }
            }

            val mono = variant("等宽与衬线字族的字形不同", normal.copy(font = SubtitleFont.MONOSPACE), serif)
            variant("默认字族区别于衬线和等宽", normal.copy(font = SubtitleFont.DEFAULT), serif, mono)
            engine.setSubtitleStyle(normal)
            frame("恢复衬线常体") { difference(serif, it) <= 4 }

            val bold = variant("只切粗体", normal.copy(fontStyle = SubtitleFontStyle.BOLD), serif)
            val both = variant("粗体再加斜体", normal.copy(fontStyle = SubtitleFontStyle.BOLD_ITALIC), bold, serif)
            variant("去粗留斜", normal.copy(fontStyle = SubtitleFontStyle.ITALIC), both, bold, serif)
            engine.setSubtitleStyle(normal.copy(fontSizeScale = 2f))
            frame("单行字号增大，着色字形的宽高均增加") {
                val larger = ink(clean, it, CYAN)
                larger.width > original.width * 1.2 && larger.height > original.height * 1.2
            }
            engine.setSubtitleStyle(normal)
            frame("还原字族、字形与字号后恢复原帧") { difference(serif, it) <= 4 }
        }
    }

    @Test
    fun srtOffsetsMoveInBothDirectionsAndBoxesReallyCompositeOverVideo() = runBlocking {
        val base = SubtitlePreferences(fontSizeScale = 1.3f, textColor = SubtitleTextColor.YELLOW, background = SubtitleBackground.NONE)
        withMpvSubtitleFrame(base) { clean ->
            select(srt)
            engine.setSubtitleDelay(0)
            val center = frame("默认位置的黄色 SRT") { ink(clean, it, YELLOW).count > 60 }
            val atZero = ink(clean, center, YELLOW)
            engine.setSubtitleStyle(base.copy(verticalOffsetPercent = -10))
            frame("负偏移下移，末行未被裁掉") {
                val low = ink(clean, it, YELLOW)
                low.top > atZero.top + HEIGHT / 16 && low.height >= atZero.height - 3
            }
            val raised = base.copy(verticalOffsetPercent = 30, textColor = SubtitleTextColor.CYAN)
            engine.setSubtitleStyle(raised)
            val plain = frame("正偏移上移且前景变青") {
                val high = ink(clean, it, CYAN)
                high.count > 60 && high.top < atZero.top - HEIGHT / 4
            }
            engine.setSubtitleStyle(raised.copy(background = SubtitleBackground.NAVY))
            val navy = frame("文字保留，出现深蓝实底框") {
                ink(clean, it, CYAN).count > 60 && ink(clean, it, NAVY).count > 200
            }
            val samples = opaqueBoxSamples(clean, plain, navy)
            engine.setSubtitleStyle(raised.copy(background = SubtitleBackground.SOLID))
            frame("同两种视频底色上均为实黑") { pixels -> samples.all { near(pixels[it], 0, 12) } }
            engine.setSubtitleStyle(raised.copy(background = SubtitleBackground.SEMI_TRANSPARENT))
            frame("半透明黑底在同坐标仍透出底片") { pixels ->
                samples.all { i -> brightness(pixels[i]) * 100 in brightness(clean[i]) * 22..brightness(clean[i]) * 85 }
            }
            engine.setSubtitleStyle(raised)
            frame("关闭背景后画面不留色块") { difference(plain, it) <= 4 }
        }
    }

    @Test
    fun nativeAssKeepsInlinePositionAndColorUntilExplicitOverrideAndRestoresThemLive() = runBlocking {
        withMpvSubtitleFrame(SubtitlePreferences()) { clean ->
            select(ass)
            engine.setSubtitleDelay(0)
            val authored = frame("原生 ASS 行内定位及品红色，而非样式表的绿色") {
                val mark = ink(clean, it, MAGENTA)
                mark.count > 80 && mark.left < WIDTH / 6 && mark.top < HEIGHT / 6
            }
            val custom = SubtitlePreferences(
                font = SubtitleFont.MONOSPACE, fontStyle = SubtitleFontStyle.BOLD_ITALIC, fontSizeScale = 1.6f,
                textColor = SubtitleTextColor.CYAN, background = SubtitleBackground.NAVY, verticalOffsetPercent = 5,
            )
            engine.setSubtitleStyle(custom)
            // 先让字幕消失再出现，排除“截图仍是改设置前的旧缓冲”的假阳性。
            engine.setSubtitleDelay(HIDDEN_DELAY_MS)
            frame("延迟后 ASS 消失") { difference(clean, it) <= 4 }
            engine.setSubtitleDelay(0)
            frame("未开启覆盖时任意文本偏好不动作者排版") { difference(authored, it) <= 4 }

            engine.setSubtitleStyle(custom.copy(overrideAss = true))
            val forced = frame("显式覆盖 ASS 行内颜色、定位与背景") {
                val mark = ink(clean, it, CYAN)
                mark.count > 80 && mark.top > HEIGHT / 2 && ink(clean, it, NAVY).count > 200 && ink(clean, it, MAGENTA).count < 8
            }
            val forcedTop = ink(clean, forced, CYAN).top
            val edited = custom.copy(overrideAss = true, textColor = SubtitleTextColor.YELLOW, verticalOffsetPercent = 30)
            engine.setSubtitleStyle(edited)
            frame("覆盖中的 ASS 即时接受颜色与位置变更") {
                val mark = ink(clean, it, YELLOW)
                mark.count > 80 && mark.top < forcedTop - HEIGHT / 6
            }
            engine.setSubtitleStyle(edited.copy(overrideAss = false))
            frame("不换轨、不 seek，关覆盖后恢复原始 ASS 帧") { difference(authored, it) <= 4 }
            select(srt)
            frame("切回 SRT，仍使用最新的黄色文本偏好") { ink(clean, it, YELLOW).count > 60 && ink(clean, it, NAVY).count > 200 }
        }
    }

    /** 从实拍帧而非假定的矩形中找两个底片颜色明显不同、且没有文字或描边的像素。 */
    private fun opaqueBoxSamples(clean: IntArray, plain: IntArray, navy: IntArray): IntArray {
        var first = -1
        var second = -1
        var area = 0
        for (i in navy.indices) {
            if (!near(navy[i], NAVY, 12) || !near(clean[i], plain[i], 4) || brightness(clean[i]) < 160 || near(clean[i], NAVY, 60)) continue
            area++
            if (first < 0) first = i else if (second < 0 && !near(clean[first], clean[i], 80)) second = i
        }
        assertTrue("未观察到同一深蓝实底遮住两种不同视频颜色：面积=$area", area >= 200 && second >= 0)
        return intArrayOf(first, second)
    }

    private companion object {
        const val CYAN = 0x00FFFF
        const val YELLOW = 0xFFFF00
        const val MAGENTA = 0xFF00FF
        const val NAVY = 0x14284B
    }
}
