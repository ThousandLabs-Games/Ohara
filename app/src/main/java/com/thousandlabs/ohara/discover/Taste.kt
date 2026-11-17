package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Vos goûts, appris de votre bibliothèque : fandoms, personnages, types de fics, genres, étiquettes, auteurs
 * et mots marquants des résumés. Une fic notée haut, mise en favoris ou lue jusqu'au bout compte beaucoup ;
 * une fic abandonnée ou mal notée compte en négatif.
 */
object Taste {
    private val STOP = setOf(
        "the", "and", "that", "with", "this", "from", "have", "they", "their", "what", "when", "into", "will", "your", "about",
        "after", "before", "being", "just", "only", "more", "than", "then", "there", "where", "which", "while", "would", "could",
        "should", "story", "chapter", "chapters", "fic", "fanfic", "fanfiction", "summary", "rated", "complete", "update", "updates",
        "also", "been", "were", "some", "like", "over", "them", "himself", "herself", "his", "her", "him", "she", "out", "not",
        "all", "but", "for", "are", "has", "had", "who", "how", "one", "can", "new", "world", "life", "time", "now", "first",
        "dans", "avec", "pour", "elle", "il", "les", "des", "une", "est", "sur", "par", "plus", "mais", "qui", "que", "son", "ses"
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} .'-]+"), " ").replace(Regex("\\s+"), " ").trim()

    fun keywords(text: String, limit: Int = 10): List<String> =
        Regex("[\\p{L}]{4,}").findAll(text.lowercase()).map { it.value }.filter { it !in STOP }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(limit).map { it.key }

    /** Caractéristiques d'une fic, avec leur poids. */
    fun terms(f: Fic): Map<String, Double> {
        val m = HashMap<String, Double>()
        fun add(k: String, w: Double) { if (k.length > 2) m[k] = (m[k] ?: 0.0) + w }
        f.fandoms.forEach { add("f:" + norm(it), 3.0) }
        f.characters.take(6).forEach { add("c:" + norm(it).substringBefore(' '), 2.0) }
        Types.detect(f).forEach { add("t:" + it.code, 2.2) }
        f.genres.forEach { add("g:" + norm(it), 1.0) }
        f.tags.take(14).forEach { add("tag:" + norm(it), 1.0) }
        if (f.author.isNotBlank()) add("a:" + JunkFilter.authorKey(f), 1.6)
        keywords(f.summary).forEach { add("w:$it", 0.35) }
        return m
    }

    /** Ce que la fic dit de vos goûts (positif : vous aimez ; négatif : vous n'aimez pas). */
    fun signal(f: Fic): Double {
        var s = 0.0
        if (Shelves.FAVORITES in f.shelves) s += 3.0
        if (Shelves.DONE in f.shelves) s += 1.5
        if (Shelves.READING in f.shelves) s += 1.2
        if (Shelves.CLAUDE in f.shelves) s += 1.0
        if (Shelves.TO_READ in f.shelves && s == 0.0) s += 0.4
        if (f.note >= 0) s += (f.note - 5) / 2.0
        val total = f.totalChapters
        if (total > 0 && f.maxChapterRead >= 0) s += 1.5 * min(1.0, (f.maxChapterRead + 1).toDouble() / total)
        if (f.secondsRead > 0) s += ln(1.0 + f.secondsRead / 3600.0)
        if (Shelves.ABANDONED in f.shelves) s -= 2.5 + (if (f.note in 0..3) 1.0 else 0.0)
        return s
    }

    data class Profile(val weights: Map<String, Double>, val norm: Double, val ficCount: Int) {
        val isEmpty get() = ficCount == 0 || weights.isEmpty()

        fun top(prefix: String, n: Int): List<Pair<String, Double>> =
            weights.entries.filter { it.key.startsWith(prefix) && it.value > 0 }.sortedByDescending { it.value }
                .take(n).map { it.key.removePrefix(prefix) to it.value }
    }

    fun profile(fics: List<Fic> = Library.fics.value): Profile {
        val w = HashMap<String, Double>()
        var count = 0
        for (f in fics) {
            val s = signal(f)
            if (s == 0.0) continue
            count++
            val t = terms(f)
            val len = sqrt(t.values.sumOf { it * it }).coerceAtLeast(1.0)
            for ((k, v) in t) w[k] = (w[k] ?: 0.0) + s * v / len
        }
        return Profile(w, sqrt(w.values.sumOf { it * it }), count)
    }

