package com.privateplayer.data.webupload

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalHttpProtocolTest {
    private fun request(text: String) =
        LocalHttpProtocol.readRequest(ByteArrayInputStream(text.toByteArray(StandardCharsets.ISO_8859_1)))

    @Test
    fun `parses a browser upload without reading its body`() {
        val input = ByteArrayInputStream(
            ("POST /api/uploads?path=&name=%E5%BD%B1%E7%89%87.mp4 HTTP/1.1\r\n" +
                "Host: 192.168.1.4:8765\r\nContent-Length: 21474836480\r\n\r\nvideo")
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val parsed = LocalHttpProtocol.readRequest(input)
        assertEquals("POST", parsed.method)
        assertEquals("21474836480", parsed.headers["content-length"])
        assertEquals("video", String(input.readBytes(), StandardCharsets.ISO_8859_1))
    }

    @Test
    fun `rejects duplicate content lengths`() {
        assertThrows(BadHttpRequest::class.java) {
            request("POST /api/uploads HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n")
        }
    }

    @Test
    fun `rejects transfer encoding to prevent ambiguous body boundaries`() {
        assertThrows(BadHttpRequest::class.java) {
            request("POST /api/uploads HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n")
        }
    }

    @Test
    fun `rejects oversized headers`() {
        assertThrows(BadHttpRequest::class.java) {
            request("GET / HTTP/1.1\r\nX-Long: " + "a".repeat(9000) + "\r\n\r\n")
        }
    }

    @Test
    fun `rejects malformed request line`() {
        assertThrows(BadHttpRequest::class.java) {
            request("PUT /api/uploads HTTP/1.1\r\nHost: example\r\n\r\n")
        }
    }
}
