package com.privateplayer.media

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 从 APK 读取页面，接口由用例控制；不启动服务、不访问真实文件树。 */
internal suspend fun withPackagedWebPage(
    url: String,
    api: (WebResourceRequest) -> WebResourceResponse?,
    test: suspend (WebView) -> Unit,
) {
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        lateinit var page: WebView
        scenario.onActivity { activity ->
            page = WebView(activity).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        when (request.url.path) {
                            "/" -> WebResourceResponse("text/html", "UTF-8", activity.assets.open("web-upload/index.html"))
                            "/app.js" -> WebResourceResponse("text/javascript", "UTF-8", activity.assets.open("web-upload/app.js"))
                            "/style.css" -> WebResourceResponse("text/css", "UTF-8", activity.assets.open("web-upload/style.css"))
                            else -> api(request) ?: WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
                        }
                }
            }
            activity.setContentView(page)
            page.loadUrl(url)
        }
        try {
            test(page)
        } finally {
            scenario.onActivity {
                (page.parent as? ViewGroup)?.removeView(page)
                page.destroy()
            }
        }
    }
}

internal fun webJson(body: String, status: Int = 200, reason: String = "OK") =
    WebResourceResponse("application/json", "UTF-8", status, reason, emptyMap(), body.byteInputStream())

internal suspend fun WebView.js(script: String): String? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { result -> evaluateJavascript(script) { result.resume(it) } }
}

internal suspend fun WebView.awaitCondition(expression: String) = withTimeout(10_000L) {
    while (js(expression) != "true") delay(25L)
}
