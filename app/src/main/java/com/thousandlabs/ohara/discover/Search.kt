package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.net.VerificationNeeded
import com.thousandlabs.ohara.sources.FanFictionNet
import com.thousandlabs.ohara.sources.SpaceBattles
import com.thousandlabs.ohara.sources.WebNovel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class StatusFilter(val label: String) { ANY("Tous"), COMPLETE("Terminées"), ONGOING("En cours") }

enum class SearchSort(val label: String) {
    RELEVANCE("Pertinence"),
    PROMISING("Prometteuses d'abord"),
    UPDATED("Mises à jour récentes"),
    PUBLISHED("Les plus récentes"),
    WORDS("Les plus longues"),
    POPULAR("Les plus suivies")
}

/** Tout ce qu'on peut demander dans la recherche avancée. Les valeurs à 0 ou vides ne filtrent pas. */
data class SearchQuery(
    val sites: Set<Site> = setOf(Site.FFN, Site.SB, Site.WN),
    val keywords: String = "",
    val fandom: String = "",
    val characters: List<String> = emptyList(),
    val include: Set<FicType> = emptySet(),
    val exclude: Set<FicType> = emptySet(),
    val genres: Set<String> = emptySet(),
    val minWords: Long = 0,
    val maxWords: Long = 0,
    val minChapters: Int = 0,
    val maxChapters: Int = 0,
    val status: StatusFilter = StatusFilter.ANY,
    val publishedWithinDays: Int = 0,
    val updatedWithinDays: Int = 0,
    val publishedFromYear: Int = 0,
    val newOnly: Boolean = false,
    val promisingOnly: Boolean = false,
    val englishOnly: Boolean = true,
    val sort: SearchSort = SearchSort.RELEVANCE
) {
    val isEmpty: Boolean get() = keywords.isBlank() && fandom.isBlank() && characters.isEmpty() && include.isEmpty() && !newOnly

    /** Délai de publication réellement demandé (« nouveautés » = 30 jours). */
    val pubDays: Int get() = if (newOnly) (if (publishedWithinDays in 1..30) publishedWithinDays else 30) else publishedWithinDays

    fun toJson(): JSONObject = JSONObject().apply {
        put("sites", JSONArray(sites.map { it.code })); put("kw", keywords); put("fandom", fandom)
        put("chars", JSONArray(characters)); put("inc", JSONArray(include.map { it.code })); put("exc", JSONArray(exclude.map { it.code }))
        put("genres", JSONArray(genres.toList())); put("minW", minWords); put("maxW", maxWords)
        put("minC", minChapters); put("maxC", maxChapters); put("status", status.name)
        put("pubD", publishedWithinDays); put("updD", updatedWithinDays); put("year", publishedFromYear)
        put("new", newOnly); put("prom", promisingOnly); put("en", englishOnly); put("sort", sort.name)
    }

    /** Résumé lisible (« Naruto · self-insert · plus de 100 k mots »). */
    fun describe(): String {
        val p = mutableListOf<String>()
        if (keywords.isNotBlank()) p += "« ${keywords.trim()} »"
        if (fandom.isNotBlank()) p += fandom.trim()
        if (characters.isNotEmpty()) p += characters.joinToString(", ")
        include.forEach { p += it.label }
        exclude.forEach { p += "sans ${it.label}" }
        genres.forEach { p += it }
        if (minWords > 0) p += "plus de ${minWords / 1000} k mots"
        if (maxWords > 0) p += "moins de ${maxWords / 1000} k mots"
        if (minChapters > 0) p += "au moins $minChapters chapitres"
        if (maxChapters > 0) p += "au plus $maxChapters chapitres"
        if (status != StatusFilter.ANY) p += status.label.lowercase()
        if (newOnly) p += "nouveautés"
        if (promisingOnly) p += "prometteuses"
        return p.joinToString(" · ").ifEmpty { "Toutes les fics" }
    }

    companion object {
        private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

        fun fromJson(o: JSONObject) = SearchQuery(
            sites = o.optJSONArray("sites").strs().map { Site.of(it) }.toSet().ifEmpty { setOf(Site.FFN, Site.SB, Site.WN) },
            keywords = o.optString("kw"), fandom = o.optString("fandom"), characters = o.optJSONArray("chars").strs(),
            include = o.optJSONArray("inc").strs().mapNotNull { FicType.of(it) }.toSet(),
            exclude = o.optJSONArray("exc").strs().mapNotNull { FicType.of(it) }.toSet(),
            genres = o.optJSONArray("genres").strs().toSet(), minWords = o.optLong("minW"), maxWords = o.optLong("maxW"),
            minChapters = o.optInt("minC"), maxChapters = o.optInt("maxC"),
            status = runCatching { StatusFilter.valueOf(o.optString("status")) }.getOrDefault(StatusFilter.ANY),
            publishedWithinDays = o.optInt("pubD"), updatedWithinDays = o.optInt("updD"), publishedFromYear = o.optInt("year"),
            newOnly = o.optBoolean("new"), promisingOnly = o.optBoolean("prom"), englishOnly = o.optBoolean("en", true),
            sort = runCatching { SearchSort.valueOf(o.optString("sort")) }.getOrDefault(SearchSort.RELEVANCE)
        )
    }
}

