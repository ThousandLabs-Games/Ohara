package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.sources.Cleaner
import com.thousandlabs.ohara.sources.LinkInfo
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * La bibliothèque se remplit toute seule à partir de ce que vous lisez dans le navigateur.
 *
 * Sur chaque page de chapitre, un petit script mesure le texte principal et jusqu'où vous êtes descendu.
 * Un chapitre compte comme lu quand vous en avez parcouru au moins 60 %. Dès que vous avez lu le nombre de mots réglé
 * (10 000 par défaut) d'une même fic, elle entre dans la bibliothèque, étagère « En cours ».
 * Pour les fics déjà dans la bibliothèque : la position est mise à jour et le chapitre lu est gardé sur le téléphone.
 */
object ReadTracker {
    /** Mots lus avant qu'une fic entre dans la bibliothèque. */
    var threshold: Int get() = Prefs.getInt("lib_threshold", 10_000); set(v) = Prefs.putInt("lib_threshold", v)
    /** Garder les chapitres lus pour les relire sans connexion. */
    var keepRead: Boolean get() = Prefs.getBool("keep_read", true); set(v) = Prefs.putBool("keep_read", v)

    /** Fic en observation (pas encore assez lue pour la bibliothèque). */
    private data class Watch(
        val site: Site, val storyId: String, var title: String, var author: String, var fandom: String,
        var words: Long, val chapters: MutableSet<String>, var lastUrl: String, var lastIndex: Int, var updated: Long,
        var chapterCount: Int, var seconds: Long = 0L
    )

    private lateinit var file: File
    private val watches = HashMap<String, Watch>()

    fun init(root: File) {
        Counted.init(root)
        file = File(root, "lectures_en_cours.json")
        runCatching {
            val o = JSONObject(file.readText())
            for (k in o.keys()) {
                val w = o.getJSONObject(k)
                val ch = w.optJSONArray("c")
                watches[k] = Watch(
                    Site.of(w.optString("s")), w.optString("id"), w.optString("t"), w.optString("a"), w.optString("f"),
                    w.optLong("w"), (0 until (ch?.length() ?: 0)).map { ch!!.getString(it) }.toMutableSet(),
                    w.optString("u"), w.optInt("i", -1), w.optLong("d"), w.optInt("n"), w.optLong("sec")
                )
            }
        }
    }

    private fun save() {
        val o = JSONObject()
        synchronized(watches) {
            // on oublie les fics survolées il y a plus de 6 mois
            val old = System.currentTimeMillis() - 180L * 24 * 3600 * 1000
            watches.entries.removeAll { it.value.updated < old }
            for ((k, w) in watches) o.put(k, JSONObject().put("s", w.site.code).put("id", w.storyId).put("t", w.title).put("a", w.author)
                .put("f", w.fandom).put("w", w.words).put("c", org.json.JSONArray(w.chapters.toList())).put("u", w.lastUrl)
                .put("i", w.lastIndex).put("d", w.updated).put("n", w.chapterCount).put("sec", w.seconds))
        }
        com.thousandlabs.ohara.data.SafeFile.write(file, o.toString())
    }

    /** Mots déjà lus d'une fic pas encore dans la bibliothèque (pour l'afficher dans le navigateur). */
    fun wordsFor(ficId: String): Long = synchronized(watches) { watches[ficId]?.words ?: 0L }

    // ---------- Script injecté dans les pages de chapitre ----------

    /** Où se trouve le texte du chapitre selon le site (version téléphone ou ordinateur). */
    fun mainSelector(info: LinkInfo): String = when (info.site) {
        Site.FFN -> "#storytext,#storycontent,.storytext,.storycontent"
        Site.SB -> info.chapterKey?.let { "#js-post-$it .bbWrapper,[data-content=\"post-$it\"] .bbWrapper,article .bbWrapper" }
            ?: ".p-body-pageContent,.block-body"
        Site.WN -> ".cha-words,.cha-content,.chapter_content,.chapter-content,#chapter-content,[class*=chapter-content],[class*=cha-paragraph]"
        Site.OTHER -> "article,#chapter-content,.chapter-content,.entry-content,main"
        Site.LOCAL -> "#storytext"
    }

