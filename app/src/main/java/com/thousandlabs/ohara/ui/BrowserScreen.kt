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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
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
import com.thousandlabs.ohara.translate.Names
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
class Tab(val id: Long, initialUrl: String, val private: Boolean = false) {
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
    /** Aperçu de la page (pour la liste des onglets) et icône du site. */
    var thumb by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var icon by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** Page affichée en mode lecture. */
    var readerMode by mutableStateOf(false)
    /** Dernière règle de masquage créée sur cet onglet (pour « Annuler »). */
    var lastMasked by mutableStateOf<Masking.Rule?>(null)
    /** Page mise en mode lecture à la main (même si ce n'est pas un chapitre reconnu). */
    var readerUrl: String? = null
    val isStart: Boolean get() = url.isBlank()
}

/** Le navigateur d'Ohara : onglets, chargement des pages, scripts (masquage, suivi de lecture, traduction). */
object BrowserHolder {
    val tabs = mutableStateListOf<Tab>()
    var current by mutableIntStateOf(0)
    var showTabs by mutableStateOf(false)
    var verifying by mutableStateOf(false)
    var picking by mutableStateOf(false)
    /** Élément choisi avec « Masquer un élément » : sélecteur, texte, nombre d'éléments semblables, dans le texte du chapitre ?, texte complet. */
    data class Picked(val selector: String, val text: String, val count: Int, val inText: Boolean, val full: String)
    var picked by mutableStateOf<Picked?>(null)
    var findOpen by mutableStateOf(false)
    /** Lien touché longuement (menu du lien). */
    var linkMenu by mutableStateOf<String?>(null)
    var groupTabs: Boolean get() = Prefs.getBool("tab_groups", true); set(v) = Prefs.putBool("tab_groups", v)
    private const val PRIVATE_PROFILE = "prive"
    /** Barres du haut et du bas affichées (elles se cachent quand on descend dans la page). */
    var barsVisible by mutableStateOf(true)
    var autoHideBars: Boolean get() = Prefs.getBool("hide_bars", true); set(v) = Prefs.putBool("hide_bars", v)
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

    /** Les noms à ne pas traduire ont changé : la page affichée est retraduite tout de suite. */
    fun retranslateOpenTab() { com.thousandlabs.ohara.translate.Translate.namesChanged(); web?.let { PageTranslator.retranslate(it) } }

    fun restore() {
        if (restored) return
        restored = true
        val urls = Prefs.getList("tabs")
        urls.forEach { tabs.add(Tab(nextId++, it)) }
        if (tabs.isEmpty()) tabs.add(Tab(nextId++, ""))
        current = Prefs.getInt("tab_cur", 0).coerceIn(0, tabs.lastIndex)
    }

