package com.privateplayer.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing

@Composable
internal fun FanSurface(fan: SpeedFanGeometry, hover: Int, modifier: Modifier) {
    val density = LocalDensity.current
    val art = remember(fan, density.density) { with(density) {
        FanDrawing(fan, Spacing.xxs.toPx(), Radius.thumbnail.toPx(), 1.dp.toPx())
    } }
    Canvas(modifier) {
        drawPath(art.surface, PlayerTokens.SpeedMenuContainer)
        drawPath(art.seams, PlayerTokens.SpeedSeam, style = art.hairline)
        drawPath(art.surface, PlayerTokens.TrackInactive, style = art.hairline)
        drawPath(art.cells[SpeedFanGeometry.Origin], PlayerTokens.SpeedKeyContainer)
        drawPath(art.cells[SpeedFanGeometry.Origin], PlayerTokens.SpeedSeam, style = art.hairline)
        if (hover >= SpeedFanGeometry.Origin) {
            drawPath(art.cells[hover], PlayerTokens.SpeedKeyActive)
            drawPath(art.cells[hover], PlayerTokens.Accent, style = art.edge)
        }
    }
}

/** 路径只随扇面尺寸建立；同档移指只移动浮泡，跨档仅替换绘制索引。 */
private class FanDrawing(fan: SpeedFanGeometry, inset: Float, corner: Float, line: Float) {
    val hairline = Stroke(line)
    val edge = Stroke(line * 1.5f)
    val surface = Path().apply {
        moveTo(0f, fan.radius)
        arcTo(fan.arcBounds(fan.radius), -90f, 90f, false)
        close()
    }
    val seams = Path().apply {
        for (edge in 1 until SpeedFanGeometry.Sectors) {
            val end = fan.pointOnRay(edge)
            val startFraction = fan.origin.width / maxOf(end.x, fan.radius - end.y)
            moveTo(end.x * startFraction, fan.radius - (fan.radius - end.y) * startFraction)
            lineTo(end.x, end.y)
        }
        for (ring in 1 until SpeedFanGeometry.Rings) arcTo(fan.arcBounds(fan.innerRadius(ring)), -90f, 90f, true)
    }
    val cells: Array<Path>

    init {
        val square = Path().apply { addRect(fan.origin) }
        cells = Array(SpeedPresets.size) { index ->
            if (index == SpeedFanGeometry.Origin) {
                val key = fan.origin.deflate(minOf(inset, fan.origin.width * 0.08f))
                Path().apply { addRoundRect(RoundRect(key, CornerRadius(minOf(corner, key.width / 4f)))) }
            } else {
                val ring = SpeedFanGeometry.ring(index)
                val sweep = SpeedFanGeometry.Sweep
                val start = -90f + SpeedFanGeometry.sector(index) * sweep
                val cell = Path().apply {
                    arcTo(fan.arcBounds(fan.outerRadius(ring)), start, sweep, true)
                    if (ring == 0) lineTo(0f, fan.radius)
                    else arcTo(fan.arcBounds(fan.innerRadius(ring)), start + sweep, -sweep, false)
                    close()
                }
                if (ring == 0) Path.combine(PathOperation.Difference, cell, square) else cell
            }
        }
    }
}

private fun SpeedFanGeometry.arcBounds(r: Float) = Rect(-r, radius - r, r, radius + r)
