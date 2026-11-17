package com.thousandlabs.ohara.sources

import android.webkit.CookieManager
import com.thousandlabs.ohara.data.ChapterInfo
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder

/**
 * WebNovel. Le site s'affiche avec JavaScript : on passe par sa propre interface de données
 * (celle qu'utilise sa page web), avec les cookies du navigateur intégré, et par le navigateur
 * invisible en secours. Seuls les chapitres gratuits ou déjà débloqués par le lecteur sont lisibles.
 */
object WebNovel : Source {
    override val site = Site.WN
    const val BASE = "https://www.webnovel.com"

    override fun matches(host: String) = host.endsWith("webnovel.com")

    override fun parseLink(url: String): LinkInfo? {
        val m = Regex("/book/(?:[^/?#]*_)?(\\d{8,})(?:/(?:[^/?#]*_)?(\\d{8,}))?").find(url) ?: return null
        return LinkInfo(site, m.groupValues[1], chapterKey = m.groupValues[2].ifEmpty { null })
    }

    override fun storyUrl(storyId: String) = "$BASE/book/$storyId"
    override fun chapterUrl(fic: Fic, index: Int): String =
        fic.chapters.getOrNull(index)?.url ?: storyUrl(fic.storyId)

    private fun csrf(): String? =
        CookieManager.getInstance().getCookie(BASE)?.split(";")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("_csrfToken=") }?.substringAfter("=")

    /** Le jeton du site est posé par sa page d'accueil : on la charge une fois si besoin. */
    private suspend fun ensureToken(storyId: String): String {
        csrf()?.let { return it }
        Fetcher.get(storyUrl(storyId), needsJs = true, readyJs = "document.cookie.indexOf('_csrfToken')>=0")
        return csrf() ?: throw IOException("WebNovel n'a pas répondu. Ouvrez le site une fois dans le navigateur d'Ohara.")
    }

    override suspend fun fetchInfo(storyId: String): Fic {
        // 1. interface de données du site ; 2. en secours, lecture des pages (sommaire et page du livre)
        val api = runCatching { fetchInfoApi(storyId) }
        api.getOrNull()?.let { return it }
        return runCatching { fetchInfoPages(storyId) }.getOrElse { e ->
            throw IOException("WebNovel illisible (données : ${api.exceptionOrNull()?.message} ; pages : ${e.message})")
        }
    }

    /** Lecture du sommaire (/catalog) et de la page du livre, affichés dans le navigateur invisible. */
    private suspend fun fetchInfoPages(storyId: String): Fic {
        val book = storyUrl(storyId)
        val cat = Fetcher.get("$book/catalog", needsJs = true,
            readyJs = "document.querySelectorAll('a[data-cid],a[href*=\"/book/\"]').length>8").body
        val cdoc = Jsoup.parse(cat, BASE)
        val chapRe = Regex("/book/(?:[^/?#]*_)?$storyId/(?:[^/?#]*_)?(\\d{8,})")
        val chapters = mutableListOf<ChapterInfo>()
        val seen = HashSet<String>()
        for (a in cdoc.select("a[href]")) {
            val href = a.attr("href")
            val cid = a.attr("data-cid").takeIf { it.length >= 8 } ?: chapRe.find(href)?.groupValues?.get(1) ?: continue
            if (!seen.add(cid)) continue
            val name = a.attr("title").ifBlank { a.selectFirst("strong")?.text().orEmpty() }.ifBlank { a.text() }.trim()
            val locked = a.select("use").any { it.attr("xlink:href").contains("lock") || it.attr("href").contains("lock") } ||
                a.select("[class*=lock]").isNotEmpty()
            chapters += ChapterInfo(name.ifBlank { "Chapitre ${chapters.size + 1}" }, "$BASE/book/$storyId/$cid", key = cid, locked = locked)
        }
        if (chapters.isEmpty()) throw IOException("sommaire vide")
        val bdoc = runCatching {
            Jsoup.parse(Fetcher.get(book, needsJs = true, readyJs = "!!document.querySelector('meta[property=\"og:title\"],h1')").body, BASE)
        }.getOrNull() ?: cdoc
        val title = bdoc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - ")?.substringBefore(" | ")?.trim()
            ?.ifBlank { null } ?: bdoc.selectFirst("h1")?.text().orEmpty()
        val summary = bdoc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: bdoc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        val author = bdoc.selectFirst("meta[property=og:novel:author],meta[name=author]")?.attr("content")
            ?: bdoc.selectFirst("a[href*=/profile/]")?.text().orEmpty()
        val category = bdoc.selectFirst("meta[property=og:novel:category]")?.attr("content").orEmpty()
        return Fic(
            id = Fic.makeId(site, storyId), site = site, storyId = storyId, url = book, title = title.ifBlank { "Fic WebNovel" },
            author = author, fandoms = listOfNotNull(category.takeIf { it.isNotBlank() }), summary = summary,
            chapterCount = chapters.size, chapters = chapters,
            cover = "https://book-pic.webnovel.com/bookcover/$storyId?imageMogr2/thumbnail/300"
        )
    }

    private suspend fun fetchInfoApi(storyId: String): Fic {
        val token = ensureToken(storyId)
        val book = storyUrl(storyId)
        // Informations du livre
        val infoJson = runCatching {
            JSONObject(Fetcher.getJson("$BASE/go/pcm/book/get-book-detail?_csrfToken=$token&bookId=$storyId", book))
        }.getOrNull()
        val listJson = JSONObject(Fetcher.getJson("$BASE/go/pcm/chapter/get-chapter-list?_csrfToken=$token&bookId=$storyId&pageIndex=0", book))
        val data = listJson.optJSONObject("data") ?: throw IOException("Liste des chapitres WebNovel illisible.")
        val chapters = mutableListOf<ChapterInfo>()
        val volumes = data.optJSONArray("volumeItems")
        if (volumes != null) for (v in 0 until volumes.length()) {
            val items = volumes.getJSONObject(v).optJSONArray("chapterItems") ?: continue
            for (c in 0 until items.length()) {
                val it = items.getJSONObject(c)
                val cid = it.optString("chapterId").ifEmpty { it.optString("id") }
                val name = it.optString("chapterName").ifEmpty { it.optString("name") }
                val locked = it.optInt("chapterLevel", 0) > 0 && it.optInt("isAuth", 1) == 0 ||
                    it.optInt("isVip", 0) > 0 && it.optInt("isAuth", 1) == 0
                chapters += ChapterInfo(name.ifBlank { "Chapitre ${chapters.size + 1}" }, "$BASE/book/$storyId/$cid", key = cid, locked = locked)
            }
        }
        if (chapters.isEmpty()) throw IOException("Aucun chapitre trouvé sur WebNovel.")
        val b = infoJson?.optJSONObject("data")?.optJSONObject("bookInfo") ?: data.optJSONObject("bookInfo")
        val title = b?.optString("bookName").orEmpty()
        val author = b?.optString("authorName").orEmpty().ifEmpty { b?.optJSONObject("authorInfo")?.optString("authorName").orEmpty() }
        val summary = b?.optString("description").orEmpty().ifEmpty { b?.optString("bookDesc").orEmpty() }
        val category = b?.optString("categoryName").orEmpty()
        val tags = mutableListOf<String>()
        b?.optJSONArray("tagInfo")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("tagName")?.let { t -> if (t.isNotBlank()) tags += t } }
        val status = when (b?.optInt("actionStatus", -1)) { 30, 50 -> FicStatus.COMPLETE; 10, 20 -> FicStatus.ONGOING; else -> FicStatus.UNKNOWN }
        val words = b?.optLong("totalWords") ?: 0L
        val views = b?.optLong("totalPv")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        val collections = b?.optLong("collectionNum")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        val pub = b?.optLong("publishTime") ?: 0L
        val upd = b?.optLong("updateTime")?.takeIf { it > 0 } ?: b?.optLong("lastChapterUpdateTime") ?: 0L
        var fic = Fic(
            id = Fic.makeId(site, storyId), site = site, storyId = storyId, url = book, title = title,
            author = author, fandoms = listOfNotNull(category.takeIf { it.isNotBlank() }), summary = summary,
            tags = tags, status = status, words = words, chapterCount = chapters.size, chapters = chapters,
            published = normTime(pub), updated = normTime(upd), views = views, favs = collections,
            cover = "https://book-pic.webnovel.com/bookcover/$storyId?imageMogr2/thumbnail/300"
        )
        if (title.isBlank()) runCatching {
            // secours : on lit la page du livre
            val page = Fetcher.get(book, needsJs = true, readyJs = "!!document.querySelector('h1,meta[property=\"og:title\"]')")
            val doc = Jsoup.parse(page.body, BASE)
            fic = fic.copy(
                title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - ")?.trim()
                    ?: doc.selectFirst("h1")?.text().orEmpty(),
                summary = fic.summary.ifEmpty { doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty() }
            )
        }
        return fic
    }

    /** Les dates du site sont tantôt en secondes, tantôt en millisecondes. */
    private fun normTime(t: Long): Long = when { t <= 0 -> 0; t < 10_000_000_000L -> t * 1000; else -> t }

    override suspend fun fetchChapter(fic: Fic, index: Int): String {
        val ch = fic.chapters.getOrNull(index) ?: throw IOException("Chapitre inconnu.")
        if (ch.locked) throw IOException("Chapitre verrouillé sur WebNovel : débloquez-le sur le site d'abord.")
        val api = runCatching {
            val token = ensureToken(fic.storyId)
            val j = JSONObject(Fetcher.getJson("$BASE/go/pcm/chapter/getContent?_csrfToken=$token&bookId=${fic.storyId}&chapterId=${ch.key}", ch.url))
            val info = j.optJSONObject("data")?.optJSONObject("chapterInfo")
            val parts = info?.optJSONArray("contents")
            val sb = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) {
                val c = parts.optJSONObject(i)?.optString("content").orEmpty()
                if (c.isNotBlank()) sb.append(if (c.trimStart().startsWith("<")) c else "<p>${escape(c)}</p>")
            } else info?.optString("content")?.let { sb.append(it) }
            sb.toString()
        }.getOrDefault("")
        if (api.isNotBlank()) return Cleaner.clean(api)
        // secours : page du chapitre dans le navigateur invisible
        val page = Fetcher.get(ch.url, needsJs = true, readyJs = "!!document.querySelector('.cha-words p,.cha-content p,.chapter_content p')")
        val doc = Jsoup.parse(page.body, BASE)
        val el = doc.selectFirst(".cha-words") ?: doc.selectFirst(".cha-content") ?: doc.selectFirst(".chapter_content")
            ?: throw IOException("Texte du chapitre introuvable sur WebNovel.")
        el.select(".para-comment, .j_open_para_comment, .cha-bts, script, style").remove()
        return Cleaner.clean(el)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ---------- Recherche ----------

    fun searchUrl(q: String, page: Int) = "$BASE/search?keywords=${URLEncoder.encode(q, "UTF-8")}&type=fanfic&pageIndex=$page"

    /** Lit une page de résultats affichée (liens vers /book/…). */
    fun parseList(html: String): List<Fic> {
        val doc = Jsoup.parse(html, BASE)
        val out = LinkedHashMap<String, Fic>()
        for (a in doc.select("a[href*=/book/]")) {
            val info = parseLink(a.absUrl("href")) ?: continue
            if (info.chapterKey != null || out.containsKey(info.storyId)) continue
            val box = a.parents().firstOrNull { it.tagName() == "li" } ?: a.parent() ?: continue
            val title = (box.selectFirst("h3, h2, strong")?.text() ?: a.attr("title")).ifBlank { a.text() }.trim()
            if (title.length < 2) continue
            val summary = box.select("p").maxByOrNull { it.text().length }?.text().orEmpty()
            val tags = box.select("a[href*=/tags/], a[href*=/stories/]").map { it.text().trim() }.filter { it.isNotEmpty() && it.length < 40 }
            val author = box.selectFirst("a[href*=/profile/]")?.text().orEmpty()
            out[info.storyId] = Fic(
                id = Fic.makeId(site, info.storyId), site = site, storyId = info.storyId, url = storyUrl(info.storyId),
                title = title, author = author, summary = summary, tags = tags.distinct(),
                fandoms = tags.take(1), cover = "https://book-pic.webnovel.com/bookcover/${info.storyId}?imageMogr2/thumbnail/300"
            )
        }
        return out.values.toList()
    }
}
