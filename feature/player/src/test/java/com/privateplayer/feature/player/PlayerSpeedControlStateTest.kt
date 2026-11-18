package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSpeedControlStateTest {
    private val anchor = Rect(20f, 500f, 96f, 548f)
    private val fan = requireNotNull(SpeedFanGeometry.fit(anchor, Rect(4f, 4f, 396f, 574f), 1f))
    private fun fromButton(local: Offset) = fan.topLeft + local - anchor.topLeft
    private fun at(speed: Float) = fromButton(fan.labelBounds(SpeedPresets.indexOfFirst { it == speed }).center)

    private inner class Recorder(start: Float) {
        var speed = start
        var enabled = true
        val rates = mutableListOf<Float>()
        val haptics = mutableListOf<SpeedHaptic>()
        var finished = 0
        var time = 0L
        val control = PlayerSpeedControlState({
            SpeedPreview(speed) { _, changed, end ->
                if (changed != null) { rates += changed; speed = changed }
                if (end) finished++
            }
        }, { enabled }, { haptics += it })

        fun hold(down: Offset = at(1f)) {
            control.anchor = anchor
            control.geometry = fan
            control.beginPress()
            control.beginSlide(down, time, 8f)
            time += 500L
        }
        fun move(p: Offset, up: Boolean = false) {
            time += 16L
            control.track(p, time, released = up)
        }
    }

    @Test fun bothEndsOfPillAndSubSlopDriftKeepExactBaselineWithoutOneTimesPreview() {
        for (down in listOf(Offset(1f, 24f), at(1f), Offset(anchor.width - 1f, 24f))) {
            val log = Recorder(2f)
            log.hold(down)
            log.move(down + Offset(3f, -4f))
            log.move(down + Offset(0f, 8f))
            assertEquals(SpeedFanGeometry.Outside, log.control.hover)
            log.move(down - Offset(2f, 1f), up = true)
            assertFalse(log.control.active)
            assertEquals(2f, log.speed, 0f)
            assertTrue(log.rates.isEmpty())
            assertEquals(listOf(SpeedHaptic.LongPress), log.haptics)
            assertEquals(1, log.finished)
        }
    }

    @Test fun deliberateMotionInsideSquareAndReturnFromFiveBothAuditionOneOnMove() {
        val log = Recorder(2f)
        log.hold()
        val one = at(1f) + Offset(10f, 0f)
        log.move(one)
        assertEquals(1f, log.speed, 0f)
        assertEquals(SpeedFanGeometry.Origin, log.control.hover)
        repeat(5) { log.move(at(1f)) }
        assertEquals(anchor.topLeft + at(1f), log.control.fingerInRoot)
        log.move(at(5f))
        log.move(at(1f))
        assertEquals(1f, log.speed, 0f)
        assertEquals(2f, log.control.preview!!.originalSpeed, 0f)
        log.move(at(1f), up = true)
        log.control.dismiss()
        assertEquals(listOf(1f, 5f, 1f), log.rates)
        assertEquals(listOf(SpeedHaptic.LongPress, SpeedHaptic.Tick, SpeedHaptic.Tick, SpeedHaptic.Tick), log.haptics)
        assertEquals(1, log.finished)
    }

    @Test fun firstIntentCanArriveOnUpIncludingOneAndOtherTargets() {
        for (rate in listOf(1f, 0.75f, 8f)) {
            val log = Recorder(2f)
            log.hold()
            log.move(if (rate == 1f) at(1f) + Offset(-12f, 0f) else at(rate), up = true)
            assertEquals(listOf(rate), log.rates)
            assertEquals(listOf(SpeedHaptic.LongPress, SpeedHaptic.Tick), log.haptics)
            assertFalse(log.control.active)
        }
    }

    @Test fun hardExteriorRestoresImmediatelyAndReentryThroughOneStillWorks() {
        for (outside in listOf(Offset(fan.radius * 0.95f, fan.radius * 0.05f), Offset(-0.1f, fan.radius),
            Offset(0f, -0.1f), Offset(fan.radius * 0.2f, fan.radius + 0.1f))) {
            val log = Recorder(1.375f)
            log.hold()
            log.move(at(7f))
            log.move(fromButton(outside))
            assertEquals(1.375f, log.speed, 0f)
            assertEquals(SpeedFanGeometry.Outside, log.control.hover)
            assertEquals(0, log.finished)
            log.move(at(1f))
            assertEquals(1f, log.speed, 0f)
            log.move(fromButton(outside), up = true)
            assertEquals(listOf(7f, 1.375f, 1f, 1.375f), log.rates)
            assertEquals(listOf(SpeedHaptic.LongPress, SpeedHaptic.Tick, SpeedHaptic.Tick), log.haptics)
            assertEquals(1, log.finished)
        }
    }

    @Test fun cancelOrLayoutChangeRestoresBaselineOnlyOnceAndDoesNotAcceptLateUp() {
        for (layoutChanged in listOf(false, true)) {
            val log = Recorder(1.375f)
            log.enabled = false
            log.hold()
            log.move(at(5f))
            log.move(at(1f))
            if (layoutChanged) log.control.geometry = null else log.control.dismiss()
            log.move(at(5f), up = true)
            assertEquals(listOf(5f, 1f, 1.375f), log.rates)
            assertTrue(log.haptics.isEmpty())
            assertEquals(1, log.finished)
        }
    }

    @Test fun revokedTicketCannotRetakeAnExternalSpeedEvenWhenItsValueMatchesTheCandidate() {
        val log = Recorder(1.375f)
        log.hold()
        log.move(at(8f))
        log.control.preview!!.revoke()
        log.move(at(1f))
        log.move(at(1f), up = true)
        assertEquals(listOf(8f), log.rates)
        assertFalse(log.control.active)
        assertEquals(SpeedFanGeometry.Outside, log.control.hover)
    }

    @Test fun baselineCanBeAuditionedWithoutDuplicateEngineWritesAndTapRemainsIndependent() {
        val log = Recorder(2f)
        log.hold()
        log.move(at(2f))
        assertEquals(SpeedPresets.indexOfFirst { it == 2f }, log.control.hover)
        log.move(at(2f), up = true)
        assertTrue(log.rates.isEmpty())
        for (i in SpeedPresets.indices) {
            log.control.open()
            assertEquals(SpeedFanGeometry.Outside, log.control.hover)
            log.control.tap(i)
            assertEquals(SpeedPresets[i], log.speed, 0f)
            assertFalse(log.control.active)
        }
        assertEquals(listOf(SpeedHaptic.LongPress, SpeedHaptic.Tick), log.haptics)
    }
}
