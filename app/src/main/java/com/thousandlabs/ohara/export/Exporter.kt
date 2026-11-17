package com.thousandlabs.ohara.export

import android.content.Context
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.discover.Types
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.sources.Cleaner
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exports d'une fic : livre EPUB, texte brut, et « Pack Claude » prêt à transmettre. */
object Exporter {
    enum class Format(val ext: String, val mime: String, val label: String) {
        EPUB("epub", "application/epub+zip", "Livre EPUB"),
        TXT("txt", "text/plain", "Texte (TXT)"),
        PACK("zip", "application/zip", "Pack Claude")
    }

    /** Chapitres à exporter, comptés à partir de 0, bornes comprises. */
    data class Range(val from: Int, val to: Int)

    fun fullRange(f: Fic) = Range(0, (f.totalChapters - 1).coerceAtLeast(0))

    fun fileName(f: Fic, format: Format, range: Range? = null): String {
        val base = f.title.ifBlank { "fic" }.replace(Regex("[\\\\/:*?\"<>|]+"), " ").replace(Regex("\\s+"), " ").trim().take(80)
        val part = if (range != null && (range.from > 0 || range.to < f.totalChapters - 1)) " (ch. ${range.from + 1}-${range.to + 1})" else ""
        val suffix = if (format == Format.PACK) " - Pack Claude" else ""
        return "$base$part$suffix.${format.ext}"
    }

    /** Télécharge les chapitres manquants de l'intervalle (les chapitres verrouillés sont ignorés). */
    suspend fun ensureChapters(f: Fic, range: Range, onProgress: (Int, Int) -> Unit) {
        val missing = (range.from..range.to).filter { it in f.chapters.indices && !f.chapters[it].locked && !Library.hasChapter(f.id, it) }
        missing.forEachIndexed { i, idx ->
            onProgress(i, missing.size)
            Downloads.ensureChapter(f, idx)
        }
        onProgress(missing.size, missing.size)
    }

    private fun chapterHtml(f: Fic, i: Int): String? {
        val raw = Library.readChapter(f.id, i) ?: return null
        return Masking.applyToHtml(f, raw, remove = Masking.removeInExports).html
    }

    private fun chapterTitle(f: Fic, i: Int) = f.chapters.getOrNull(i)?.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${i + 1}"

    private fun outDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun export(context: Context, f0: Fic, format: Format, range: Range = fullRange(f0), onProgress: (String) -> Unit = {}): File {
        ensureChapters(f0, range) { d, t -> if (t > 0) onProgress("Téléchargement des chapitres manquants : $d/$t") }
        val f = Library.get(f0.id) ?: f0
        onProgress("Création du fichier…")
        val out = File(outDir(context), fileName(f, format, range))
        when (format) {
            Format.EPUB -> epub(f, range, out)
            Format.TXT -> out.writeText(txt(f, range))
            Format.PACK -> pack(f, range, out)
        }
        return out
    }

    // ---------- Informations ----------

