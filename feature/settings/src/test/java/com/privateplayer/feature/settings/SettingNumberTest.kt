package com.privateplayer.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingNumberTest {
    @Test
    fun `decimal inputs accept exact bounds and comma decimal`() {
        assertEquals(0.25, parseSettingNumber("0.25", 0.25, 4.0, false)!!, 0.0)
        assertEquals(4.0, parseSettingNumber("4", 0.25, 4.0, false)!!, 0.0)
        assertEquals(1.75, parseSettingNumber("1,75", 0.25, 4.0, false)!!, 0.0)
    }

    @Test
    fun `out of range or non finite values are rejected`() {
        listOf("", "-1", "0.24", "4.01", "NaN", "Infinity", "1e2", "1.2.3").forEach {
            assertNull(parseSettingNumber(it, 0.25, 4.0, false))
        }
    }

    @Test
    fun `integer options reject fractions and enforce boundaries`() {
        assertEquals(5.0, parseSettingNumber("5", 5.0, 60.0, true)!!, 0.0)
        assertEquals(60.0, parseSettingNumber("60", 5.0, 60.0, true)!!, 0.0)
        assertNull(parseSettingNumber("5.5", 5.0, 60.0, true))
        assertNull(parseSettingNumber("61", 5.0, 60.0, true))
    }
}
