package com.privateplayer.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 从目标包的编译资源读取普通、圆形和 Android 13 的单色层，不用设计稿替代系统 Drawable。 */
@RunWith(AndroidJUnit4::class)
class LauncherIconInstrumentedTest {
    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun installedAdaptiveIconsShareColorAndMonochromeArtwork() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val normal = app.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = app.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertTrue(app.applicationInfo.icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round))
        val backdrop = app.getColor(R.color.ic_launcher_background)
        val normalImage = render(normal)
        val roundImage = render(round)
        val mono = render(requireNotNull(normal.monochrome))
        val roundMono = render(requireNotNull(round.monochrome))
        try {
            assertTrue("同一系统遮罩下普通与圆形入口不一致", normalImage.sameAs(roundImage))
            assertTrue("圆形入口缺少相同单色图形", mono.sameAs(roundMono))
            val ink = IntArray(SIDE * SIDE)
            mono.getPixels(ink, 0, SIDE, 0, 0, SIDE, SIDE)
            var transparentBetweenStrokes = 0
            for (y in 0 until SIDE) {
                val first = (0 until SIDE).firstOrNull { x -> Color.alpha(ink[y * SIDE + x]) > 240 } ?: continue
                val last = (SIDE - 1 downTo first).first { x -> Color.alpha(ink[y * SIDE + x]) > 240 }
                transparentBetweenStrokes += (first..last).count { x -> Color.alpha(ink[y * SIDE + x]) < 16 }
            }
            assertTrue("单色图形不能是实心轮廓或空白图层", transparentBetweenStrokes > 100)
            val colors = IntArray(SIDE * SIDE)
            normalImage.getPixels(colors, 0, SIDE, 0, 0, SIDE, SIDE)
            assertTrue("没有背景色", colors.any { it == backdrop })
            assertTrue("没有可见的彩色前景", colors.any { Color.alpha(it) == 255 && it != backdrop })
            if (InstrumentationRegistry.getArguments().getString("launcherEvidence") == "true") {
                val glyph = requireNotNull(normal.monochrome).mutate().apply { setTint(app.getColor(android.R.color.system_accent1_900)) }
                val preview = render(glyph, app.getColor(android.R.color.system_accent1_100))
                try {
                    File(app.cacheDir, "launcher-monochrome.png").outputStream().use { check(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { preview.recycle() }
            }
            android.util.Log.i("ZeroPlayerIconProof", "installed normal=${normal.intrinsicWidth}, round=${round.intrinsicWidth}; monochrome canvas=${SIDE}x$SIDE transparentBetweenStrokes=$transparentBetweenStrokes")
        } finally {
            normalImage.recycle(); roundImage.recycle(); mono.recycle(); roundMono.recycle()
        }
    }

    private fun render(drawable: Drawable, background: Int = Color.TRANSPARENT): Bitmap =
        Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(background)
            drawable.setBounds(0, 0, SIDE, SIDE)
            drawable.draw(Canvas(this))
        }

    private companion object { const val SIDE = 216 }
}
