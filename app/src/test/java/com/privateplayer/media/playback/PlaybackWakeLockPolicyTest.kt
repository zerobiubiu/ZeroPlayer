package com.privateplayer.media.playback

import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWakeLockPolicyTest {
    @Test
    fun holdsForPlaybackAndInFlightPlayOnly() {
        assertTrue(requiresPlaybackWakeLock(PlaybackSnapshot(status = PlaybackStatus.PLAYING)))
        assertTrue(requiresPlaybackWakeLock(PlaybackSnapshot(status = PlaybackStatus.LOADING, playWhenReady = true)))
        assertFalse(requiresPlaybackWakeLock(PlaybackSnapshot(status = PlaybackStatus.LOADING, playWhenReady = false)))
        assertTrue(requiresPlaybackWakeLock(PlaybackSnapshot(status = PlaybackStatus.PAUSED, playWhenReady = true)))
        assertFalse(requiresPlaybackWakeLock(PlaybackSnapshot(status = PlaybackStatus.PAUSED, playWhenReady = false)))
        for (status in listOf(PlaybackStatus.IDLE, PlaybackStatus.ENDED, PlaybackStatus.ERROR)) {
            assertFalse(requiresPlaybackWakeLock(PlaybackSnapshot(status = status, playWhenReady = true)))
            assertFalse(requiresPlaybackWakeLock(PlaybackSnapshot(status = status, playWhenReady = false)))
        }
    }
}
