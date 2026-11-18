package com.privateplayer.media

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDeletePageInstrumentedTest {
    @Test
    fun `打包网页确认时原样提交列表身份并显示删除冲突`() = runBlocking {
        val path = "片/a+b &c"
        val device = "9007199254740993"
        val inode = "9223372036854775807"
        val conflict = "文件夹已被替换，请刷新文件列表"
        val reads = AtomicInteger()
        val deletes = ConcurrentLinkedQueue<Pair<String, Uri>>()
        withPackagedWebPage(
            url = "https://web-delete.test/",
            api = { request ->
                when (request.url.path) {
                    "/api/config" -> webJson("""{"initialPath":"片"}""")
                    "/api/files" -> {
                        // 第二次返回相同数字的 JSON number；网页不能把已丢精度的身份转回字符串提交。
                        val wireInode = if (reads.incrementAndGet() == 1) JSONObject.quote(inode) else inode
                        webJson("""{"path":"片","entries":[{"name":"a+b &c","path":"$path","type":"directory","device":"$device","inode":$wireInode,"size":0,"modifiedAt":0}]}""")
                    }
                    "/api/delete" -> {
                        deletes.add(request.method to request.url)
                        webJson(JSONObject().put("error", conflict).toString(), 409, "Conflict")
                    }
                    else -> null
                }
            },
            test = { page ->
                val button = "document.querySelector('#files-list button[aria-label^=\"删除：\"]')"
                page.awaitCondition("$button !== null")
                val management = "$button.closest('details')"
                assertEquals("false", page.js("$management.open"))
                page.js("$management.querySelector('summary').click()")
                assertEquals("true", page.js("$management.open"))
                assertEquals("true", page.js("document.querySelector('#file-input').multiple && !document.querySelector('#file-input').disabled"))
                assertEquals("true", page.js("document.querySelector('#queue-panel').hidden && document.querySelector('#start-uploads').hidden"))
                page.js("$button.click()")
                assertEquals("true", page.js("document.getElementById('action-dialog').open"))
                assertEquals("true", page.js("document.getElementById('dialog-description').textContent.includes('a+b &c') && document.getElementById('dialog-description').textContent.includes('全部内容将永久删除')"))
                assertEquals("true", page.js("document.querySelector('.notice').textContent.includes('无需登录') && document.querySelector('.notice').textContent.includes('删除文件')"))
                assertTrue(deletes.isEmpty())
                page.js("document.getElementById('dialog-submit').click()")
                page.awaitCondition("!document.getElementById('dialog-error').hidden")
                assertEquals("true", page.js("document.getElementById('dialog-error').textContent === ${JSONObject.quote(conflict)}"))
                val (method, url) = deletes.single()
                assertEquals("POST", method)
                assertEquals(path, url.getQueryParameter("path"))
                assertEquals("directory", url.getQueryParameter("type"))
                assertEquals(device, url.getQueryParameter("device"))
                assertEquals(inode, url.getQueryParameter("inode"))
                assertEquals(1, reads.get())

                page.js("document.getElementById('dialog-cancel').click(); document.getElementById('refresh-files').click()")
                page.awaitCondition("document.getElementById('files-state').dataset.error === 'true'")
                assertEquals(2, reads.get())
                assertEquals(1, deletes.size)
            },
        )
    }
}
