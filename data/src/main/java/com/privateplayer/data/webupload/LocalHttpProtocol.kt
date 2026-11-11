package com.privateplayer.data.webupload

import java.io.EOFException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** 仅支持本地上传所需的 HTTP/1.x 请求，限制请求行和请求头避免无限缓冲。 */
internal data class LocalHttpRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
)

internal class BadHttpRequest(message: String) : Exception(message)

internal object LocalHttpProtocol {
    private const val MAX_LINE_BYTES = 8192
    private const val MAX_HEADER_BYTES = 32768
    private const val MAX_HEADERS = 80

    /** 查询参数按 application/x-www-form-urlencoded 解码，重复键一律拒绝。 */
    fun queryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) throw BadHttpRequest("查询参数无效")
            val separator = part.indexOf('=')
            val key: String
            val value: String
            try {
                key = URLDecoder.decode(if (separator < 0) part else part.substring(0, separator), "UTF-8")
                value = URLDecoder.decode(if (separator < 0) "" else part.substring(separator + 1), "UTF-8")
            } catch (_: IllegalArgumentException) {
                throw BadHttpRequest("查询参数编码无效")
            }
            if (key.isEmpty() || result.put(key, value) != null) throw BadHttpRequest("查询参数重复或无效")
        }
        return result
    }

    fun readRequest(input: InputStream): LocalHttpRequest {
        var total = 0
        fun line(): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val byte = input.read()
                if (byte == -1) throw EOFException("请求头未完整接收")
                total++
                if (total > MAX_HEADER_BYTES || bytes.size >= MAX_LINE_BYTES) {
                    throw BadHttpRequest("请求头过大")
                }
                if (byte == '\n'.code) {
                    if (bytes.isEmpty() || bytes.removeAt(bytes.lastIndex) != '\r'.code.toByte()) {
                        throw BadHttpRequest("请求行结束符无效")
                    }
                    return String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
                }
                if (byte == 0 || (byte < 32 && byte != '\r'.code && byte != '\t'.code)) {
                    throw BadHttpRequest("请求头包含无效字符")
                }
                bytes.add(byte.toByte())
            }
        }

        val parts = line().split(' ')
        if (parts.size != 3 || parts[2] !in setOf("HTTP/1.1", "HTTP/1.0") ||
            parts[0] !in setOf("GET", "POST") || !parts[1].startsWith('/')) {
            throw BadHttpRequest("不支持的请求格式")
        }
        val headers = mutableMapOf<String, String>()
        var count = 0
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (++count > MAX_HEADERS) throw BadHttpRequest("请求头过多")
            val separator = header.indexOf(':')
            if (separator <= 0) throw BadHttpRequest("请求头格式无效")
            val name = header.substring(0, separator).trim().lowercase(Locale.ROOT)
            val value = header.substring(separator + 1).trim()
            if (name.isEmpty() || headers.put(name, value) != null) {
                throw BadHttpRequest("请求头重复或无效")
            }
        }
        if (headers.containsKey("transfer-encoding")) {
            throw BadHttpRequest("不支持分块传输，请使用浏览器上传")
        }
        return LocalHttpRequest(parts[0], parts[1], headers)
    }
}
