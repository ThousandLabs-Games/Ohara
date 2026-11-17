package com.thousandlabs.ohara.sources

import com.thousandlabs.ohara.data.ChapterInfo
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder

/** SpaceBattles (forum XenForo avec « threadmarks » : chaque chapitre est un message marqué). */
object SpaceBattles : Source {
    override val site = Site.SB
    const val BASE = "https://forums.spacebattles.com"
    const val CREATIVE_WRITING = 18

    override fun matches(host: String) = host.endsWith("spacebattles.com")

    override fun parseLink(url: String): LinkInfo? {
        val thread = Regex("/threads/(?:[^/]*\\.)?(\\d+)").find(url)?.groupValues?.get(1)
        val post = Regex("(?:#post-|/post-|/posts/)(\\d+)").find(url)?.groupValues?.get(1)
        if (thread == null && post == null) return null
        return LinkInfo(site, thread ?: "", chapterKey = post)
    }

    /** Un lien « /posts/123 » ne dit pas de quel fil il s'agit : on demande au site. */
    suspend fun resolvePost(postId: String): LinkInfo? {
        val page = Fetcher.get("$BASE/posts/$postId/")
        val thread = Regex("/threads/(?:[^/]*\\.)?(\\d+)").find(page.url)?.groupValues?.get(1)
            ?: Jsoup.parse(page.body, BASE).selectFirst("link[rel=canonical]")?.attr("href")
                ?.let { Regex("/threads/(?:[^/]*\\.)?(\\d+)").find(it)?.groupValues?.get(1) }
            ?: return null
        return LinkInfo(site, thread, chapterKey = postId)
    }

    override fun storyUrl(storyId: String) = "$BASE/threads/$storyId/"
    override fun chapterUrl(fic: Fic, index: Int): String =
        fic.chapters.getOrNull(index)?.url ?: "$BASE/threads/${fic.storyId}/reader/page-${index / 10 + 1}"

    private fun threadmarksUrl(id: String, page: Int) = "$BASE/threads/$id/threadmarks?threadmark_category=1&page=$page"

