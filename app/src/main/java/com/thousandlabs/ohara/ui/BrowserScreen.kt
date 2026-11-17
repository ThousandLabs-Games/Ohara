package com.thousandlabs.ohara.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.History
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.net.ApiBrowser
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.net.Http
import com.thousandlabs.ohara.privacy.AdBlocker
import com.thousandlabs.ohara.privacy.Privacy
import com.thousandlabs.ohara.sources.GenericSource
import com.thousandlabs.ohara.sources.LinkInfo
import com.thousandlabs.ohara.sources.Sources
import com.thousandlabs.ohara.translate.Translate
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Moteurs de recherche respectueux de la vie privée, pour la navigation libre. */
val SEARCH_ENGINES = listOf(
    "DuckDuckGo" to "https://duckduckgo.com/?q=",
    "Brave Search" to "https://search.brave.com/search?q=",
    "Startpage" to "https://www.startpage.com/do/search?q=",
    "Mojeek" to "https://www.mojeek.com/search?q=",
    "Qwant" to "https://www.qwant.com/?q="
)

/** Un onglet du navigateur. Sa page vierge (adresse vide) est la page d'accueil d'Ohara. */
class Tab(val id: Long, initialUrl: String) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var blocked by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    var serverError by mutableIntStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    /** Chapitre téléchargé affiché sans connexion. */
    var offline by mutableStateOf(false)
    /** Texte sélectionné (et son paragraphe), pour « Masquer ». */
    var selection by mutableStateOf<Pair<String, String>?>(null)
    var lastUsed by mutableLongStateOf(System.currentTimeMillis())
    /** Informations de la page de fic (titre, auteur…) envoyées par le suivi de lecture. */
    var meta: String? = null
    @Volatile var pageUrl: String? = initialUrl.ifBlank { null }
    var web: WebView? = null
    val isStart: Boolean get() = url.isBlank()
}

/** Le navigateur d'Ohara : onglets, chargement des pages, scripts (masquage, suivi de lecture, traduction). */
object BrowserHolder {
    val tabs = mutableStateListOf<Tab>()
    var current by mutableIntStateOf(0)
    var showTabs by mutableStateOf(false)
    var verifying by mutableStateOf(false)
    var picking by mutableStateOf(false)
    var picked by mutableStateOf<Triple<String, String, Int>?>(null)
    var findOpen by mutableStateOf(false)
    var findInfo by mutableStateOf("")
    private var nextId = 1L
    private var restored = false
    private const val OFFLINE_HOST = "hors-ligne.ohara"

    val tab: Tab
        get() {
            if (!restored) restore()
            if (tabs.isEmpty()) tabs.add(Tab(nextId++, ""))
            return tabs[current.coerceIn(0, tabs.lastIndex)]
        }

    /** Navigateur visible (onglet actuel), s'il existe. */
    val web: WebView? get() = tabs.getOrNull(current)?.web

    fun restore() {
        if (restored) return
        restored = true
        val urls = Prefs.getList("tabs")
        urls.forEach { tabs.add(Tab(nextId++, it)) }
        if (tabs.isEmpty()) tabs.add(Tab(nextId++, ""))
        current = Prefs.getInt("tab_cur", 0).coerceIn(0, tabs.lastIndex)
    }

    fun saveTabs() {
        Prefs.putList("tabs", tabs.map { if (it.offline) "" else it.url })
        Prefs.putInt("tab_cur", current)
    }

    fun allowedHost(host: String?): Boolean {
        if (host == null) return false
        if (host == OFFLINE_HOST) return true
        if (Prefs.advancedBrowsing && Prefs.advancedUnlocked) return true
        return Privacy.isSourceHost(host) || GenericSource.matches(host)
    }

    private val DOMAIN = Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$")

