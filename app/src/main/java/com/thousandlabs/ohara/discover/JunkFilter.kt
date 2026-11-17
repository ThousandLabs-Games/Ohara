package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Filtre anti-déchets, toujours actif sur les résultats de recherche, les recommandations et les nouveautés
 * (jamais sur la bibliothèque : ce que vous avez ajouté reste à vous).
 *
 * - Harem et fics « react » : exclus d'office.
 * - Fics chinoises récentes : masquées à partir de l'année réglée ; les anciennes restent.
 * - System et transmigration : acceptés quand la fic est de bonne qualité (score « Prometteuse » suffisant).
 * - « Masquer ce genre », auteurs bloqués, fics masquées à la main.
 * - Liste blanche : une fic marquée « Toujours afficher » passe tous les filtres ; un auteur de confiance aussi,
 *   sauf pour le harem et les fics « react ».
 */
object JunkFilter {

    enum class Reason(val label: String) {
        HAREM("Harem"),
        REACT("Fic « react »"),
        CHINESE("Fic chinoise récente"),
        LOW_SYSTEM("System ou transmigration de faible qualité"),
        GENRE("Genre masqué"),
        AUTHOR("Auteur bloqué"),
        MANUAL("Masquée à la main")
    }

    data class Verdict(val hidden: Boolean, val reason: Reason? = null, val detail: String = "") {
        companion object { val OK = Verdict(false) }
    }

    // ---------- Réglages ----------

    /** Les fics chinoises publiées à partir de cette année sont masquées. */
    var chineseFromYear: Int get() = Prefs.getInt("jf_cn_year", 2019); set(v) = Prefs.putInt("jf_cn_year", v)
    /** Score minimum pour garder une fic System ou transmigration. */
    var systemMinScore: Int get() = Prefs.getInt("jf_sys_min", 60); set(v) = Prefs.putInt("jf_sys_min", v)

    var hiddenGenres: List<String> get() = Prefs.getList("jf_genres"); set(v) = Prefs.putList("jf_genres", v)
    var blockedAuthors: List<String> get() = Prefs.getList("jf_blocked"); set(v) = Prefs.putList("jf_blocked", v)
    var trustedAuthors: List<String> get() = Prefs.getList("jf_trusted"); set(v) = Prefs.putList("jf_trusted", v)
    var whitelist: List<String> get() = Prefs.getList("jf_white"); set(v) = Prefs.putList("jf_white", v)
    var manualHidden: List<String> get() = Prefs.getList("jf_manual"); set(v) = Prefs.putList("jf_manual", v)

    /** Clé d'auteur : « site:nom » (le même pseudo sur deux sites peut être deux personnes). */
    fun authorKey(f: Fic) = "${f.site.code}:${f.author.trim().lowercase()}"
    fun authorLabel(key: String): String {
        val site = Site.of(key.substringBefore(':'))
        return key.substringAfter(':') + " (" + site.short + ")"
    }

    fun isTrusted(f: Fic) = f.author.isNotBlank() && authorKey(f) in trustedAuthors
    fun isBlocked(f: Fic) = f.author.isNotBlank() && authorKey(f) in blockedAuthors

    fun blockAuthor(f: Fic) {
        val k = authorKey(f)
        blockedAuthors = (blockedAuthors - k) + k
        trustedAuthors = trustedAuthors - k
    }
    fun trustAuthor(f: Fic) {
        val k = authorKey(f)
        trustedAuthors = (trustedAuthors - k) + k
        blockedAuthors = blockedAuthors - k
    }
    fun forgetAuthor(key: String) {
        blockedAuthors = blockedAuthors - key
        trustedAuthors = trustedAuthors - key
    }

    fun addHiddenGenre(term: String) {
        val t = term.trim().lowercase()
        if (t.length >= 2 && t !in hiddenGenres) hiddenGenres = hiddenGenres + t
    }
    fun removeHiddenGenre(term: String) { hiddenGenres = hiddenGenres - term }

