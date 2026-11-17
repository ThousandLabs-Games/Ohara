package com.thousandlabs.ohara.net

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.thousandlabs.ohara.privacy.AdBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Charge une page dans un navigateur invisible, comme si le lecteur l'ouvrait lui-même.
 * Plus lent qu'une requête directe, mais passe les vérifications anti-robot automatiques
 * et exécute le JavaScript des sites qui en ont besoin (WebNovel).
 */
object WebFetcher {
    private lateinit var app: Context
    private val mutex = Mutex()

    fun init(context: Context) { app = context.applicationContext }

    /**
     * @param readyJs expression JavaScript qui vaut true quand le contenu voulu est affiché.
     */
    suspend fun fetch(url: String, readyJs: String? = null, timeoutMs: Long = 25_000): Page = mutex.withLock {
        withContext(Dispatchers.Main) { load(url, readyJs, timeoutMs) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(url: String, readyJs: String?, timeoutMs: Long): Page = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val web = WebView(app)
        var finished = false
        var sawChallenge = false
        val started = System.currentTimeMillis()

        fun done(result: Result<Page>) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching { web.stopLoading(); web.destroy() }
            CookieManager.getInstance().flush()
            if (cont.isActive) result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            userAgentString = Http.userAgentFor(url)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        lateinit var poll: Runnable
        poll = Runnable {
            if (finished) return@Runnable
            val probe = "(function(){try{var t=document.title||'';var h=document.documentElement?document.documentElement.innerHTML.length:0;" +
                "var c=(t.indexOf('Just a moment')>=0||!!document.querySelector('#challenge-form,#challenge-running,#cf-challenge-running'));" +
                "var r=" + (readyJs ?: "true") + ";return (c?'C':'-')+(r?'R':'-')+h;}catch(e){return '--0';}})()"
            web.evaluateJavascript(probe) { raw ->
                if (finished) return@evaluateJavascript
                val s = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("--0")
                val challenge = s.startsWith("C")
                val ready = s.getOrNull(1) == 'R'
                if (challenge) sawChallenge = true
                if (!challenge && ready) {
                    web.evaluateJavascript("document.documentElement.outerHTML") { html ->
                        val text = runCatching { JSONArray("[$html]").getString(0) }.getOrDefault("")
                        done(Result.success(Page(200, text, web.url ?: url)))
                    }
                } else if (System.currentTimeMillis() - started > timeoutMs) {
                    done(Result.failure(if (sawChallenge) VerificationNeeded(url) else IOException("La page met trop de temps à répondre.")))
                } else handler.postDelayed(poll, 700)
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return if (AdBlocker.shouldBlock(request.url.toString(), url)) AdBlocker.emptyResponse() else null
            }

            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {}

            override fun onPageFinished(view: WebView, u: String?) {
                handler.removeCallbacks(poll)
                handler.postDelayed(poll, 400)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode == 404) {
                    done(Result.failure(SiteError(404, url)))
                }
            }
        }

        handler.postDelayed({ done(Result.failure(if (sawChallenge) VerificationNeeded(url) else IOException("La page met trop de temps à répondre."))) }, timeoutMs + 5000)
        cont.invokeOnCancellation { handler.post { done(Result.failure(IOException("Annulé"))) } }
        web.loadUrl(if (Http.isFfn(url)) Http.desktopUrl(url) else url)
    }
}
