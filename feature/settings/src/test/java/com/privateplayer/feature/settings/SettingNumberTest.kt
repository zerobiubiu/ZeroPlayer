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

    @Test
    fun `合法新值提交写入`() {
        val edit = resolveNumericEdit("2.5", savedValue = 1.0, lastWrittenValue = null, minimum = 0.25, maximum = 4.0, integerOnly = false)
        assertEquals(NumericEdit.Commit(2.5), edit)
    }

    @Test
    fun `非法值一律拒绝不写入`() {
        listOf("9", "abc", "", "0.1").forEach { text ->
            val edit = resolveNumericEdit(text, savedValue = 1.0, lastWrittenValue = null, minimum = 0.25, maximum = 4.0, integerOnly = false)
            assertEquals(NumericEdit.Reject, edit)
        }
    }

    @Test
    fun `与已保存值相同不再重复写入`() {
        val edit = resolveNumericEdit("1.0", savedValue = 1.0, lastWrittenValue = null, minimum = 0.25, maximum = 4.0, integerOnly = false)
        assertEquals(NumericEdit.Unchanged, edit)
    }

    @Test
    fun `与本次会话上次写入值相同不再重复写入`() {
        val edit = resolveNumericEdit("2.5", savedValue = 1.0, lastWrittenValue = 2.5, minimum = 0.25, maximum = 4.0, integerOnly = false)
        assertEquals(NumericEdit.Unchanged, edit)
    }

    @Test
    fun `连续修改时最后一次新值仍会被提交`() {
        val edits = listOf("1.5", "2", "3")
            .map { resolveNumericEdit(it, savedValue = 1.0, lastWrittenValue = null, minimum = 0.25, maximum = 4.0, integerOnly = false) }
        assertEquals(NumericEdit.Commit(3.0), edits.last())
    }

    @Test
    fun `整数项拒绝小数并按范围提交`() {
        assertEquals(
            NumericEdit.Reject,
            resolveNumericEdit("5.5", savedValue = null, lastWrittenValue = null, minimum = 5.0, maximum = 60.0, integerOnly = true),
        )
        assertEquals(
            NumericEdit.Commit(30.0),
            resolveNumericEdit("30", savedValue = 10.0, lastWrittenValue = null, minimum = 5.0, maximum = 60.0, integerOnly = true),
        )
    }
}