    /** Proximité d'une fic avec un profil (0 à 1, seules les ressemblances positives comptent). */
    fun affinity(p: Profile, f: Fic): Double {
        if (p.isEmpty || p.norm == 0.0) return 0.0
        val t = terms(f)
        val len = sqrt(t.values.sumOf { it * it })
        if (len == 0.0) return 0.0
        var dot = 0.0
        for ((k, v) in t) dot += (p.weights[k] ?: 0.0) * v
        return (dot / (p.norm * len)).coerceIn(-1.0, 1.0)
    }

    /** Ressemblance entre deux fics (0 à 1). */
    fun similarity(a: Fic, b: Fic): Double {
        val ta = terms(a); val tb = terms(b)
        var dot = 0.0
        for ((k, v) in ta) dot += v * (tb[k] ?: 0.0)
        val n = sqrt(ta.values.sumOf { it * it }) * sqrt(tb.values.sumOf { it * it })
        if (n == 0.0) return 0.0
        var s = dot / n
        // longueur et statut proches : petit bonus
        if (a.words > 0 && b.words > 0) {
            val r = min(a.words, b.words).toDouble() / maxOf(a.words, b.words)
            s += 0.05 * r
        }
        return s.coerceIn(0.0, 1.0)
    }

    private fun pretty(k: String): String = k.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /** Pourquoi cette fic est proposée (« Naruto · self-insert · un auteur que vous lisez »). */
    fun explain(p: Profile, f: Fic, max: Int = 3): String {
        val t = terms(f)
        val common = t.keys.mapNotNull { k -> p.weights[k]?.takeIf { it > 0 }?.let { k to it * t[k]!! } }
            .sortedByDescending { it.second }.map { it.first }
        val parts = mutableListOf<String>()
        for (k in common) {
            val label = when {
                k.startsWith("f:") -> pretty(k.removePrefix("f:"))
                k.startsWith("t:") -> FicType.of(k.removePrefix("t:"))?.label
                k.startsWith("c:") -> pretty(k.removePrefix("c:"))
                k.startsWith("a:") -> "un auteur que vous lisez"
                k.startsWith("g:") -> pretty(k.removePrefix("g:"))
                else -> null
            } ?: continue
            if (label !in parts) parts += label
            if (parts.size >= max) break
        }
        return parts.joinToString(" · ")
    }
}

/** Une proposition : la fic, la note globale, le score « Prometteuse » et la raison. */
data class Rec(val fic: Fic, val score: Int, val promising: Int, val why: String) {
    fun toJson(): JSONObject = JSONObject().put("f", fic.toJson()).put("s", score).put("p", promising).put("w", why)

    companion object {
        fun fromJson(o: JSONObject) = Rec(Fic.fromJson(o.getJSONObject("f")), o.optInt("s"), o.optInt("p"), o.optString("w"))
    }
}

/**
 * Onglets « Pour vous » et « Nouveautés », et « Trouver une fic similaire ».
 * Les propositions sont gardées sur le téléphone et rafraîchies au plus une fois par jour, ou à la demande.
 */
