package com.privateplayer.data.webupload

import com.privateplayer.domain.repository.WebUploadEndpoint
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class WebUploadListenException(message: String) : IOException(message)

/** 一次启动拥有一组监听、一份恢复操作及一个有界请求池；不绑定通配地址。 */
internal class WebUploadListeners(
    private val recover: () -> Unit,
    private val handle: (Socket) -> Unit,
    private val onListenerLost: () -> Unit,
    private val port: Int = 8765,
    private val newSocket: () -> ServerSocket = ::ServerSocket,
) : Closeable {
    private val lock = Any()
    private val listeners = LinkedHashMap<ServerSocket, WebUploadEndpoint>()
    private val clients = HashSet<Socket>()
    private val workers = ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8), ThreadPoolExecutor.AbortPolicy())
    private var started = false
    private var closed = false

    fun endpoints(): List<WebUploadEndpoint> = synchronized(lock) { listeners.values.toList() }

    fun start(addresses: List<LanAddress>) {
        synchronized(lock) {
            requireOpen()
            check(!started) { "监听会话不能重复启动" }
            started = true
        }
        var ready = false
        try {
            if (addresses.isEmpty()) throw WebUploadListenException("未找到可用的局域网 IPv4 地址，请连接 Wi-Fi、以太网或开启热点")
            var permissionDenied = false
            for (target in addresses) {
                synchronized(lock) {
                    requireOpen()
                    try {
                        val socket = newSocket()
                        var retained = false
                        try {
                            socket.reuseAddress = true
                            socket.bind(InetSocketAddress(target.address, port))
                            listeners[socket] = WebUploadEndpoint(target.kind, target.interfaceName, "http://${target.address.hostAddress}:${socket.localPort}/")
                            retained = true
                        } finally {
                            if (!retained) socket.closeQuietly()
                        }
                    } catch (_: IOException) {
                        // 此地址可能已消失或端口被占用，仍尝试其它地址。
                    } catch (_: SecurityException) {
                        permissionDenied = true
                    }
                }
            }
            synchronized(lock) {
                requireOpen()
                if (listeners.isEmpty()) throw WebUploadListenException(
                    if (permissionDenied) "没有局域网监听权限，请检查系统权限" else "所有局域网地址均无法监听端口 $port，请检查端口占用、网络或局域网权限",
                )
            }
            recover()
            synchronized(lock) {
                requireOpen()
                for ((socket, endpoint) in listeners) {
                    Thread({ acceptLoop(socket) }, "web-upload-${endpoint.interfaceName}").apply { isDaemon = true; start() }
                }
                ready = true
            }
        } finally {
            if (!ready) close()
        }
    }

    private fun requireOpen() {
        if (closed) throw SocketException("服务已停止")
    }

    private fun acceptLoop(server: ServerSocket) {
        try {
            while (dispatch(server.accept())) {
                // 下一次 accept 在锁外阻塞，由 close 唤醒。
            }
        } catch (_: IOException) {
            // 关停或接口丢失，下面仅撤销这个监听。
        } catch (_: SecurityException) {
            // 系统可能在运行期间撤销权限。
        } finally {
            val notify = synchronized(lock) {
                server.closeQuietly()
                listeners.remove(server) != null && !closed
            }
            if (notify) onListenerLost()
        }
    }

    private fun dispatch(client: Socket): Boolean = synchronized(lock) {
        var submitted = false
        try {
            if (closed) return@synchronized false
            client.soTimeout = 60_000
            clients.add(client)
            workers.execute {
                try {
                    client.use { if (!it.isClosed) handle(it) }
                } finally {
                    synchronized(lock) { clients.remove(client) }
                }
            }
            submitted = true
        } catch (_: SocketException) {
            // 单个客户端断开不影响监听。
        } catch (_: RejectedExecutionException) {
            // 请求池已满，不保留连接。
        } finally {
            if (!submitted) {
                clients.remove(client)
                client.closeQuietly()
            }
        }
        true
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            listeners.keys.forEach { it.closeQuietly() }
            listeners.clear()
            clients.forEach { it.closeQuietly() }
            clients.clear()
            workers.queue.clear()
            // 不打断已收满文件的同步和入库；正在读写网络的工作已由关闭 socket 唤醒。
            workers.shutdown()
        }
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // 仍需释放本会话其余资源。
    }
}
