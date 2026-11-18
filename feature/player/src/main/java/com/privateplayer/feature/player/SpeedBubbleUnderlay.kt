package com.privateplayer.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.privateplayer.core.design.theme.Radius

/** 同窗口实际放置的前景几何。仅在键帽存在时挖掉其下的扇层，背后的视频本身不参与裁剪。 */
internal class SpeedBubbleUnderlay {
    var hudInRoot by mutableStateOf(Rect.Zero)
    private var left by mutableIntStateOf(0)
    private var top by mutableIntStateOf(0)
    private var width by mutableIntStateOf(0)
    private var height by mutableIntStateOf(0)

    fun placeInFan(at: IntOffset, size: IntSize) {
        left = at.x; top = at.y
        width = size.width; height = size.height
    }

    val cutout: Modifier = Modifier.drawWithCache {
        val w = width.toFloat(); val h = height.toFloat()
        val corner = minOf(Radius.thumbnail.toPx(), w / 2, h / 2)
        val hole = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(corner))) }
        onDrawWithContent {
            val x = left.toFloat(); val y = top.toFloat()
            if (w <= 0f || h <= 0f || x >= size.width || x + w <= 0f || y >= size.height || y + h <= 0f) drawContent() else {
                // 位置变化只平移缓存的圆角轮廓，不调整透明度、不分配全屏离屏缓冲。
                val canvas = drawContext.canvas
                canvas.save()
                try {
                    canvas.translate(x, y)
                    canvas.clipPath(hole, ClipOp.Difference)
                    canvas.translate(-x, -y)
                    drawContent()
                } finally { canvas.restore() }
            }
        }
    }
}
