package com.thousandlabs.ohara.net

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume

/**
 * Requêtes envoyées depuis l'intérieur d'un navigateur, exactement comme le site les envoie lui-même.
 *
 * Certains sites (WebNovel) refusent les requêtes directes de l'appli même avec les bons cookies :
 * la protection anti-robot reconnaît qu'elles ne viennent pas d'un vrai navigateur. Ici, la requête part
 * d'une page du site déjà ouverte : de préférence celle affichée dans le navigateur d'Ohara, sinon une page
 * ouverte en arrière-plan et gardée quelques minutes pour enchaîner les chapitres rapidement.
 */
object ApiBrowser {
    private lateinit var app: Context
    private val mutex = Mutex()
    private val views = HashMap<String, Pair<WebView, Long>>()
    private var counter = 0

    /** Navigateur visible d'Ohara s'il affiche actuellement une page de ce site (renseigné par l'interface). */
    var visibleProvider: ((String) -> WebView?)? = null

    fun init(context: Context) { app = context.applicationContext }

    private fun base(h: String) = h.lowercase().removePrefix("www.").removePrefix("m.")

    suspend fun fetch(apiUrl: String, originUrl: String, timeoutMs: Long = 25_000): String = mutex.withLock {
        withContext(Dispatchers.Main) {
            val host = base(URI(apiUrl).host ?: "")
            val visible = visibleProvider?.invoke(host)
            val web = visible ?: pageFor(host, originUrl)
            try {
                run(web, apiUrl, timeoutMs)
            } catch (e: VerificationNeeded) {
                // la page de fond a pu expirer : on la recharge une fois
                if (visible == null) { drop(host); run(pageFor(host, originUrl), apiUrl, timeoutMs) } else throw e
            }
        }
    }

    private fun drop(host: String) {
        views.remove(host)?.first?.let { runCatching { it.destroy() } }
    }

    private suspend fun eval(web: WebView, js: String): String = suspendCancellableCoroutine { c ->
        web.evaluateJavascript(js) { raw -> if (c.isActive) c.resume(runCatching { JSONArray("[$raw]").optString(0) }.getOrDefault("")) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun pageFor(host: String, originUrl: String): WebView {
        views[host]?.let { (w, at) -> if (System.currentTimeMillis() - at < 10 * 60_000L) return w else drop(host) }
        val url = if (Http.needsDesktop(originUrl)) Http.desktopUrl(originUrl) else originUrl
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.blockNetworkImage = true
        w.settings.loadsImagesAutomatically = false
        w.settings.userAgentString = Http.userAgentFor(url)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = WebViewClient()
        w.loadUrl(url)
        val start = System.currentTimeMillis()
        var challenge = false
        while (true) {
            delay(500)
            val s = eval(w, "(function(){var t=document.title||'';var c=t.indexOf('Just a moment')>=0||!!document.querySelector('#challenge-form,#challenge-running');" +
                "return (c?'C':'-')+(document.readyState=='complete'?'R':'-');})()")
            challenge = s.startsWith("C")
            if (!challenge && s.getOrNull(1) == 'R') break
            if (System.currentTimeMillis() - start > 25_000) {
                runCatching { w.destroy() }
                throw if (challenge) VerificationNeeded(url) else IOException("Le site ne répond pas.")
            }
        }
        views[host] = w to System.currentTimeMillis()
        return w
    }

    private suspend fun run(web: WebView, apiUrl: String, timeoutMs: Long): String {
        val id = "r" + (++counter)
        val js = "(function(){window.__oh=window.__oh||{};window.__oh['$id']=null;" +
            "fetch(${JSONObject.quote(apiUrl)},{credentials:'include',headers:{'Accept':'application/json, text/plain, */*'}})" +
            ".then(function(r){return r.text().then(function(t){window.__oh['$id']=JSON.stringify({s:r.status,t:t});});})" +
            ".catch(function(e){window.__oh['$id']=JSON.stringify({s:0,t:String(e)});});})()"
        web.evaluateJavascript(js, null)
        val start = System.currentTimeMillis()
        while (true) {
            delay(250)
            val r = eval(web, "(function(){var v=window.__oh&&window.__oh['$id'];if(v){delete window.__oh['$id'];}return v||'';})()")
            if (r.isNotEmpty()) {
                val o = JSONObject(r)
                val status = o.optInt("s"); val text = o.optString("t")
                if (status == 0) throw IOException("Requête impossible : $text")
                if (Http.isChallengeHtml(text) || status == 403 && text.trimStart().startsWith("<")) throw VerificationNeeded(apiUrl)
                if (status >= 400) throw SiteError(status, apiUrl)
                return text
            }
            if (System.currentTimeMillis() - start > timeoutMs) throw IOException("Le site met trop de temps à répondre.")
        }
    }
}