    fun alwaysShow(f: Fic) {
        whitelist = (whitelist - f.id) + f.id
        manualHidden = manualHidden - f.id
        HiddenLog.remove(f.id)
        val o = runCatching { JSONObject(Prefs.getString("jf_white_t", "{}")) }.getOrDefault(JSONObject())
        Prefs.putString("jf_white_t", o.put(f.id, f.title).toString())
    }
    fun unWhitelist(id: String) {
        whitelist = whitelist - id
        val o = runCatching { JSONObject(Prefs.getString("jf_white_t", "{}")) }.getOrDefault(JSONObject())
        o.remove(id); Prefs.putString("jf_white_t", o.toString())
    }
    /** Liste blanche lisible : (identifiant, titre). */
    fun whitelistEntries(): List<Pair<String, String>> {
        val o = runCatching { JSONObject(Prefs.getString("jf_white_t", "{}")) }.getOrDefault(JSONObject())
        return whitelist.map { it to o.optString(it, it) }
    }
    fun hideFic(f: Fic) {
        manualHidden = (manualHidden - f.id) + f.id
        whitelist = whitelist - f.id
        HiddenLog.record(f, Verdict(true, Reason.MANUAL))
    }

    // ---------- Détection ----------

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val HAREM = rx("\\bharem(s|ic)?\\b|\\bharem[- ]?(fic|story|route)|\\bmulti[- ]?(pairing|ship)s?\\b.{0,15}\\b(girls|wives|women)\\b|\\bpolygam\\w*|\\bwaifus\\b")
    private val NOT_HAREM = rx("\\b(no|not|non|anti|zero|without|never)[- ]?(a )?harem\\b|harem[: ]+(no|none|never)\\b|\\bno harems?\\b|\\bnot a harem\\b")

    private val REACT = rx(
        "\\breact(s|ing|ion)?\\s+(to|fic)\\b|\\breaction (fic|story)\\b|\\b(characters?|they|everyone|cast|class)\\s+(react|watch|read)s?\\b.{0,40}\\b(future|show|anime|series|movies?|books?|fics?|videos?|memories)\\b|" +
            "\\bwatch(es|ing)?\\s+(their|his|her|the)\\s+(future|anime|show|series|memories)\\b|\\bread(s|ing)?\\s+(their|the)\\s+(future|books)\\b|\\bwatching the (show|anime|series|movie)\\b"
    )
    /** Dans un titre, « reacts » ou « reacting » suffit (« Naruto Characters Reacting to… »). */
    private val REACT_TITLE = rx("\\breact(s|ing|ion)\\b")

    /** Indices d'une fanfiction de style web-roman chinois (souvent traduite ou écrite par des auteurs chinois). */
    private val CN_STRONG = rx(
        "golden finger|sign[- ]?in system|signing in|check[- ]?in system|face[- ]?slap\\w*|cultivat(e|ion|or)|dao heart|young master|" +
            "\\bdantian\\b|\\bqi\\b refin|foundation establishment|nascent soul|spirit stones?|dog food|seeking death|courting death|" +
            "\\bmc\\b|\\bthe protagonist\\b|\\bthis seat\\b|\\bthis king\\b|\\bgolden core\\b|heavenly dao|\\bbreakthrough\\b|" +
            "translated from chinese|chinese (novel|author|translation)|\\bmtl\\b|machine translat|raws?\\b|\\bqidian\\b|\\bcp\\b chinese"
    )
    private val CN_TITLE = rx(
        "^\\s*[A-Z][\\w' .&-]{1,30}:\\s*(i |i'm |starting|start(s)? with|begin|beginning|opening|reborn|my |the strongest|invincible|become|became|summon|sign|daily|awaken)|" +
            "^\\s*(starting|start) (as|with|from)\\b|\\b(i can|i have|i became|i am) .{0,30}(infinite|strongest|system|invincible|god|emperor|sss)|\\bsss[- ]rank\\b|\\bten thousand\\b|\\binvincible\\b"
    )
    private val HAN = Regex("[\\u4E00-\\u9FFF]")

    /** Probabilité grossière (0-10) que la fic soit une fanfiction chinoise. */
    fun chineseSignals(f: Fic): Int {
        var s = 0
        val h = f.title + " \n " + f.summary + " \n " + f.tags.joinToString(" ")
        if (HAN.containsMatchIn(h)) s += 6
        if (f.language.equals("Chinese", true)) s += 6
        s += CN_STRONG.findAll(h).take(3).count() * 2
        if (CN_TITLE.containsMatchIn(f.title)) s += 3
        if (f.site == Site.WN) s += 2
        return s
    }