/** Un résultat : la fic, son score « Prometteuse » et les types repérés. */
data class SearchHit(val fic: Fic, val score: Int, val types: Set<FicType>)

/** Une note pour le lecteur (site qui demande une vérification, site ignoré…). */
data class SiteNote(val site: Site, val text: String, val verifyUrl: String? = null)

/**
 * Une source de résultats pour un site : chaque appel à [next] lit la page suivante.
 * [remoteKeywords] : le site a déjà cherché les mots-clés (inutile de les re-filtrer strictement).
 */
private abstract class Feed(val site: Site) {
    var done = false
    var remoteKeywords = false
    abstract suspend fun next(): List<Fic>
}

private const val DAY = 24L * 3600 * 1000

/** FanFiction.net : parcours filtré de la page du fandom quand on le connaît, sinon recherche par mots-clés. */
private class FfnFeed(val q: SearchQuery, val note: (SiteNote) -> Unit) : Feed(Site.FFN) {
    private var page = 1
    private var fandomUrl: String? = null
    private var params: Map<String, String>? = null
    private var decided = false

    override suspend fun next(): List<Fic> {
        if (!decided) {
            decided = true
            if (q.fandom.isNotBlank()) {
                fandomUrl = FandomIndex.urlFor(q.fandom)
                if (fandomUrl == null) {
                    if (!FandomIndex.ready) note(SiteNote(site, "Préparation de l'index des fandoms de FanFiction.net (une seule fois, environ une minute)…"))
                    FandomIndex.ensure()
                    fandomUrl = FandomIndex.urlFor(q.fandom)
                }
                if (fandomUrl == null) note(SiteNote(site, "Fandom « ${q.fandom} » introuvable sur FanFiction.net : recherche par mots-clés."))
            }
        }
        val url = fandomUrl
        if (url != null) {
            if (params == null) {
                val filters = Search.ffnFilters(url)
                params = Search.ffnParams(filters, q)
            }
            val html = Fetcher.get(FanFictionNet.browseUrl(url, params!!, page), readyJs = READY).body
            page++
            val list = FanFictionNet.parseList(html, defaultFandom = q.fandom.trim(), fandomUrl = url)
            if (list.isEmpty() || page > 60) done = true
            return list
        }
        val kw = listOf(q.keywords, q.fandom).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (kw.isEmpty()) {
            done = true
            note(SiteNote(site, "FanFiction.net : indiquez un fandom ou des mots-clés."))
            return emptyList()
        }
        remoteKeywords = true
        val sort = when (q.sort) { SearchSort.UPDATED -> 1; SearchSort.PUBLISHED -> 2; SearchSort.POPULAR, SearchSort.PROMISING -> 4; else -> 0 }
        val html = Fetcher.get(FanFictionNet.searchUrl(kw, page, sort), readyJs = READY).body
        page++
        val list = FanFictionNet.parseList(html)
        if (list.isEmpty() || page > 40) done = true
        return list
    }

    companion object { const val READY = "!!document.querySelector('.z-list,#content_wrapper_inner,.gui_warning')" }
}

/** SpaceBattles : recherche dans les titres de la section Creative Writing, étiquettes, ou derniers fils. */
private class SbFeed(val q: SearchQuery) : Feed(Site.SB) {
    private var page = 1

