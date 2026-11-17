package com.thousandlabs.ohara.sources

import com.thousandlabs.ohara.data.ChapterInfo
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/**
 * Sites ajoutés par le lecteur (« Ajouter comme source »). Ohara repère tout seul le texte
 * principal de la page et le lien « chapitre suivant », puis suit les chapitres un par un.
 */
object GenericSource : Source {
    override val site = Site.OTHER

    fun domains(): List<String> = Prefs.getList("custom_sources")
    fun addDomain(host: String) {
        val h = host.lowercase().removePrefix("www.")
        if (h.isNotEmpty() && h !in domains()) Prefs.putList("custom_sources", domains() + h)
    }
    fun removeDomain(host: String) = Prefs.putList("custom_sources", domains() - host)

    override fun matches(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return domains().any { h == it || h.endsWith(".$it") }
    }

    /** Pour un site ajouté, l'histoire est identifiée par l'adresse de son premier chapitre connu. */
    override fun parseLink(url: String): LinkInfo = LinkInfo(site, idFor(url), chapterIndex = 0)

    fun idFor(url: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(url.substringBefore("#").toByteArray())
        return md.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Adresse de départ, gardée à part car l'identifiant ne permet pas de la retrouver. */
    private fun startUrlOf(storyId: String): String? = Prefs.getString("gen_start_$storyId", "").ifEmpty { null }
    fun rememberStart(storyId: String, url: String) = Prefs.putString("gen_start_$storyId", url)

    override fun storyUrl(storyId: String) = startUrlOf(storyId) ?: ""
    override fun chapterUrl(fic: Fic, index: Int) = fic.chapters.getOrNull(index)?.url ?: fic.url

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    }

    override suspend fun fetchInfo(storyId: String): Fic {
        val start = startUrlOf(storyId) ?: throw IOException("Adresse de départ inconnue.")
        val chapters = mutableListOf<ChapterInfo>()
        val seen = HashSet<String>()
        var url: String? = start
        var title = ""
        var author = ""
        while (url != null && chapters.size < 1500 && seen.add(url.substringBefore("#"))) {
            val page = Fetcher.get(url)
            val doc = Jsoup.parse(page.body, url)
            val (t, content) = extract(doc)
            if (chapters.isEmpty()) {
                title = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.let { site -> t.removeSuffix(site).trim(' ', '-', '|') } ?: t
                author = doc.selectFirst("meta[name=author]")?.attr("content").orEmpty()
            }
            synchronized(cache) { cache[url] = Cleaner.clean(content) }
            chapters += ChapterInfo(t.ifBlank { "Chapitre ${chapters.size + 1}" }, url)
            url = nextLink(doc, url)
        }
        val host = runCatching { URI(start).host.removePrefix("www.") }.getOrDefault("")
        return Fic(
            id = Fic.makeId(site, storyId), site = site, storyId = storyId, url = start,
            title = commonTitle(chapters.map { it.title }).ifBlank { title.ifBlank { host } },
            author = author, fandoms = listOf(host), status = FicStatus.UNKNOWN,
            chapterCount = chapters.size, chapters = chapters
        )
    }

    override suspend fun fetchChapter(fic: Fic, index: Int): String {
        val ch = fic.chapters.getOrNull(index) ?: throw IOException("Chapitre inconnu.")
        synchronized(cache) { cache[ch.url] }?.let { return it }
        val doc = Jsoup.parse(Fetcher.get(ch.url).body, ch.url)
        return Cleaner.clean(extract(doc).second)
    }

    /** Partie commune des titres de chapitres (« Mon histoire - Chapitre 3 » → « Mon histoire »). */
    private fun commonTitle(titles: List<String>): String {
        if (titles.size < 2) return titles.firstOrNull().orEmpty()
        var prefix = titles.first()
        for (t in titles.drop(1).take(5)) while (!t.startsWith(prefix) && prefix.isNotEmpty()) prefix = prefix.dropLast(1)
        return prefix.trim().trimEnd('-', ':', '|', '–', '—', ',').trim().takeIf { it.length >= 3 } ?: titles.first()
    }

    // ---------- Repérage du texte principal ----------

    private val JUNK = "script, style, noscript, nav, header, footer, aside, form, iframe, button, svg, " +
        "#comments, .comments, .comment, [class*=share], [class*=social], [class*=advert], [id^=ad-], [class^=ad-], " +
        "[class*=sidebar], [id*=sidebar], [class*=related], [class*=newsletter], [class*=cookie], [class*=popup]"

    /** (titre, élément contenant le texte) */
    fun extract(doc: Document): Pair<String, Element> {
        val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.title()
        val body = doc.body() ?: return title to Element("div")
        val work = body.clone()
        work.select(JUNK).remove()
        var best: Element = work
        var bestScore = 0.0
        for (el in work.select("article, main, section, div, td")) {
            val ps = el.children().filter { it.tagName() == "p" }
            val pLen = ps.sumOf { it.text().length }
            val own = el.ownText().length
            val brs = el.children().count { it.tagName() == "br" }
            val linkText = el.select("a").sumOf { it.text().length }
            val score = pLen + own * (if (brs > 3) 1.0 else 0.3) - linkText * 0.5 + ps.size * 10
            if (score > bestScore) { bestScore = score; best = el }
        }
        return title to (if (bestScore < 200) work else best)
    }

    private val NEXT_TEXT = Regex(
        "^(next|next chapter|next page|next part|suivant|chapitre suivant|page suivante|siguiente|weiter|>|>>|›|»|→|next ?[›»>→]|[›»>→] ?next)$",
        RegexOption.IGNORE_CASE
    )

    fun nextLink(doc: Document, current: String): String? {
        val host = runCatching { URI(current).host }.getOrNull()
        val candidates = doc.select("link[rel=next], a[rel=next]").map { it.absUrl("href") } +
            doc.select("a[href]").filter { a ->
                val t = a.text().trim()
                NEXT_TEXT.matches(t) || t.contains("next chapter", true) || t.contains("chapitre suivant", true) ||
                    a.className().contains("next", true) && t.length < 30
            }.map { it.absUrl("href") }
        return candidates.firstOrNull { u ->
            u.startsWith("http") && u.substringBefore("#") != current.substringBefore("#") &&
                runCatching { URI(u).host == host }.getOrDefault(false)
        }
    }
}
