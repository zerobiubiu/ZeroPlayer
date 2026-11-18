package com.privateplayer.feature.player

import com.privateplayer.domain.model.PlaybackPreferences
import com.privateplayer.domain.model.PlaybackProgressMode
import com.privateplayer.domain.model.PlaybackProgressMode.DISABLED
import com.privateplayer.domain.model.PlaybackProgressMode.RECORD_ONLY
import com.privateplayer.domain.model.PlaybackProgressMode.RESUME
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressModeTest {
    @Test fun existingFlagCombinationsAndDefaultKeepTheirIntent() {
        assertEquals(DISABLED, PlaybackProgressMode.fromStoredFlags(false, false))
        assertEquals(DISABLED, PlaybackProgressMode.fromStoredFlags(false, true))
        assertEquals(RECORD_ONLY, PlaybackProgressMode.fromStoredFlags(true, false))
        assertEquals(RESUME, PlaybackProgressMode.fromStoredFlags(true, true))
        assertEquals(RESUME, PlaybackPreferences().progressMode)
    }

    @Test fun recordingAndReopeningAreDistinctIncludingUnknownDuration() {
        for (duration in listOf(0L, 100_000L)) {
            assertEquals(0, DISABLED.positionToSave(600, duration))
            assertEquals(0, DISABLED.positionOnOpen(600, duration))
            assertEquals(600, RECORD_ONLY.positionToSave(600, duration))
            assertEquals(0, RECORD_ONLY.positionOnOpen(600, duration))
            assertEquals(600, RESUME.positionToSave(600, duration))
            assertEquals(600, RESUME.positionOnOpen(600, duration))
        }
    }

    @Test fun completedAndInvalidBookmarksStartAtZero() {
        assertEquals(97_999, RESUME.positionOnOpen(97_999, 100_000))
        assertEquals(0, RESUME.positionOnOpen(98_000, 100_000))
        assertEquals(0, RESUME.positionOnOpen(100_001, 100_000))
        assertEquals(0, RESUME.positionOnOpen(-1, 0))
        assertEquals(100_000, RECORD_ONLY.positionToSave(101_000, 100_000))
    }
}