    override suspend fun next(): List<Fic> {
        val kw = q.keywords.trim()
        val tagType = q.include.firstOrNull { it.sbTag != null && it != FicType.CROSSOVER }
        val url = when {
            kw.isNotEmpty() -> {
                remoteKeywords = true
                val term = if (q.fandom.isNotBlank()) "$kw ${q.fandom.trim()}" else kw
                SpaceBattles.searchUrl(term, page, q.sort == SearchSort.UPDATED || q.sort == SearchSort.PUBLISHED)
            }
            q.fandom.isNotBlank() -> SpaceBattles.tagUrl(q.fandom, page)
            tagType != null -> SpaceBattles.tagUrl(tagType.sbTag!!, page)
            q.sort == SearchSort.PUBLISHED || q.newOnly -> SpaceBattles.newestUrl(page)
            else -> SpaceBattles.latestUrl(page)
        }
        val html = Fetcher.get(url).body
        page++
        val list = SpaceBattles.parseList(html)
        if (list.isEmpty() || page > 40) done = true
        return list
    }
}

/** WebNovel : recherche dans les fanfictions du site (page affichée avec JavaScript). */
private class WnFeed(val q: SearchQuery, val note: (SiteNote) -> Unit) : Feed(Site.WN) {
    private var page = 1

    override suspend fun next(): List<Fic> {
        val term = listOf(q.keywords, q.fandom).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (term.isEmpty()) {
            done = true
            note(SiteNote(site, "WebNovel : indiquez un fandom ou des mots-clés."))
            return emptyList()
        }
        remoteKeywords = true
        val html = Fetcher.get(
            WebNovel.searchUrl(term, page), needsJs = true,
            readyJs = "document.querySelectorAll('a[href*=\"/book/\"]').length>4||!!document.querySelector('.g_empty,.empty')"
        ).body
        page++
        val list = WebNovel.parseList(html)
        if (list.isEmpty() || page > 15) done = true
        return list
    }
}

object Search {
    // ---------- FanFiction.net : filtres de la page d'un fandom ----------

    private val filterCache = HashMap<String, List<FanFictionNet.Filter>>()

    suspend fun ffnFilters(fandomUrl: String): List<FanFictionNet.Filter> {
        synchronized(filterCache) { filterCache[fandomUrl] }?.let { return it }
        val html = Fetcher.get(fandomUrl, readyJs = FfnFeed.READY).body
        val f = FanFictionNet.parseFilters(html)
        synchronized(filterCache) { filterCache[fandomUrl] = f }
        return f
    }

    private fun daysOf(label: String): Int? {
        val m = Regex("(\\d+)\\s*(day|week|month|year)", RegexOption.IGNORE_CASE).find(label) ?: return null
        val n = m.groupValues[1].toInt()
        return n * when (m.groupValues[2].lowercase()) { "day" -> 1; "week" -> 7; "month" -> 30; else -> 365 }
    }

    private fun kOf(label: String, sign: Char): Long? {
        val m = Regex(Regex.escape(sign.toString()) + "\\s*([0-9.]+)\\s*([kK]?)").find(label) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        return (if (m.groupValues[2].isNotEmpty()) v * 1000 else v).toLong()
    }

    /** Traduit la recherche en réglages des menus du site. */
    fun ffnParams(filters: List<FanFictionNet.Filter>, q: SearchQuery): Map<String, String> {
        val p = LinkedHashMap<String, String>()
        fun opts(name: String) = filters.firstOrNull { it.name == name }?.options.orEmpty()
        fun pick(name: String, pred: (String) -> Boolean): Boolean {
            val o = opts(name).firstOrNull { pred(it.second.lowercase()) } ?: return false
            p[name] = o.first
            return true
        }
        when (q.sort) {
            SearchSort.UPDATED -> pick("srt") { "update" in it }
            SearchSort.PUBLISHED -> pick("srt") { "publish" in it }
            SearchSort.WORDS, SearchSort.POPULAR, SearchSort.PROMISING, SearchSort.RELEVANCE -> pick("srt") { "favorite" in it || "favs" in it }
        }
        pick("r") { it.contains("all") }
        if (q.englishOnly) pick("lan") { it.trim() == "english" || it.endsWith(" english") || it.endsWith(":english") }
        when (q.status) {
            StatusFilter.COMPLETE -> pick("s") { "complete" in it }
            StatusFilter.ONGOING -> pick("s") { "progress" in it }
            StatusFilter.ANY -> {}
        }
        if (q.minWords > 0) {
            opts("len").mapNotNull { o -> kOf(o.second, '>')?.let { o to it } }.filter { it.second <= q.minWords }
                .maxByOrNull { it.second }?.let { p["len"] = it.first.first }
        } else if (q.maxWords > 0) {
            opts("len").mapNotNull { o -> kOf(o.second, '<')?.let { o to it } }.filter { it.second >= q.maxWords }
                .minByOrNull { it.second }?.let { p["len"] = it.first.first }
        }
        val pub = q.pubDays
        val (days, kind) = if (pub > 0) pub to "publish" else q.updatedWithinDays to "update"
        if (days > 0) {
            opts("t").mapNotNull { o -> if (kind in o.second.lowercase()) daysOf(o.second)?.let { o to it } else null }
                .filter { it.second >= days }.minByOrNull { it.second }?.let { p["t"] = it.first.first }
        }
        val genres = q.genres.toList()
        genres.getOrNull(0)?.let { g -> pick("g1") { it == g.lowercase() } }
        genres.getOrNull(1)?.let { g -> pick("g2") { it == g.lowercase() } }
        q.characters.take(4).forEachIndexed { i, c ->
            val n = c.trim().lowercase()
            if (n.isNotEmpty()) pick("c${i + 1}") { it.contains(n) && !it.startsWith("character") }
        }
        return p
    }