    fun script(info: LinkInfo): String {
        val sel = JSONObject.quote(mainSelector(info))
        return """(function(){
if(window.__ohRead)return;window.__ohRead=1;var SEL=$sel,sent={},metaHref='',lastY=null,lastHref='';
/* Blocs de texte de la page : un seul d'habitude, plusieurs quand le site enchaîne les chapitres (WebNovel, mode Reader de SpaceBattles). */
function units(){var l=[];try{document.querySelectorAll(SEL).forEach(function(e){if((e.innerText||'').length<400)return;for(var i=0;i<l.length;i++){if(l[i].contains(e))return;}l.push(e);});}catch(x){}return l;}
function main(){var l=units();if(!l.length){var ps=document.querySelectorAll('p');var tot=0;ps.forEach(function(p){tot+=(p.innerText||'').length});return tot<1500?null:document.body;}
if(l.length==1)return l[0];var mid=window.innerHeight*0.6,best=l[0];for(var i=0;i<l.length;i++){var r=l[i].getBoundingClientRect();if(r.top<=mid){best=l[i];if(r.bottom>=mid)break;}}return best;}
/* Identifiant du chapitre affiché, quand la page en contient plusieurs (message SpaceBattles, chapitre WebNovel). */
function key(e){var x=e,n=0,m;while(x&&x.nodeType==1&&n<10){var a=x.getAttribute('data-cid')||x.getAttribute('data-chapter-id')||x.getAttribute('data-chapterid');if(a&&/^\d{6,}$/.test(a))return a;
a=x.getAttribute('data-content')||x.id||'';if((m=/^(?:js-)?post-(\d+)$/.exec(a)))return m[1];x=x.parentElement;n++;}return '';}
/* Mots du texte d'origine (pas de la traduction affichée). */
function count(e){var w=document.createTreeWalker(e,NodeFilter.SHOW_TEXT),x,n=0;while((x=w.nextNode())){var v=x.__orig!=null?x.__orig:x.nodeValue;var p=v.match(/[^\s]+/g);if(p)n+=p.length;}return n;}
function info(){var a=document.querySelector('a[href^="/u/"],a.username,.username,a[href*="/profile/"],.author a,[class*=author] a');
var h=document.querySelector('h1,.p-title-value,.cha-tit h3,.cha-tit,b.xcontrast_txt');
var og=document.querySelector('meta[property="og:title"]');var sels=document.querySelectorAll('select');var n=0;sels.forEach(function(s){n=Math.max(n,s.options.length)});
var crumbs=Array.prototype.map.call(document.querySelectorAll('#pre_story_links a,.lc-wrapper a,a[href^="/anime/"],a[href^="/book/"],a[href^="/game/"],a[href^="/tv/"],a[href^="/movie/"],a[href^="/misc/"],a[href^="/cartoon/"],a[href^="/comic/"]'),function(x){return x.textContent.trim()});
return JSON.stringify({title:document.title||'',h1:h?h.textContent.trim().slice(0,300):'',og:og?og.content:'',author:a?a.textContent.trim().slice(0,120):'',opts:n,crumbs:crumbs.slice(0,6)});}
function tick(){if(document.visibilityState==='hidden')return;var m=main();if(!m)return;var r=m.getBoundingClientRect();var top=r.top+window.scrollY;
var f=Math.max(0,Math.min(1,(window.scrollY+window.innerHeight-top)/Math.max(1,m.offsetHeight)));
var href=location.href,y=window.scrollY;var moved=lastY===null||href!==lastHref||Math.abs(y-lastY)>24;lastY=y;lastHref=href;
var k=key(m),id=k||href.split('#')[0];
OharaRead.report(href,k,count(m),f,moved,metaHref===href?'':info());metaHref=href;
if(!sent[id]&&f>=0.6){sent[id]=1;var h=orig(m);OharaRead.chapter(href,k,h.length<1500000?h:'');}}
/* Le chapitre gardé est le texte d'origine : sans la traduction affichée, ni les repères ajoutés par Ohara. */
function orig(m){var c=m.cloneNode(true);var a=document.createTreeWalker(m,NodeFilter.SHOW_TEXT),b=document.createTreeWalker(c,NodeFilter.SHOW_TEXT),x,y;
while((x=a.nextNode())&&(y=b.nextNode())){if(x.__orig!=null)y.nodeValue=x.__orig;}
c.querySelectorAll('.oh-mk,script,style,noscript,iframe').forEach(function(e){e.remove();});
c.querySelectorAll('[data-oh-m],[data-oh-br],[data-oh-shown]').forEach(function(e){e.removeAttribute('data-oh-m');e.removeAttribute('data-oh-br');e.removeAttribute('data-oh-shown');e.style.display='';});
return c.innerHTML;}
var t=null;window.addEventListener('scroll',function(){if(t)return;t=setTimeout(function(){t=null;tick();},1500);},{passive:true});
document.addEventListener('visibilitychange',function(){if(document.visibilityState==='visible')lastY=null;});
setTimeout(tick,1200);setInterval(tick,5000);
})();"""
    }