    override suspend fun fetchInfo(storyId: String): Fic {
        val first = Fetcher.get(threadmarksUrl(storyId, 1))
        val doc = Jsoup.parse(first.body, BASE)
        val chapters = parseThreadmarks(doc).toMutableList()
        val pages = doc.select(".pageNav-page a, .pageNav-main a").mapNotNull { it.text().trim().toIntOrNull() }.maxOrNull() ?: 1
        for (p in 2..pages.coerceAtMost(60)) {
            val d = Jsoup.parse(Fetcher.get(threadmarksUrl(storyId, p)).body, BASE)
            chapters += parseThreadmarks(d)
        }
        val distinct = chapters.distinctBy { it.key.ifEmpty { it.url } }
        if (distinct.isEmpty()) throw IOException("Aucun chapitre marqué (threadmark) dans ce fil.")

        val rawTitle = doc.selectFirst("h1.p-title-value")?.let { h -> h.select("span.label, .label-append").remove(); h.text() }.orEmpty()
        val author = doc.selectFirst(".p-description .username, .p-description a.username")
        val tags = doc.select(".tagList a.tagItem, .tagList .tagItem").map { it.text().trim() }.filter { it.isNotEmpty() }
        val created = doc.selectFirst(".p-description time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: 0L
        val headerText = doc.select(".block-header, .threadmarkListingHeader, .block-outer").text()
        val totalWords = Regex("Words?:?\\s*([0-9][0-9.,]*\\s*[kKmM]?)").find(headerText)?.groupValues?.get(1)?.let { Parse.number(it) }
            ?: distinct.sumOf { it.words.toLong() }
        val last = distinct.maxOfOrNull { it.date } ?: 0L
        val complete = Regex("\\b(complete|completed|finished)\\b", RegexOption.IGNORE_CASE).containsMatchIn(rawTitle) ||
            tags.any { it.equals("complete", true) || it.equals("completed", true) }
        return Fic(
            id = Fic.makeId(site, storyId), site = site, storyId = storyId, url = storyUrl(storyId),
            title = cleanTitle(rawTitle), author = author?.text().orEmpty(), authorUrl = author?.absUrl("href").orEmpty(),
            fandoms = fandomsFromTitle(rawTitle), tags = tags,
            status = if (complete) FicStatus.COMPLETE else FicStatus.ONGOING,
            words = totalWords, chapterCount = distinct.size, chapters = distinct,
            published = if (created > 0) created else distinct.firstOrNull()?.date ?: 0L,
            updated = if (last > 0) last else created
        )
    }

    private fun parseThreadmarks(doc: Document): List<ChapterInfo> {
        val items = doc.select("div.structItem--threadmark").ifEmpty { doc.select("li.threadmarkListingItem, .threadmarkList li") }
        return items.mapNotNull { item ->
            val a = item.selectFirst(".structItem-title a[href], a[href*=post]") ?: return@mapNotNull null
            val href = a.absUrl("href")
            val key = Regex("(?:post-|posts/)(\\d+)").find(href)?.groupValues?.get(1).orEmpty()
            val date = item.selectFirst("time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: 0L
            val words = Regex("([0-9][0-9.,]*\\s*[kK]?)\\s*words", RegexOption.IGNORE_CASE).find(item.text())?.groupValues?.get(1)
                ?.let { Parse.int(it) } ?: 0
            ChapterInfo(a.text().trim().ifEmpty { "Chapitre" }, href, key = key, words = words, date = date)
        }
    }

    /** Pages « lecteur » : 10 chapitres par page. On garde la dernière page lue pour les 9 suivants. */
    private var readerCache: Triple<String, Int, Map<String, String>>? = null

    override suspend fun fetchChapter(fic: Fic, index: Int): String {
        val ch = fic.chapters.getOrNull(index) ?: throw IOException("Chapitre inconnu.")
        val page = index / 10 + 1
        val cached = readerCache?.takeIf { it.first == fic.storyId && it.second == page }?.third
        val posts = cached ?: run {
            val p = Fetcher.get("$BASE/threads/${fic.storyId}/reader/page-$page")
            val map = parsePosts(Jsoup.parse(p.body, BASE))
            readerCache = Triple(fic.storyId, page, map)
            map
        }
        posts[ch.key]?.let { return it }
        // ordre différent dans le lecteur : on va chercher le message lui-même
        val d = Jsoup.parse(Fetcher.get(ch.url).body, BASE)
        return parsePosts(d)[ch.key] ?: throw IOException("Message du chapitre introuvable.")
    }

    private fun parsePosts(doc: Document): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (art in doc.select("article.message, article[data-content^=post-]")) {
            val key = art.attr("data-content").removePrefix("post-").ifEmpty { art.id().removePrefix("js-post-") }
            val body = art.selectFirst(".message-body .bbWrapper") ?: art.selectFirst(".bbWrapper") ?: continue
            out[key] = cleanPost(body)
        }
        return out
    }

    private fun cleanPost(body: Element): String {
        val b = body.clone()
        b.select(".bbCodeBlock-expandLink, .bbCodeBlock-shrinkLink, .js-selectToQuote, script, style, noscript, .message-lastEdit").remove()
        b.select(".bbCodeSpoiler-button").forEach { it.replaceWith(org.jsoup.nodes.TextNode("[Spoiler] ")) }
        b.select(".bbCodeBlock--quote .bbCodeBlock-title").remove()
        b.select(".bbCodeBlock--quote").tagName("blockquote")
        return Cleaner.clean(b)
    }

    private fun cleanTitle(t: String) = t.trim()

    /** « Titre (Naruto/Worm) » ou « [Naruto] Titre » → fandoms. */
    fun fandomsFromTitle(t: String): List<String> {
        val inParens = Regex("[(\\[]([^()\\[\\]]{2,60})[)\\]]").findAll(t).map { it.groupValues[1] }
            .filterNot { Regex("\\b(complete|si|oc|au|quest|ch\\.?|chapter|discussion|thread|ongoing|hiatus|\\d+)\\b", RegexOption.IGNORE_CASE).matches(it.trim()) }
            .toList()
        val raw = inParens.lastOrNull() ?: return emptyList()
        return raw.split("/", " x ", " X ", "×", ",").map { it.trim() }.filter { it.length in 2..40 }.take(4)
    }

    // ---------- Listes (étiquettes, recherche, nouveautés) ----------

    fun tagUrl(tag: String, page: Int): String {
        val slug = tag.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "$BASE/tags/$slug/?page=$page"
    }

    fun searchUrl(q: String, page: Int, byDate: Boolean): String =
        "$BASE/search/?q=${URLEncoder.encode(q, "UTF-8")}&t=post&c[child_nodes]=1&c[nodes][0]=$CREATIVE_WRITING" +
            "&c[title_only]=1&o=${if (byDate) "date" else "relevance"}&page=$page"

    fun latestUrl(page: Int): String =
        "$BASE/forums/creative-writing.$CREATIVE_WRITING/?order=last_threadmark&direction=desc&page=$page"

    fun newestUrl(page: Int): String =
        "$BASE/forums/creative-writing.$CREATIVE_WRITING/?order=post_date&direction=desc&page=$page"

    fun parseList(html: String): List<Fic> {
        val doc = Jsoup.parse(html, BASE)
        val out = mutableListOf<Fic>()
        for (item in doc.select("div.structItem--thread")) {
            val a = item.select(".structItem-title a[href*=/threads/]").lastOrNull() ?: continue
            val id = Regex("/threads/(?:[^/]*\\.)?(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            val labels = item.select(".structItem-title .label").map { it.text() }
            val title = a.text()
            val author = item.attr("data-author").ifEmpty { item.selectFirst(".structItem-minor .username")?.text().orEmpty() }
            val start = item.selectFirst(".structItem-startDate time[data-time], .structItem-minor time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: 0L
            val latest = item.selectFirst(".structItem-latestDate[data-time], .structItem-cell--latest time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: start
            val metas = item.select(".structItem-cell--meta dd").map { Parse.int(it.text()) }
            val text = item.text()
            val words = Regex("Words?:?\\s*([0-9][0-9.,]*\\s*[kKmM]?)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { Parse.number(it) }
                ?: Regex("([0-9][0-9.,]*\\s*[kKmM]?)\\s*words", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { Parse.number(it) } ?: 0L
            val marks = Regex("Threadmarks?:?\\s*([0-9][0-9,]*)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { Parse.int(it) } ?: 0
            val tags = item.select(".tagItem").map { it.text() }
            val complete = (labels + title + tags).any { Regex("\\b(complete|completed)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            out += Fic(
                id = Fic.makeId(site, id), site = site, storyId = id, url = storyUrl(id), title = title,
                author = author, fandoms = fandomsFromTitle(title), tags = (labels + tags).distinct(),
                status = if (complete) FicStatus.COMPLETE else FicStatus.ONGOING, words = words, chapterCount = marks,
                published = start, updated = latest, replies = metas.getOrElse(0) { 0 }, views = metas.getOrElse(1) { 0 }
            )
        }
        for (row in doc.select("li.block-row .contentRow, div.contentRow")) {
            val a = row.selectFirst(".contentRow-title a[href*=/threads/]") ?: continue
            val id = Regex("/threads/(?:[^/]*\\.)?(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            if (out.any { it.storyId == id }) continue
            a.select(".label").remove()
            val title = a.text()
            val minor = row.selectFirst(".contentRow-minor")
            val author = minor?.selectFirst(".username")?.text().orEmpty()
            val date = minor?.selectFirst("time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: 0L
            val replies = Regex("Replies:?\\s*([0-9][0-9,.kK]*)").find(minor?.text().orEmpty())?.groupValues?.get(1)?.let { Parse.int(it) } ?: 0
            val tags = row.select(".tagItem").map { it.text() }
            out += Fic(
                id = Fic.makeId(site, id), site = site, storyId = id, url = storyUrl(id), title = title,
                author = author, fandoms = fandomsFromTitle(title), summary = row.selectFirst(".contentRow-snippet")?.text().orEmpty(),
                tags = tags, status = if (tags.any { it.equals("complete", true) }) FicStatus.COMPLETE else FicStatus.UNKNOWN,
                published = date, updated = date, replies = replies
            )
        }
        return out
    }
}