object Discover {
    data class State(
        val items: List<Rec> = emptyList(),
        val updatedAt: Long = 0,
        val running: Boolean = false,
        val progress: String = "",
        val message: String? = null,
        val notes: List<SiteNote> = emptyList()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private var forYouJob: Job? = null
    private var newsJob: Job? = null

    private val _forYou = MutableStateFlow(State())
    val forYou: StateFlow<State> = _forYou.asStateFlow()
    private val _news = MutableStateFlow(State())
    val news: StateFlow<State> = _news.asStateFlow()

    fun init(root: File) {
        dir = root
        _forYou.value = load("pour_vous.json")
        _news.value = load("nouveautes.json")
    }

    private fun load(name: String): State = runCatching {
        val o = JSONObject(File(dir, name).readText())
        val a = o.getJSONArray("items")
        State(List(a.length()) { Rec.fromJson(a.getJSONObject(it)) }, o.optLong("at"))
    }.getOrDefault(State())

    private fun save(name: String, s: State) = runCatching {
        val a = JSONArray(); s.items.forEach { a.put(it.toJson()) }
        File(dir, name).writeText(JSONObject().put("at", s.updatedAt).put("items", a).toString())
    }

    private const val DAY = 24L * 3600 * 1000

    fun refreshIfStale() {
        if (System.currentTimeMillis() - _forYou.value.updatedAt > DAY && !Taste.profile().isEmpty) refreshForYou()
    }

    /** Fics déjà connues : dans la bibliothèque, ou sous une ancienne adresse fusionnée. */
    private fun known(f: Fic): Boolean = Library.contains(f.id) || Library.findByUrl(f.url) != null

    private fun rank(cands: Collection<Fic>, p: Taste.Profile, now: Long, limit: Int): List<Rec> =
        JunkFilter.apply(cands.filterNot { known(it) }) { it }
            .map { f ->
                val prom = Scoring.score(f, now)
                val aff = (Taste.affinity(p, f) / 0.45).coerceIn(0.0, 1.0)
                Rec(f, (aff * 62 + prom * 0.38).toInt(), prom, Taste.explain(p, f))
            }
            .sortedByDescending { it.score }.take(limit)

    /** Recherches de départ pour un fandom (les plus suivies, puis les récentes qui démarrent fort). */
    private fun seedQueries(p: Taste.Profile, news: Boolean): List<SearchQuery> {
        val lib = Library.fics.value
        val sitesUsed = lib.map { it.site }.toSet().ifEmpty { setOf(Site.FFN, Site.SB) }
        val fandoms = (Prefs.pinnedFandoms + p.top("f:", 4).map { it.first }).distinctBy { it.lowercase() }.take(if (news) 6 else 4)
        val types = p.top("t:", 2).mapNotNull { FicType.of(it.first) }
        val out = mutableListOf<SearchQuery>()
        for (fd in fandoms) {
            val fandomName = lib.flatMap { it.fandoms }.firstOrNull { it.lowercase() == fd.lowercase() } ?: fd
            val chars = lib.filter { f -> f.fandoms.any { it.equals(fandomName, true) } }.flatMap { it.characters }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            val sites = sitesUsed.intersect(setOf(Site.FFN, Site.SB, Site.WN))
            if (news) {
                out += SearchQuery(sites = sites, fandom = fandomName, newOnly = true, sort = SearchSort.PUBLISHED)
            } else {
                out += SearchQuery(sites = sites - Site.WN, fandom = fandomName, sort = SearchSort.POPULAR, minWords = 20_000)
                chars.firstOrNull()?.let { c -> out += SearchQuery(sites = setOf(Site.FFN), fandom = fandomName, characters = listOf(c), sort = SearchSort.POPULAR) }
                out += SearchQuery(sites = setOf(Site.FFN), fandom = fandomName, updatedWithinDays = 30, sort = SearchSort.UPDATED)
                if (Site.WN in sitesUsed) out += SearchQuery(sites = setOf(Site.WN), fandom = fandomName)
            }
        }
        if (!news && Site.SB in sitesUsed) for (t in types) if (t.sbTag != null) out += SearchQuery(sites = setOf(Site.SB), include = setOf(t))
        if (news && Site.SB in sitesUsed) out += SearchQuery(sites = setOf(Site.SB), newOnly = true, sort = SearchSort.PUBLISHED)
        return out
    }

    fun refreshForYou() {
        if (forYouJob?.isActive == true) return
        forYouJob = scope.launch {
            val p = Taste.profile()
            if (p.isEmpty) {
                _forYou.value = State(message = "Ajoutez, lisez ou notez quelques fics : les propositions apparaîtront ici.")
                return@launch
            }
            val notes = mutableListOf<SiteNote>()
            val qs = seedQueries(p, news = false)
            val cands = LinkedHashMap<String, Fic>()
            qs.forEachIndexed { i, q ->
                _forYou.value = _forYou.value.copy(running = true, progress = "Recherche ${i + 1}/${qs.size} : ${q.describe()}", message = null)
                runCatching { Search.collect(q, 1, notes).forEach { cands.putIfAbsent(it.id, it) } }
            }
            val now = System.currentTimeMillis()
            val items = rank(cands.values, p, now, 80)
            val s = State(items = items.ifEmpty { _forYou.value.items }, updatedAt = now, notes = notes,
                message = if (items.isEmpty()) "Aucune proposition pour le moment : les sites n'ont pas répondu ou tout a été filtré." else null)
            _forYou.value = s
            if (items.isNotEmpty()) save("pour_vous.json", s)
        }
    }

    fun refreshNews() {
        if (newsJob?.isActive == true) return
        newsJob = scope.launch {
            val p = Taste.profile()
            if (p.isEmpty && Prefs.pinnedFandoms.isEmpty()) {
                _news.value = State(message = "Épinglez des fandoms ou ajoutez des fics : leurs nouveautés apparaîtront ici.")
                return@launch
            }
            val notes = mutableListOf<SiteNote>()
            val qs = seedQueries(p, news = true)
            val cands = LinkedHashMap<String, Fic>()
            qs.forEachIndexed { i, q ->
                _news.value = _news.value.copy(running = true, progress = "Nouveautés ${i + 1}/${qs.size} : ${q.fandom.ifBlank { "SpaceBattles" }}", message = null)
                runCatching { Search.collect(q, 2, notes).forEach { cands.putIfAbsent(it.id, it) } }
            }
            val now = System.currentTimeMillis()
            val items = rank(cands.values, p, now, 100).sortedWith(compareByDescending<Rec> { Scoring.isPromising(it.promising) }.thenByDescending { it.fic.published })
            val s = State(items = items, updatedAt = now, notes = notes,
                message = if (items.isEmpty()) "Pas de nouveauté retenue pour vos fandoms ces 30 derniers jours." else null)
            _news.value = s
            save("nouveautes.json", s)
        }
    }

    /** « Trouver une fic similaire » : mêmes fandoms, personnages et types, puis tri par ressemblance. */
    suspend fun similar(base: Fic, onProgress: (String) -> Unit = {}): Pair<List<Rec>, List<SiteNote>> {
        val notes = mutableListOf<SiteNote>()
        val types = Types.detect(base)
        val qs = mutableListOf<SearchQuery>()
        val fandom = base.fandoms.firstOrNull().orEmpty()
        val sites = setOf(Site.FFN, Site.SB, Site.WN)
        if (fandom.isNotBlank()) {
            if (base.characters.isNotEmpty()) qs += SearchQuery(sites = setOf(Site.FFN), fandom = fandom, characters = base.characters.take(2), sort = SearchSort.POPULAR)
            qs += SearchQuery(sites = setOf(Site.FFN, Site.SB), fandom = fandom, sort = SearchSort.POPULAR)
            types.firstOrNull { it != FicType.CROSSOVER }?.let { t -> qs += SearchQuery(sites = sites, fandom = fandom, keywords = if (base.site == Site.FFN) "" else t.label, sort = SearchSort.POPULAR) }
            if (base.site == Site.WN) qs += SearchQuery(sites = setOf(Site.WN), fandom = fandom)
        }
        for (t in types.filter { it.sbTag != null && it != FicType.CROSSOVER }.take(2)) qs += SearchQuery(sites = setOf(Site.SB), include = setOf(t))
        if (qs.isEmpty()) Taste.keywords(base.summary, 3).takeIf { it.isNotEmpty() }?.let { qs += SearchQuery(sites = sites, keywords = it.joinToString(" ")) }
        val cands = LinkedHashMap<String, Fic>()
        qs.forEachIndexed { i, q ->
            onProgress("Recherche ${i + 1}/${qs.size} : ${q.describe()}")
            runCatching { Search.collect(q, 1, notes).forEach { cands.putIfAbsent(it.id, it) } }
        }
        cands.remove(base.id)
        base.altUrls.forEach { u -> cands.values.firstOrNull { it.url == u }?.let { cands.remove(it.id) } }
        val now = System.currentTimeMillis()
        val p = Taste.profile()
        val list = JunkFilter.apply(cands.values.toList()) { it }.map { f ->
            val sim = Taste.similarity(base, f)
            val prom = Scoring.score(f, now)
            val why = buildList {
                if (f.fandoms.any { a -> base.fandoms.any { it.equals(a, true) } }) add("même fandom")
                val common = f.characters.filter { c -> base.characters.any { it.equals(c, true) } }
                if (common.isNotEmpty()) add(common.take(2).joinToString(", "))
                Types.detect(f).intersect(types).take(2).forEach { add(it.label) }
                if (Library.contains(f.id)) add("déjà dans votre bibliothèque")
            }.joinToString(" · ")
            Rec(f, ((sim / 0.6).coerceIn(0.0, 1.0) * 65 + prom * 0.35 + if (!p.isEmpty) Taste.affinity(p, f) * 5 else 0.0).toInt(), prom, why)
        }.sortedByDescending { it.score }.take(60)
        return list to notes
    }
}
