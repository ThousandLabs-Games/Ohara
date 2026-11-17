package com.thousandlabs.ohara.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.LinkOpener
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.net.Http
import com.thousandlabs.ohara.privacy.AdBlocker
import com.thousandlabs.ohara.privacy.Privacy
import com.thousandlabs.ohara.sources.GenericSource
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.launch
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

/**
 * Le navigateur intégré, gardé en vie entre les onglets.
 * Limité aux sites de fics ; la navigation libre se débloque dans les réglages.
 */
object BrowserHolder {
    var web: WebView? = null
    var pendingUrl by mutableStateOf<String?>(null)
    /** Une vérification anti-robot est en cours pour un téléchargement. */
    var verifying by mutableStateOf(false)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var blocked by mutableIntStateOf(0)
    var showWeb by mutableStateOf(false)
    var picking by mutableStateOf(false)
    var picked by mutableStateOf<Triple<String, String, Int>?>(null)
    @Volatile var pageUrl: String? = null
    private var autoAdded = HashSet<String>()

    fun allowedHost(host: String?): Boolean {
        if (host == null) return false
        if (Prefs.advancedBrowsing && Prefs.advancedUnlocked) return true
        return Privacy.isSourceHost(host) || GenericSource.matches(host)
    }

    fun open(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$").matches(t) -> "https://$t"
            Prefs.advancedBrowsing && Prefs.advancedUnlocked ->
                SEARCH_ENGINES.getOrElse(Prefs.searchEngine) { SEARCH_ENGINES[0] }.second + URLEncoder.encode(t, "UTF-8")
            else -> "https://www.fanfiction.net/search/?keywords=${URLEncoder.encode(t, "UTF-8")}&ready=1&type=story"
        }
        val clean = Privacy.upgrade(Privacy.cleanUrl(url))
        val host = runCatching { java.net.URI(clean).host }.getOrNull()
        if (!allowedHost(host)) {
            Toast.show("Ce site ne fait pas partie des sites de fics. La navigation libre se débloque dans les réglages.")
            return
        }
        showWeb = true
        web?.let { load(it, clean) } ?: run { pendingUrl = clean }
    }

    /** Charge une adresse avec le bon identifiant de navigateur (version ordinateur pour FanFiction.net). */
    fun load(view: WebView, raw: String) {
        val u = if (Http.isFfn(raw)) Http.desktopUrl(raw) else raw
        val ua = Http.userAgentFor(u)
        if (view.settings.userAgentString != ua) view.settings.userAgentString = ua
        view.loadUrl(u, Privacy.headers())
    }

    fun cosmeticJs(page: String?): String {
        val css = AdBlocker.cosmeticCss(page)
        if (css.isEmpty()) return ""
        return "(function(){var s=document.getElementById('ohara-cos');if(!s){s=document.createElement('style');s.id='ohara-cos';" +
            "(document.head||document.documentElement).appendChild(s);}s.textContent=${JSONObject.quote(css)};})();"
    }

    const val PICKER_JS = """(function(){
if(window.__oharaPick)return;window.__oharaPick=true;
var box=document.createElement('div');
box.style.cssText='position:absolute;z-index:2147483647;pointer-events:none;border:2px solid #1E8E6E;background:rgba(30,142,110,.18);border-radius:3px;';
document.documentElement.appendChild(box);var cur=null;
function esc(s){return window.CSS&&CSS.escape?CSS.escape(s):s.replace(/[^\w-]/g,'\\\\$&');}
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

    fun startPicker() {
        picking = true; picked = null
        web?.evaluateJavascript(PICKER_JS, null)
    }

    fun stopPicker() {
        picking = false; picked = null
        web?.evaluateJavascript("if(window.oharaPick)oharaPick.stop()", null)
    }

    fun hidePicked() {
        val (selector, _, _) = picked ?: return
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return
        AdBlocker.UserHiding.add(host, selector)
        web?.evaluateJavascript("if(window.oharaPick)oharaPick.hide(${JSONObject.quote(selector)})", null)
        picked = null
        Toast.show("Élément masqué sur tout le site.")
    }

    /** Lecture dans le navigateur : la fic est ajoutée à la bibliothèque, au bon chapitre (remplace l'historique de Brave). */
    fun maybeAutoAdd(u: String) {
        if (!Prefs.autoAddVisited || LinkFlow.pending != null) return
        val (_, info) = Sources.resolve(u) ?: return
        if (info.chapterIndex == null && info.chapterKey == null) return
        if (!autoAdded.add(u.substringBefore('#'))) return
        AppScope.launch {
            val known = Library.contains(info.ficId)
            runCatching { LinkOpener.open(u, Shelves.READING) }.onSuccess { o ->
                if (!known) Toast.show("« ${o.fic.title} » ajoutée à votre bibliothèque (chapitre ${o.chapter + 1}).")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView {
        web?.let { return it }
        val w = WebView(context)
        Privacy.configure(w)
        w.settings.userAgentString = Http.userAgent
        w.addJavascriptInterface(object {
            private val main = Handler(Looper.getMainLooper())
            @JavascriptInterface fun picked(selector: String, text: String, count: Int) { main.post { picked = Triple(selector, text, count) } }
        }, "OharaPick")
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            override fun onReceivedTitle(view: WebView, t: String?) { title = t.orEmpty() }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val u = request.url.toString()
                if (!u.startsWith("http")) return true
                val host = request.url.host
                if (!allowedHost(host)) {
                    Toast.show("Lien vers un site hors de la liste (${host ?: "?"}) : bloqué.")
                    return true
                }
                val clean = Privacy.upgrade(Privacy.cleanUrl(u))
                val target = if (Http.isFfn(clean)) Http.desktopUrl(clean) else clean
                if (target != u || view.settings.userAgentString != Http.userAgentFor(target)) { load(view, target); return true }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return if (AdBlocker.shouldBlock(request.url.toString(), pageUrl)) AdBlocker.emptyResponse() else null
            }

            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                // redirection du site vers sa version mobile : on repasse sur la version ordinateur
                if (u != null && Http.isFfn(u) && Http.desktopUrl(u) != u) { view.stopLoading(); load(view, u); return }
                pageUrl = u; url = u.orEmpty(); AdBlocker.PageStats.blocked = 0; blocked = 0
                picking = false; picked = null
            }

            override fun onPageCommitVisible(view: WebView, u: String?) {
                cosmeticJs(u).takeIf { it.isNotEmpty() }?.let { view.evaluateJavascript(it, null) }
            }

            override fun onPageFinished(view: WebView, u: String?) {
                url = u.orEmpty(); blocked = AdBlocker.PageStats.blocked
                cosmeticJs(u).takeIf { it.isNotEmpty() }?.let { view.evaluateJavascript(it, null) }
                if (u != null) {
                    if (Sources.resolve(u) != null) view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                        val html = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
                        if (html.isNotEmpty()) LinkFlow.onBrowserPage(u, html)
                    }
                    if (verifying) view.evaluateJavascript("document.title") { t ->
                        val title = runCatching { JSONArray("[$t]").getString(0) }.getOrDefault("")
                        if (!title.contains("Just a moment", true) && !title.contains("Attention Required", true)) {
                            verifying = false
                            Fetcher.verificationDone(u)
                            Downloads.resumeVerified()
                            Toast.show("Vérification réussie : le téléchargement reprend.")
                        }
                    }
                }
            }
        }
        web = w
        return w
    }
}

@Composable
fun BrowserTab() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    val h = BrowserHolder
    val web = remember { h.create(context) }

    LaunchedEffect(h.pendingUrl) {
        h.pendingUrl?.let { u -> h.pendingUrl = null; h.showWeb = true; h.load(web, u) }
    }
    BackHandler(enabled = h.picking) { h.stopPicker() }
    BackHandler(enabled = !h.picking && h.showWeb) { if (web.canGoBack()) web.goBack() else h.showWeb = false }

    val free = Prefs.advancedBrowsing && Prefs.advancedUnlocked
    val ficLink = remember(h.url) { Sources.resolve(h.url)?.second }
    val inLib = ficLink != null && Library.contains(ficLink.ficId)
    val host = remember(h.url) { runCatching { java.net.URI(h.url).host }.getOrNull().orEmpty() }

    Column(Modifier.fillMaxSize()) {
        // barre d'adresse
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h.showWeb) IconButton(onClick = { if (web.canGoBack()) web.goBack() else h.showWeb = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Page précédente") }
                    else IconButton(onClick = {}, enabled = false) { Icon(Icons.Outlined.Public, null) }
                    if (editing || !h.showWeb) {
                        OutlinedTextField(
                            value = field, onValueChange = { field = it }, singleLine = true,
                            placeholder = { Text(if (free) "Adresse ou recherche" else "Lien de fic ou recherche sur FanFiction.net") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { h.open(field); editing = false }),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)
                        )
                    } else {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable { field = h.url; editing = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(h.title.ifBlank { host }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    if (h.showWeb && AdBlocker.enabledOn(h.url) && h.blocked > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Icon(Icons.Outlined.Shield, "Éléments bloqués", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("${h.blocked}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (h.showWeb) IconButton(onClick = { if (h.progress < 100) web.stopLoading() else web.reload() }) {
                        Icon(if (h.progress < 100) Icons.Outlined.Close else Icons.Outlined.Refresh, if (h.progress < 100) "Arrêter" else "Recharger")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Plus") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (h.showWeb) {
                                DropdownMenuItem(text = { Text("Page suivante") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }, enabled = web.canGoForward(), onClick = { menu = false; web.goForward() })
                                DropdownMenuItem(text = { Text("Masquer un élément") }, leadingIcon = { Icon(Icons.Outlined.TouchApp, null) }, onClick = { menu = false; h.startPicker() })
                                val on = AdBlocker.enabledOn(h.url)
                                DropdownMenuItem(text = { Text(if (on) "Désactiver le bloqueur sur ce site" else "Réactiver le bloqueur sur ce site") }, leadingIcon = { Icon(Icons.Outlined.Shield, null) }, onClick = {
                                    menu = false
                                    val base = host.removePrefix("www.").removePrefix("m.")
                                    Prefs.adblockDisabledSites = if (on) Prefs.adblockDisabledSites + base else Prefs.adblockDisabledSites.filterNot { host == it || host.endsWith(".$it") }
                                    web.reload()
                                })
                                if (AdBlocker.UserHiding.selectors(host).isNotEmpty()) DropdownMenuItem(text = { Text("Réafficher les éléments masqués") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) }, onClick = {
                                    menu = false; AdBlocker.UserHiding.clear(host.removePrefix("www.").removePrefix("m.")); web.reload()
                                })
                                if (free && !Privacy.isSourceHost(host) && !GenericSource.matches(host)) DropdownMenuItem(text = { Text("Ajouter ce site comme source de fics") }, leadingIcon = { Icon(Icons.Outlined.AddLink, null) }, onClick = {
                                    menu = false; GenericSource.addDomain(host); Toast.show("$host ajouté : Ohara pourra télécharger ses chapitres.")
                                })
                                DropdownMenuItem(text = { Text("Page d'accueil") }, leadingIcon = { Icon(Icons.Outlined.Home, null) }, onClick = { menu = false; h.showWeb = false })
                            }
                            DropdownMenuItem(text = { Text("Effacer les données de navigation") }, leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) }, onClick = {
                                menu = false; Privacy.clearBrowsingData(context.applicationContext); web.clearHistory(); Toast.show("Cookies, cache et historique effacés.")
                            })
                        }
                    }
                }
                if (h.showWeb && h.progress < 100) LinearProgressIndicator(progress = { h.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (LinkFlow.pending != null && h.showWeb && !h.verifying) {
            NoteCard("Ohara ouvre la fic dès que la page est affichée. Si une case « Je ne suis pas un robot » apparaît, cochez-la.", icon = Icons.Outlined.VerifiedUser, action = {
                TextButton(onClick = { LinkFlow.pending = null }) { Text("Annuler") }
            })
        }
        if (h.verifying && h.showWeb) {
            NoteCard("Cochez la case de vérification du site si elle s'affiche. Le téléchargement reprendra tout seul dès que la page normale apparaît.", icon = Icons.Outlined.VerifiedUser, action = {
                TextButton(onClick = { h.verifying = false; Fetcher.verificationDone(h.url); Downloads.resumeVerified(); Toast.show("Le téléchargement reprend.") }) { Text("Terminé") }
            })
        }

        if (h.showWeb && ficLink != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (LinkFlow.working) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        Text("Lecture de la fic…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    } else Spacer(Modifier.weight(1f))
                    if (!inLib) TextButton(enabled = !LinkFlow.working, onClick = { LinkFlow.openFromPage(web, h.url, Shelves.TO_READ, read = false) }) {
                        Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ajouter", maxLines = 1)
                    }
                    TextButton(enabled = !LinkFlow.working, onClick = { LinkFlow.openFromPage(web, h.url, Shelves.TO_READ, read = false, download = true) }) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Télécharger", maxLines = 1)
                    }
                    Button(enabled = !LinkFlow.working, onClick = { LinkFlow.openFromPage(web, h.url) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lire", maxLines = 1)
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { (web.parent as? ViewGroup)?.removeView(web); web }, modifier = Modifier.fillMaxSize())
            if (!h.showWeb) StartPage(onPaste = {
                val t = clipboard.getText()?.text.orEmpty()
                if (Sources.extractUrl(t) != null) h.open(Sources.extractUrl(t)!!) else Toast.show("Le presse-papiers ne contient pas de lien.")
            })
        }

        if (h.picking) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    val p = h.picked
                    Text(if (p == null) "Touchez l'élément à masquer (bannière, encart, bouton…)." else "Sélection : ${p.second.ifBlank { p.first }}",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p != null) Text("${Fmt.plural(p.third, "élément")} concerné${if (p.third > 1) "s" else ""} sur la page · reste masqué sur tout le site",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { web.evaluateJavascript("if(window.oharaPick)oharaPick.parent()", null) }, enabled = p != null) { Text("Agrandir") }
                        Button(onClick = { h.hidePicked() }, enabled = p != null) { Text("Masquer") }
                        TextButton(onClick = { h.stopPicker() }) { Text("Terminer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartPage(onPaste: () -> Unit) {
    val free = Prefs.advancedBrowsing && Prefs.advancedUnlocked
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text("Sites de fics", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text("Bloqueur de publicités et protections anti-pistage actifs. Les fics que vous lisez ici s'ajoutent à votre bibliothèque, au bon chapitre.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            val sites = buildList {
                add(Triple("FanFiction.net", "Toutes les catégories", "https://www.fanfiction.net/"))
                add(Triple("FanFiction.net — Anime/Manga", "Index des fandoms", "https://www.fanfiction.net/anime/"))
                add(Triple("SpaceBattles — Creative Writing", "Derniers chapitres publiés", "https://forums.spacebattles.com/forums/creative-writing.18/?order=last_threadmark&direction=desc"))
                add(Triple("WebNovel — Fanfictions", "Classements et nouveautés", "https://www.webnovel.com/stories/fanfic"))
                GenericSource.domains().forEach { add(Triple(it, "Site ajouté", "https://$it/")) }
            }
            sites.forEach { (name, sub, u) ->
                SettingRow(name, sub, Icons.Outlined.Public, onClick = { BrowserHolder.open(u) })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow("Coller un lien", "Un lien de chapitre copié depuis Brave s'ouvre à cet endroit", Icons.Outlined.ContentPaste, onClick = onPaste)
            if (free) {
                SettingRow("Navigation libre activée", "Recherche avec ${SEARCH_ENGINES.getOrElse(Prefs.searchEngine) { SEARCH_ENGINES[0] }.first}", Icons.Outlined.Public)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
