package com.thousandlabs.ohara.sources

import com.thousandlabs.ohara.data.ChapterInfo
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.net.SiteError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder

/** FanFiction.net */
object FanFictionNet : Source {
    override val site = Site.FFN
    const val BASE = "https://www.fanfiction.net"

    val GENRES = listOf(
        "Adventure", "Angst", "Crime", "Drama", "Family", "Fantasy", "Friendship", "General", "Horror", "Humor",
        "Hurt/Comfort", "Mystery", "Parody", "Poetry", "Romance", "Sci-Fi", "Spiritual", "Supernatural",
        "Suspense", "Tragedy", "Western"
    )

    /** Catégories du site, pour l'index des fandoms. */
    val CATEGORIES = linkedMapOf(
        "anime" to "Anime/Manga", "book" to "Livres", "cartoon" to "Dessins animés", "comic" to "Comics",
        "game" to "Jeux", "misc" to "Divers", "movie" to "Films", "play" to "Théâtre/Comédies musicales", "tv" to "Séries TV"
    )

    override fun matches(host: String) = host.endsWith("fanfiction.net")

    override fun parseLink(url: String): LinkInfo? {
        val m = Regex("fanfiction\\.net/s/(\\d+)(?:/(\\d+))?").find(url) ?: return null
        val ch = m.groupValues[2].toIntOrNull()
        return LinkInfo(site, m.groupValues[1], chapterIndex = ch?.let { (it - 1).coerceAtLeast(0) })
    }

    override fun storyUrl(storyId: String) = "$BASE/s/$storyId/1/"
    override fun chapterUrl(fic: Fic, index: Int) = "$BASE/s/${fic.storyId}/${index + 1}/"

    override suspend fun fetchInfo(storyId: String): Fic {
        val page = Fetcher.get(storyUrl(storyId), readyJs = "!!document.querySelector('#storytext,#profile_top,.gui_warning')")
        val doc = Jsoup.parse(page.body, BASE)
        checkMissing(doc, storyUrl(storyId))
        return parseStory(doc, storyId)
    }

    override suspend fun fetchChapter(fic: Fic, index: Int): String {
        val url = chapterUrl(fic, index)
        val page = Fetcher.get(url, readyJs = "!!document.querySelector('#storytext,.gui_warning')")
        val doc = Jsoup.parse(page.body, BASE)
        checkMissing(doc, url)
        val text = doc.selectFirst("#storytext") ?: doc.selectFirst("#storycontent")
            ?: throw IOException("Texte du chapitre introuvable sur la page.")
        return Cleaner.clean(text)
    }

    private fun checkMissing(doc: Document, url: String) {
        val warn = doc.selectFirst(".gui_warning")?.text().orEmpty()
        if (warn.contains("Story Not Found", true) || warn.contains("Chapter not found", true) ||
            doc.title().contains("Story Not Found", true)
        ) throw SiteError(404, url)
    }

    fun parseStory(doc: Document, storyId: String): Fic {
        val top = doc.selectFirst("#profile_top") ?: throw IOException("Page de l'histoire illisible.")
        val title = top.selectFirst("b.xcontrast_txt")?.text().orEmpty()
        val authorEl = top.selectFirst("a.xcontrast_txt[href^=/u/]")
        val summary = top.select("div.xcontrast_txt").firstOrNull()?.text().orEmpty()
        val metaEl = top.selectFirst("span.xgray")
        val meta = parseMeta(metaEl)
        val links = doc.select("#pre_story_links a")
        val fandomText = links.lastOrNull()?.text().orEmpty()
        val crossover = links.any { it.text().contains("Crossover", true) } || fandomText.contains(" + ")
        val fandoms = if (crossover) fandomText.replace(" Crossover", "").split(" + ", " & ").map { it.trim() }.filter { it.isNotEmpty() }
        else listOfNotNull(fandomText.takeIf { it.isNotBlank() })
        val options = doc.select("#chap_select option")
        val chapters = if (options.isNotEmpty()) {
            options.distinctBy { it.attr("value") }.mapIndexed { i, o ->
                val t = o.text().replace(Regex("^\\d+\\.\\s*"), "")
                ChapterInfo(t.ifBlank { "Chapitre ${i + 1}" }, "$BASE/s/$storyId/${i + 1}/")
            }
        } else listOf(ChapterInfo(title.ifBlank { "Chapitre unique" }, "$BASE/s/$storyId/1/"))
        val cover = top.selectFirst("img.cimage")?.absUrl("src").orEmpty()
        val fandomUrl = links.lastOrNull()?.absUrl("href").orEmpty()
        return Fic(
            id = Fic.makeId(site, storyId), site = site, storyId = storyId, url = storyUrl(storyId),
            title = title, author = authorEl?.text().orEmpty(), authorUrl = authorEl?.absUrl("href").orEmpty(),
            fandoms = fandoms, summary = summary, rating = meta.rating, language = meta.language,
            genres = meta.genres, characters = meta.characters, status = meta.status, words = meta.words,
            chapterCount = chapters.size, chapters = chapters, published = meta.published, updated = meta.updated,
            favs = meta.favs, follows = meta.follows, reviews = meta.reviews, cover = cover, crossover = crossover,
            fandomUrl = fandomUrl
        )
    }

