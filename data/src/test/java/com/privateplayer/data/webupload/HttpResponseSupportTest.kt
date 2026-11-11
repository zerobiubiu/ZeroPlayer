package com.privateplayer.data.webupload

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpResponseSupportTest {
    @Test
    fun `查询参数支持中文和根目录`() {
        val parameters = LocalHttpProtocol.queryParameters("path=&name=%E5%BD%B1%E7%89%87+1.mp4")
        assertEquals("", parameters["path"])
        assertEquals("影片 1.mp4", parameters["name"])
    }

    @Test
    fun `拒绝重复参数和错误编码`() {
        assertThrows(BadHttpRequest::class.java) { LocalHttpProtocol.queryParameters("path=a&path=b") }
        assertThrows(BadHttpRequest::class.java) { LocalHttpProtocol.queryParameters("name=%GG") }
    }

    @Test
    fun `文件名允许无扩展名并拒绝路径穿越`() {
        assertEquals("字幕", validateUploadName("字幕"))
        for (name in listOf("", " ", ".", "..", "../a", "a/b", "a\\b", "a\u0000b")) {
            assertThrows(BadHttpRequest::class.java) { validateUploadName(name) }
        }
        val decoded = LocalHttpProtocol.queryParameters("name=%2E%2E%2Fsecret")["name"]!!
        assertThrows(BadHttpRequest::class.java) { validateUploadName(decoded) }
    }

    @Test
    fun `重名时不覆盖并保留扩展名`() {
        assertEquals("影片 (2).mp4", availableUploadName("影片.mp4", setOf("影片.mp4", "影片 (1).mp4")))
        assertEquals("说明 (1)", availableUploadName("说明", setOf("说明")))
        assertEquals("大写 (1).TXT", availableUploadName("大写.TXT", setOf("大写.txt")))
    }

    @Test
    fun `长名称避重不截断 UTF8 字符且不超过文件系统限制`() {
        val original = "影".repeat(83) + ".mp4"
        val candidate = availableUploadName(original, setOf(original.lowercase()))
        assertTrue(candidate.endsWith(" (1).mp4"))
        assertTrue(candidate.toByteArray(StandardCharsets.UTF_8).size <= 255)
        assertFalse(candidate.contains('\uFFFD'))
    }

    @Test
    fun `上传长度检查覆盖零字节大文件和越界`() {
        assertEquals(0L, requestContentLength("0")!!)
        assertEquals(21_474_836_480L, requestContentLength("21474836480")!!)
        assertNull(requestContentLength(null))
        assertNull(requestContentLength("-1"))
        assertNull(requestContentLength("+1"))
        assertNull(requestContentLength("9223372036854775808"))
    }

    @Test
    fun `响应长度支持大于四十亿字节和零字节`() {
        val headers = responseHeaders(200, "application/octet-stream", 21_474_836_480L)
        assertTrue(headers.contains("Content-Length: 21474836480\r\n"))
        assertTrue(responseHeaders(200, "application/octet-stream", 0L).contains("Content-Length: 0\r\n"))
        assertThrows(IllegalArgumentException::class.java) { responseHeaders(200, "x", -1) }
    }
}
