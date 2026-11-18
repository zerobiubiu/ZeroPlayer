package com.privateplayer.data.webupload

import com.privateplayer.domain.model.PrivateFileIdentity
import com.privateplayer.domain.repository.FileTreeEntry
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class WebDeleteTargetTest {
    @Test
    fun `文件与目录均按确认的路径及完整 Long 身份删除`() {
        for (type in listOf("file", "directory")) {
            val entry = FileTreeEntry(
                name = "显示名", path = "片/a+b&c", isDirectory = type == "directory",
                sizeBytes = 0, modifiedAt = 0, identity = PrivateFileIdentity(9_007_199_254_740_993L, Long.MAX_VALUE),
            )
            val fields = buildMap<String, String> { entry.writeWebDeleteTarget { key, value -> put(key, value) } }
            assertEquals(type, fields["type"])
            assertEquals("9007199254740993", fields["device"])
            assertEquals("9223372036854775807", fields["inode"])
            val query = LocalHttpProtocol.queryParameters(fields.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            })
            var calls = 0
            withWebDeleteTarget(query) { path, identity, isDirectory ->
                calls++
                assertEquals(entry.path, path)
                assertEquals(entry.identity, identity)
                assertEquals(entry.isDirectory, isDirectory)
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `旧路径请求及缺失或无效的确认信息不会触发删除`() {
        val valid = LocalHttpProtocol.queryParameters("path=a&type=file&device=12&inode=34")
        fun reject(query: Map<String, String>) {
            assertThrows(BadHttpRequest::class.java) {
                withWebDeleteTarget(query) { _, _, _ -> fail("不应执行删除：$query") }
            }
        }
        reject(mapOf("path" to "a"))
        for (key in valid.keys) {
            reject(valid - key)
            reject(valid + (key to ""))
        }
        reject(valid + ("type" to "unknown"))
        reject(valid + ("device" to "1.5"))
        reject(valid + ("inode" to "9223372036854775808"))
    }
}
