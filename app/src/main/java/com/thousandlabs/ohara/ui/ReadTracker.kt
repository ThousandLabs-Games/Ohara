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
        var chapterCount: Int
    )

    private lateinit var file: File
    private val watches = HashMap<String, Watch>()

    fun init(root: File) {
        file = File(root, "lectures_en_cours.json")
        runCatching {
            val o = JSONObject(file.readText())
            for (k in o.keys()) {
                val w = o.getJSONObject(k)
                val ch = w.optJSONArray("c")
                watches[k] = Watch(
                    Site.of(w.optString("s")), w.optString("id"), w.optString("t"), w.optString("a"), w.optString("f"),
                    w.optLong("w"), (0 until (ch?.length() ?: 0)).map { ch!!.getString(it) }.toMutableSet(),
                    w.optString("u"), w.optInt("i", -1), w.optLong("d"), w.optInt("n")
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
                .put("i", w.lastIndex).put("d", w.updated).put("n", w.chapterCount))
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
    }

    fun script(info: LinkInfo): String {
        val sel = JSONObject.quote(mainSelector(info))
        return """(function(){
if(window.__ohRead)return;window.__ohRead=1;var SEL=$sel,sent=false,meta=false;
function main(){var best=null,len=0;document.querySelectorAll(SEL).forEach(function(e){var l=(e.innerText||'').length;if(l>len){len=l;best=e;}});
if(!best||len<400){var ps=document.querySelectorAll('p');var tot=0;ps.forEach(function(p){tot+=(p.innerText||'').length});if(tot<1500)return null;return document.body;}return best;}
function info(){var a=document.querySelector('a[href^="/u/"],a.username,.username,a[href*="/profile/"],.author a,[class*=author] a');
var h=document.querySelector('h1,.p-title-value,.cha-tit h3,.cha-tit,b.xcontrast_txt');
var og=document.querySelector('meta[property="og:title"]');var sels=document.querySelectorAll('select');var n=0;sels.forEach(function(s){n=Math.max(n,s.options.length)});
var crumbs=Array.prototype.map.call(document.querySelectorAll('#pre_story_links a,.lc-wrapper a,a[href^="/anime/"],a[href^="/book/"],a[href^="/game/"],a[href^="/tv/"],a[href^="/movie/"],a[href^="/misc/"],a[href^="/cartoon/"],a[href^="/comic/"]'),function(x){return x.textContent.trim()});
return JSON.stringify({title:document.title||'',h1:h?h.textContent.trim().slice(0,300):'',og:og?og.content:'',author:a?a.textContent.trim().slice(0,120):'',opts:n,crumbs:crumbs.slice(0,6)});}
function tick(){var m=main();if(!m)return;var r=m.getBoundingClientRect();var top=r.top+window.scrollY;
var f=Math.max(0,Math.min(1,(window.scrollY+window.innerHeight-top)/Math.max(1,m.offsetHeight)));
var words=(m.innerText||'').split(/\s+/).length;
OharaRead.report(words,f,meta?'':info());meta=true;
if(!sent&&f>=0.6){sent=true;var h=orig(m);OharaRead.chapter(h.length<1500000?h:'');}}
/* Le chapitre gardé est le texte d'origine : sans la traduction affichée, ni les repères ajoutés par Ohara. */
function orig(m){var c=m.cloneNode(true);var a=document.createTreeWalker(m,NodeFilter.SHOW_TEXT),b=document.createTreeWalker(c,NodeFilter.SHOW_TEXT),x,y;
while((x=a.nextNode())&&(y=b.nextNode())){if(x.__orig!=null)y.nodeValue=x.__orig;}
c.querySelectorAll('.oh-mk,script,style,noscript,iframe').forEach(function(e){e.remove();});
c.querySelectorAll('[data-oh-m],[data-oh-br],[data-oh-shown]').forEach(function(e){e.removeAttribute('data-oh-m');e.removeAttribute('data-oh-br');e.removeAttribute('data-oh-shown');e.style.display='';});
return c.innerHTML;}
var t=null;window.addEventListener('scroll',function(){if(t)return;t=setTimeout(function(){t=null;tick();},1500);},{passive:true});
setTimeout(tick,1200);setInterval(tick,8000);
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

    private val lastSaveAt = HashMap<String, Long>()
    private val counted = HashSet<String>()

    /** Temps de lecture : compté seulement quand vous avancez dans la page (pas quand le téléphone reste posé). */
    private data class Pace(var at: Long, var fraction: Float)
    private val pace = HashMap<String, Pace>()

    private fun readingSeconds(url: String, fraction: Float): Long {
        val now = System.currentTimeMillis()
        val p = pace[url]
        pace[url] = Pace(now, fraction)
        if (pace.size > 50) pace.keys.take(25).forEach { pace.remove(it) }
        if (p == null || kotlin.math.abs(fraction - p.fraction) < 0.002f) return 0
        return ((now - p.at) / 1000).coerceIn(0, 60)
    }

    /** Rapport régulier d'une page de chapitre : mots du chapitre, part parcourue, informations de la page. */
    fun onReport(url: String, words: Int, fraction: Float, metaJson: String) {
        val (_, info) = Sources.resolve(url) ?: return
        val id = info.ficId
        val key = info.chapterKey ?: info.chapterIndex?.toString() ?: url.substringBefore('#')
        val meta = runCatching { if (metaJson.isNotBlank()) JSONObject(metaJson) else null }.getOrNull()
        val lib = Library.get(id)
        val secs = readingSeconds(url.substringBefore('#'), fraction)
        if (secs > 0) {
            ReadingLog.add(0, secs)
            if (lib != null) Library.update(id) { it.copy(secondsRead = it.secondsRead + secs) }
        }
        if (lib != null) {
            val now = System.currentTimeMillis()
            // mots lus : un chapitre nouveau (pas une relecture) compte une fois
            val idx0 = chapterIndex(info, lib)
            if (fraction >= 0.6f && counted.add("$id|$key") && (idx0 == null || idx0 > lib.maxChapterRead)) {
                ReadingLog.add(words.toLong(), 0)
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
                ReadingLog.add(words.toLong(), 0)
                save()
            }
            if (w.words >= threshold) {
                watches.remove(id)
                save()
                val fic = Fic(
                    id = id, site = info.site, storyId = info.storyId, url = src.storyUrl(info.storyId), title = w.title.ifBlank { "Fic sans titre" },
                    author = w.author, fandoms = listOfNotNull(w.fandom.takeIf { it.isNotBlank() }), status = FicStatus.UNKNOWN,
                    chapterCount = maxOf(w.chapterCount, (w.lastIndex + 1)), resumeUrl = url, wordsRead = w.words,
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
    fun onChapter(url: String, html: String) {
        if (!keepRead || html.isBlank()) return
        val (_, info) = Sources.resolve(url) ?: return
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
            progressChapter = info.chapterIndex ?: 0, wordsRead = (w?.words ?: 0L)
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
