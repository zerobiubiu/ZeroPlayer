package com.privateplayer.data.webupload

import com.privateplayer.domain.repository.WebUploadEndpoint
import com.privateplayer.domain.repository.WebUploadNetwork
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUploadListenersTest {
    private val wifi = LanAddress(WebUploadNetwork.WIFI, "wifi-test", loopback(1))
    private val ethernet = LanAddress(WebUploadNetwork.ETHERNET, "ethernet-test", loopback(2))

    private fun loopback(last: Int) = InetAddress.getByAddress(byteArrayOf(127, 0, 0, last.toByte())) as Inet4Address

    private fun connect(target: LanAddress, port: Int) = Socket().apply {
        soTimeout = 5_000
        connect(InetSocketAddress(target.address, port), 5_000)
    }

    private fun assertCanBind(target: LanAddress, port: Int) {
        ServerSocket().use {
            it.reuseAddress = true
            it.bind(InetSocketAddress(target.address, port))
        }
    }

    private fun assertPeerClosed(client: Socket) {
        try {
            assertEquals(-1, client.getInputStream().read())
        } catch (_: SocketException) {
            // 收到复位也表示远端已关闭；超时不是关闭。
        }
    }

    @Test
    fun twoListenersRecoverOnceShareRequestStateAndStopActiveAndQueuedClients() {
        val recoveries = AtomicInteger()
        val requests = AtomicInteger()
        val failures = AtomicInteger()
        val finished = CountDownLatch(2)
        val listeners = WebUploadListeners(
            recover = { recoveries.incrementAndGet() },
            handle = { client ->
                try {
                    client.getOutputStream().write(requests.incrementAndGet())
                    client.getInputStream().read()
                } catch (_: SocketException) {
                    // 读取被会话关闭打断。
                } finally {
                    finished.countDown()
                }
            },
            onListenerLost = { failures.incrementAndGet() },
            port = 0,
        )
        listeners.use {
            it.start(listOf(wifi, ethernet))
            val endpoints = it.endpoints()
            val ports = endpoints.map { endpoint -> URI(endpoint.url).port }
            assertEquals(listOf(
                WebUploadEndpoint(wifi.kind, wifi.interfaceName, "http://127.0.0.1:${ports[0]}/"),
                WebUploadEndpoint(ethernet.kind, ethernet.interfaceName, "http://127.0.0.2:${ports[1]}/"),
            ), endpoints)
            assertEquals(1, recoveries.get())
            connect(wifi, ports[0]).use { first ->
                assertEquals(1, first.getInputStream().read())
                connect(ethernet, ports[1]).use { second ->
                    assertEquals(2, second.getInputStream().read())
                    connect(wifi, ports[0]).use { queued ->
                        listeners.close()
                        assertPeerClosed(first)
                        assertPeerClosed(second)
                        assertPeerClosed(queued)
                    }
                }
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(2, requests.get())
            assertEquals(0, failures.get())
            assertTrue(it.endpoints().isEmpty())
            assertCanBind(wifi, ports[0])
            assertCanBind(ethernet, ports[1])
        }
    }

    @Test
    fun busyAddressIsSkippedAndAllFailuresDoNotRecoverOrAdvertise() {
        ServerSocket().use { busy ->
            busy.reuseAddress = false
            busy.bind(InetSocketAddress(wifi.address, 0))
            val port = busy.localPort
            val recoveries = AtomicInteger()
            WebUploadListeners({ recoveries.incrementAndGet() }, {}, {}, port).use { partial ->
                partial.start(listOf(wifi, ethernet))
                assertEquals(listOf(WebUploadEndpoint(ethernet.kind, ethernet.interfaceName, "http://127.0.0.2:$port/")), partial.endpoints())
                connect(ethernet, port).use(::assertPeerClosed)
            }
            assertCanBind(ethernet, port)
            WebUploadListeners({ recoveries.incrementAndGet() }, {}, {}, port).use { all ->
                val error = assertThrows(WebUploadListenException::class.java) { all.start(listOf(wifi)) }
                assertTrue(error.message.orEmpty().contains("端口 $port"))
                assertTrue(all.endpoints().isEmpty())
            }
            assertEquals(1, recoveries.get())
        }
    }

    @Test
    fun noLanReturnsActionableErrorWithoutOpeningSocketOrRecovering() {
        WebUploadListeners({ error("不应恢复") }, {}, {}, 0, { error("不应打开监听") }).use {
            val error = assertThrows(WebUploadListenException::class.java) { it.start(emptyList()) }
            assertTrue(error.message.orEmpty().contains("未找到可用的局域网 IPv4 地址"))
            assertTrue(it.endpoints().isEmpty())
        }
    }

    @Test
    fun recoveryExceptionClosesEveryBoundSocket() {
        val sockets = mutableListOf<ServerSocket>()
        val ports = mutableListOf<Int>()
        WebUploadListeners(
            recover = { ports += sockets.map { it.localPort }; throw IOException("恢复中断") },
            handle = {}, onListenerLost = {}, port = 0,
            newSocket = { ServerSocket().also(sockets::add) },
        ).use { listeners ->
            val error = assertThrows(IOException::class.java) { listeners.start(listOf(wifi, ethernet)) }
            assertEquals("恢复中断", error.message)
            assertEquals(2, ports.size)
            assertTrue(sockets.all { it.isClosed })
            assertTrue(listeners.endpoints().isEmpty())
            assertCanBind(wifi, ports[0])
            assertCanBind(ethernet, ports[1])
        }
    }

    @Test
    fun cancelDuringRecoveryCannotPublishOrStealPortFromNextSession() {
        val recovering = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thread = Executors.newSingleThreadExecutor()
        val listener = ServerSocket()
        val notifications = AtomicInteger()
        val first = WebUploadListeners({
            recovering.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }, { error("已取消的会话不能处理请求") }, { notifications.incrementAndGet() }, 0, { listener })
        try {
            val starting = thread.submit { first.start(listOf(wifi)) }
            assertTrue(recovering.await(5, TimeUnit.SECONDS))
            val port = listener.localPort
            first.close()
            assertTrue(listener.isClosed)
            WebUploadListeners({}, { it.getOutputStream().write(42) }, {}, port).use { next ->
                next.start(listOf(wifi))
                release.countDown()
                val canceled = assertThrows(ExecutionException::class.java) { starting.get(5, TimeUnit.SECONDS) }
                assertTrue(canceled.cause is SocketException)
                assertTrue(first.endpoints().isEmpty())
                assertEquals(0, notifications.get())
                connect(wifi, port).use { assertEquals(42, it.getInputStream().read()) }
            }
        } finally {
            release.countDown()
            first.close()
            listener.close()
            thread.shutdownNow()
        }
    }

    @Test
    fun acceptedSocketRacingStopIsClosedBeforeDispatch() {
        val accepted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val staleFailures = AtomicInteger()
        val listener = object : ServerSocket() {
            override fun accept(): Socket {
                val client = super.accept()
                accepted.countDown()
                if (!release.await(5, TimeUnit.SECONDS)) {
                    client.close()
                    throw IOException("测试未释放 accept")
                }
                return client
            }
        }
        try {
            WebUploadListeners({}, { requests.incrementAndGet() }, { staleFailures.incrementAndGet() }, 0, { listener }).use { session ->
                session.start(listOf(wifi))
                val port = listener.localPort
                connect(wifi, port).use { client ->
                    assertTrue(accepted.await(5, TimeUnit.SECONDS))
                    session.close()
                    release.countDown()
                    assertPeerClosed(client)
                }
                assertCanBind(wifi, port)
                assertEquals(0, requests.get())
                assertEquals(0, staleFailures.get())
            }
        } finally {
            release.countDown()
            listener.close()
        }
    }

    @Test
    fun listenerLossWithdrawsOnlyItsAddressAndLeavesHealthyListenerUsable() {
        val sockets = mutableListOf<ServerSocket>()
        val lost = CountDownLatch(1)
        WebUploadListeners({}, { it.getOutputStream().write(7) }, { lost.countDown() }, 0, { ServerSocket().also(sockets::add) }).use { session ->
            session.start(listOf(wifi, ethernet))
            val surviving = session.endpoints()[1]
            sockets[0].close()
            assertTrue(lost.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(surviving), session.endpoints())
            connect(ethernet, URI(surviving.url).port).use { assertEquals(7, it.getInputStream().read()) }
        }
    }
}
