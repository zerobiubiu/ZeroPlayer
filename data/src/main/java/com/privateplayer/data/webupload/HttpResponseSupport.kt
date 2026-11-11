package com.privateplayer.data.webupload

import java.nio.charset.StandardCharsets
import java.util.Locale

/** HTTP 上传长度只接受无符号十进制，零字节也是有效文件。 */
internal fun requestContentLength(raw: String?): Long? =
    raw?.takeIf { it.isNotEmpty() && it.all { digit -> digit in '0'..'9' } }?.toLongOrNull()

/** 拒绝路径分隔符和不可能在私有目录中表示的名称，不按文件类型过滤。 */
internal fun validateUploadName(name: String): String {
    if (name.isBlank() || name == "." || name == ".." ||
        name.any { it == '/' || it == '\\' || it == '\u0000' } ||
        name.toByteArray(StandardCharsets.UTF_8).size > 255) {
        throw BadHttpRequest("文件名无效或过长")
    }
    return name
}

/** 保留原名称；需要避重时尽量保留扩展名和 UTF-8 完整字符。 */
internal fun availableUploadName(name: String, existingLowercase: Set<String>): String {
    validateUploadName(name)
    if (name.lowercase(Locale.ROOT) !in existingLowercase) return name
    val dot = name.lastIndexOf('.')
    var base = if (dot > 0 && dot < name.lastIndex) name.substring(0, dot) else name
    var extension = if (dot > 0 && dot < name.lastIndex) name.substring(dot) else ""
    var number = 1
    while (true) {
        val suffix = " ($number)"
        val suffixBytes = suffix.toByteArray(StandardCharsets.UTF_8).size
        var extensionBytes = extension.toByteArray(StandardCharsets.UTF_8).size
        if (extensionBytes + suffixBytes >= 255) {
            base = name
            extension = ""
            extensionBytes = 0
        }
        val candidate = truncateUtf8(base, 255 - suffixBytes - extensionBytes) + suffix + extension
        if (candidate.lowercase(Locale.ROOT) !in existingLowercase) return candidate
        if (number == Int.MAX_VALUE) throw IllegalStateException("无法生成不重名的文件名")
        number++
    }
}

private fun truncateUtf8(value: String, maxBytes: Int): String {
    val result = StringBuilder()
    var index = 0
    var bytes = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val chars = String(Character.toChars(codePoint))
        val count = chars.toByteArray(StandardCharsets.UTF_8).size
        if (bytes + count > maxBytes) break
        result.append(chars)
        bytes += count
        index += Character.charCount(codePoint)
    }
    return result.toString()
}

/** 大文件的响应长度保持 Long，不经过 Int 转换。 */
internal fun responseHeaders(code: Int, type: String, length: Long, extra: String = ""): String {
    require(length >= 0L)
    val reason = when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        400 -> "Bad Request"
        404 -> "Not Found"
        409 -> "Conflict"
        411 -> "Length Required"
        507 -> "Insufficient Storage"
        else -> "Internal Server Error"
    }
    return "HTTP/1.1 $code $reason\r\n" +
        "Content-Type: $type\r\nContent-Length: $length\r\n" + extra +
        "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
        "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n" +
        "Referrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
}
