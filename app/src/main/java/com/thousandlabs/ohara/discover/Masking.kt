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
    var authorNotes: Boolean get() = Prefs.getBool("mask_an", false); set(v) = Prefs.putBool("mask_an", v)
    var removeInExports: Boolean get() = Prefs.getBool("mask_export", true); set(v) = Prefs.putBool("mask_export", v)

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

    private fun normalize(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()

    private val PROMO = Regex(
        "patreon|ko-?fi\\.com|\\bko-?fi\\b|buymeacoffee|buy me a coffee|subscribestar|discord\\.gg|join (my|our|the) discord|" +
            "advanced? chapters?|chapters? ahead|early access|chapters? in advance|support me (on|at)|" +
            "\\btip jar\\b|paypal\\.me|\\bpower stones?\\b|\\bgolden tickets?\\b|vote with your",
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
        if (rules.any { it.applies(fic) && n.contains(it.phrase) }) return "règle"
        if (autoPromo && PROMO.containsMatchIn(n) && n.length < 900) return "promo"
        if (authorNotes && AUTHOR_NOTE.containsMatchIn(n)) return "note"
        return null
    }

    /**
     * Pour le lecteur : les passages masqués sont repliés (on peut les rouvrir d'un geste).
     */
    fun applyToHtml(fic: Fic, html: String, remove: Boolean = false): Result {
        val doc = Jsoup.parseBodyFragment(paragraphs(html))
        val rules = rules()
        var hidden = 0
        for (el in blocks(doc)) {
            val why = reason(el.text(), fic, rules) ?: continue
            hidden++
            if (remove) el.remove()
            else {
                val wrapper = Element("div").addClass("ohara-masque").attr("data-raison", why)
                el.replaceWith(wrapper)
                wrapper.appendChild(el)
            }
        }
        // un passage masqué peut couvrir tout un message de promo : on retire les séparateurs devenus orphelins
        return Result(doc.body().html(), hidden)
    }
}
