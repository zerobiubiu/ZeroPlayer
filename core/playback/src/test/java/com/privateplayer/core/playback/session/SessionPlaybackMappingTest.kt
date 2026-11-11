package com.privateplayer.core.playback.session

import androidx.media3.common.Player
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlaybackMappingTest {
    private val media = SessionMedia(mediaId = 7L, title = "标题", durationMs = 50_000L)

    @Test
    fun `each engine status maps without pretending playback has started`() {
        val expected = mapOf(
            PlaybackStatus.IDLE to Player.STATE_IDLE,
            PlaybackStatus.LOADING to Player.STATE_BUFFERING,
            PlaybackStatus.PLAYING to Player.STATE_READY,
            PlaybackStatus.PAUSED to Player.STATE_READY,
            PlaybackStatus.ENDED to Player.STATE_ENDED,
            PlaybackStatus.ERROR to Player.STATE_IDLE,
        )
        expected.forEach { (status, playerState) ->
            val wantsPlayback = status == PlaybackStatus.PLAYING
            val result = mapSessionPlayback(
                PlaybackSnapshot(mediaId = 7L, status = status, playWhenReady = wantsPlayback), media,
            )
            assertEquals(playerState, result.playbackState)
            assertEquals(wantsPlayback, result.playWhenReady)
            assertEquals(status == PlaybackStatus.LOADING, result.isLoading)
        }
    }

    @Test
    fun `loading reflects the engines playback intent without reporting ready`() {
        val autoPlay = mapSessionPlayback(
            PlaybackSnapshot(mediaId = 7L, status = PlaybackStatus.LOADING, playWhenReady = true), media,
        )
        val paused = mapSessionPlayback(
            PlaybackSnapshot(mediaId = 7L, status = PlaybackStatus.LOADING, playWhenReady = false), media,
        )
        assertEquals(Player.STATE_BUFFERING, autoPlay.playbackState)
        assertTrue(autoPlay.isLoading)
        assertTrue(autoPlay.playWhenReady)
        assertEquals(Player.STATE_BUFFERING, paused.playbackState)
        assertTrue(paused.isLoading)
        assertFalse(paused.playWhenReady)
    }

    @Test
    fun `resume intent stays buffering until the engine actually plays`() {
        val paused = PlaybackSnapshot(
            mediaId = 7L, status = PlaybackStatus.PAUSED, playWhenReady = true,
        )
        val waiting = mapSessionPlayback(paused, media)
        assertEquals(Player.STATE_BUFFERING, waiting.playbackState)
        assertTrue(waiting.playWhenReady)
        assertFalse(waiting.isLoading)

        val resumed = mapSessionPlayback(paused.copy(status = PlaybackStatus.PLAYING), media)
        assertEquals(Player.STATE_READY, resumed.playbackState)
        assertTrue(resumed.playWhenReady)
    }

    @Test
    fun `missing media disables controls and clears timeline values`() {
        val result = mapSessionPlayback(
            PlaybackSnapshot(
                mediaId = 7L, status = PlaybackStatus.PLAYING, positionMs = 800L,
                bufferedMs = 900L, playWhenReady = true,
            ),
            null,
        )
        assertEquals(Player.STATE_IDLE, result.playbackState)
        assertFalse(result.playWhenReady)
        assertFalse(result.canControl)
        assertFalse(result.canSeek)
        assertNull(result.durationMs)
        assertEquals(0L, result.positionMs)
        assertEquals(0L, result.bufferedPositionMs)
    }

    @Test
    fun `switching media never pairs new metadata with old playback`() {
        val previous = PlaybackSnapshot(
            mediaId = 7L, status = PlaybackStatus.PLAYING, playWhenReady = true,
            positionMs = 12_000L, durationMs = 45_000L, bufferedMs = 15_000L,
        )
        val nextMedia = SessionMedia(mediaId = 8L, title = "新标题", durationMs = 60_000L)
        val transition = mapSessionPlayback(previous, nextMedia)
        assertNull(transition.media)
        assertEquals(Player.STATE_IDLE, transition.playbackState)
        assertFalse(transition.playWhenReady)
        assertFalse(transition.isLoading)
        assertFalse(transition.canControl)
        assertFalse(transition.canSeek)
        assertNull(transition.durationMs)
        assertEquals(0L, transition.positionMs)
        assertEquals(0L, transition.bufferedPositionMs)
        assertEquals(0L, transition.bufferedDurationMs)

        val loading = mapSessionPlayback(
            previous.copy(
                mediaId = 8L, status = PlaybackStatus.LOADING,
                positionMs = 0L, durationMs = 0L, bufferedMs = 0L,
            ),
            nextMedia,
        )
        assertEquals(nextMedia, loading.media)
        assertEquals(Player.STATE_BUFFERING, loading.playbackState)
        assertTrue(loading.playWhenReady)
        assertTrue(loading.canControl)
        assertEquals(60_000L, loading.durationMs)
        assertEquals(0L, loading.positionMs)
    }

    @Test
    fun `metadata updates for the same media retain real playback`() {
        val snapshot = PlaybackSnapshot(
            mediaId = 7L, status = PlaybackStatus.PLAYING, playWhenReady = true, positionMs = 1_234L,
        )
        val updated = media.copy(title = "更新标题", artworkData = byteArrayOf(1, 2, 3))
        val result = mapSessionPlayback(snapshot, updated)
        assertEquals(updated, result.media)
        assertEquals(Player.STATE_READY, result.playbackState)
        assertEquals(1_234L, result.positionMs)
        assertTrue(result.playWhenReady)
        assertTrue(result.canSeek)
    }

    @Test
    fun `seek is available only when engine can seek the current item`() {
        PlaybackStatus.entries.forEach { status ->
            val result = mapSessionPlayback(PlaybackSnapshot(mediaId = 7L, status = status), media)
            assertEquals(
                status == PlaybackStatus.LOADING || status == PlaybackStatus.PLAYING ||
                    status == PlaybackStatus.PAUSED || status == PlaybackStatus.ENDED,
                result.canControl,
            )
            assertEquals(
                status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED,
                result.canSeek,
            )
        }
    }

    @Test
    fun `engine duration takes precedence and unknown duration stays unknown`() {
        assertEquals(45_000L, mapSessionPlayback(PlaybackSnapshot(mediaId = 7L, durationMs = 45_000L), media).durationMs)
        assertEquals(50_000L, mapSessionPlayback(PlaybackSnapshot(mediaId = 7L), media).durationMs)
        assertNull(mapSessionPlayback(PlaybackSnapshot(mediaId = 7L), media.copy(durationMs = 0L)).durationMs)
        assertNull(
            mapSessionPlayback(
                PlaybackSnapshot(mediaId = 7L, durationMs = Long.MAX_VALUE),
                media.copy(durationMs = Long.MAX_VALUE),
            ).durationMs,
        )
    }

    @Test
    fun `buffer is an absolute timestamp and not a made up duration`() {
        val buffered = mapSessionPlayback(
            PlaybackSnapshot(mediaId = 7L, positionMs = 12_000L, bufferedMs = 15_000L), media,
        )
        assertEquals(12_000L, buffered.positionMs)
        assertEquals(15_000L, buffered.bufferedPositionMs)
        assertEquals(3_000L, buffered.bufferedDurationMs)

        val unavailable = mapSessionPlayback(
            PlaybackSnapshot(mediaId = 7L, positionMs = 12_000L, bufferedMs = 0L), media,
        )
        assertEquals(0L, unavailable.bufferedPositionMs)
        assertEquals(0L, unavailable.bufferedDurationMs)
    }

    @Test
    fun `invalid native values cannot produce invalid Media3 state`() {
        val result = mapSessionPlayback(
            PlaybackSnapshot(mediaId = 7L, positionMs = -1L, bufferedMs = -2L, speed = Float.NaN, volume = Float.NaN),
            media,
        )
        assertEquals(0L, result.positionMs)
        assertEquals(0L, result.bufferedPositionMs)
        assertEquals(1f, result.speed, 0f)
        assertEquals(1f, result.volume, 0f)
        assertEquals(1f, mapSessionPlayback(PlaybackSnapshot(mediaId = 7L, volume = 2f), media).volume, 0f)
        assertFalse(
            mapSessionPlayback(
                PlaybackSnapshot(mediaId = 7L, status = PlaybackStatus.PLAYING, playWhenReady = false), media,
            )
                .playWhenReady,
        )
    }
}