    // ---------- Rapports de la page ----------

    /** Titre de la fic d'après la page (le titre de l'onglet contient souvent « Chapitre… » ou le nom du site). */
    private fun ficTitle(site: Site, m: JSONObject): String {
        val t = m.optString("title")
        return when (site) {
            Site.FFN -> Regex("^(.*?)(?: Chapter \\d+.*)?, an? .+ fanfic", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
                ?: m.optString("h1").ifBlank { t.substringBefore(" Chapter ").substringBefore(" | ") }
            Site.SB -> m.optString("h1").ifBlank { t.substringBefore(" | ") }.replace(Regex("\\s*\\[?(Threadmarks?|Reader)\\]?.*$"), "").trim()
            Site.WN -> (m.optString("og").ifBlank { t }).split(" - ", " | ").let { parts -> if (parts.size >= 2) parts[parts.size - 2] else parts[0] }.trim()
            Site.LOCAL -> m.optString("h1").ifBlank { t.substringBefore(" — ") }
            Site.OTHER -> m.optString("og").ifBlank { m.optString("h1").ifBlank { t } }
        }.trim().ifBlank { "Fic sans titre" }
    }

    private fun fandomOf(site: Site, m: JSONObject): String {
        if (site == Site.FFN) {
            Regex(", an? (.+?) fanfic", RegexOption.IGNORE_CASE).find(m.optString("title"))?.let { return it.groupValues[1] }
            val c = m.optJSONArray("crumbs")
            if (c != null && c.length() > 0) return c.optString(c.length() - 1)
        }
        if (site == Site.SB) Regex("\\(([^)]{2,40})\\)").find(m.optString("h1"))?.let { return it.groupValues[1] }
        return ""
    }

    private fun chapterIndex(info: LinkInfo, fic: Fic?): Int? =
        info.chapterIndex ?: info.chapterKey?.let { k -> fic?.chapters?.indexOfFirst { it.key == k }?.takeIf { it >= 0 } }

    /** Lien de la page, complété par le chapitre affiché quand la page en enchaîne plusieurs. */
    private fun resolveUnit(url: String, unitKey: String): LinkInfo? {
        val info = Sources.resolve(url)?.second ?: return null
        return if (unitKey.isNotBlank() && info.chapterIndex == null && (info.chapterKey == null || info.site == Site.SB && url.contains("/reader")))
            info.copy(chapterKey = unitKey) else info
    }

    /** Chapitres déjà comptés dans les mots lus (gardé d'un lancement à l'autre : une relecture ne compte pas deux fois). */
    private object Counted {
        private lateinit var file: File
        private val keys = LinkedHashSet<String>()
        fun init(root: File) {
            file = File(root, "chapitres_comptes.json")
            runCatching { val a = org.json.JSONArray(file.readText()); for (i in 0 until a.length()) keys += a.getString(i) }
        }
        @Synchronized fun add(k: String): Boolean {
            if (!keys.add(k)) return false
            while (keys.size > 20_000) keys.remove(keys.first())
            com.thousandlabs.ohara.data.SafeFile.write(file, org.json.JSONArray(keys.toList()).toString())
            return true
        }
    }

    private val lastSaveAt = HashMap<String, Long>()

    /** Ohara est à l'écran (mis à jour par l'activité) : rien n'est compté appli fermée, écran éteint ou en arrière-plan. */
    @Volatile var foreground = false

    /** Délai sans geste au-delà duquel on considère que vous ne lisez plus (téléphone posé). */
    private const val IDLE_MS = 150_000L
    /** Écart entre deux rapports au-delà duquel la page était en pause : ce temps-là n'est pas compté. */
    private const val GAP_MS = 20_000L
    private var lastTick = 0L
    private var lastMove = 0L
    private var carryMs = 0L

    /**
     * Temps de lecture : la page envoie un rapport toutes les 5 secondes (et pendant le défilement).
     * Tout le temps passé entre deux rapports est compté, y compris les pauses où vous lisez sans toucher l'écran,
     * tant que vous avez fait défiler la page dans les 2 min 30 précédentes. Seul l'onglet affiché compte.
     */
    @Synchronized
    private fun readingSeconds(moved: Boolean, active: Boolean): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!active || !foreground) { lastTick = 0L; return 0 }
        val gap = if (lastTick == 0L) 0L else now - lastTick
        lastTick = now
        if (moved) lastMove = now
        if (gap <= 0L || gap > GAP_MS || now - lastMove > IDLE_MS) return 0
        carryMs += gap
        val s = carryMs / 1000
        carryMs %= 1000
        return s
    }

    /** Rapport régulier d'une page de chapitre : mots du chapitre, part parcourue, informations de la page. */
    fun onReport(url: String, unitKey: String, words: Int, fraction: Float, moved: Boolean, active: Boolean, metaJson: String) {
        val info = resolveUnit(url, unitKey) ?: return
        val id = info.ficId
        val key = info.chapterKey ?: info.chapterIndex?.toString() ?: url.substringBefore('#')
        val meta = runCatching { if (metaJson.isNotBlank()) JSONObject(metaJson) else null }.getOrNull()
        val lib = Library.get(id)
        val secs = readingSeconds(moved, active)
        if (secs > 0) {
            ReadingLog.add(0, secs, ficId = id)
            if (lib != null) Library.update(id) { it.copy(secondsRead = it.secondsRead + secs) }
            else synchronized(watches) { watches[id]?.let { it.seconds += secs } }
        }
        if (lib != null) {
            val now = System.currentTimeMillis()
            // mots lus : un chapitre nouveau (pas une relecture) compte une fois
            val idx0 = chapterIndex(info, lib)
            if (fraction >= 0.6f && Counted.add("$id|$key") && (idx0 == null || idx0 > lib.maxChapterRead)) {
                ReadingLog.add(words.toLong(), 0, chapters = 1, ficId = id)
                Library.update(id) { it.copy(wordsRead = it.wordsRead + words) }
            }
            if (now - (lastSaveAt[id] ?: 0L) < 3000 && fraction < 0.6f) return
            lastSaveAt[id] = now
            val idx = chapterIndex(info, lib)
            Library.update(id) { f ->
                f.copy(
                    resumeUrl = url, lastOpened = now,
                    progressChapter = idx ?: f.progressChapter,
                    maxChapterRead = if (idx != null && fraction >= 0.9f) maxOf(f.maxChapterRead, idx) else f.maxChapterRead,
                    newChapters = if (idx != null && idx >= f.totalChapters - 1) 0 else f.newChapters,
                    shelves = if (Shelves.TO_READ in f.shelves && fraction > 0.3f) f.shelves - Shelves.TO_READ + Shelves.READING else f.shelves
                )
            }
            return
        }
        // fic pas encore dans la bibliothèque : on additionne les chapitres lus
        val src = Sources.of(info.site)
        synchronized(watches) {
            val w = watches.getOrPut(id) {
                Watch(info.site, info.storyId, "", "", "", 0L, mutableSetOf(), url, -1, 0L, 0)
            }
            if (meta != null) {
                if (w.title.isBlank()) w.title = ficTitle(info.site, meta)
                if (w.author.isBlank()) w.author = meta.optString("author")
                if (w.fandom.isBlank()) w.fandom = fandomOf(info.site, meta)
                if (info.site == Site.FFN) w.chapterCount = maxOf(w.chapterCount, meta.optInt("opts"))
            }
            w.lastUrl = url; w.updated = System.currentTimeMillis()
            info.chapterIndex?.let { w.lastIndex = it }
            if (fraction >= 0.6f && w.chapters.add(key)) {
                w.words += words
                ReadingLog.add(words.toLong(), 0, chapters = 1, ficId = id)
                save()
            }
            if (w.words >= threshold) {
                watches.remove(id)
                save()
                val fic = Fic(
                    id = id, site = info.site, storyId = info.storyId, url = src.storyUrl(info.storyId), title = w.title.ifBlank { "Fic sans titre" },
                    author = w.author, fandoms = listOfNotNull(w.fandom.takeIf { it.isNotBlank() }), status = FicStatus.UNKNOWN,
                    chapterCount = maxOf(w.chapterCount, (w.lastIndex + 1)), resumeUrl = url, wordsRead = w.words, secondsRead = w.seconds,
                    progressChapter = maxOf(0, w.lastIndex), maxChapterRead = w.lastIndex
                )
                Library.upsert(fic, Shelves.READING)
                Library.update(id) { it.copy(lastOpened = System.currentTimeMillis(), resumeUrl = url) }
                Toast.show("« ${fic.title} » entre dans votre bibliothèque (${w.words / 1000} 000 mots lus).")
                // compléter la fiche (chapitres, résumé…) en arrière-plan, si le site le permet
                AppScope.launch {
                    runCatching { withContext(Dispatchers.IO) { src.fetchInfo(info.storyId) } }.onSuccess { full ->
                        Library.upsert(full)
                    }
                }
            }
        }
    }

    /** Le chapitre a été parcouru : il est gardé sur le téléphone (fics de la bibliothèque). */
    fun onChapter(url: String, unitKey: String, html: String) {
        if (!keepRead || html.isBlank()) return
        val info = resolveUnit(url, unitKey) ?: return
        val fic = Library.get(info.ficId) ?: return
        val idx = chapterIndex(info, fic) ?: return
        if (Library.hasChapter(fic.id, idx)) return
        AppScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val clean = Cleaner.clean(html)
                    if (clean.length > 300) Library.writeChapter(fic.id, idx, clean)
                }
            }
        }
    }

    /** Ajout immédiat depuis le menu « Ajouter à la bibliothèque » (sans attendre les 10 000 mots). */
    fun addNow(url: String, metaJson: String?, download: Boolean) {
        val (src, info) = Sources.resolve(url) ?: run { Toast.show("Cette page n'est pas une fic reconnue."); return }
        if (Library.contains(info.ficId)) {
            Toast.show("Déjà dans votre bibliothèque.")
            if (download) Library.get(info.ficId)?.let { com.thousandlabs.ohara.download.Downloads.enqueue(it); Toast.show("Téléchargement lancé.") }
            return
        }
        val meta = runCatching { metaJson?.let { JSONObject(it) } }.getOrNull() ?: JSONObject()
        val w = synchronized(watches) { watches.remove(info.ficId) }.also { save() }
        val fic = Fic(
            id = info.ficId, site = info.site, storyId = info.storyId, url = src.storyUrl(info.storyId),
            title = w?.title?.ifBlank { null } ?: ficTitle(info.site, meta), author = w?.author?.ifBlank { null } ?: meta.optString("author"),
            fandoms = listOfNotNull((w?.fandom?.ifBlank { null } ?: fandomOf(info.site, meta)).takeIf { it.isNotBlank() }),
            chapterCount = maxOf(meta.optInt("opts"), (info.chapterIndex ?: 0) + 1), resumeUrl = url,
            progressChapter = info.chapterIndex ?: 0, wordsRead = (w?.words ?: 0L), secondsRead = (w?.seconds ?: 0L)
        )
        Library.upsert(fic, Shelves.READING)
        Toast.show(if (download) "« ${fic.title} » ajoutée : téléchargement lancé." else "« ${fic.title} » ajoutée à votre bibliothèque.")
        AppScope.launch {
            val full = runCatching { withContext(Dispatchers.IO) { src.fetchInfo(info.storyId) } }.getOrNull()
            if (full != null) Library.upsert(full)
            if (download) Library.get(info.ficId)?.let { com.thousandlabs.ohara.download.Downloads.enqueue(it) }
        }
    }
}