    private fun date(t: Long): String =
        if (t <= 0) "inconnue" else SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(t))

    private fun infoLines(f: Fic): List<Pair<String, String>> = buildList {
        add("Titre" to f.title)
        if (f.author.isNotBlank()) add("Auteur" to f.author)
        add("Fandom" to f.fandomLabel)
        add("Site" to f.site.label)
        add("Adresse" to f.url)
        add("Statut" to f.status.label)
        if (f.words > 0) add("Mots" to "%,d".format(Locale.FRANCE, f.words))
        add("Chapitres" to f.totalChapters.toString())
        if (f.rating.isNotBlank()) add("Classement" to f.rating)
        if (f.genres.isNotEmpty()) add("Genres" to f.genres.joinToString(", "))
        if (f.characters.isNotEmpty()) add("Personnages" to f.characters.joinToString(", "))
        val types = Types.detect(f)
        if (types.isNotEmpty()) add("Type" to types.joinToString(", ") { it.label })
        if (f.tags.isNotEmpty()) add("Étiquettes" to f.tags.take(30).joinToString(", "))
        add("Publiée" to date(f.published))
        add("Mise à jour" to date(f.updated))
    }

    // ---------- Texte ----------

    fun txt(f: Fic, range: Range): String = buildString {
        append(f.title).append('\n')
        if (f.author.isNotBlank()) append("par ").append(f.author).append('\n')
        append('\n')
        infoLines(f).drop(1).forEach { (k, v) -> append(k).append(" : ").append(v).append('\n') }
        if (f.summary.isNotBlank()) append("\nRésumé :\n").append(f.summary).append('\n')
        for (i in range.from..range.to) {
            val html = chapterHtml(f, i) ?: continue
            append("\n\n=== Chapitre ").append(i + 1).append(" : ").append(chapterTitle(f, i)).append(" ===\n\n")
            append(Cleaner.toText(html)).append('\n')
        }
    }

    // ---------- EPUB ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun xhtmlBody(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).charset("UTF-8").prettyPrint(false)
        doc.select("font").unwrap()
        doc.select("center").tagName("div").attr("style", "text-align:center")
        doc.select("div.ohara-masque").unwrap()
        doc.select("[data-raison]").removeAttr("data-raison")
        return doc.body().html()
    }

    private fun page(title: String, body: String) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
