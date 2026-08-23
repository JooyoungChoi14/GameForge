package com.gameforge.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import java.net.URLEncoder

/**
 * GeckoView 기반 BrowserEngine 구현.
 * DevCompanion GeckoEngine 패턴 재사용.
 */
class GeckoEngine(private val context: Context) : BrowserEngine {

    private var runtime: GeckoRuntime? = null
    private var session: GeckoSession? = null

    private var _currentUrl: String = "about:blank"
    override val currentUrl: String get() = _currentUrl

    private var _canGoBack = false
    override val canGoBack: Boolean get() = _canGoBack

    private var _canGoForward = false
    override val canGoForward: Boolean get() = _canGoForward

    override val engineDetail: String = "150.0.20260511200624"

    private val pendingEvals = mutableMapOf<String, CompletableDeferred<String>>()
    private var evalCounter = 0

    // ── 생명주기 ────────────────────────────────────────────────

    override suspend fun initialize() {
        withContext(Dispatchers.Main) {
            if (runtime == null) {
                runtime = GeckoRuntime.create(context)
            }
            createSession()
        }
    }

    private fun createSession() {
        val rt = runtime ?: return

        session?.let { old ->
            old.close()
            old.contentDelegate = null
            old.progressDelegate = null
            old.navigationDelegate = null
            old.promptDelegate = null
        }

        val newSession = GeckoSession(GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .build())

        newSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {}
            override fun onPreviewImage(session: GeckoSession, previewImageUrl: String?) {}
            override fun onCrash(session: GeckoSession, crash: GeckoResult<GeckoSession>?) {}
            override fun onFirstComposite(session: GeckoSession) {}
            override fun onWebExtensionMessage(
                session: GeckoSession,
                extension: WebExtension?,
                message: Any?,
                sender: WebExtension.MessageSender?
            ): GeckoResult<Any>? = null
        }

        newSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                _currentUrl = url
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {}
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {}
        }

        newSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?) {
                _currentUrl = url ?: "about:blank"
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _canGoBack = canGoBack
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _canGoForward = canGoForward
            }
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<GeckoSession>? {
                return null // allow all
            }
        }

        newSession.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        newSession.open(rt)
        session = newSession
    }

    override fun shutdown() {
        session?.close()
        session = null
    }

    override fun reset() {
        createSession()
    }

    // ── 네비게이션 ────────────────────────────────────────────

    override fun loadUrl(url: String) {
        session?.loadUri(url)
    }

    override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String, encoding: String, historyUrl: String?) {
        val encodedData = URLEncoder.encode(data, "UTF-8")
        session?.loadUri("data:$mimeType;charset=utf-8,$encodedData")
    }

    override fun goBack() { session?.goBack() }
    override fun goForward() { session?.goForward() }
    override fun reload() { session?.reload() }

    override fun clearHistory() {
        // GeckoView doesn't have direct clearHistory — load about:blank as workaround
        session?.loadUri("about:blank")
        _canGoBack = false
        _canGoForward = false
    }

    // ── JavaScript 평가 ────────────────────────────────────────

    override suspend fun evalJs(js: String): String {
        val session = this.session ?: return ""
        val id = "eval_${evalCounter++}"
        val deferred = CompletableDeferred<String>()
        pendingEvals[id] = deferred

        try {
            val result = session.evaluate(js)
            return result?.toString() ?: ""
        } catch (e: Exception) {
            Log.w("GeckoEngine", "evalJs error: ${e.message}")
            return ""
        } finally {
            pendingEvals.remove(id)
        }
    }

    private suspend fun GeckoSession.evaluate(js: String): Any? {
        return withContext(Dispatchers.Main) {
            val result = GeckoResult<Any>()
            this@evaluate.evaluate(js, result)
            try {
                result.poll(5000) // 5s timeout
            } catch (e: Exception) {
                null
            }
        }
    }

    // ── 스크린샷 ────────────────────────────────────────────────

    override suspend fun screenshot(): ByteArray? {
        // TODO: Requires GeckoView widget reference for canvas-based screenshot
        // See DevCompanion's GeckoEngine.screenshot() for reference implementation
        return null
    }

    /** GeckoView 세션 획득 (UI에서 Compose 사용) */
    fun getSession(): GeckoSession? = session

    /** GeckoView 런타임 획득 */
    fun getRuntime(): GeckoRuntime? = runtime
}