    /** Ce que l'on tape dans la barre d'adresse : une adresse, ou une recherche. */
    fun toUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val isAddress = t.startsWith("http://") || t.startsWith("https://") || DOMAIN.matches(t)
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            DOMAIN.matches(t) -> "https://$t"
            Prefs.advancedBrowsing && Prefs.advancedUnlocked ->
                SEARCH_ENGINES.getOrElse(Prefs.searchEngine) { SEARCH_ENGINES[0] }.second + URLEncoder.encode(t, "UTF-8")
            else -> "https://www.fanfiction.net/search/?keywords=${URLEncoder.encode(t, "UTF-8")}&ready=1&type=story"
        }
        if (!isAddress) History.add(History.Kind.SEARCH, t, url)
        return Privacy.upgrade(Privacy.cleanUrl(url))
    }

    fun open(raw: String) { toUrl(raw)?.let { openUrl(it, newTab = false) } }

    fun openUrl(url: String, newTab: Boolean) {
        restore()
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (!allowedHost(host)) {
            Toast.show("Ce site ne fait pas partie des sites de fics. La navigation libre se débloque dans les réglages.")
            return
        }
        showTabs = false
        val t = if (newTab && !tab.isStart) newTab(select = true) else tab
        t.offline = false
        t.url = url
        t.web?.let { load(t, it, url) }
    }

    fun newTab(url: String = "", select: Boolean = true): Tab {
        restore()
        val t = Tab(nextId++, url)
        tabs.add(t)
        if (select) { current = tabs.lastIndex; t.lastUsed = System.currentTimeMillis() }
        showTabs = false
        saveTabs()
        return t
    }

    fun select(i: Int) {
        current = i.coerceIn(0, tabs.lastIndex)
        tab.lastUsed = System.currentTimeMillis()
        showTabs = false
        trim()
        saveTabs()
    }

    private fun destroy(t: Tab) {
        t.web?.let { w -> (w.parent as? ViewGroup)?.removeView(w); w.destroy() }
        t.web = null
    }

    fun close(t: Tab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        destroy(t)
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(Tab(nextId++, ""))
        if (current >= tabs.size || i < current) current = (current - 1).coerceAtLeast(0)
        saveTabs()
    }

    fun closeAll() {
        tabs.forEach { destroy(it) }
        tabs.clear(); tabs.add(Tab(nextId++, "")); current = 0
        saveTabs()
    }

    /** Au-delà de 6 onglets en mémoire, les plus anciens sont libérés (rechargés quand on y revient). */
    private fun trim() {
        val live = tabs.filter { it.web != null && it !== tab }.sortedBy { it.lastUsed }
        if (live.size <= 5) return
        live.take(live.size - 5).forEach { destroy(it) }
    }

    fun goHome() { tab.offline = false; tab.url = ""; tab.title = ""; saveTabs() }

    fun load(t: Tab, w: WebView, url: String) {
        val ua = if (t.desktop) Http.desktopUserAgent else Http.userAgent
        if (w.settings.userAgentString != ua) w.settings.userAgentString = ua
        w.loadUrl(url, Privacy.headers())
    }

    fun setDesktop(t: Tab, on: Boolean) {
        t.desktop = on
        val w = t.web ?: return
        w.settings.userAgentString = if (on) Http.desktopUserAgent else Http.userAgent
        if (!t.isStart) w.reload()
    }

    // ---------- Chapitres téléchargés, affichés comme des pages ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun offlineHtml(ficId: String, idx: Int): String? {
        val f = Library.get(ficId) ?: return null
        val raw = Library.readChapter(ficId, idx) ?: return null
        val total = maxOf(f.totalChapters, idx + 1)
        val title = f.chapters.getOrNull(idx)?.title ?: "Chapitre ${idx + 1}"
        val prev = (idx - 1 downTo 0).firstOrNull { Library.hasChapter(ficId, it) }
        val next = (idx + 1 until total + 1).firstOrNull { Library.hasChapter(ficId, it) }
        fun link(i: Int?, label: String) = if (i == null) "<span class=\"off\">$label</span>" else "<a href=\"https://$OFFLINE_HOST/$ficId/$i\">$label</a>"
        val nav = "<nav>${link(prev, "« Précédent")}<span>${idx + 1} / $total</span>${link(next, "Suivant »")}</nav>"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>${esc(f.title)} — ${esc(title)}</title><style>" +
            "body{font-family:Roboto,'Noto Sans',sans-serif;font-size:17px;line-height:1.55;color:#222;background:#fff;margin:0;padding:12px 14px 60px;}" +
            "@media (prefers-color-scheme: dark){body{color:#ddd;background:#121212}a{color:#8ab4f8}}" +
            ".bandeau{font-size:12px;color:#777;border-bottom:1px solid #ddd;padding-bottom:8px;margin-bottom:10px}" +
            "h1{font-size:19px;margin:6px 0 2px} h2{font-size:16px;font-weight:500;color:#666;margin:0 0 16px}" +
            "p{margin:0 0 .9em} nav{display:flex;justify-content:space-between;align-items:center;margin:18px 0;font-size:15px}" +
            "nav a{text-decoration:none;color:#1a5fb4;padding:8px 4px} nav .off{color:#aaa;padding:8px 4px} img{max-width:100%}" +
            "</style></head><body><div class=\"bandeau\">Chapitre gardé sur le téléphone · lisible sans connexion</div>" +
            "<h1>${esc(f.title)}</h1><h2>${esc(title)}</h2>$nav<div id=\"storytext\" class=\"storytext\">$raw</div>$nav</body></html>"
    }

    private var pendingOffline: Triple<Long, String, String>? = null

    fun openOffline(ficId: String, idx: Int) {
        restore()
        val f = Library.get(ficId) ?: return
        val html = offlineHtml(ficId, idx) ?: run { Toast.show("Ce chapitre n'est pas sur le téléphone."); return }
        Nav.home(Nav.TAB_BROWSER)
        val t = if (!tab.isStart) newTab() else tab
        val real = f.chapters.getOrNull(idx)?.url ?: Sources.of(f.site).chapterUrl(f, idx)
        t.offline = true
        t.url = real
        t.title = f.title
        val w = t.web
        if (w != null) w.loadDataWithBaseURL(real, html, "text/html", "utf-8", real)
        else pendingOffline = Triple(t.id, real, html)
    }

    // ---------- Masquer un élément (comme sur Brave) ----------

    const val PICKER_JS = """(function(){
if(window.__oharaPick)return;window.__oharaPick=true;
var box=document.createElement('div');
box.style.cssText='position:absolute;z-index:2147483647;pointer-events:none;border:2px solid #1E8E6E;background:rgba(30,142,110,.18);border-radius:3px;';
document.documentElement.appendChild(box);var cur=null;
function esc(s){return window.CSS&&CSS.escape?CSS.escape(s):s;}
function sel(el){var parts=[];var e=el;while(e&&e.nodeType==1&&e!==document.body&&parts.length<4){
if(e.id&&!/\d{3,}/.test(e.id)){parts.unshift('#'+esc(e.id));break;}
var p=e.tagName.toLowerCase();var cls=Array.prototype.filter.call(e.classList,function(c){return !/\d{3,}/.test(c)&&c.length<40;}).slice(0,2);
if(cls.length)p+='.'+cls.map(esc).join('.');parts.unshift(p);e=e.parentElement;}return parts.join(' > ');}
function show(el){cur=el;var r=el.getBoundingClientRect();box.style.left=(r.left+window.scrollX)+'px';box.style.top=(r.top+window.scrollY)+'px';
box.style.width=r.width+'px';box.style.height=r.height+'px';var s=sel(el);var n=0;try{n=document.querySelectorAll(s).length;}catch(x){}
OharaPick.picked(s,(el.innerText||el.tagName).slice(0,80),n);}
window.__oharaPickH=function(e){e.preventDefault();e.stopPropagation();show(e.target);};
document.addEventListener('click',window.__oharaPickH,true);
window.oharaPick={parent:function(){if(cur&&cur.parentElement&&cur.parentElement!==document.body&&cur.parentElement!==document.documentElement)show(cur.parentElement);},
stop:function(){document.removeEventListener('click',window.__oharaPickH,true);box.remove();window.__oharaPick=false;},
hide:function(s){try{document.querySelectorAll(s).forEach(function(x){x.style.setProperty('display','none','important');});}catch(x){}}};
})();"""

    fun startPicker() { picking = true; picked = null; web?.evaluateJavascript(PICKER_JS, null) }
    fun stopPicker() { picking = false; picked = null; web?.evaluateJavascript("if(window.oharaPick)oharaPick.stop()", null) }
    fun hidePicked() {
        val (selector, _, _) = picked ?: return
        val host = runCatching { java.net.URI(tab.url).host }.getOrNull() ?: return
        AdBlocker.UserHiding.add(host, selector)
        web?.evaluateJavascript("if(window.oharaPick)oharaPick.hide(${JSONObject.quote(selector)})", null)
        picked = null
        Toast.show("Élément masqué sur tout le site.")
    }

    fun cosmeticJs(page: String?): String {
        val css = AdBlocker.cosmeticCss(page)
        if (css.isEmpty()) return ""
        return "(function(){var s=document.getElementById('ohara-cos');if(!s){s=document.createElement('style');s.id='ohara-cos';" +
            "(document.head||document.documentElement).appendChild(s);}s.textContent=${JSONObject.quote(css)};})();"
    }

    /** Page d'une fic reconnue (FanFiction.net, SpaceBattles, WebNovel, sites ajoutés). */
    fun ficInfo(url: String): LinkInfo? = if (url.isBlank()) null else Sources.resolve(url)?.second

    /** Scripts de la page : masquage, sélection, suivi de lecture, traduction. */
    fun pageScripts(w: WebView, u: String) {
        val info = ficInfo(u)
        w.evaluateJavascript(PageMask.script(PageMask.config(info)), null)
        w.evaluateJavascript(PageMask.SELECTION_JS, null)
        if (info != null && (info.chapterIndex != null || info.chapterKey != null || (info.site == Site.SB && u.contains("/reader")))) w.evaluateJavascript(ReadTracker.script(info), null)
        PageTranslator.inject(w)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webFor(t: Tab, context: Context): WebView {
        t.web?.let { return it }
        val w = WebView(context)
        Privacy.configure(w)
        w.settings.userAgentString = if (t.desktop) Http.desktopUserAgent else Http.userAgent
        val main = Handler(Looper.getMainLooper())
        w.addJavascriptInterface(object {
            @JavascriptInterface fun picked(selector: String, text: String, count: Int) { main.post { picked = Triple(selector, text, count) } }
        }, "OharaPick")
        w.addJavascriptInterface(object {
            @JavascriptInterface fun report(words: Int, fraction: Float, meta: String) {
                val u = t.pageUrl ?: return
                main.post { if (meta.isNotBlank()) t.meta = meta; ReadTracker.onReport(u, words, fraction, meta) }
            }
            @JavascriptInterface fun chapter(html: String) { val u = t.pageUrl ?: return; main.post { ReadTracker.onChapter(u, html) } }
        }, "OharaRead")
        w.addJavascriptInterface(object {
            @JavascriptInterface fun selection(text: String, para: String) { main.post { t.selection = if (text.isBlank()) null else text to para } }
        }, "OharaSel")
        PageTranslator.attach(w)
        w.setFindListener { active, count, done -> if (done) findInfo = if (count == 0) "Aucun résultat" else "${active + 1} / $count" }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { t.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { if (!title.isNullOrBlank()) t.title = title }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == OFFLINE_HOST) {
                    val parts = uri.pathSegments
                    val id = parts.getOrNull(0); val i = parts.getOrNull(1)?.toIntOrNull()
                    if (id != null && i != null) offlineHtml(id, i)?.let { html ->
                        val f = Library.get(id) ?: return true
                        val real = f.chapters.getOrNull(i)?.url ?: Sources.of(f.site).chapterUrl(f, i)
                        t.offline = true; t.url = real
                        view.loadDataWithBaseURL(real, html, "text/html", "utf-8", real)
                    }
                    return true
                }
                if (!request.isForMainFrame) return false
                val u = uri.toString()
                if (!u.startsWith("http")) {
                    if (u.startsWith("intent:") || u.startsWith("mailto:") || u.startsWith("tel:")) Toast.show("Lien vers une autre appli ignoré.")
                    return true
                }
                if (!allowedHost(uri.host)) {
                    Toast.show("Lien vers un site hors de la liste (${uri.host ?: "?"}) : bloqué.")
                    return true
                }
                t.offline = false
                val clean = Privacy.upgrade(Privacy.cleanUrl(u))
                if (clean != u) { load(t, view, clean); return true }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return if (AdBlocker.shouldBlock(request.url.toString(), t.pageUrl)) AdBlocker.emptyResponse() else null
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 500) t.serverError = errorResponse.statusCode
            }

            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                t.pageUrl = u
                if (u != null && u.startsWith("http") && !t.offline) t.url = u
                AdBlocker.PageStats.blocked = 0; t.blocked = 0; t.serverError = 0; t.selection = null; t.meta = null
                if (t === tab) { picking = false; picked = null }
                PageTranslator.newPage()
            }

            override fun onPageCommitVisible(view: WebView, u: String?) {
                cosmeticJs(u).takeIf { it.isNotEmpty() }?.let { view.evaluateJavascript(it, null) }
            }

            override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                t.canBack = view.canGoBack(); t.canForward = view.canGoForward()
                if (u != null && u.startsWith("http") && !t.offline) t.url = u
            }

            override fun onPageFinished(view: WebView, u: String?) {
                t.blocked = AdBlocker.PageStats.blocked
                t.canBack = view.canGoBack(); t.canForward = view.canGoForward()
                cosmeticJs(u).takeIf { it.isNotEmpty() }?.let { view.evaluateJavascript(it, null) }
                if (u == null || !u.startsWith("http")) return
                pageScripts(view, u)
                if (!t.offline) {
                    History.add(History.Kind.PAGE, view.title.orEmpty(), u)
                    saveTabs()
                }
                val host = runCatching { java.net.URI(u).host }.getOrNull()
                if (Privacy.isSourceHost(host)) view.evaluateJavascript("document.title") { raw ->
                    val title = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
                    if (!title.contains("Just a moment", true) && !title.contains("Attention Required", true)) {
                        Fetcher.verificationDone(u)
                        if (verifying) {
                            verifying = false
                            Downloads.resumeVerified()
                            Toast.show("Vérification réussie : le téléchargement reprend.")
                        }
                    }
                }
            }
        }
        t.web = w
        ApiBrowser.visibleProvider = { host ->
            web?.takeIf { v -> runCatching { java.net.URI(v.url ?: "").host?.lowercase() == host }.getOrDefault(false) }
        }
        val po = pendingOffline
        if (po != null && po.first == t.id) {
            pendingOffline = null
            w.loadDataWithBaseURL(po.second, po.third, "text/html", "utf-8", po.second)
        } else if (!t.isStart) load(t, w, t.url)
        return w
    }

    fun share(context: Context) {
        val u = tab.url
        if (u.isBlank()) return
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u), "Partager"))
    }

    /** Ajouter / télécharger la fic de la page affichée. */
    fun addCurrent(download: Boolean) {
        val u = tab.url
        if (ficInfo(u) == null) { Toast.show("Ouvrez d'abord une page de la fic (un chapitre)."); return }
        ReadTracker.addNow(u, tab.meta, download)
    }
}