<head><meta charset="UTF-8"/><title>${esc(title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
$body
</body>
</html>"""

    private const val CSS = """body { font-family: serif; line-height: 1.5; margin: 0 4%; }
h1 { font-size: 1.5em; text-align: center; margin: 2em 0 1em; }
h2 { font-size: 1.25em; text-align: center; margin: 1.5em 0 1em; }
p { margin: 0 0 0.8em; text-indent: 0; }
blockquote { margin: 1em 1.5em; font-style: italic; }
hr { border: none; border-top: 1px solid #999; margin: 1.5em 20%; }
table { border-collapse: collapse; } td { padding: 0.2em 0.5em; }
.info { font-size: 0.9em; } .info dt { font-weight: bold; } .info dd { margin: 0 0 0.4em 1em; }
.resume { font-style: italic; margin: 1.5em 0; }"""

    private fun ZipOutputStream.put(name: String, text: String, stored: Boolean = false) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val e = ZipEntry(name)
        if (stored) {
            e.method = ZipEntry.STORED
            e.size = bytes.size.toLong(); e.compressedSize = bytes.size.toLong()
            e.crc = CRC32().also { it.update(bytes) }.value
        }
        putNextEntry(e); write(bytes); closeEntry()
    }

    private fun epub(f: Fic, range: Range, out: File) {
        val uid = "urn:ohara:${f.id}"
        val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val chapters = (range.from..range.to).filter { Library.hasChapter(f.id, it) }
        ZipOutputStream(FileOutputStream(out)).use { z ->
            z.put("mimetype", "application/epub+zip", stored = true)
            z.put("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""")
            z.put("OEBPS/style.css", CSS)
            val info = infoLines(f).drop(1).joinToString("\n") { (k, v) -> "<dt>${esc(k)}</dt><dd>${esc(v)}</dd>" }
            z.put("OEBPS/titre.xhtml", page(f.title, """<h1>${esc(f.title)}</h1>
${if (f.author.isNotBlank()) "<p style=\"text-align:center\">par ${esc(f.author)}</p>" else ""}
${if (f.summary.isNotBlank()) "<p class=\"resume\">${esc(f.summary)}</p>" else ""}
<dl class="info">$info</dl>"""))
            for (i in chapters) {
                val html = chapterHtml(f, i) ?: continue
                z.put("OEBPS/ch${"%04d".format(i + 1)}.xhtml", page(chapterTitle(f, i), "<h2>${esc(chapterTitle(f, i))}</h2>\n" + xhtmlBody(html)))
            }
            val manifest = StringBuilder()
            val spine = StringBuilder()
            manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            manifest.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
            manifest.append("<item id=\"titre\" href=\"titre.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            spine.append("<itemref idref=\"titre\"/>\n")
            for (i in chapters) {
                val id = "ch${"%04d".format(i + 1)}"
                manifest.append("<item id=\"$id\" href=\"$id.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                spine.append("<itemref idref=\"$id\"/>\n")
            }
            val subjects = (f.fandoms + f.genres).joinToString("") { "<dc:subject>${esc(it)}</dc:subject>" }
            z.put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">${esc(uid)}</dc:identifier>
<dc:title>${esc(f.title)}</dc:title>
<dc:creator>${esc(f.author.ifBlank { "Auteur inconnu" })}</dc:creator>
<dc:language>en</dc:language>
<dc:source>${esc(f.url)}</dc:source>
<dc:description>${esc(f.summary)}</dc:description>
$subjects
<meta property="dcterms:modified">$modified</meta>
</metadata>
<manifest>
$manifest</manifest>
<spine toc="ncx">
$spine</spine>
</package>""")
            val navItems = chapters.joinToString("\n") { i -> "<li><a href=\"ch${"%04d".format(i + 1)}.xhtml\">${esc(chapterTitle(f, i))}</a></li>" }
            z.put("OEBPS/nav.xhtml", page("Sommaire", """<nav epub:type="toc" id="toc"><h1>Sommaire</h1><ol>
<li><a href="titre.xhtml">${esc(f.title)}</a></li>
$navItems
</ol></nav>"""))
            val points = (listOf(-1) + chapters).mapIndexed { n, i ->
                val (label, href) = if (i < 0) f.title to "titre.xhtml" else chapterTitle(f, i) to "ch${"%04d".format(i + 1)}.xhtml"
                "<navPoint id=\"p$n\" playOrder=\"${n + 1}\"><navLabel><text>${esc(label)}</text></navLabel><content src=\"$href\"/></navPoint>"
            }.joinToString("\n")
            z.put("OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="${esc(uid)}"/></head>
<docTitle><text>${esc(f.title)}</text></docTitle>
<navMap>
$points
</navMap>
</ncx>""")
        }
    }

    // ---------- Pack Claude ----------

    /** Découpe le texte en parties d'environ [wordsPerPart] mots, sans couper un chapitre. */
    fun parts(f: Fic, range: Range, wordsPerPart: Int): List<Pair<IntRange, String>> {
        val out = mutableListOf<Pair<IntRange, String>>()
        val sb = StringBuilder()
        var words = 0
        var start = -1
        var last = -1
        for (i in range.from..range.to) {
            val html = chapterHtml(f, i) ?: continue
            val text = Cleaner.toText(html)
            val w = Library.countWords(html)
            if (words > 0 && words + w > wordsPerPart) {
                out += (start..last) to sb.toString()
                sb.clear(); words = 0; start = -1
            }
            if (start < 0) start = i
            last = i
            sb.append("=== Chapitre ").append(i + 1).append(" : ").append(chapterTitle(f, i)).append(" ===\n\n").append(text).append("\n\n")
            words += w
        }
        if (sb.isNotEmpty()) out += (start..last) to sb.toString()
        return out
    }

    private fun fiche(f: Fic): String = buildString {
        append("# ").append(f.title).append("\n\n")
        infoLines(f).drop(1).forEach { (k, v) -> append("- **").append(k).append("** : ").append(v).append('\n') }
        if (f.summary.isNotBlank()) append("\n## Résumé\n\n").append(f.summary).append('\n')
        if (f.note >= 0 || f.comment.isNotBlank()) {
            append("\n## Mon avis\n\n")
            if (f.note >= 0) append("- Note : ").append(f.note).append("/10\n")
            if (f.comment.isNotBlank()) append("- Commentaire : ").append(f.comment).append('\n')
        }
        if (f.abandonReason.isNotBlank()) append("- Raison d'abandon : ").append(f.abandonReason).append('\n')
        append("\nLu jusqu'au chapitre ").append((f.maxChapterRead + 1).coerceAtLeast(0)).append(" sur ").append(f.totalChapters).append(".\n")
    }

    private fun personnages(f: Fic): String = buildString {
        append("# Personnages — ").append(f.title).append("\n\n")
        if (f.characterNotes.isNotEmpty()) {
            append("## Mes fiches personnages\n\n")
            f.characterNotes.forEach { c -> append("### ").append(c.name).append("\n\n").append(c.note.ifBlank { "(pas de note)" }).append("\n\n") }
        }
        if (f.characters.isNotEmpty()) {
            append("## Personnages annoncés par le site\n\n")
            f.characters.forEach { append("- ").append(it).append('\n') }
        }
        if (f.characterNotes.isEmpty() && f.characters.isEmpty()) append("Aucune fiche pour l'instant. Vous pouvez en ajouter depuis la fiche de la fic dans Ohara.\n")
    }

    private fun surlignages(f: Fic): String = buildString {
        append("# Passages surlignés — ").append(f.title).append("\n\n")
        if (f.highlights.isEmpty()) append("Aucun passage surligné.\n")
        f.highlights.sortedWith(compareBy({ it.chapter }, { it.createdAt })).forEach { h ->
            append("## Chapitre ").append(h.chapter + 1).append(" : ").append(chapterTitle(f, h.chapter)).append("\n\n")
            h.text.lines().forEach { append("> ").append(it).append('\n') }
            if (h.note.isNotBlank()) append("\nMa note : ").append(h.note).append('\n')
            append('\n')
        }
    }

    /** Un modèle de consigne pour Claude. */
    data class Prompt(val id: String, val label: String, val text: String)

    fun prompts(f: Fic, partCount: Int, range: Range): List<Prompt> {
        val complete = f.status == FicStatus.COMPLETE
        val by = if (f.author.isNotBlank()) " de ${f.author}" else ""
        val sent = "Je vous envoie la fiche, les personnages, mes passages surlignés et le texte en $partCount partie${if (partCount > 1) "s" else ""} (chapitres ${range.from + 1} à ${range.to + 1})."
        return listOf(
            Prompt("continuer", "Continuer la fic",
                "Voici « ${f.title} »$by, une fanfiction ${f.fandomLabel}${if (complete) " terminée" else " en cours"}.\n$sent\n" +
                    "Lisez tout avant de répondre. Ensuite, écrivez le chapitre suivant en anglais, dans le même ton, le même rythme et la même façon de faire parler les personnages.\n" +
                    "Gardez la cohérence avec les événements et les pouvoirs établis. Longueur visée : [environ 4 000 mots].\n" +
                    "Ce que j'aimerais voir arriver : [vos idées, ou « à vous de choisir »]."),
            Prompt("nouvelle", "Nouvelle fic, même ton",
                "Voici « ${f.title} »$by.\n$sent\nInspirez-vous de son style (voix du narrateur, humour, longueur des scènes, façon de décrire les combats et les dialogues).\n" +
                    "Écrivez le premier chapitre d'une nouvelle fic en anglais : [fandom, idée de départ, personnage principal].\n" +
                    "Ne reprenez pas l'intrigue : seulement le ton et la qualité d'écriture."),
            Prompt("resumer", "Résumer l'histoire",
                "Voici « ${f.title} »$by.\n$sent\nRésumez en français l'intrigue jusqu'au dernier chapitre envoyé : personnages principaux, arcs en cours, mystères non résolus, et ce qui devrait logiquement arriver ensuite."),
            Prompt("style", "Analyser le style",
                "Voici « ${f.title} »$by.\n$sent\nDécrivez en français, précisément, ce qui fait le style de cette fic (narration, dialogues, rythme, vocabulaire, humour), pour que je puisse vous demander de l'imiter ensuite.")
        )
    }

    private fun consignes(f: Fic, partCount: Int, range: Range): String = buildString {
        append("# Modèles de consignes pour Claude\n\n")
        append("Copiez l'un de ces modèles dans la conversation, après avoir envoyé les fichiers du pack.\nRemplacez ce qui est entre crochets.\n")
        prompts(f, partCount, range).forEachIndexed { i, p -> append("\n## ").append(i + 1).append(". ").append(p.label).append("\n\n").append(p.text).append('\n') }
    }

    /**
     * Fichiers séparés pour « Envoyer à Claude » (l'appli Claude ne lit pas les archives) :
     * fiche, personnages, surlignages, puis le texte en parties.
     */
    suspend fun claudeFiles(context: Context, f0: Fic, range: Range, onProgress: (String) -> Unit = {}): Pair<List<File>, Int> {
        ensureChapters(f0, range) { d, t -> if (t > 0) onProgress("Téléchargement des chapitres manquants : $d/$t") }
        val f = Library.get(f0.id) ?: f0
        onProgress("Préparation des fichiers…")
        val dir = File(context.cacheDir, "claude").apply { deleteRecursively(); mkdirs() }
        val base = fileName(f, Format.TXT).removeSuffix(".txt").take(50).trim()
        val parts = parts(f, range, Prefs.packWordsPerPart.coerceAtLeast(5000))
        val out = mutableListOf<File>()
        fun put(name: String, text: String) { out += File(dir, name).apply { writeText(text) } }
        put("1 - $base - fiche.md", fiche(f))
        if (f.characterNotes.isNotEmpty() || f.characters.isNotEmpty()) put("2 - $base - personnages.md", personnages(f))
        if (f.highlights.isNotEmpty()) put("3 - $base - surlignages.md", surlignages(f))
        parts.forEachIndexed { i, (r, text) -> put("${4 + i} - $base - partie ${i + 1} (ch. ${r.first + 1}-${r.last + 1}).txt", text) }
        return out to parts.size
    }

    private fun lisezMoi(f: Fic, parts: List<Pair<IntRange, String>>): String = buildString {
        append("PACK CLAUDE — ").append(f.title).append("\n\n")
        append("Ce dossier contient tout ce qu'il faut pour travailler sur cette fic avec Claude.\n\n")
        append("Ordre conseillé pour l'envoi :\n")
        append("  1. 1_fiche.md — informations, résumé et votre avis\n")
        append("  2. 2_personnages.md — vos fiches personnages\n")
        append("  3. 3_surlignages.md — les passages que vous avez surlignés, avec vos notes\n")
        parts.forEachIndexed { i, (r, _) ->
            append("  ").append(4 + i).append(". texte/partie_").append("%02d".format(i + 1)).append(".txt — chapitres ").append(r.first + 1).append(" à ").append(r.last + 1).append('\n')
        }
        append("\nPuis copiez un modèle de 4_consignes.md dans la conversation.\n")
        append("Si la conversation devient trop longue, envoyez les parties en plusieurs fois ou commencez par les dernières.\n")
    }

    private fun pack(f: Fic, range: Range, out: File) {
        val parts = parts(f, range, Prefs.packWordsPerPart.coerceAtLeast(5000))
        ZipOutputStream(FileOutputStream(out)).use { z ->
            z.put("0_LISEZ-MOI.txt", lisezMoi(f, parts))
            z.put("1_fiche.md", fiche(f))
            z.put("2_personnages.md", personnages(f))
            z.put("3_surlignages.md", surlignages(f))
            z.put("4_consignes.md", consignes(f, parts.size, range))
            parts.forEachIndexed { i, (_, text) -> z.put("texte/partie_${"%02d".format(i + 1)}.txt", text) }
        }
    }
}
