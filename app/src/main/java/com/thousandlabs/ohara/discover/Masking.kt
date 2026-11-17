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

    fun rules(): List<Rule> = runCatching {
        val a = JSONArray(Prefs.getString("mask_rules", "[]"))
        List(a.length()) { Rule.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(list: List<Rule>) = Prefs.putString("mask_rules", JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())

    fun add(phrase: String, scope: Scope, fic: Fic) {
        val p = normalize(phrase).take(300)
        if (p.length < 4) return
        val target = when (scope) { Scope.FIC -> fic.id; Scope.AUTHOR -> authorKey(fic); Scope.ALL -> "" }
        save(rules().filterNot { it.phrase == p && it.scope == scope && it.target == target } + Rule(p, scope, target))
    }

    fun remove(rule: Rule) = save(rules().filterNot { it.phrase == rule.phrase && it.scope == rule.scope && it.target == rule.target })

    /** Texte comparable : minuscules, sans ponctuation, chiffres neutralisés (« 10 chapters ahead » = « 12 chapters ahead »). */
    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("[\u2018\u2019\u201c\u201d]"), "'")
        .replace(Regex("\\d+"), "#")
        .replace(Regex("[^\\p{L}#']+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun words(s: String) = s.split(' ').filter { it.length >= 3 }.toSet()

    /** Le paragraphe correspond-il à la règle ? Texte contenu, ou au moins 75 % des mots de la règle présents. */
    private fun matches(n: String, phrase: String): Boolean {
        if (n.contains(phrase)) return true
        val pw = words(phrase)
        if (pw.size < 4) return false
        val nw = words(n)
        return pw.count { it in nw } >= (pw.size * 0.75).toInt().coerceAtLeast(3)
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
    private val AUTHOR_NOTE = Regex("^\\s*(a/n|an:|a\\.n\\.|author'?s'? notes?|note de l'auteur|n/a)\\b", RegexOption.IGNORE_CASE)

    data class Result(val html: String, val hidden: Int)

    /** Transforme les textes découpés par des sauts de ligne (SpaceBattles…) en vrais paragraphes. */
    fun paragraphs(html: String): String {
        val pCount = Regex("<p[\\s>]", RegexOption.IGNORE_CASE).findAll(html).count()
        val brCount = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE).findAll(html).count()
        if (pCount >= 3 || brCount < 4) return html
        return html.split(Regex("(\\s*<br\\s*/?>\\s*){2,}", RegexOption.IGNORE_CASE))
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                val t = part.trim()
                if (Regex("^<(p|div|blockquote|h[1-6]|ul|ol|table|hr|center)[\\s>/]", RegexOption.IGNORE_CASE).containsMatchIn(t)) t
                else "<p>$t</p>"
            }
    }

    private fun blocks(doc: Document): List<Element> =
        doc.body().select("p, li, blockquote, h1, h2, h3, h4, center, div, td").filter { el ->
            el.children().none { c -> c.tagName() in BLOCK_TAGS }
        }

    private val BLOCK_TAGS = setOf("p", "div", "li", "blockquote", "ul", "ol", "table", "h1", "h2", "h3", "h4", "center")

    /** Pourquoi ce passage serait masqué (null s'il ne l'est pas). */
    private fun reason(text: String, fic: Fic, rules: List<Rule>): String? {
        val n = normalize(text)
        if (n.isEmpty()) return null
        if (rules.any { it.applies(fic) && matches(n, normalize(it.phrase)) }) return "règle"
        if (autoPromo && PROMO.containsMatchIn(text) && text.length < 900) return "promo"
        if (authorNotes && AUTHOR_NOTE.containsMatchIn(text)) return "note"
        return null
    }

    /**
     * Pour le lecteur : les passages masqués sont repliés (on peut les rouvrir d'un geste).
     */
    fun applyToHtml(fic: Fic, html: String, remove: Boolean = false): Result {
        val doc = Jsoup.parseBodyFragment(paragraphs(html))
        val rules = rules()
        val list = blocks(doc)
        val flagged = BooleanArray(list.size)
        list.forEachIndexed { i, el -> if (reason(el.text(), fic, rules) != null) flagged[i] = true }
        // Notes d'auteur sur plusieurs paragraphes : d'un « A/N » en début de chapitre jusqu'au séparateur,
        // et d'un « A/N » en fin de chapitre jusqu'à la fin.
        if (authorNotes && list.isNotEmpty()) {
            val sep = Regex("^\\s*([-*_=~x.o]\\s*){3,}\\s*$|^\\s*(line ?break|scene ?break)\\s*$", RegexOption.IGNORE_CASE)
            val head = list.take(15).indexOfFirst { AUTHOR_NOTE.containsMatchIn(it.text()) }
            if (head in 0..4) {
                val end = (head + 1 until minOf(list.size, head + 12)).firstOrNull { sep.matches(list[it].text()) || list[it].tagName() == "hr" }
                if (end != null) for (i in head until end) flagged[i] = true
            }
            val from = list.size - 12
            val tail = (maxOf(0, from) until list.size).firstOrNull { AUTHOR_NOTE.containsMatchIn(list[it].text()) }
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