// ======================================================================
// Interface
// ======================================================================

@Composable
fun BrowserHome() {
    val context = LocalContext.current
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    val h = BrowserHolder
    remember { h.restore(); 0 }
    val t = h.tab
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    BackHandler(enabled = h.showTabs) { h.showTabs = false }
    BackHandler(enabled = !h.showTabs && h.picking) { h.stopPicker() }
    BackHandler(enabled = !h.showTabs && !h.picking && editing) { editing = false }
    BackHandler(enabled = !h.showTabs && !h.picking && !editing && t.selection != null) {
        t.web?.evaluateJavascript("var s=window.getSelection();if(s)s.removeAllRanges();", null); t.selection = null
    }
    BackHandler(enabled = !h.showTabs && !h.picking && !editing && t.selection == null && !t.isStart) {
        val w = t.web
        if (w != null && w.canGoBack()) w.goBack() else h.goHome()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(t, editing, onEdit = { editing = it })
            if (!editing) {
                PageNotes(t)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(t.id) {
                        AndroidView(
                            factory = { ctx -> h.webFor(t, ctx).also { w -> (w.parent as? ViewGroup)?.removeView(w) } },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (t.isStart) StartPage(onSearch = { editing = true })
                }
                if (h.findOpen) FindBar(t)
                SelectionBar(t)
                if (h.picking) PickerBar()
                BottomToolbar(t, onMenu = { menu = true })
            } else Spacer(Modifier.weight(1f))
        }
        if (h.showTabs) TabSwitcher()
    }
    if (menu) BrowserMenu(t, context) { menu = false }
}

