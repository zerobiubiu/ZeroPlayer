package com.privateplayer.core.playback.session

import androidx.media3.common.Player
import com.google.common.util.concurrent.ListenableFuture
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialEngineCommandQueueTest {
    private val snapshots = MutableStateFlow(PlaybackSnapshot(mediaId = 1L, status = PlaybackStatus.PLAYING))
    private val media = MutableStateFlow<SessionMedia?>(SessionMedia(1L, "标题"))

    private fun newQueue(): SerialEngineCommandQueue =
        SerialEngineCommandQueue(snapshots, media) { expectedMediaId, action ->
            if (media.value?.mediaId == expectedMediaId && snapshots.value.mediaId == expectedMediaId) {
                action()
                true
            } else {
                false
            }
        }

    @Test
    fun `commands execute in submission order`() {
        val queue = newQueue()
        val started = CountDownLatch(1)
        val continueFirst = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        try {
            val first = queue.enqueue {
                started.countDown()
                check(continueFirst.await(5, TimeUnit.SECONDS))
                calls.add(1)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val second = queue.enqueue { calls.add(2) }
            val third = queue.enqueue { calls.add(3) }
            media.value = media.value!!.copy(artworkData = byteArrayOf(1, 2, 3))
            continueFirst.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            third.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1, 2, 3), calls.toList())
        } finally {
            continueFirst.countDown()
            queue.close()
        }
    }

    @Test
    fun `commands for the previous media are discarded`() {
        val queue = newQueue()
        val started = CountDownLatch(1)
        val continueFirst = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        try {
            val first = queue.enqueue {
                started.countDown()
                check(continueFirst.await(5, TimeUnit.SECONDS))
                calls.add(1)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val stale = queue.enqueue { calls.add(2) }
            media.value = SessionMedia(2L, "新标题")
            continueFirst.countDown()
            first.get(5, TimeUnit.SECONDS)
            stale.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1), calls.toList())
        } finally {
            continueFirst.countDown()
            queue.close()
        }
    }

    @Test
    fun `queued commands are discarded when the engine switches first`() {
        val queue = newQueue()
        val started = CountDownLatch(1)
        val continueFirst = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        try {
            val first = queue.enqueue {
                started.countDown()
                check(continueFirst.await(5, TimeUnit.SECONDS))
                calls.add(1)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val stale = queue.enqueue { calls.add(2) }
            snapshots.value = snapshots.value.copy(mediaId = 2L)
            continueFirst.countDown()
            first.get(5, TimeUnit.SECONDS)
            stale.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1), calls.toList())
        } finally {
            continueFirst.countDown()
            queue.close()
        }
    }

    @Test
    fun `a source switch after queue validation is rejected inside the shared gate`() {
        val gate = Mutex(locked = true)
        val enteredGate = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val discarded = AtomicInteger()
        val queue = SerialEngineCommandQueue(snapshots, media) { expectedMediaId, action ->
            enteredGate.countDown()
            gate.withLock {
                if (media.value?.mediaId == expectedMediaId && snapshots.value.mediaId == expectedMediaId) {
                    action()
                    true
                } else {
                    false
                }
            }
        }
        var gateHeld = true
        try {
            val stale = queue.enqueue(onDiscard = { discarded.incrementAndGet() }) { calls.add(1) }
            // 此时已通过队列预检，但回调正在等待与切源共用的门，尚未触碰引擎。
            assertTrue(enteredGate.await(5, TimeUnit.SECONDS))
            media.value = SessionMedia(2L, "新标题")
            snapshots.value = snapshots.value.copy(mediaId = 2L)
            gate.unlock()
            gateHeld = false
            stale.get(5, TimeUnit.SECONDS)
            assertEquals(1, discarded.get())
            assertTrue(calls.isEmpty())
            queue.enqueue { calls.add(2) }.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(2), calls.toList())
        } finally {
            if (gateHeld) gate.unlock()
            queue.close()
        }
    }

    @Test
    fun `release cancels a command waiting for the shared gate`() {
        val gate = Mutex(locked = true)
        val enteredGate = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val discarded = AtomicInteger()
        val retiredBeforeCompletion = AtomicBoolean(false)
        val queue = SerialEngineCommandQueue(snapshots, media) { _, action ->
            enteredGate.countDown()
            gate.withLock {
                action()
                true
            }
        }
        lateinit var pending: ListenableFuture<*>
        try {
            pending = queue.enqueue(onDiscard = {
                retiredBeforeCompletion.set(!pending.isDone)
                discarded.incrementAndGet()
            }) { calls.add(1) }
            assertTrue(enteredGate.await(5, TimeUnit.SECONDS))
            queue.close()
            // 不能靠打开切源门完成取消；回调内直接观察 Future，避免调度顺序掩盖提前终结。
            assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            assertTrue("应先退役票据，再公布取消终态", retiredBeforeCompletion.get())
            assertEquals(1, discarded.get())
            assertTrue(calls.isEmpty())
        } finally {
            queue.close()
            gate.unlock()
        }
    }

    @Test
    fun `close cancels unexecuted work even when the gate returns normally`() {
        val enteredGate = CountDownLatch(1)
        val openGate = CountDownLatch(1)
        val discarded = AtomicInteger()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val old = SerialEngineCommandQueue(snapshots, media) { _, action ->
            enteredGate.countDown()
            // 模拟已通过预检但未挂起的门：关闭后仍会正常返回，不能依赖它抛取消异常。
            check(openGate.await(5, TimeUnit.SECONDS))
            action()
            true
        }
        val next = newQueue()
        try {
            val pending = old.enqueue(onDiscard = { discarded.incrementAndGet() }) { calls.add("旧命令") }
            assertTrue(enteredGate.await(5, TimeUnit.SECONDS))
            old.close()
            next.enqueue { calls.add("新命令") }.get(5, TimeUnit.SECONDS)
            openGate.countDown()
            assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            assertEquals(1, discarded.get())
            assertEquals(listOf("新命令"), calls.toList())
        } finally {
            openGate.countDown()
            old.close()
            next.close()
        }
    }

    @Test
    fun `newer ui tickets invalidate queued system commands without cancelling other keys`() {
        val gate = Mutex(locked = true)
        val enteredGate = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val playTicket = AtomicLong(1L)
        val seekTicket = AtomicLong(1L)
        val speedTicket = AtomicLong(1L)
        val isCurrent: (Int, Long) -> Boolean = { command, ticket ->
            when (command) {
                Player.COMMAND_PLAY_PAUSE -> playTicket.get() == ticket
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> seekTicket.get() == ticket
                else -> false
            }
        }
        val queue = SerialEngineCommandQueue(snapshots, media) { _, action ->
            enteredGate.countDown()
            gate.withLock {
                action()
                true
            }
        }
        var gateHeld = true
        try {
            val oldPlay = queue.enqueue {
                executeIfCurrentCommand(Player.COMMAND_PLAY_PAUSE, 1L, isCurrent) { calls.add("旧播放") }
            }
            assertTrue(enteredGate.await(5, TimeUnit.SECONDS))
            val oldSpeed = queue.enqueue {
                if (speedTicket.get() == 1L) calls.add("旧倍速")
            }
            val seek = queue.enqueue {
                executeIfCurrentCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, 1L, isCurrent) {
                    calls.add("跳转")
                }
            }
            // 模拟后发的 UI 请求持同一门执行；播放与倍速更新各自的票据。
            playTicket.incrementAndGet()
            speedTicket.incrementAndGet()
            calls.add("新暂停")
            calls.add("新倍速")
            gate.unlock()
            gateHeld = false
            oldPlay.get(5, TimeUnit.SECONDS)
            oldSpeed.get(5, TimeUnit.SECONDS)
            seek.get(5, TimeUnit.SECONDS)
            assertEquals(listOf("新暂停", "新倍速", "跳转"), calls.toList())
        } finally {
            if (gateHeld) gate.unlock()
            queue.close()
        }
    }

    @Test
    fun `client cancellation retires a speed ticket once while waiting for the gate`() {
        val gate = Mutex(locked = true)
        val enteredGate = CountDownLatch(1)
        val discarded = AtomicInteger()
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val queue = SerialEngineCommandQueue(snapshots, media) { _, action ->
            enteredGate.countDown()
            gate.withLock {
                action()
                true
            }
        }
        var gateHeld = true
        try {
            val pending = queue.enqueue(onDiscard = { discarded.incrementAndGet() }) { calls.add(1) }
            assertTrue(enteredGate.await(5, TimeUnit.SECONDS))
            assertTrue(pending.cancel(false))
            assertEquals(1, discarded.get())
            gate.unlock()
            gateHeld = false
            queue.enqueue {}.get(5, TimeUnit.SECONDS)
            assertEquals(1, discarded.get())
            assertTrue(calls.isEmpty())
        } finally {
            if (gateHeld) gate.unlock()
            queue.close()
        }
    }

    @Test
    fun `successful execution does not discard its speed ticket`() {
        val queue = newQueue()
        val discarded = AtomicInteger()
        try {
            queue.enqueue(onDiscard = { discarded.incrementAndGet() }) {}.get(5, TimeUnit.SECONDS)
            assertEquals(0, discarded.get())
        } finally {
            queue.close()
        }
    }

    @Test
    fun `commands require matching media and snapshot before enqueue`() {
        snapshots.value = snapshots.value.copy(mediaId = 2L)
        val queue = newQueue()
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val discarded = AtomicInteger()
        try {
            queue.enqueue(onDiscard = { discarded.incrementAndGet() }) { calls.add(1) }.get(5, TimeUnit.SECONDS)
            assertTrue(calls.isEmpty())
            media.value = SessionMedia(2L, "新标题")
            queue.enqueue(expectedMediaId = 1L, onDiscard = { discarded.incrementAndGet() }) {
                calls.add(3)
            }.get(5, TimeUnit.SECONDS)
            assertEquals(2, discarded.get())
            queue.enqueue { calls.add(2) }.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(2), calls.toList())
        } finally {
            queue.close()
        }
    }

    @Test
    fun `closing cancels queued commands and rejects further work`() {
        val queue = newQueue()
        val started = CountDownLatch(1)
        val continueFirst = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val discarded = AtomicInteger()
        try {
            val first = queue.enqueue {
                started.countDown()
                check(continueFirst.await(5, TimeUnit.SECONDS))
                calls.add(1)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val waiting = queue.enqueue(onDiscard = { discarded.incrementAndGet() }) { calls.add(2) }
            queue.close()
            assertTrue(waiting.isCancelled)
            assertEquals(1, discarded.get())
            queue.enqueue { calls.add(3) }.get(5, TimeUnit.SECONDS)
            continueFirst.countDown()
            first.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1), calls.toList())
        } finally {
            continueFirst.countDown()
            queue.close()
        }
    }

    @Test
    fun `seek is not dispatched while loading`() {
        snapshots.value = PlaybackSnapshot(mediaId = 1L, status = PlaybackStatus.LOADING)
        val queue = newQueue()
        var called = false
        try {
            queue.enqueue(requiresSeekable = true) { called = true }.get(5, TimeUnit.SECONDS)
            assertFalse(called)
        } finally {
            queue.close()
        }
    }

    @Test
    fun `failed command does not block the following command`() {
        val queue = newQueue()
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        val discarded = AtomicInteger()
        try {
            val failed = queue.enqueue(onDiscard = { discarded.incrementAndGet() }) { throw IllegalStateException("失败") }
            val next = queue.enqueue { calls.add(1) }
            val error = try {
                failed.get(5, TimeUnit.SECONDS)
                null
            } catch (exception: ExecutionException) {
                exception.cause
            }
            assertTrue(error is IllegalStateException)
            assertEquals(1, discarded.get())
            next.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1), calls.toList())
        } finally {
            queue.close()
        }
    }
}
