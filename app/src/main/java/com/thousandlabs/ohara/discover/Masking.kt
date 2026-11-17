package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Passages masqués dans le lecteur : promos Patreon, appels aux dons, notes d'auteur…
 * Une règle retient une phrase et la cache dans tous les chapitres d'une fic, de tout un auteur, ou partout.
 */
object Masking {
    enum class Scope(val label: String) { FIC("Cette fic"), AUTHOR("Toutes les fics de cet auteur"), ALL("Toutes les fics") }

    data class Rule(val phrase: String, val scope: Scope, val target: String, val createdAt: Long = System.currentTimeMillis()) {
        fun toJson() = JSONObject().put("p", phrase).put("s", scope.name).put("t", target).put("a", createdAt)

        fun applies(fic: Fic) = when (scope) {
            Scope.ALL -> true
            Scope.FIC -> target == fic.id
            Scope.AUTHOR -> target == authorKey(fic)
        }

        companion object {
            fun fromJson(o: JSONObject) = Rule(
                o.optString("p"), runCatching { Scope.valueOf(o.optString("s")) }.getOrDefault(Scope.FIC), o.optString("t"), o.optLong("a")
            )
        }
    }

    fun authorKey(fic: Fic) = "${fic.site.code}:${fic.author.lowercase()}"

    var autoPromo: Boolean get() = Prefs.getBool("mask_promo", true); set(v) = Prefs.putBool("mask_promo", v)
    var authorNotes: Boolean get() = Prefs.getBool("mask_an", true); set(v) = Prefs.putBool("mask_an", v)
    var removeInExports: Boolean get() = Prefs.getBool("mask_export", true); set(v) = Prefs.putBool("mask_export", v)
    /** Garder un petit repère « passage masqué » à la place (sinon le passage disparaît complètement). */
    var showMarker: Boolean get() = Prefs.getBool("mask_marker", false); set(v) = Prefs.putBool("mask_marker", v)

    /**
     * Correctif : avant la v1.38, un appui long sur un seul mot pouvait enregistrer comme règle le début de tout le chapitre
     * (300 caractères). Ces règles trop longues sont retirées une fois.
     */
    fun repairOnce() {
        if (Prefs.getBool("mask_fix_138", false)) return
        Prefs.putBool("mask_fix_138", true)
        val all = rules()
        val kept = all.filter { it.phrase.length < 280 }
        if (kept.size != all.size) save(kept)
    }