@Composable
private fun AddressBar(t: Tab, editing: Boolean, onEdit: (Boolean) -> Unit) {
    val h = BrowserHolder
    val host = remember(t.url) { runCatching { java.net.URI(t.url).host?.removePrefix("www.") }.getOrNull().orEmpty() }
    var field by remember(editing) { mutableStateOf(if (editing && !t.isStart) t.url else "") }
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shadowElevation = 1.dp) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    TextField(
                        value = field, onValueChange = { field = it }, singleLine = true,
                        placeholder = { Text(if (Prefs.advancedBrowsing && Prefs.advancedUnlocked) "Rechercher ou saisir une adresse" else "Adresse, ou titre à chercher") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { IconButton(onClick = { if (field.isEmpty()) onEdit(false) else field = "" }) { Icon(Icons.Outlined.Close, "Effacer") } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { h.open(field); onEdit(false) }),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = cs.surfaceContainerHighest, unfocusedContainerColor = cs.surfaceContainerHighest
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                } else {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHighest)
                            .clickable { onEdit(true) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (t.offline) Icons.Outlined.WifiOff else if (t.isStart) Icons.Outlined.Search else Icons.Outlined.Lock, null,
                            Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when { t.isStart -> "Rechercher ou saisir une adresse"; t.offline -> "Hors connexion · ${t.title}"; else -> host },
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (t.isStart) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.weight(1f)
                        )
                        if (!t.isStart && t.blocked > 0 && AdBlocker.enabledOn(t.url)) {
                            Icon(Icons.Outlined.Shield, "Éléments bloqués", Modifier.size(15.dp), tint = cs.primary)
                            Text(" ${t.blocked}", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        }
                    }
                    if (!t.isStart) {
                        val on = Translate.enabled
                        IconButton(onClick = {
                            Translate.enabled = !on
                            val w = t.web
                            if (w != null) { if (!on) PageTranslator.inject(w) else PageTranslator.restore(w) }
                            Toast.show(if (!on) "Pages traduites en français jusqu'à ce que vous désactiviez la traduction." else "Traduction désactivée.")
                        }) { Icon(Icons.Outlined.Translate, if (on) "Désactiver la traduction" else "Traduire en français", tint = if (on) cs.primary else LocalContentColor.current) }
                        IconButton(onClick = { val w = t.web; if (t.progress < 100) w?.stopLoading() else w?.reload() }) {
                            Icon(if (t.progress < 100) Icons.Outlined.Close else Icons.Outlined.Refresh, if (t.progress < 100) "Arrêter" else "Recharger")
                        }
                    }
                }
            }
            val st = PageTranslator.status
            if (st != null && !t.isStart) Text(st, style = MaterialTheme.typography.labelSmall, color = cs.primary, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
            if (!editing && !t.isStart && t.progress < 100) LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
    if (editing) Suggestions(field.trim(), onPick = { onEdit(false) })
}

