package com.privateplayer.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWriteQueueTest {

    @Test
    fun `连续提交按顺序写入且最后一次生效`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writes = mutableListOf<String>()
        val scope = TestScope(dispatcher)
        val queue = SettingsWriteQueue(scope) { }

        queue.submit { writes += "1.5" }
        queue.submit { writes += "2.0" }
        queue.submit { writes += "3.0" }
        advanceUntilIdle()

        assertEquals(listOf("1.5", "2.0", "3.0"), writes)
    }

    @Test
    fun `写入失败会报告错误且不阻断后续写入`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writes = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        val scope = TestScope(dispatcher)
        val queue = SettingsWriteQueue(scope) { errors += it }

        queue.submit { error("模拟写入失败") }
        queue.submit { writes += "后续写入" }
        advanceUntilIdle()

        assertEquals(listOf("后续写入"), writes)
        assertEquals(1, errors.size)
        assertTrue(errors.single().message?.contains("模拟写入失败") == true)
    }
}
