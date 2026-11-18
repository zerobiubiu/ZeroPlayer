package com.privateplayer.media

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebUploadPageInstrumentedTest {
    @Test
    fun rowRetryUploadsOnlyRejectedFile() = runBlocking {
        val directory = "原 +目录"
        val a = "重传 A+.txt"
        val b = "待传 B 空.srt"
        val c = "稍后 C.txt"
        val removed = "移除.txt"
        val late = "下一批 D.txt"
        val posts = ConcurrentLinkedQueue<Uri>()
        fun sentFiles() = posts.map { it.getQueryParameter("name") to it.getQueryParameter("path") }

        withPackagedWebPage(
            url = "https://web-upload.test/",
            api = { request ->
                val uri = request.url
                if (request.method == "POST") posts.add(uri)
                when {
                    uri.path == "/api/config" -> webJson("""{"initialPath":"$directory"}""")
                    uri.path == "/api/files" -> webJson("""{"path":${JSONObject.quote(uri.getQueryParameter("path"))},"entries":[]}""")
                    uri.path == "/api/uploads" && request.method == "POST" -> if (posts.size == 1) {
                        webJson("""{"error":"目录不存在或不是目录"}""", 400, "Bad Request")
                    } else {
                        webJson("""{"taskId":"task-${posts.size}"}""", 202, "Accepted")
                    }
                    uri.path?.startsWith("/api/uploads/") == true && request.method == "GET" -> webJson("""{"state":"COMPLETED"}""")
                    else -> null
                }
            },
        ) { page ->
            val start = "document.getElementById('start-uploads')"
            val retry = "document.querySelector('button[aria-label=\"重新上传：$a\"]')"
            val uploading = "document.querySelector('#queue-list [data-state=uploading]')"
            page.awaitCondition("document.querySelector('#file-input:enabled') !== null")
            page.js("${selectFiles(a, contents = "原文件")}; $start.click()")
            page.awaitCondition("$retry !== null && !$retry.disabled")
            assertEquals("true", page.js("document.querySelector('#queue-list [data-state=failed]').textContent.includes('目录不存在或不是目录')"))
            assertEquals(listOf(a to directory), sentFiles())

            page.js("document.querySelector('#breadcrumbs button').click()")
            page.awaitCondition("document.getElementById('upload-path').textContent === '根目录'")
            page.js(selectFiles(b))
            assertEquals("true", page.js("!$start.hidden && $start.textContent === '上传 1 个文件'"))
            // 同一个事件循环内点击 A 后再选择文件，保证选择发生在重传响应之前。
            page.js("$retry.click(); ${selectFiles(c, removed)}")
            page.awaitCondition("document.querySelector('#queue-list > li').dataset.state === 'completed' && !$uploading")
            assertEquals(listOf(a to directory, a to directory), sentFiles())
            assertEquals(listOf(b, c, removed), page.pendingNames())
            assertEquals("true", page.js("!$start.hidden && $start.textContent === '上传 3 个文件'"))

            val remove = "document.querySelector('button[aria-label=\"移除：$removed\"]')"
            page.js("$start.click(); $remove.click(); ${selectFiles(late)}")
            page.awaitCondition("document.querySelectorAll('#queue-list [data-state=completed]').length >= 3 && !$uploading")
            assertEquals(listOf(a to directory, a to directory, b to "", c to ""), sentFiles())
            assertEquals(listOf(late), page.pendingNames())
            assertEquals("true", page.js("!$start.hidden && $start.textContent === '上传 1 个文件'"))
        }
    }

    @Test
    fun savingWithAllBytesReceivedRemainsIndeterminateUntilCompleted() = runBlocking {
        val state = AtomicReference("RUNNING")
        withPackagedWebPage(
            url = "https://web-upload-progress.test/",
            api = { request ->
                when (request.url.path) {
                    "/api/config" -> webJson("""{"initialPath":""}""")
                    "/api/files" -> webJson("""{"path":"","entries":[]}""")
                    "/api/uploads" -> if (request.method == "POST") {
                        webJson("""{"taskId":"staged"}""", 202, "Accepted")
                    } else null
                    "/api/uploads/staged" -> if (request.method == "GET") {
                        webJson("""{"state":"${state.get()}","bytesCopied":1,"sizeBytes":1}""")
                    } else null
                    else -> null
                }
            },
        ) { page ->
            val row = "document.querySelector('#queue-list > li')"
            val status = "$row.querySelector('.item-status').textContent"
            suspend fun snapshot(after: String = ""): JSONObject {
                val raw = requireNotNull(page.js("""
                    $after
                    (() => {
                      const row = $row, progress = row.querySelector('progress');
                      return {state: row.dataset.state, status: row.querySelector('.item-status').textContent,
                        text: row.innerText, hidden: row.querySelector('.progress-row').hidden,
                        visible: progress.getClientRects().length > 0, value: progress.getAttribute('value'),
                        label: progress.getAttribute('aria-label')};
                    })()
                """.trimIndent()))
                Log.i("WebUploadProgress", raw)
                return JSONObject(raw)
            }

            page.awaitCondition("document.querySelector('#file-input:enabled') !== null")
            page.js(selectFiles("staged.txt", contents = "x"))
            val transfer = snapshot("document.getElementById('start-uploads').click();")
            assertEquals("0", transfer.getString("value"))
            assertTrue("传输进度 0%" in transfer.getString("text"))

            page.awaitCondition("$status === '正在保存'")
            val saving = snapshot()
            assertEquals("importing", saving.getString("state"))
            assertTrue(saving.getBoolean("visible"))
            assertEquals(JSONObject.NULL, saving.get("value"))
            assertEquals("保存进度：staged.txt", saving.getString("label"))
            val text = saving.getString("text")
            assertFalse('%' in text || "已保存" in text)

            state.set("COMPLETED")
            page.awaitCondition("$status === '已保存'")
            val saved = snapshot()
            assertEquals("completed", saved.getString("state"))
            assertTrue(saved.getBoolean("hidden"))
            assertFalse(saved.getBoolean("visible"))
        }
    }

    @Test
    fun missingStagingOffersNeitherSaveRetryNorDownloadAndReselectionCreatesANewTask() = runBlocking {
        val posts = ConcurrentLinkedQueue<String>()
        val saved = java.util.concurrent.atomic.AtomicBoolean(false)
        withPackagedWebPage(url = "https://web-upload-recovery.test/", api = { request ->
            val path = request.url.path
            if (request.method == "POST" && path != null) posts.add(path)
            when (path) {
                "/api/config" -> webJson("""{"initialPath":""}""")
                "/api/files" -> webJson("""{"path":"","entries":[]}""")
                "/api/uploads" -> webJson("""{"taskId":"${if (posts.size == 1) "gone" else "recoverable"}"}""", 202, "Accepted")
                "/api/uploads/gone" -> webJson("""{"state":"FAILED","canRetry":false,"errorMessage":"上传暂存文件已丢失，请从网页重新上传。"}""")
                "/api/uploads/recoverable" -> if (saved.get()) webJson("""{"state":"COMPLETED"}""") else
                    webJson("""{"state":"FAILED","canRetry":true,"errorMessage":"目标文件夹不可用，请按原路径恢复后重试。"}""")
                "/api/uploads/recoverable/retry" -> {
                    saved.set(true)
                    webJson("""{"taskId":"recoverable"}""", 202, "Accepted")
                }
                else -> null
            }
        }) { page ->
            val start = "document.getElementById('start-uploads')"
            val rows = "document.getElementById('queue-list').children"
            val name = "网页原件.txt"
            page.awaitCondition("document.querySelector('#file-input:enabled') !== null")
            page.js("${selectFiles(name)}; $start.click()")
            page.awaitCondition("$rows[0].dataset.state === 'failed'")
            assertEquals("true", page.js("$rows[0].innerText.includes('请从网页重新上传') && !$rows[0].querySelector('button,a')"))

            page.js(selectFiles(name))
            assertEquals(listOf("/api/uploads"), posts.toList())
            page.js("$start.click()")
            page.awaitCondition("$rows[1].querySelector('button')?.textContent === '重试保存'")
            assertEquals("true", page.js("$rows[1].querySelector('a').textContent === '下载已接收文件'"))
            page.js("$rows[1].querySelector('button').click()")
            page.awaitCondition("$rows[1].dataset.state === 'completed'")
            assertEquals(listOf("/api/uploads", "/api/uploads", "/api/uploads/recoverable/retry"), posts.toList())
        }
    }

    private fun selectFiles(vararg names: String, contents: String = "") = """
        (() => {
          const files = new DataTransfer();
          for (const name of ${JSONArray(names.toList())}) files.items.add(new File([${JSONObject.quote(contents)}], name));
          const input = document.getElementById('file-input');
          input.files = files.files;
          input.dispatchEvent(new Event('change', {bubbles: true}));
        })()
    """.trimIndent()

    private suspend fun WebView.pendingNames(): List<String> {
        val names = JSONArray(requireNotNull(js("""
            Array.from(document.querySelectorAll('#queue-list > li'))
              .filter(row => row.querySelector('.item-status').textContent === '等待上传')
              .map(row => row.querySelector('.item-name').textContent)
        """.trimIndent())))
        return List(names.length(), names::getString)
    }
}