@Composable
private fun Suggestions(q: String, onPick: () -> Unit) {
    val history by History.entries.collectAsState()
    val lib by Library.fics.collectAsState()
    val sugg = remember(q, history, lib) {
        if (q.length < 2) emptyList() else {
            val fics = lib.filter { it.title.contains(q, true) }.take(4).map { Triple(it.title, "Bibliothèque", "fic:" + it.id) }
            val pages = history.filter { it.kind == History.Kind.PAGE && (it.title.contains(q, true) || it.target.contains(q, true)) }
                .distinctBy { it.target }.take(6).map { Triple(it.title, it.target, it.target) }
            fics + pages
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        sugg.forEach { (title, sub, target) ->
            Row(Modifier.fillMaxWidth().clickable {
                onPick()
                if (target.startsWith("fic:")) Nav.openFic(target.removePrefix("fic:")) else BrowserHolder.openUrl(target, newTab = false)
            }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (target.startsWith("fic:")) Icons.Outlined.LocalLibrary else Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PageNotes(t: Tab) {
    val h = BrowserHolder
    if (t.isStart) return
    val host = runCatching { java.net.URI(t.url).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull().orEmpty()
    if (t.serverError >= 500) NoteCard("Le serveur de $host ne répond pas (erreur ${t.serverError}). Le problème vient du site : réessayez dans quelques minutes.",
        icon = Icons.Outlined.Refresh, action = { TextButton(onClick = { t.web?.reload() }) { Text("Réessayer") } })
    if (h.verifying) NoteCard("Cochez la case de vérification du site si elle s'affiche. Le téléchargement reprendra dès que la page normale apparaît.",
        icon = Icons.Outlined.VerifiedUser, action = {
            TextButton(onClick = { h.verifying = false; Fetcher.verificationDone(t.url); Downloads.resumeVerified() }) { Text("Terminé") }
        })
    val info = remember(t.url) { h.ficInfo(t.url) }
    val lib by Library.fics.collectAsState()
    if (info != null && !t.offline && (info.chapterIndex != null || info.chapterKey != null) && lib.none { it.id == info.ficId }) {
        val read = ReadTracker.wordsFor(info.ficId)
        if (read > 0) Text("Lu : ${Fmt.number(read)} mots sur ${Fmt.number(ReadTracker.threshold.toLong())} avant l'ajout à la bibliothèque",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, tint = if (!enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BottomToolbar(t: Tab, onMenu: () -> Unit) {
    val h = BrowserHolder
    val lib by Library.fics.collectAsState()
    val news = lib.count { it.newChapters > 0 || it.completedAlert }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Page précédente", enabled = t.canBack || !t.isStart) {
                val w = t.web; if (w != null && w.canGoBack()) w.goBack() else h.goHome()
            }
            ToolButton(Icons.AutoMirrored.Outlined.ArrowForward, "Page suivante", enabled = t.canForward) { t.web?.goForward() }
            ToolButton(Icons.Outlined.Home, "Accueil") { h.goHome() }
            IconButton(onClick = { h.showTabs = true }) {
                Box(Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("${h.tabs.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = { Nav.push(Screen.Library) }) {
                Box {
                    Icon(Icons.Outlined.LocalLibrary, "Bibliothèque")
                    if (news > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            ToolButton(Icons.Outlined.MoreVert, "Menu", onClick = onMenu)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(t: Tab) {
    val sel = t.selection ?: return
    val context = LocalContext.current
    val info = remember(t.url) { BrowserHolder.ficInfo(t.url) }
    val fic = info?.let { Library.get(it.ficId) }
    var scope by remember(sel) { mutableStateOf(if (info != null) Masking.Scope.FIC else Masking.Scope.ALL) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("« ${sel.second.take(140)}${if (sel.second.length > 140) "…" else ""} »", style = MaterialTheme.typography.bodySmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (info != null) FilterChip(selected = scope == Masking.Scope.FIC, onClick = { scope = Masking.Scope.FIC }, label = { Text("Cette fic") })
                if (fic != null && fic.author.isNotBlank()) FilterChip(selected = scope == Masking.Scope.AUTHOR, onClick = { scope = Masking.Scope.AUTHOR }, label = { Text("Cet auteur") })
                FilterChip(selected = scope == Masking.Scope.ALL, onClick = { scope = Masking.Scope.ALL }, label = { Text("Partout") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val phrase = (if (sel.first.length >= 40) sel.first else sel.second).take(400)
                    val target = fic ?: info?.let { Fic(id = it.ficId, site = it.site, storyId = it.storyId, url = "", title = "") }
                        ?: Fic(id = "", site = Site.OTHER, storyId = "", url = "", title = "")
                    val s = if (scope == Masking.Scope.AUTHOR && fic == null) Masking.Scope.FIC else scope
                    Masking.add(phrase, s, target)
                    t.web?.evaluateJavascript(PageMask.hideNowJs(phrase), null)
                    Toast.show("Passage masqué, ici et dans les chapitres suivants (Réglages › Passages masqués pour l'annuler).")
                    t.selection = null
                }) { Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Masquer") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Ohara", sel.first))
                    t.web?.evaluateJavascript("var s=window.getSelection();if(s)s.removeAllRanges();", null); t.selection = null
                }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copier") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { t.web?.evaluateJavascript("var s=window.getSelection();if(s)s.removeAllRanges();", null); t.selection = null }) {
                    Icon(Icons.Outlined.Close, "Fermer")
                }
            }
        }
    }
}

@Composable
private fun PickerBar() {
    val h = BrowserHolder
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val p = h.picked
            Text(if (p == null) "Touchez l'élément à masquer (bannière, encart, bouton…)." else "Sélection : ${p.second.ifBlank { p.first }}",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p != null) Text("${Fmt.plural(p.third, "élément")} sur la page · reste masqué sur tout le site",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { h.web?.evaluateJavascript("if(window.oharaPick)oharaPick.parent()", null) }, enabled = p != null) { Text("Agrandir") }
                Button(onClick = { h.hidePicked() }, enabled = p != null) { Text("Masquer") }
                TextButton(onClick = { h.stopPicker() }) { Text("Terminer") }
            }
        }
    }
}

@Composable
private fun FindBar(t: Tab) {
    val h = BrowserHolder
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(q, { q = it; t.web?.findAllAsync(it) }, singleLine = true, placeholder = { Text("Rechercher dans la page") },
                modifier = Modifier.weight(1f).focusRequester(focus),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            Text(h.findInfo, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = { t.web?.findNext(false) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Précédent") }
            IconButton(onClick = { t.web?.findNext(true) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Suivant") }
            IconButton(onClick = { t.web?.clearMatches(); h.findOpen = false; h.findInfo = "" }) { Icon(Icons.Outlined.Close, "Fermer") }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun MenuTile(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(82.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(if (active) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (!enabled) cs.outline else if (active) cs.onPrimary else cs.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
            color = if (enabled) cs.onSurface else cs.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BrowserMenu(t: Tab, context: Context, onClose: () -> Unit) {
    val h = BrowserHolder
    val info = h.ficInfo(t.url)
    val inLib = info != null && Library.contains(info.ficId)
    val host = runCatching { java.net.URI(t.url).host }.getOrNull().orEmpty()
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            if (!t.isStart) {
                Text(t.title.ifBlank { host }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                MenuTile(Icons.Outlined.Add, "Nouvel onglet") { onClose(); h.newTab() }
                MenuTile(Icons.Outlined.LocalLibrary, "Bibliothèque") { onClose(); Nav.push(Screen.Library) }
                MenuTile(Icons.Outlined.Explore, "Découvrir") { onClose(); Nav.push(Screen.Discover) }
                MenuTile(Icons.Outlined.Download, "Téléchargements") { onClose(); Nav.push(Screen.Downloads) }
                if (info != null) {
                    MenuTile(Icons.Outlined.LibraryAdd, if (inLib) "Voir la fiche" else "Ajouter à la bibliothèque", active = inLib) {
                        onClose(); if (inLib) Nav.push(Screen.FicDetail(info.ficId)) else h.addCurrent(false)
                    }
                    MenuTile(Icons.Outlined.Download, "Télécharger la fic") { onClose(); h.addCurrent(true) }
                }
                MenuTile(Icons.Outlined.History, "Historique") { onClose(); Nav.push(Screen.HistoryScreen) }
                MenuTile(Icons.Outlined.Translate, "Traduire", active = Translate.enabled, enabled = !t.isStart) {
                    onClose(); Translate.enabled = !Translate.enabled
                    t.web?.let { if (Translate.enabled) PageTranslator.inject(it) else PageTranslator.restore(it) }
                }
                MenuTile(Icons.Outlined.DesktopWindows, "Version ordinateur", active = t.desktop, enabled = !t.isStart) { onClose(); h.setDesktop(t, !t.desktop) }
                MenuTile(Icons.Outlined.FindInPage, "Rechercher dans la page", enabled = !t.isStart) { onClose(); h.findOpen = true }
                MenuTile(Icons.Outlined.TouchApp, "Masquer un élément", enabled = !t.isStart) { onClose(); h.startPicker() }
                val adOn = !t.isStart && AdBlocker.enabledOn(t.url)
                MenuTile(Icons.Outlined.Shield, if (adOn) "Bloqueur actif" else "Bloqueur coupé ici", active = adOn, enabled = !t.isStart) {
                    onClose()
                    val base = host.removePrefix("www.").removePrefix("m.")
                    Prefs.adblockDisabledSites = if (adOn) Prefs.adblockDisabledSites + base else Prefs.adblockDisabledSites.filterNot { host == it || host.endsWith(".$it") }
                    t.web?.reload()
                }
                MenuTile(Icons.Outlined.Share, "Partager", enabled = !t.isStart) { onClose(); h.share(context) }
                if (Prefs.advancedBrowsing && Prefs.advancedUnlocked && !t.isStart && !Privacy.isSourceHost(host) && !GenericSource.matches(host)) {
                    MenuTile(Icons.Outlined.AddLink, "Site de fics") { onClose(); GenericSource.addDomain(host); Toast.show("$host ajouté comme site de fics.") }
                }
                MenuTile(Icons.Outlined.DeleteSweep, "Effacer les données") {
                    onClose(); Privacy.clearBrowsingData(context.applicationContext); h.tabs.forEach { it.web?.clearHistory() }
                    Toast.show("Cookies, cache et historique du navigateur effacés.")
                }
                MenuTile(Icons.Outlined.Settings, "Réglages") { onClose(); Nav.push(Screen.SettingsMain) }
                MenuTile(Icons.AutoMirrored.Outlined.HelpOutline, "Comment faire") { onClose(); Nav.push(Screen.Guide) }
            }
        }
    }
}

@Composable
private fun TabSwitcher() {
    val h = BrowserHolder
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { h.showTabs = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                Text(Fmt.plural(h.tabs.size, "onglet"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { h.closeAll() }) { Text("Tout fermer") }
            }
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(h.tabs.toList(), key = { it.id }) { tb ->
                    val sel = h.tabs.indexOf(tb) == h.current
                    val host = runCatching { java.net.URI(tb.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                            .then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { h.select(h.tabs.indexOf(tb)) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    Text((if (tb.isStart) "O" else host.take(1)).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { h.close(tb) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Fermer l'onglet", Modifier.size(18.dp)) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(if (tb.isStart) "Page d'accueil" else tb.title.ifBlank { host }, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Text(if (tb.offline) "hors connexion" else if (tb.isStart) "Ohara" else host, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            Button(onClick = { h.newTab() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Nouvel onglet")
            }
        }
    }
}

/** Page d'accueil d'un nouvel onglet : recherche, reprise de lecture, sites, raccourcis. */
@Composable
private fun StartPage(onSearch: () -> Unit) {
    val lib by Library.fics.collectAsState()
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val recent = remember(lib) { lib.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }.take(10) }
    val updated = lib.count { it.newChapters > 0 || it.completedAlert }
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(28.dp))
            Text("Ohara", style = MaterialTheme.typography.headlineMedium.merge(SerifTitle), color = cs.primary,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHighest)
                    .clickable(onClick = onSearch).padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Rechercher ou saisir une adresse", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val u = Sources.extractUrl(clipboard.getText()?.text.orEmpty())
                    if (u != null) BrowserHolder.openUrl(u, newTab = false) else Toast.show("Le presse-papiers ne contient pas de lien.")
                }) { Icon(Icons.Outlined.AddLink, "Coller un lien", tint = cs.primary) }
            }
            if (recent.isNotEmpty()) {
                SectionTitle("Reprendre la lecture")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { f ->
                        Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.openFic(f.id) }.padding(4.dp)) {
                            Box {
                                CoverTile(f, 104.dp)
                                if (f.newChapters > 0) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(cs.primary).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text("+${f.newChapters}", color = cs.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(f.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Ch. ${f.progressChapter + 1}" + if (f.totalChapters > 0) "/${f.totalChapters}" else "",
                                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            SectionTitle("Sites")
            val sites = buildList {
                add(Triple("FanFiction", "https://www.fanfiction.net/", Color(0xFF2F5E9E)))
                add(Triple("SpaceBattles", "https://forums.spacebattles.com/forums/creative-writing.18/", Color(0xFF8A5A2B)))
                add(Triple("WebNovel", "https://www.webnovel.com/stories/fanfic", Color(0xFF7A3FA0)))
                GenericSource.domains().forEach { add(Triple(it.substringBefore('.').replaceFirstChar { c -> c.uppercase() }, "https://$it/", Color(0xFF5B6B66))) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                sites.take(5).forEach { (name, url, color) ->
                    Column(Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable { BrowserHolder.openUrl(url, newTab = false) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) {
                            Text(name.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SectionTitle("Raccourcis")
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MenuTile(Icons.Outlined.LocalLibrary, if (updated > 0) "Bibliothèque ($updated)" else "Bibliothèque", active = updated > 0) { Nav.push(Screen.Library) }
                MenuTile(Icons.Outlined.Explore, "Découvrir") { Nav.push(Screen.Discover) }
                MenuTile(Icons.Outlined.Download, "Téléchargements") { Nav.push(Screen.Downloads) }
                MenuTile(Icons.Outlined.History, "Historique") { Nav.push(Screen.HistoryScreen) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Les fics que vous lisez entrent toutes seules dans votre bibliothèque après ${Fmt.number(ReadTracker.threshold.toLong())} mots lus. " +
                    "Appui long sur une phrase pour la masquer, ici et dans les chapitres suivants.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            )
        }
    }
}