    class Meta {
        var rating = ""; var language = ""; var genres = listOf<String>(); var characters = listOf<String>()
        var status = FicStatus.ONGOING; var words = 0L; var chapters = 0; var favs = 0; var follows = 0; var reviews = 0
        var published = 0L; var updated = 0L; var fandom = ""; var crossover = false
    }

    /** Lit la ligne de statistiques grise (« Rated: T - English - Adventure - Chapters: 5 - … »). */
    fun parseMeta(el: Element?): Meta {
        val m = Meta()
        if (el == null) return m
        val times = el.select("span[data-xutime]").map { it.attr("data-xutime").toLongOrNull()?.times(1000) ?: 0L }
        val text = el.text()
        if (text.contains("Updated:")) {
            m.updated = times.getOrElse(0) { 0L }; m.published = times.getOrElse(1) { m.updated }
        } else {
            m.published = times.getOrElse(0) { 0L }; m.updated = m.published
        }
        val tokens = text.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        val free = mutableListOf<String>()
        for (t in tokens) {
            val kv = Regex("^([A-Za-z]+):\\s*(.*)$").find(t)
            if (kv != null) {
                val v = kv.groupValues[2]
                when (kv.groupValues[1]) {
                    "Rated" -> m.rating = v.removePrefix("Fiction").trim()
                    "Chapters" -> m.chapters = Parse.int(v)
                    "Words" -> m.words = Parse.number(v)
                    "Reviews" -> m.reviews = Parse.int(v)
                    "Favs" -> m.favs = Parse.int(v)
                    "Follows" -> m.follows = Parse.int(v)
                    "Status" -> if (v.contains("Complete")) m.status = FicStatus.COMPLETE
                }
            } else free += t
        }
        if (free.any { it == "Complete" }) m.status = FicStatus.COMPLETE
        free.removeAll { it == "Complete" }
        if (free.firstOrNull() == "Crossover") { m.crossover = true; free.removeAt(0) }
        // Les listes commencent parfois par le fandom (« Naruto - Rated: … »)
        val ratedPos = tokens.indexOfFirst { it.startsWith("Rated:") }
        if (ratedPos > 0) {
            val before = tokens.subList(0, ratedPos).filter { it != "Crossover" }
            m.fandom = before.joinToString(" - ")
            free.removeAll(before.toSet())
        }
        val genreRegex = Regex("^(" + GENRES.joinToString("|") { Regex.escape(it) } + ")(/(" + GENRES.joinToString("|") { Regex.escape(it) } + "))*$")
        for (t in free) {
            when {
                m.language.isEmpty() && LANGUAGES.contains(t) -> m.language = t
                genreRegex.matches(t) -> m.genres = splitGenres(t)
                else -> if (m.characters.isEmpty()) m.characters = splitCharacters(t)
            }
        }
        return m
    }

    private val LANGUAGES = setOf(
        "English", "Spanish", "French", "German", "Chinese", "Japanese", "Dutch", "Portuguese", "Russian", "Italian",
        "Bulgarian", "Polish", "Hungarian", "Hebrew", "Arabic", "Swedish", "Norwegian", "Danish", "Finnish", "Filipino",
        "Esperanto", "Hindi", "Punjabi", "Farsi", "Greek", "Romanian", "Albanian", "Serbian", "Turkish", "Czech",
        "Indonesian", "Croatian", "Catalan", "Latin", "Korean", "Vietnamese", "Thai", "Devanagari", "Icelandic",
        "Afrikaans", "Estonian", "Malay", "Ukrainian", "Slovak", "Lithuanian", "Latvian", "Tamil", "Slovenian"
    )

    private fun splitGenres(t: String): List<String> =
        t.replace("Hurt/Comfort", "Hurt\u0001Comfort").split("/").map { it.replace("\u0001", "/") }

