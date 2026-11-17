package com.thousandlabs.ohara.sources

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Site
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/** Ce qu'on peut lire dans un lien : quelle histoire, et éventuellement quel chapitre. */
data class LinkInfo(
    val site: Site,
    val storyId: String,
    /** Chapitre visé, compté à partir de 0, si le lien le dit directement. */
    val chapterIndex: Int? = null,
    /** Identifiant du chapitre propre au site, à retrouver dans la liste (message SpaceBattles, chapitre WebNovel). */
    val chapterKey: String? = null
) {
    val ficId: String get() = Fic.makeId(site, storyId)
}

/** Un site de fanfictions. */
interface Source {
    val site: Site
    fun matches(host: String): Boolean
    fun parseLink(url: String): LinkInfo?
    fun storyUrl(storyId: String): String
    fun chapterUrl(fic: Fic, index: Int): String

    /** Fiche complète de l'histoire, avec la liste des chapitres. */
    suspend fun fetchInfo(storyId: String): Fic

    /** Texte d'un chapitre, nettoyé (HTML simple). */
    suspend fun fetchChapter(fic: Fic, index: Int): String

    /** Index du chapitre qui correspond à un identifiant propre au site. */
    fun indexOfKey(fic: Fic, key: String): Int? = fic.chapters.indexOfFirst { it.key == key }.takeIf { it >= 0 }
}

object Sources {
    val all: List<Source> = listOf(FanFictionNet, SpaceBattles, WebNovel)

    fun of(site: Site): Source = all.firstOrNull { it.site == site } ?: GenericSource

    fun forUrl(url: String): Source? {
        val h = runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
        return all.firstOrNull { it.matches(h) } ?: GenericSource.takeIf { GenericSource.matches(h) }
    }

    /** Analyse un lien collé ou partagé. */
    fun resolve(url: String): Pair<Source, LinkInfo>? {
        val clean = extractUrl(url) ?: return null
        val src = forUrl(clean) ?: return null
        val info = src.parseLink(clean) ?: return null
        return src to info
    }

    /** Retrouve une adresse dans un texte partagé (« Regardez cette fic : https://… »). */
    fun extractUrl(text: String): String? =
        Regex("https?://[^\\s\"'<>]+").find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
}

/** Nettoyage du texte des chapitres : on garde la mise en forme utile, rien d'autre. */
object Cleaner {
    private val safelist: Safelist = Safelist.relaxed()
        .removeTags("img", "a", "colgroup", "col", "caption", "dl", "dt", "dd", "cite", "code", "pre")
        .addTags("hr", "center", "s", "del", "span", "font")
        .addAttributes("p", "style", "align")
        .addAttributes("span", "style")
        .addAttributes("div", "style", "align")
        .addAttributes("font", "color")
        .addAttributes("td", "colspan")

    fun clean(el: Element): String = clean(el.html())

    fun clean(html: String): String {
        val cleaned = Jsoup.clean(html, "", safelist, Document.OutputSettings().prettyPrint(false))
        // on ne garde que l'alignement dans les styles : pas de couleurs ni de polices imposées
        return cleaned
            .replace(Regex("style=\"([^\"]*)\"")) { m ->
                val align = Regex("text-align:\\s*(center|right|left|justify)").find(m.groupValues[1])?.groupValues?.get(1)
                if (align != null) "style=\"text-align:$align\"" else ""
            }
            .replace(Regex("<p>\\s*(&nbsp;|\\s)*</p>"), "")
            .trim()
    }

    /** Texte brut d'un HTML, paragraphes séparés par une ligne vide. */
    private val RX_BLANK_LINES = Regex("\n{3,}")

    fun toText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("br").append("\\n")
        doc.select("p, div, h1, h2, h3, h4, li, blockquote, tr").append("\\n\\n")
        doc.select("hr").append("\\n* * *\\n\\n")
        return doc.text().replace("\\n", "\n")
            .lines().joinToString("\n") { it.trim() }
            .replace(RX_BLANK_LINES, "\n\n").trim()
    }
}

/** Petits outils de lecture des nombres et dates affichés par les sites. */
object Parse {
    /** « 12,345 » → 12345 ; « 45.2k » → 45200 ; « 1.2M » → 1200000. */
    fun number(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val t = s.trim().replace(" ", "").replace(" ", "")
        val m = Regex("([0-9][0-9,.]*)\\s*([kKmM]?)").find(t) ?: return 0
        val raw = m.groupValues[1]
        val mult = when (m.groupValues[2].lowercase()) { "k" -> 1_000.0; "m" -> 1_000_000.0; else -> 1.0 }
        return if (mult > 1.0) {
            ((raw.replace(",", ".").toDoubleOrNull() ?: 0.0) * mult).toLong()
        } else raw.replace(",", "").replace(".", "").toLongOrNull() ?: 0
    }

    fun int(s: String?): Int = number(s).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