    fun saveTabs() {
        val kept = tabs.filter { !it.private }
        Prefs.putList("tabs", kept.map { if (it.offline) "" else it.url })
        Prefs.putInt("tab_cur", kept.indexOf(tabs.getOrNull(current)).coerceAtLeast(0))
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

    fun newTab(url: String = "", select: Boolean = true, private: Boolean = false): Tab {
        restore()
        val t = Tab(nextId++, url, private)
        tabs.add(t)
        if (select) { current = tabs.lastIndex; t.lastUsed = System.currentTimeMillis() }
        showTabs = false
        saveTabs()
        return t
    }

    /** Photo réduite de la page affichée, pour la liste des onglets. */
    fun capture(t: Tab) {
        val w = t.web ?: return
        if (t.isStart || w.width <= 0 || w.height <= 0) return
        runCatching {
            val scale = 0.4f
            val bmp = android.graphics.Bitmap.createBitmap((w.width * scale).toInt().coerceAtLeast(1), (w.height * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-w.scrollX.toFloat(), -w.scrollY.toFloat())
            w.draw(c)
            t.thumb = bmp.asImageBitmap()
        }
    }

    fun openTabs() { capture(tab); showTabs = true }

    fun select(i: Int) {
        capture(tab)
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
        if (t.private && tabs.none { it.private }) forgetPrivate()
        if (tabs.isEmpty()) tabs.add(Tab(nextId++, ""))
        if (current >= tabs.size || i < current) current = (current - 1).coerceAtLeast(0)
        saveTabs()
    }

    /** Plus aucun onglet privé : leurs cookies et données sont effacés. */
    private fun forgetPrivate() {
        runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE))
                androidx.webkit.ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
        }
    }

    fun closeAll() {
        val hadPrivate = tabs.any { it.private }
        tabs.forEach { destroy(it) }
        if (hadPrivate) forgetPrivate()
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
var c=window.__ohMaskCfg,inT=false;if(c&&c.sel){try{document.querySelectorAll(c.sel).forEach(function(r){if(r.contains(el)||el.contains(r))inT=true;});}catch(x){}}
OharaPick.picked(s,(el.innerText||el.tagName).slice(0,80),n,inT,(el.textContent||'').trim().slice(0,1000));}
window.__oharaPickH=function(e){e.preventDefault();e.stopPropagation();show(e.target);};
document.addEventListener('click',window.__oharaPickH,true);
window.oharaPick={parent:function(){if(cur&&cur.parentElement&&cur.parentElement!==document.body&&cur.parentElement!==document.documentElement)show(cur.parentElement);},
stop:function(){document.removeEventListener('click',window.__oharaPickH,true);box.remove();window.__oharaPick=false;},
hide:function(s){try{document.querySelectorAll(s).forEach(function(x){x.style.setProperty('display','none','important');});}catch(x){}}};
})();"""

    fun startPicker() { picking = true; picked = null; web?.evaluateJavascript(PICKER_JS, null) }
    fun stopPicker() { picking = false; picked = null; web?.evaluateJavascript("if(window.oharaPick)oharaPick.stop()", null) }
    fun hidePicked() {
        val p = picked ?: return
        val selector = p.selector
        // Un morceau du texte du chapitre n'est jamais masqué par élément (cela cacherait tous les paragraphes du site) :
        // une phrase devient une règle de texte, limitée à cette fic.
        if (p.inText) {
            val info = ficInfo(tab.url)
            if (p.full.length in 4..400 && info != null) {
                val fic = Library.get(info.ficId) ?: com.thousandlabs.ohara.data.Fic(id = info.ficId, site = info.site, storyId = info.storyId, url = "", title = "")
                tab.lastMasked = Masking.add(p.full, Masking.Scope.FIC, fic)
                web?.evaluateJavascript(PageMask.hideNowJs(p.full), null)
                stopPicker()
                Toast.show("C'est du texte du chapitre : seul ce passage est masqué, dans cette fic.")
            } else Toast.show("C'est le texte du chapitre lui-même : il ne peut pas être masqué. Pour une phrase, faites un appui long dessus.")
            return
        }
        if (p.count > 3) {
            Toast.show("${p.count} éléments semblables sur la page : « Agrandir » pour choisir l'encart entier, sinon trop de choses seraient masquées.")
            return
        }
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
    fun pageScripts(w: WebView, u: String, t: Tab) {
        val info = ficInfo(u)
        val chapter = PageTools.isChapter(info, u)
        val cfg = PageMask.config(info)
        // Masquage coupé sur ce site : le script reste chargé (il sert à « Masquer un élément ») mais ne cache rien.
        if (!SiteRules.on(SiteRules.Feature.MASK, u)) cfg.put("off", true)
        w.evaluateJavascript(PageMask.script(cfg), null)
        if (SiteRules.on(SiteRules.Feature.SELECT, u)) w.evaluateJavascript(PageMask.SELECTION_JS, null)
        if (chapter && !t.private) w.evaluateJavascript(ReadTracker.script(info!!), null)
        if (SiteRules.on(SiteRules.Feature.PULL, u) && (chapter || t.offline)) {
            val (prev, next) = if (info != null) PageTools.neighbours(u, info) else null to null
            w.evaluateJavascript(PageTools.pullScript(prev, next), null)
        }
        // Le mode lecture suit la lecture de chapitre en chapitre, mais ne s'applique pas aux autres pages (sommaire, recherche…).
        if (t.readerMode && !chapter && t.readerUrl != u) t.readerMode = false
        if (!t.offline && ((t.readerMode && (chapter || t.readerUrl == u)) || (SiteRules.on(SiteRules.Feature.READER, u) && chapter))) {
            t.readerMode = true
            w.evaluateJavascript(PageTools.readerScript(PageTools.readerConfig(info)), null)
        }
        if (SiteRules.on(SiteRules.Feature.TRANSLATE, u)) PageTranslator.inject(w)
    }

    fun toggleReader(t: Tab) {
        val w = t.web ?: return
        if (t.readerMode) { t.readerMode = false; w.reload(); return }
        t.readerMode = true
        t.readerUrl = t.url
        val info = ficInfo(t.url)
        if (SiteRules.on(SiteRules.Feature.PULL, t.url)) { val (p, n) = if (info != null) PageTools.neighbours(t.url, info) else null to null; w.evaluateJavascript(PageTools.pullScript(p, n), null) }
        w.evaluateJavascript(PageTools.readerScript(PageTools.readerConfig(info)), null)
        if (SiteRules.on(SiteRules.Feature.TRANSLATE, t.url)) PageTranslator.inject(w)
    }

    fun applyReaderStyle(t: Tab) {
        if (t.readerMode) t.web?.evaluateJavascript(PageTools.readerScript(PageTools.readerConfig(ficInfo(t.url))), null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webFor(t: Tab, context: Context): WebView {
        t.web?.let { return it }
        val w = WebView(context)
        if (t.private) runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                androidx.webkit.ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
                androidx.webkit.WebViewCompat.setProfile(w, PRIVATE_PROFILE)
            }
        }
        Privacy.configure(w, private = t.private)
        w.setOnLongClickListener {
            val r = w.hitTestResult
            val link = r.extra
            if (SiteRules.on(SiteRules.Feature.LINKS, t.url) && (r.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && !link.isNullOrBlank() && link.startsWith("http")) {
                linkMenu = link; true
            } else false
        }
        w.settings.userAgentString = if (t.desktop) Http.desktopUserAgent else Http.userAgent
        val main = Handler(Looper.getMainLooper())
        w.addJavascriptInterface(object {
            @JavascriptInterface fun picked(selector: String, text: String, count: Int, inText: Boolean, full: String) { main.post { picked = Picked(selector, text, count, inText, full) } }
        }, "OharaPick")
        w.addJavascriptInterface(object {
            @JavascriptInterface fun report(words: Int, fraction: Float, meta: String) {
                if (t.private) return
                val u = t.pageUrl ?: return
                main.post { if (meta.isNotBlank()) t.meta = meta; ReadTracker.onReport(u, words, fraction, meta) }
            }
            @JavascriptInterface fun chapter(html: String) { if (t.private) return; val u = t.pageUrl ?: return; main.post { ReadTracker.onChapter(u, html) } }
        }, "OharaRead")
        w.addJavascriptInterface(object {
            @JavascriptInterface fun failed() { main.post { t.readerMode = false; Toast.show("Pas de texte principal trouvé : le mode lecture ne s'applique pas à cette page.") } }
        }, "OharaReader")
        w.addJavascriptInterface(object {
            @JavascriptInterface fun selection(text: String, para: String) { main.post { t.selection = if (text.isBlank()) null else text to para } }
        }, "OharaSel")
        PageTranslator.attach(w)
        var lastToggle = 0L
        var acc = 0
        w.setOnScrollChangeListener { v, _, y, _, oldY ->
            if (t !== tab || !SiteRules.on(SiteRules.Feature.BARS, t.url)) { if (!barsVisible) barsVisible = true; return@setOnScrollChangeListener }
            val now = android.os.SystemClock.uptimeMillis()
            // Quand les barres apparaissent ou disparaissent, la page change de hauteur et défile d'elle-même :
            // ces mouvements-là sont ignorés, sinon les barres clignotent en bas d'un chapitre.
            if (now - lastToggle < 450) { acc = 0; return@setOnScrollChangeListener }
            val wv = v as WebView
            val bottom = y + wv.height >= (wv.contentHeight * wv.scale).toInt() - 8
            val dy = y - oldY
            acc = if ((acc > 0) == (dy > 0)) acc + dy else dy
            val want = when {
                y < 60 -> true
                bottom -> barsVisible
                acc > 40 -> false
                acc < -90 -> true
                else -> barsVisible
            }
            if (want != barsVisible) { barsVisible = want; lastToggle = now; acc = 0 }
        }
        w.setFindListener { active, count, done -> if (done) findInfo = if (count == 0) "Aucun résultat" else "${active + 1} / $count" }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { t.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { if (!title.isNullOrBlank()) t.title = title }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { if (icon != null) t.icon = icon.asImageBitmap() }
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
                if (t === tab) barsVisible = true
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
                pageScripts(view, u, t)
                view.postDelayed({ if (t === tab && !showTabs) capture(t) }, 1200)
                if (!t.offline && !t.private) {
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
        val showBars = editing || h.barsVisible || t.isStart || h.picking || h.findOpen || t.selection != null
        Column(Modifier.fillMaxSize()) {
            if (showBars) PrivateTint(t.private) { AddressBar(t, editing, onEdit = { editing = it }) }
            else Spacer(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding())
            if (!editing) {
                if (showBars || t.serverError >= 500 || BrowserHolder.verifying) PageNotes(t)
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
                if (t.selection == null) MaskUndoBar(t)
                if (h.picking) PickerBar()
                if (showBars) PrivateTint(t.private) { BottomToolbar(t, onMenu = { menu = true }) }
                else Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
            } else Spacer(Modifier.weight(1f))
        }
        if (h.showTabs) TabSwitcher()
    }
    var bookmarkDialog by remember { mutableStateOf(false) }
    var readerStyle by remember { mutableStateOf(false) }
    var claudeFic by remember { mutableStateOf<String?>(null) }
    var maskedSheet by remember { mutableStateOf(false) }
    var siteSheet by remember { mutableStateOf(false) }
    if (menu) BrowserMenu(t, context, onBookmark = { bookmarkDialog = true }, onReaderStyle = { readerStyle = true }, onClaude = { claudeFic = it }, onMasked = { maskedSheet = true }, onSite = { siteSheet = true }) { menu = false }
    if (maskedSheet) MaskedSheet(t) { maskedSheet = false }
    if (siteSheet) SiteSheet(t) { siteSheet = false }
    claudeFic?.let { id -> Library.get(id)?.let { f -> ClaudeSheet(f) { claudeFic = null } } ?: run { claudeFic = null } }
    if (bookmarkDialog) BookmarkDialog(t.title.ifBlank { t.url }, t.url) { bookmarkDialog = false }
    if (readerStyle) ReaderStyleSheet(t) { readerStyle = false }
    h.linkMenu?.let { link -> LinkMenu(link, context) { h.linkMenu = null } }
}

/** Barres sombres pour les onglets privés, comme dans les autres navigateurs. */
@Composable
private fun PrivateTint(private: Boolean, content: @Composable () -> Unit) {
    if (!private) { content(); return }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB9A6F0), onPrimary = Color(0xFF241A45),
        surface = Color(0xFF1B1C1F), onSurface = Color(0xFFE6E3DE), onSurfaceVariant = Color(0xFFB4B1AA),
        surfaceContainer = Color(0xFF202125), surfaceContainerHigh = Color(0xFF2A2B30), surfaceContainerHighest = Color(0xFF34353A),
        background = Color(0xFF161719), onBackground = Color(0xFFE6E3DE), outline = Color(0xFF7A7873)
    ), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
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
                        Icon(if (t.private) Icons.Outlined.VisibilityOff else if (t.offline) Icons.Outlined.WifiOff else if (t.isStart) Icons.Outlined.Search else Icons.Outlined.Lock, if (t.private) "Onglet privé" else null,
                            Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when { t.isStart && t.private -> "Onglet privé · rechercher ou saisir une adresse"; t.isStart -> "Rechercher ou saisir une adresse"; t.offline -> "Hors connexion · ${t.title}"; else -> host },
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
            val marks = com.thousandlabs.ohara.data.Bookmarks.items.value.filter { it.title.contains(q, true) || it.url.contains(q, true) }
                .take(3).map { Triple(it.title, "Favori · " + it.folder, "fav:" + it.url) }
            val pages = history.filter { it.kind == History.Kind.PAGE && (it.title.contains(q, true) || it.target.contains(q, true)) }
                .distinctBy { it.target }.take(6).map { Triple(it.title, it.target, it.target) }
            fics + marks + pages.filter { p -> marks.none { it.third == "fav:" + p.third } }
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        sugg.forEach { (title, sub, target) ->
            Row(Modifier.fillMaxWidth().clickable {
                onPick()
                when {
                    target.startsWith("fic:") -> Nav.openFic(target.removePrefix("fic:"))
                    target.startsWith("fav:") -> BrowserHolder.openUrl(target.removePrefix("fav:"), newTab = false)
                    else -> BrowserHolder.openUrl(target, newTab = false)
                }
            }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(when { target.startsWith("fic:") -> Icons.Outlined.LocalLibrary; target.startsWith("fav:") -> Icons.Outlined.StarOutline; else -> Icons.Outlined.History }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            IconButton(onClick = { h.openTabs() }) {
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

@Composable
private fun MaskUndoBar(t: Tab) {
    val rule = t.lastMasked ?: return
    LaunchedEffect(rule) { kotlinx.coroutines.delay(9000); if (t.lastMasked == rule) t.lastMasked = null }
    Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 6.dp,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Passage masqué, ici et dans les chapitres suivants.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = { Masking.remove(rule); t.lastMasked = null; t.web?.reload() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) { Text("Annuler") }
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
            Text("Masquer : « ${sel.second.take(140)}${if (sel.second.length > 140) "…" else ""} »", style = MaterialTheme.typography.bodySmall, maxLines = 3,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (info != null) FilterChip(selected = scope == Masking.Scope.FIC, onClick = { scope = Masking.Scope.FIC }, label = { Text("Cette fic") })
                if (fic != null && fic.author.isNotBlank()) FilterChip(selected = scope == Masking.Scope.AUTHOR, onClick = { scope = Masking.Scope.AUTHOR }, label = { Text("Cet auteur") })
                FilterChip(selected = scope == Masking.Scope.ALL, onClick = { scope = Masking.Scope.ALL }, label = { Text("Partout") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    // La phrase touchée (ou la sélection si elle est plus longue), jamais tout le bloc.
                    val phrase = (if (sel.first.length > sel.second.length) sel.first else sel.second).take(400)
                    val target = fic ?: info?.let { Fic(id = it.ficId, site = it.site, storyId = it.storyId, url = "", title = "") }
                        ?: Fic(id = "", site = Site.OTHER, storyId = "", url = "", title = "")
                    val s = if (scope == Masking.Scope.AUTHOR && fic == null) Masking.Scope.FIC else scope
                    val rule = Masking.add(phrase, s, target)
                    t.web?.evaluateJavascript(PageMask.hideNowJs(phrase), null)
                    t.lastMasked = rule
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
            // Un nom (quelques mots) : le garder tel quel pendant la traduction.
            if (Names.looksLikeName(sel.first)) {
                val n = Names.clean(sel.first) ?: ""
                TextButton(onClick = {
                    if (Translate.enabled) {
                        Toast.show("Coupez d'abord la traduction, puis sélectionnez « $n » tel qu'il est écrit en anglais.")
                    } else {
                        Names.addUser(n)
                        Toast.show("« $n » ne sera plus traduit. Réglages › Noms à ne pas traduire pour le retirer.")
                        t.web?.evaluateJavascript("var s=window.getSelection();if(s)s.removeAllRanges();", null); t.selection = null
                        BrowserHolder.retranslateOpenTab()
                    }
                }) { Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ne pas traduire « ${n.take(30)} »", maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
            Text(if (p == null) "Touchez l'élément à masquer (bannière, encart, bouton…)." else "Sélection : ${p.text.ifBlank { p.selector }}",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p != null) Text(when {
                p.inText -> "Texte du chapitre : seul ce passage sera masqué, dans cette fic"
                p.count > 3 -> "${p.count} éléments semblables : touchez « Agrandir » pour choisir l'encart entier"
                else -> "${Fmt.plural(p.count, "élément")} sur la page · reste masqué sur tout le site"
            }, style = MaterialTheme.typography.labelSmall,
                color = if (p.count > 3 && !p.inText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun BrowserMenu(t: Tab, context: Context, onBookmark: () -> Unit, onReaderStyle: () -> Unit, onClaude: (String) -> Unit, onMasked: () -> Unit, onSite: () -> Unit, onClose: () -> Unit) {
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
                MenuTile(Icons.Outlined.VisibilityOff, "Onglet privé") { onClose(); h.newTab(private = true) }
                MenuTile(Icons.Outlined.LocalLibrary, "Bibliothèque") { onClose(); Nav.push(Screen.Library) }
                MenuTile(Icons.Outlined.Explore, "Découvrir") { onClose(); Nav.push(Screen.Discover) }
                MenuTile(Icons.Outlined.Download, "Téléchargements") { onClose(); Nav.push(Screen.Downloads) }
                if (info != null) {
                    MenuTile(Icons.Outlined.LibraryAdd, if (inLib) "Voir la fiche" else "Ajouter à la bibliothèque", active = inLib) {
                        onClose(); if (inLib) Nav.push(Screen.FicDetail(info.ficId)) else h.addCurrent(false)
                    }
                    MenuTile(Icons.Outlined.Download, "Télécharger la fic") { onClose(); h.addCurrent(true) }
                    MenuTile(Icons.AutoMirrored.Outlined.Send, "Envoyer à Claude") {
                        onClose()
                        if (inLib) onClaude(info.ficId) else Toast.show("Ajoutez d'abord la fic à la bibliothèque (même menu), puis envoyez-la à Claude.")
                    }
                    if (inLib && Library.get(info.ficId)?.let { Recap.available(it) } == true)
                        MenuTile(Icons.Outlined.History, "Précédemment") { onClose(); Recap.pending = info.ficId }
                }
                val fav = !t.isStart && com.thousandlabs.ohara.data.Bookmarks.contains(t.url)
                MenuTile(Icons.Outlined.StarOutline, if (fav) "Dans les favoris" else "Ajouter aux favoris", active = fav, enabled = !t.isStart) {
                    onClose(); onBookmark()
                }
                MenuTile(Icons.Outlined.Bookmarks, "Favoris") { onClose(); Nav.push(Screen.BookmarksScreen) }
                MenuTile(Icons.AutoMirrored.Outlined.MenuBook, if (t.readerMode) "Quitter le mode lecture" else "Mode lecture", active = t.readerMode, enabled = !t.isStart) {
                    onClose(); h.toggleReader(t)
                }
                if (t.readerMode) MenuTile(Icons.Outlined.TextFields, "Affichage de lecture") { onClose(); onReaderStyle() }
                MenuTile(Icons.Outlined.History, "Historique") { onClose(); Nav.push(Screen.HistoryScreen) }
                MenuTile(Icons.Outlined.Translate, "Traduire", active = Translate.enabled, enabled = !t.isStart) {
                    onClose(); Translate.enabled = !Translate.enabled
                    t.web?.let { if (Translate.enabled) PageTranslator.inject(it) else PageTranslator.restore(it) }
                }
                MenuTile(Icons.Outlined.DesktopWindows, "Version ordinateur", active = t.desktop, enabled = !t.isStart) { onClose(); h.setDesktop(t, !t.desktop) }
                MenuTile(Icons.Outlined.FindInPage, "Rechercher dans la page", enabled = !t.isStart) { onClose(); h.findOpen = true }
                MenuTile(Icons.Outlined.TouchApp, "Masquer un élément", enabled = !t.isStart) { onClose(); h.startPicker() }
                MenuTile(Icons.Outlined.Visibility, "Passages masqués", enabled = !t.isStart) { onClose(); onMasked() }
                MenuTile(Icons.Outlined.Tune, "Réglages de ce site", enabled = !t.isStart && !t.offline) { onClose(); onSite() }
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
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var privateView by remember { mutableStateOf(h.tabs.getOrNull(h.current)?.private == true) }
    var grouped by remember { mutableStateOf(h.groupTabs) }
    val collapsed = remember { mutableStateListOf<String>() }
    val all = h.tabs.toList().filter { it.private == privateView }
    val list = all.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) }
    val privCount = h.tabs.count { it.private }
    // Groupes : les onglets d'une même fic, sinon d'un même site (dès qu'il y en a au moins deux).
    val groups = remember(list.map { it.id to it.url }, grouped) {
        if (!grouped) listOf(Triple("", "", list))
        else {
            val keyOf = { tb: Tab ->
                val info = h.ficInfo(tb.url)
                when {
                    tb.isStart -> "start" to "Nouveaux onglets"
                    info != null -> ("fic:" + info.ficId) to (Library.get(info.ficId)?.title ?: tb.title.substringBefore(" Chapter").substringBefore(" | ").ifBlank { "Fic" })
                    else -> runCatching { java.net.URI(tb.url).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull().orEmpty().let { ("site:" + it) to it }
                }
            }
            val byKey = list.groupBy { keyOf(it) }
            val multi = byKey.filter { it.value.size >= 2 }.map { (k, v) -> Triple(k.first, k.second, v) }
            val rest = byKey.filter { it.value.size < 2 }.values.flatten()
            multi + if (rest.isNotEmpty()) listOf(Triple("", if (multi.isEmpty()) "" else "Autres onglets", rest)) else emptyList()
        }
    }
    Surface(Modifier.fillMaxSize(), color = if (privateView) Color(0xFF17181A) else cs.surfaceContainerLow,
        contentColor = if (privateView) Color(0xFFE4E2DD) else cs.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { h.showTabs = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                Text(Fmt.plural(all.size, if (privateView) "onglet privé" else "onglet"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { grouped = !grouped; h.groupTabs = grouped }) {
                    Icon(if (grouped) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, if (grouped) "Ne plus grouper" else "Grouper les onglets")
                }
                TextButton(onClick = { all.forEach { h.close(it) } }) { Text("Tout fermer") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!privateView, { privateView = false }, { Text("Onglets (${h.tabs.size - privCount})") },
                    leadingIcon = { Icon(Icons.Outlined.Tab, null, Modifier.size(18.dp)) })
                FilterChip(privateView, { privateView = true }, { Text("Privés ($privCount)") },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp)) })
            }
            TextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Rechercher dans vos onglets") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceContainerHighest, unfocusedContainerColor = cs.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (privateView && all.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Navigation privée", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Les onglets privés ne laissent rien derrière eux : ni historique, ni lecture comptée pour la bibliothèque, ni page gardée en cache. " +
                        "Leurs cookies sont effacés quand le dernier onglet privé est fermé.",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            } else LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (key, label, tabsOf) ->
                    if (label.isNotEmpty()) item(key = "g:$key:$label", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        val open = key.isEmpty() || key !in collapsed
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (privateView) Color(0xFF26282B) else cs.surfaceContainerHigh)
                            .clickable(enabled = key.isNotEmpty()) { if (key in collapsed) collapsed.remove(key) else collapsed.add(key) }
                            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when { key.startsWith("fic:") -> Icons.AutoMirrored.Outlined.MenuBook; key.startsWith("site:") -> Icons.Outlined.Public; else -> Icons.Outlined.Tab },
                                null, Modifier.size(18.dp), tint = cs.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${tabsOf.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            if (key.isNotEmpty()) {
                                IconButton(onClick = { tabsOf.forEach { h.close(it) } }) { Icon(Icons.Outlined.Close, "Fermer le groupe", Modifier.size(18.dp)) }
                                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Replier" else "Déplier", Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                    if (key.isEmpty() || key !in collapsed) items(tabsOf, key = { it.id }) { tb -> TabCard(tb) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.ExtendedFloatingActionButton(onClick = { h.newTab(private = privateView) },
                    containerColor = cs.primary, contentColor = cs.onPrimary,
                    icon = { Icon(if (privateView) Icons.Outlined.VisibilityOff else Icons.Outlined.Add, null) },
                    text = { Text(if (privateView) "Onglet privé" else "Nouvel onglet") })
            }
        }
    }
}

@Composable
private fun TabCard(tb: Tab) {
    val h = BrowserHolder
    val cs = MaterialTheme.colorScheme
    val sel = h.tabs.indexOf(tb) == h.current
    val host = runCatching { java.net.URI(tb.url).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull().orEmpty()
    val head = when { sel -> cs.primary; tb.private -> Color(0xFF2E3034); else -> cs.surfaceContainerHighest }
    val onHead = when { sel -> cs.onPrimary; tb.private -> Color(0xFFE4E2DD); else -> cs.onSurface }
    Surface(
        shape = RoundedCornerShape(22.dp), color = head, shadowElevation = if (sel) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().height(250.dp).clickable { h.select(h.tabs.indexOf(tb)) }
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val ic = tb.icon
                if (ic != null) Image(ic, null, Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
                else Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text((if (tb.isStart) "O" else host.take(1)).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (tb.isStart) (if (tb.private) "Onglet privé" else "Page d'accueil") else tb.title.ifBlank { host }, style = MaterialTheme.typography.labelLarge,
                    color = onHead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { h.close(tb) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Close, "Fermer l'onglet", Modifier.size(18.dp), tint = onHead) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(start = 5.dp, end = 5.dp, bottom = 5.dp).clip(RoundedCornerShape(18.dp)).background(if (tb.private) Color(0xFF1E2023) else cs.surface)) {
                val th = tb.thumb
                when {
                    th != null -> Image(th, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    tb.isStart -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (tb.private) Icon(Icons.Outlined.VisibilityOff, null, tint = Color(0xFFB8B6B0))
                        else Text("Ohara", style = MaterialTheme.typography.titleLarge.merge(SerifTitle), color = cs.primary)
                        Text(if (tb.private) "Navigation privée" else "Nouvel onglet", style = MaterialTheme.typography.labelSmall, color = if (tb.private) Color(0xFFB8B6B0) else cs.onSurfaceVariant)
                    }
                    else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text(tb.title.ifBlank { host }, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Text(if (tb.offline) "hors connexion" else host, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                if (tb.private) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("Privé", fontSize = 10.sp, color = Color.White)
                }
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
            val marks by com.thousandlabs.ohara.data.Bookmarks.items.collectAsState()
            if (marks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Favoris", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Nav.push(Screen.BookmarksScreen) }) { Text("Tout voir") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(marks.take(20), key = { it.url }) { b ->
                        Row(Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh)
                            .clickable { BrowserHolder.openUrl(b.url, newTab = false) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.StarOutline, null, Modifier.size(16.dp), tint = cs.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
