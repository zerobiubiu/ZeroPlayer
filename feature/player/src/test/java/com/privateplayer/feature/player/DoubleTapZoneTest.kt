package com.privateplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapZoneTest {
    private fun boundaries(width: Int, height: Int, left: Float, right: Float) {
        assertEquals(PlayerGesture.DoubleTap.Rewind, doubleTapZone(0f, width, height))
        assertEquals(PlayerGesture.DoubleTap.Rewind, doubleTapZone(Math.nextDown(left), width, height))
        assertEquals(PlayerGesture.DoubleTap.PlayPause, doubleTapZone(left, width, height))
        assertEquals(PlayerGesture.DoubleTap.PlayPause, doubleTapZone(Math.nextDown(right), width, height))
        assertEquals(PlayerGesture.DoubleTap.Forward, doubleTapZone(right, width, height))
        assertEquals(PlayerGesture.DoubleTap.Forward, doubleTapZone(width - 1f, width, height))
    }

    @Test fun portraitAndSquareUseEqualThirds() {
        boundaries(300, 720, 100f, 200f)
        boundaries(300, 300, 100f, 200f)
    }

    @Test fun landscapeInterpolatesAndCapsAtTwoToOne() {
        boundaries(600, 400, 190f, 410f)
        boundaries(600, 300, 180f, 420f)
        boundaries(600, 180, 180f, 420f)
    }
}