    // ---------- Filtrage local ----------

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun yearOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)

    /** La fic correspond-elle à la recherche ? Une information absente du site ne fait pas écarter la fic. */
    fun matches(q: SearchQuery, f: Fic, keywordsDoneBySite: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val text = norm(f.title + " " + f.summary + " " + f.tags.joinToString(" ") + " " + f.fandoms.joinToString(" ") + " " + f.characters.joinToString(" "))
        if (q.keywords.isNotBlank() && !keywordsDoneBySite) {
            val words = norm(q.keywords).split(' ').filter { it.length >= 2 }
            if (words.any { it !in text }) return false
        }
        if (q.fandom.isNotBlank()) {
            val fd = norm(q.fandom)
            val inFandoms = f.fandoms.any { norm(it).contains(fd) || fd.contains(norm(it)) && norm(it).length >= 3 }
            if (!inFandoms && !text.contains(fd)) return false
        }
        for (c in q.characters) {
            val n = norm(c)
            if (n.isEmpty()) continue
            val first = n.split(' ').first()
            if (f.characters.none { norm(it).contains(first) } && !text.contains(n) && !text.contains(first)) return false
        }
        val types = Types.detect(f)
        if (!types.containsAll(q.include)) return false
        if (types.any { it in q.exclude }) return false
        for (g in q.genres) {
            if (f.genres.isNotEmpty()) { if (f.genres.none { it.equals(g, true) }) return false }
            else if (!text.contains(norm(g))) return false
        }
        if (f.words > 0) {
            if (q.minWords > 0 && f.words < q.minWords) return false
            if (q.maxWords > 0 && f.words > q.maxWords) return false
        }
        val ch = f.totalChapters
        if (ch > 0) {
            if (q.minChapters > 0 && ch < q.minChapters) return false
            if (q.maxChapters > 0 && ch > q.maxChapters) return false
        }
        when (q.status) {
            StatusFilter.COMPLETE -> if (f.status != FicStatus.COMPLETE) return false
            StatusFilter.ONGOING -> if (f.status == FicStatus.COMPLETE) return false
            StatusFilter.ANY -> {}
        }
        val pubDays = q.pubDays
        if (pubDays > 0 && f.published > 0 && now - f.published > pubDays * DAY) return false
        if (q.newOnly && f.published <= 0 && f.updated > 0 && now - f.updated > 30 * DAY) return false
        if (q.updatedWithinDays > 0 && f.updated > 0 && now - f.updated > q.updatedWithinDays * DAY) return false
        if (q.publishedFromYear > 0 && f.published > 0 && yearOf(f.published) < q.publishedFromYear) return false
        if (q.promisingOnly && !Scoring.isPromising(Scoring.score(f, now))) return false
        return true
    }

    fun sort(list: List<SearchHit>, sort: SearchSort): List<SearchHit> = when (sort) {
        SearchSort.RELEVANCE -> list
        SearchSort.PROMISING -> list.sortedByDescending { it.score }
        SearchSort.UPDATED -> list.sortedByDescending { it.fic.updated }
        SearchSort.PUBLISHED -> list.sortedByDescending { it.fic.published.takeIf { p -> p > 0 } ?: it.fic.updated }
        SearchSort.WORDS -> list.sortedByDescending { it.fic.words }
        SearchSort.POPULAR -> list.sortedByDescending { it.fic.favs + it.fic.follows + it.fic.views / 50 + it.fic.replies * 2 }
    }

    /**
     * Lit une série de pages pour une recherche, sans garder d'état (utilisé par « Pour vous »,
     * « Nouveautés » et « Trouver une fic similaire »). Les fics de la bibliothèque sont gardées, à l'appelant de trier.
     */
    suspend fun collect(q: SearchQuery, rounds: Int = 1, notes: MutableList<SiteNote>? = null): List<Fic> {
        val feeds = feedsFor(q) { n -> notes?.add(n) }
        val out = LinkedHashMap<String, Fic>()
        repeat(rounds) {
            val active = feeds.filter { !it.done }
            if (active.isEmpty()) return@repeat
            val pages = coroutineScope {
                active.map { feed ->
                    async {
                        runCatching { feed to feed.next() }.getOrElse { e ->
                            feed.done = true
                            if (e is VerificationNeeded) notes?.add(SiteNote(feed.site, "${feed.site.label} demande une vérification.", e.url))
                            feed to emptyList()
                        }
                    }
                }.awaitAll()
            }
            for ((feed, list) in pages) for (f in list) if (matches(q, f, feed.remoteKeywords)) out.putIfAbsent(f.id, f)
        }
        return out.values.toList()
    }

    private fun feedsFor(q: SearchQuery, note: (SiteNote) -> Unit): List<Feed> = buildList {
        if (Site.FFN in q.sites) add(FfnFeed(q, note))
        if (Site.SB in q.sites) add(SbFeed(q))
        if (Site.WN in q.sites) add(WnFeed(q, note))
    }

    // ---------- Session de recherche (écran « Recherche avancée ») ----------

    data class State(
        val query: SearchQuery? = null,
        val hits: List<SearchHit> = emptyList(),
        val loading: Boolean = false,
        val canLoadMore: Boolean = false,
        val notes: List<SiteNote> = emptyList(),
        val hiddenCount: Int = 0,
        val error: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var feeds: List<Feed> = emptyList()
    private val seen = HashSet<String>()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(q: SearchQuery) {
        job?.cancel()
        seen.clear()
        val notes = mutableListOf<SiteNote>()
        feeds = feedsFor(q) { synchronized(notes) { notes += it }; _state.value = _state.value.copy(notes = synchronized(notes) { notes.toList() }) }
        _state.value = State(query = q, loading = true)
        job = scope.launch { loadRounds(q, notes, minNew = 12) }
    }

    fun loadMore() {
        val q = _state.value.query ?: return
        if (_state.value.loading || job?.isActive == true) return
        val notes = _state.value.notes.toMutableList()
        _state.value = _state.value.copy(loading = true)
        job = scope.launch { loadRounds(q, notes, minNew = 10) }
    }

    fun clear() {
        job?.cancel(); feeds = emptyList(); seen.clear(); _state.value = State()
    }

    /** Lit des pages jusqu'à obtenir assez de nouveaux résultats après filtrage (ou épuisement des sites). */
    private suspend fun loadRounds(q: SearchQuery, notes: MutableList<SiteNote>, minNew: Int) {
        var added = 0
        var rounds = 0
        var hidden = _state.value.hiddenCount
        val now = System.currentTimeMillis()
        while (added < minNew && rounds < 4) {
            val active = feeds.filter { !it.done }
            if (active.isEmpty()) break
            rounds++
            val pages = coroutineScope {
                active.map { feed ->
                    async {
                        runCatching { feed to feed.next() }.getOrElse { e ->
                            feed.done = true
                            val n = if (e is VerificationNeeded) SiteNote(feed.site, "${feed.site.label} demande une vérification « Je ne suis pas un robot ».", e.url)
                            else SiteNote(feed.site, "${feed.site.label} : ${e.message ?: "erreur"}")
                            synchronized(notes) { notes += n }
                            feed to emptyList()
                        }
                    }
                }.awaitAll()
            }
            // les sites alternent dans l'ordre « pertinence »
            val fresh = mutableListOf<Fic>()
            val lists = pages.map { (feed, list) -> list.filter { f -> f.id !in seen && matches(q, f, feed.remoteKeywords, now) } }
            val maxLen = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) for (l in lists) l.getOrNull(i)?.let { if (seen.add(it.id)) fresh += it }
            val kept = JunkFilter.apply(fresh) { it }
            hidden += fresh.size - kept.size
            val hits = kept.map { f -> SearchHit(Library.get(f.id)?.mergeSiteData(f) ?: f, Scoring.score(f, now), Types.detect(f)) }
            added += hits.size
            val all = Search.sort(_state.value.hits + hits, q.sort)
            _state.value = _state.value.copy(hits = all, hiddenCount = hidden, notes = synchronized(notes) { notes.toList() })
        }
        _state.value = _state.value.copy(loading = false, canLoadMore = feeds.any { !it.done }, notes = synchronized(notes) { notes.toList() })
    }
}
