package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFanFeedbackTest {
    @Test fun bubbleFollowsFingerWithinTheSameCellAndClearsTheFingerAtEveryScreenEdge() {
        for (area in listOf(IntSize(230, 190), IntSize(320, 568), IntSize(915, 411))) {
            for (key in listOf(IntSize(64, 36), IntSize(80, 44), IntSize(112, 58))) {
                for (x in listOf(1f, 24f, area.width / 2f, area.width - 5f)) {
                    for (y in listOf(1f, 24f, 58f, area.height / 2f, area.height - 1f)) {
                        val pos = speedBubblePosition(Offset(x, y), area, key, 52f, 24f)
                        assertTrue(pos.x >= 0 && pos.x + key.width <= area.width)
                        assertTrue(pos.y >= 0 && pos.y + key.height <= area.height)
                        val clearance = maxOf(pos.x - x, x - (pos.x + key.width), pos.y - y, y - (pos.y + key.height))
                        assertTrue("$area / $key / ($x,$y): 遮挡指腹", clearance >= 23f)
                    }
                }
            }
        }
        val area = IntSize(411, 915)
        val key = IntSize(74, 40)
        val a = speedBubblePosition(Offset(132f, 712f), area, key, 52f, 24f)
        val b = speedBubblePosition(Offset(140f, 709f), area, key, 52f, 24f)
        assertEquals(52f, 712f - a.y - key.height / 2f, 0.1f)
        assertEquals(8, b.x - a.x)
        assertEquals(-3, b.y - a.y)
    }

    @Test fun decimalKeycapChoosesOneStableNearbyOffsetClearOfBothAdjacentLabels() {
        // 1.3 字号、420dpi 横屏实测；两块邻字原来分别在键帽的左上与右下。
        val area = IntSize(2400, 1080)
        val key = IntSize(200, 102)
        val finger = Offset(320.8f, 695.5f)
        val labels = arrayOf(Rect(104f, 479f, 298f, 547f), Rect(329f, 557f, 450f, 625f),
            Rect(260f, 662f, 381f, 730f), Rect(106f, 602f, 247f, 670f))
        val dock = SpeedBubbleDock(area, key, finger, Rect(1008f, 214f, 1391f, 327f),
            63f, 10.5f, 136.5f, 126f, labels)
        val base = dock.position(finger)
        for (delta in listOf(-3, -1, 0, 1, 3)) {
            val pos = dock.position(finger + Offset(delta.toFloat(), delta.toFloat()))
            val cap = Rect(pos.x.toFloat(), pos.y.toFloat(), (pos.x + key.width).toFloat(), (pos.y + key.height).toFloat())
            assertFalse(labels.any(cap::overlaps))
            assertTrue(cap.bottom < finger.y - 63f)
            assertEquals(base.x + delta, pos.x)
            assertEquals(base.y + delta, pos.y)
        }
    }

    @Test fun smallWindowPreservesFingerClearanceAndDoesNotIntersectMeasuredHud() {
        // 760×1280 / 440dpi 的 0.25× 原键帽与 HUD 重叠 32px。
        val area = IntSize(760, 1280)
        val key = IntSize(207, 106)
        val hud = Rect(144f, 261f, 615f, 379f)
        val dock = SpeedBubbleDock(area, key, Offset(209.5f, 542.5f), hud, 66f, 11f, 143f, 132f,
            arrayOf(Rect(108f, 507f, 311f, 578f), Rect(344f, 589f, 471f, 660f)))
        for (x in listOf(130f, 209.5f, 305f)) for (y in listOf(492f, 542.5f, 577f)) {
            val pos = dock.position(Offset(x, y))
            val cap = Rect(pos.x.toFloat(), pos.y.toFloat(), (pos.x + key.width).toFloat(), (pos.y + key.height).toFloat())
            assertFalse(cap.overlaps(hud))
            assertTrue(maxOf(x - cap.right, cap.left - x, y - cap.bottom, cap.top - y) >= 65f)
            assertTrue(cap.left >= 0 && cap.top >= 0 && cap.right <= area.width && cap.bottom <= area.height)
        }
    }

    @Test fun hudMovesBesideTheFanOnlyWhenCenteredOverAnActualKeyAndTheRightSideFits() {
        val fan = requireNotNull(SpeedFanGeometry.fit(Rect(55f, 573f, 280f, 705f), Rect(11f, 11f, 1269f, 749f), 2.75f))
        // 1280×760 居中 x=404 时压到 3×；扇右边 x=718，另留 8dp。
        assertEquals(740, fan.speedHudLeft(404, 154, 471, 118, 1258, 22, Offset.Zero))
        assertEquals(404, fan.speedHudLeft(404, 154, 471, 118, 1150, 22, Offset.Zero))
        // 包围框右上空角不是可选键，普通宽屏中心也不需要动。
        assertEquals(650, fan.speedHudLeft(650, 89, 20, 30, 1258, 22, Offset.Zero))
        assertEquals(1008, fan.speedHudLeft(1008, 214, 383, 113, 2378, 22, Offset.Zero))
        val portrait = requireNotNull(SpeedFanGeometry.fit(Rect(55f, 950f, 280f, 1082f), Rect(11f, 11f, 749f, 1269f), 2.75f))
        assertEquals(144, portrait.speedHudLeft(144, 261, 471, 118, 738, 22, Offset.Zero))
    }

    @Test fun whiteNumeralsRetainContrastOnTranslucentMaterialsEvenOverBrightVideo() {
        for (surface in listOf(PlayerTokens.SpeedMenuContainer, PlayerTokens.SpeedKeyContainer)) {
            assertTrue(surface.alpha > 0f && surface.alpha < 1f)
        }
        assertEquals(1f, PlayerTokens.Text.alpha, 0f)
        for (video in listOf(Color.White, Color.Black, Color.Yellow, Color.Cyan, Color.Magenta, Color.Red, Color.Green, Color.Blue)) {
            val fan = PlayerTokens.SpeedMenuContainer.compositeOver(video)
            val key = PlayerTokens.SpeedKeyContainer.compositeOver(video)
            for (material in listOf(fan, key, PlayerTokens.SpeedKeyActive.compositeOver(fan))) {
                val contrast = (PlayerTokens.Text.luminance() + 0.05f) / (material.luminance() + 0.05f)
                assertTrue("$video / $material contrast=$contrast", contrast >= 4.5f)
            }
        }
    }
}