    private fun splitCharacters(t: String): List<String> =
        t.replace("[", ",").replace("]", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // ---------- Listes (recherche, parcours d'un fandom) ----------

    fun parseList(html: String, defaultFandom: String = "", fandomUrl: String = ""): List<Fic> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("div.z-list").mapNotNull { z ->
            val a = z.selectFirst("a.stitle") ?: return@mapNotNull null
            val id = Regex("/s/(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val author = z.select("a[href^=/u/]").firstOrNull()
            val indent = z.selectFirst("div.z-indent")
            val metaEl = indent?.selectFirst("div.z-padtop2")
            val summary = indent?.ownText()?.trim().orEmpty()
            val meta = parseMeta(metaEl)
            val fandoms = when {
                meta.fandom.isNotEmpty() -> meta.fandom.split(" & ").map { it.trim() }
                defaultFandom.isNotEmpty() -> listOf(defaultFandom)
                else -> emptyList()
            }
            val cover = z.selectFirst("img.cimage")?.let { it.absUrl("data-original").ifEmpty { it.absUrl("src") } }.orEmpty()
            Fic(
                id = Fic.makeId(site, id), site = site, storyId = id, url = storyUrl(id),
                title = a.text(), author = author?.text().orEmpty(), authorUrl = author?.absUrl("href").orEmpty(),
                fandoms = fandoms, summary = summary, rating = meta.rating, language = meta.language,
                genres = meta.genres, characters = meta.characters, status = meta.status, words = meta.words,
                chapterCount = meta.chapters, published = meta.published, updated = meta.updated,
                favs = meta.favs, follows = meta.follows, reviews = meta.reviews, cover = cover,
                crossover = meta.crossover || fandoms.size > 1,
                fandomUrl = if (meta.fandom.isEmpty()) fandomUrl else ""
            )
        }
    }

    /** Un filtre proposé par le site sur la page d'un fandom (tri, genre, personnage…). */
    data class Filter(val name: String, val label: String, val options: List<Pair<String, String>>, val default: String)

    private val FILTER_LABELS = mapOf(
        "srt" to "Trier par", "t" to "Mise à jour", "r" to "Classement", "lan" to "Langue", "len" to "Longueur",
        "s" to "Statut", "g1" to "Genre", "g2" to "Second genre", "_g1" to "Exclure le genre",
        "c1" to "Personnage 1", "c2" to "Personnage 2", "c3" to "Personnage 3", "c4" to "Personnage 4",
        "_c1" to "Exclure le personnage", "_c2" to "Exclure un 2e personnage", "v1" to "Univers", "_v1" to "Exclure l'univers",
        "pm" to "Couple"
    )

    /** Lit les menus de filtres de la page d'un fandom. */
    fun parseFilters(html: String): List<Filter> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("select[name]").mapNotNull { s ->
            val name = s.attr("name")
            if (name !in FILTER_LABELS) return@mapNotNull null
            val opts = s.select("option").map { it.attr("value") to it.text().trim() }
            if (opts.size < 2) return@mapNotNull null
            val sel = s.selectFirst("option[selected]")?.attr("value") ?: opts.first().first
            Filter(name, FILTER_LABELS[name] ?: name, opts, sel)
        }.distinctBy { it.name }
    }

    /** Adresse de la page « crossovers » d'un fandom, si elle est indiquée. */
    fun crossoverUrl(html: String): String? =
        Jsoup.parse(html, BASE).select("a[href*=-Crossovers/]").firstOrNull()?.absUrl("href")

    fun browseUrl(fandomUrl: String, params: Map<String, String>, page: Int): String {
        val q = params.filter { it.value.isNotEmpty() && it.value != "0" || it.key == "srt" }
            .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val base = fandomUrl.substringBefore("?").let { if (it.endsWith("/")) it else "$it/" }
        return "$base?&$q&p=$page"
    }

    fun searchUrl(keywords: String, page: Int, sort: Int = 0): String =
        "$BASE/search/?keywords=${URLEncoder.encode(keywords, "UTF-8")}&ready=1&type=story&ppage=$page" +
            (if (sort > 0) "&sortid=$sort" else "")

    /** Index des fandoms d'une catégorie : (nom, adresse, nombre d'histoires). */
    fun parseFandomIndex(html: String): List<Triple<String, String, Int>> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("#list_output a[href], .list_output a[href]").mapNotNull { a ->
            val href = a.absUrl("href")
            if (!href.startsWith(BASE) || href.contains("/s/") || href.contains("/u/")) return@mapNotNull null
            val name = a.attr("title").ifBlank { a.text() }.trim()
            val count = Parse.int(a.parent()?.selectFirst("span")?.text())
            if (name.isEmpty()) null else Triple(name, href, count)
        }.distinctBy { it.second }
    }
}