    private fun yearOf(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)

    private fun termMatches(term: String, f: Fic, hay: String): Boolean {
        if (f.tags.any { it.equals(term, true) } || f.genres.any { it.equals(term, true) }) return true
        if (Types.detect(f).any { it.label.equals(term, true) || it.code == term }) return true
        return Regex("\\b" + Regex.escape(term) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(hay)
    }

    fun check(f: Fic): Verdict {
        if (f.id in whitelist) return Verdict.OK
        if (f.id in manualHidden) return Verdict(true, Reason.MANUAL)
        if (isBlocked(f)) return Verdict(true, Reason.AUTHOR, f.author)
        val hay = Types.haystack(f)
        if (HAREM.containsMatchIn(hay) && !NOT_HAREM.containsMatchIn(hay)) return Verdict(true, Reason.HAREM)
        if (REACT.containsMatchIn(f.title) || REACT_TITLE.containsMatchIn(f.title) || REACT.containsMatchIn(f.summary.take(400)) || f.tags.any { it.contains("react", true) }) return Verdict(true, Reason.REACT)
        hiddenGenres.firstOrNull { termMatches(it, f, hay) }?.let { return Verdict(true, Reason.GENRE, it) }
        if (isTrusted(f)) return Verdict.OK
        val start = if (f.published > 0) f.published else f.updated
        if (start > 0 && yearOf(start) >= chineseFromYear && chineseSignals(f) >= 5) return Verdict(true, Reason.CHINESE)
        val types = Types.detect(f)
        if (FicType.SYSTEM in types || FicType.TRANSMIGRATION in types) {
            val sc = Scoring.score(f)
            if (sc < systemMinScore) return Verdict(true, Reason.LOW_SYSTEM, "score $sc")
        }
        return Verdict.OK
    }

    /** Applique le filtre à une liste ; les fics écartées sont notées dans le dossier « Masquées ». */
    fun <T> apply(items: List<T>, ficOf: (T) -> Fic): List<T> = items.filter { item ->
        val f = ficOf(item)
        if (Library.contains(f.id)) return@filter true
        val v = check(f)
        if (v.hidden) HiddenLog.record(f, v)
        !v.hidden
    }
}

/**
 * Dossier « Masquées » : les dernières fics écartées par le filtre, avec la raison.
 * De là, « Toujours afficher » les met sur la liste blanche.
 */
object HiddenLog {
    data class Entry(val fic: Fic, val reason: JunkFilter.Reason, val detail: String, val at: Long)

    private lateinit var file: File
    private val entries = LinkedHashMap<String, Entry>()
    private const val MAX = 400

    fun init(root: File) {
        file = File(root, "masquees.json")
        runCatching {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val f = Fic.fromJson(o.getJSONObject("f"))
                val r = runCatching { JunkFilter.Reason.valueOf(o.optString("r")) }.getOrDefault(JunkFilter.Reason.MANUAL)
                entries[f.id] = Entry(f, r, o.optString("d"), o.optLong("a"))
            }
        }
    }

    @Synchronized
    fun record(f: Fic, v: JunkFilter.Verdict) {
        val r = v.reason ?: return
        entries.remove(f.id)
        entries[f.id] = Entry(f.copy(chapters = emptyList()), r, v.detail, System.currentTimeMillis())
        while (entries.size > MAX) entries.remove(entries.keys.first())
        save()
    }

    @Synchronized
    fun remove(id: String) { if (entries.remove(id) != null) save() }

    @Synchronized
    fun clear() { entries.clear(); save() }

    @Synchronized
    fun list(): List<Entry> = entries.values.reversed()

    private var pending = false
    private fun save() {
        if (pending) return
        pending = true
        Thread {
            Thread.sleep(800)
            val json = synchronized(this) {
                pending = false
                JSONArray().also { a ->
                    entries.values.forEach { e -> a.put(JSONObject().put("f", e.fic.toJson()).put("r", e.reason.name).put("d", e.detail).put("a", e.at)) }
                }.toString()
            }
            runCatching { file.writeText(json) }
        }.start()
    }
}