    fun rules(): List<Rule> = runCatching {
        val a = JSONArray(Prefs.getString("mask_rules", "[]"))
        List(a.length()) { Rule.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(list: List<Rule>) = Prefs.putString("mask_rules", JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())

    fun add(phrase: String, scope: Scope, fic: Fic): Rule? {
        val p = normalize(phrase).take(300)
        if (p.length < 4) return null
        val target = when (scope) { Scope.FIC -> fic.id; Scope.AUTHOR -> authorKey(fic); Scope.ALL -> "" }
        val r = Rule(p, scope, target)
        save(rules().filterNot { it.phrase == p && it.scope == scope && it.target == target } + r)
        return r
    }

    fun remove(rule: Rule) = save(rules().filterNot { it.phrase == rule.phrase && it.scope == rule.scope && it.target == rule.target })

    /** Texte comparable : minuscules, sans ponctuation, chiffres neutralisés (« 10 chapters ahead » = « 12 chapters ahead »). */
    // Expressions pr\u00e9par\u00e9es une seule fois : les recr\u00e9er pour chaque paragraphe rendait l'envoi \u00e0 Claude
    // et les exports tr\u00e8s lents sur les longues fics.
    private val RX_QUOTES = Regex("[\u2018\u2019\u201c\u201d]")
    private val RX_DIGITS = Regex("\\d+")
    private val RX_NON_WORD = Regex("[^\\p{L}#']+")

    private fun normalize(s: String) = s.lowercase()
        .replace(RX_QUOTES, "'")
        .replace(RX_DIGITS, "#")
        .replace(RX_NON_WORD, " ")
        .trim()

    /** R\u00e8gle pr\u00eate \u00e0 comparer : phrase normalis\u00e9e et mots porteurs, calcul\u00e9s une fois par chapitre. */
    private class Prepared(val phrase: String, val words: Set<String>)

    private fun prepare(rules: List<Rule>, fic: Fic): List<Prepared> =
        rules.filter { it.applies(fic) }.map { normalize(it.phrase) }.filter { it.isNotEmpty() }.distinct().map { Prepared(it, words(it)) }

    /** Mots courants ignorés pour la ressemblance : sinon une phrase de promo « ressemble » à n'importe quel paragraphe. */
    private val STOP = ("the and you your for with that this was were are not but have has had from they them their there then than what when where which " +
        "will would could should into onto about after before again just only also very much more most some such over under been being its it's his her him " +
        "she he our out can all any one two who why how did does doing done get got let make made said says say like know see saw look looked back now well " +
        "even still way here yes yeah que les des une est pas pour dans par sur qui avec mais vous nous elle ils son ses aux sont été chapter chapters").split(' ').toSet()

    private fun words(s: String) = s.split(' ').filter { it.length >= 4 && it !in STOP }.toSet()

    /**
     * Le passage correspond-il à la règle ? Il doit avoir à peu près la taille de la règle (une phrase ne fait jamais
     * masquer tout un chapitre), puis contenir la règle, ou au moins 80 % de ses mots porteurs (4 au minimum).
     */
    private fun matches(n: String, nw: Lazy<Set<String>>, rule: Prepared): Boolean {
        val phrase = rule.phrase
        if (n.length > maxOf(phrase.length * 2.5, phrase.length + 160.0)) return false
        if (n.contains(phrase)) return true
        if (n.length < phrase.length * 0.6 || n.length > phrase.length * 1.8) return false
        val pw = rule.words
        if (pw.size < 4) return false
        return pw.count { it in nw.value } >= maxOf(4, Math.ceil(pw.size * 0.8).toInt())
    }

    private val PROMO = Regex(
        "patreon|ko-?fi\\.com|\\bko-?fi\\b|buymeacoffee|buy me a coffee|subscribestar|discord\\.gg|join (my|our|the) discord|" +
            "advanced? chapters?|chapters? ahead|early access|chapters? in advance|support me (on|at)|" +
            "\\btip jar\\b|paypal\\.me|\\bpower stones?\\b|\\bgolden tickets?\\b|vote with your|p\\s*a\\s*t\\s*r\\s*e\\s*o\\s*n|pat-?reon|" +
            "\\b#? ?chapters? (ahead|in advance)|\\+ ?\\d+ chapters|read (up to )?\\d+ chapters|add (it|this|the book|this book) to (your )?(library|collection)|" +
            "don'?t forget to (review|vote|comment|like)|please (review|vote|comment)|leave a (review|comment)|reviews? (are|is) appreciated|" +
            "\\bcheck out my\\b|my other (stories|fics|works)|\\bwebnovel\\.com/profile",
        RegexOption.IGNORE_CASE
    )
    private val SEPARATOR = Regex("^\\s*([-*_=~x.o]\\s*){3,}\\s*$|^\\s*(line ?break|scene ?break)\\s*$", RegexOption.IGNORE_CASE)
    private val AUTHOR_NOTE = Regex("^\\s*(a/n|an:|a\\.n\\.|author'?s'? notes?|note de l'auteur|n/a)\\b", RegexOption.IGNORE_CASE)

    data class Result(val html: String, val hidden: Int)

    /** Transforme les textes découpés par des sauts de ligne (SpaceBattles…) en vrais paragraphes. */
    private val RX_P = Regex("<p[\\s>]", RegexOption.IGNORE_CASE)
    private val RX_BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val RX_BR_RUN = Regex("(?:<br\\s*/?>\\s*){2,}", RegexOption.IGNORE_CASE)
    private val RX_BLOCK_START = Regex("^<(p|div|blockquote|h[1-6]|ul|ol|table|hr|center)[\\s>/]", RegexOption.IGNORE_CASE)

    fun paragraphs(html: String): String {
        val pCount = RX_P.findAll(html).take(3).count()
        if (pCount >= 3) return html
        val brCount = RX_BR.findAll(html).take(4).count()
        if (brCount < 4) return html
        return html.split(RX_BR_RUN)
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                val t = part.trim()
                if (RX_BLOCK_START.containsMatchIn(t)) t else "<p>$t</p>"
            }
    }

    private fun blocks(doc: Document): List<Element> =
        doc.body().select("p, li, blockquote, h1, h2, h3, h4, center, div, td").filter { el ->
            el.children().none { c -> c.tagName() in BLOCK_TAGS }
        }

    private val BLOCK_TAGS = setOf("p", "div", "li", "blockquote", "ul", "ol", "table", "h1", "h2", "h3", "h4", "center")

    /** Pourquoi ce passage serait masqué (null s'il ne l'est pas). */
    private fun reason(text: String, rules: List<Prepared>): String? {
        if (text.isBlank()) return null
        if (rules.isNotEmpty()) {
            val n = normalize(text)
            if (n.isEmpty()) return null
            val nw = lazy(LazyThreadSafetyMode.NONE) { words(n) }
            if (rules.any { matches(n, nw, it) }) return "règle"
        }
        if (autoPromo && PROMO.containsMatchIn(text) && text.length < 900) return "promo"
        if (authorNotes && AUTHOR_NOTE.containsMatchIn(text)) return "note"
        return null
    }

    /**
     * Pour le lecteur : les passages masqués sont repliés (on peut les rouvrir d'un geste).
     */
    fun applyToHtml(fic: Fic, html: String, remove: Boolean = false): Result {
        val doc = Jsoup.parseBodyFragment(paragraphs(html))
        val rules = prepare(rules(), fic)
        val list = blocks(doc)
        val texts = list.map { it.text() }
        val flagged = BooleanArray(list.size)
        val byRule = BooleanArray(list.size)
        texts.forEachIndexed { i, t -> reason(t, rules)?.let { flagged[i] = true; byRule[i] = it == "règle" } }
        // Garde-fou : des règles qui masqueraient plus d'un tiers du chapitre sont ignorées pour ce chapitre.
        val total = texts.sumOf { it.length }
        val ruled = list.indices.filter { byRule[it] }.sumOf { texts[it].length }
        if (total > 1500 && ruled > total * 0.34) list.indices.filter { byRule[it] }.forEach { flagged[it] = false }
        // Notes d'auteur sur plusieurs paragraphes : d'un « A/N » en début de chapitre jusqu'au séparateur,
        // et d'un « A/N » en fin de chapitre jusqu'à la fin.
        if (authorNotes && list.isNotEmpty()) {
            val head = texts.take(15).indexOfFirst { AUTHOR_NOTE.containsMatchIn(it) }
            if (head in 0..4) {
                val end = (head + 1 until minOf(list.size, head + 12)).firstOrNull { SEPARATOR.matches(texts[it]) || list[it].tagName() == "hr" }
                if (end != null) for (i in head until end) flagged[i] = true
            }
            val from = list.size - 12
            val tail = (maxOf(0, from) until list.size).firstOrNull { AUTHOR_NOTE.containsMatchIn(texts[it]) }
            if (tail != null) for (i in tail until list.size) flagged[i] = true
        }
        var hidden = 0
        list.forEachIndexed { i, el ->
            if (!flagged[i]) return@forEachIndexed
            hidden++
            if (remove) el.remove()
            else {
                val wrapper = Element("div").addClass("ohara-masque").addClass(if (showMarker) "repere" else "cache")
                el.replaceWith(wrapper)
                wrapper.appendChild(el)
            }
        }
        // séparateurs devenus orphelins entre deux passages masqués
        doc.select("hr").forEach { hr ->
            val prev = hr.previousElementSibling(); val next = hr.nextElementSibling()
            if ((prev == null || prev.hasClass("ohara-masque")) && (next == null || next.hasClass("ohara-masque"))) {
                if (remove) hr.remove() else hr.addClass("ohara-masque").addClass(if (showMarker) "repere" else "cache")
            }
        }
        return Result(doc.body().html(), hidden)
    }
}
