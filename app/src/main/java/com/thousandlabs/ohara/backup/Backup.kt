package com.thousandlabs.ohara.backup

import com.thousandlabs.ohara.data.ChapterInfo
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sauvegarde de toute la bibliothèque dans un seul fichier « .ohara » (une archive) :
 * fics, progression, notes, surlignages, étagères, journal de lecture, réglages et, au choix, le texte des chapitres.
 * Le fichier se range où vous voulez sur le téléphone (ou une carte SD), sans lien avec un ordinateur.
 */
object Backup {
    const val MIME = "application/octet-stream"
    const val FORMAT = 1

    data class Summary(val fics: Int, val chapters: Int, val date: Long)

    /** Réglages qui ne doivent pas suivre une sauvegarde (propres à cet appareil). */
    private val LOCAL_KEYS = setOf("ab_updated", "upd_last_check")

    private fun prefsToJson(): JSONObject {
        val o = JSONObject()
        for ((k, v) in Prefs.exportAll()) {
            if (k in LOCAL_KEYS) continue
            val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> null } ?: continue
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o
    }

    private fun prefsFromJson(o: JSONObject): Map<String, Any?> {
        val m = HashMap<String, Any?>()
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            m[k] = when (e.optString("t")) {
                "b" -> e.optBoolean("v")
                "i" -> e.optInt("v")
                "l" -> e.optLong("v")
                "f" -> e.optDouble("v").toFloat()
                "s" -> e.optString("v")
                else -> null
            }
        }
        return m
    }

    private val EXTRA_FILES = listOf("favoris.json", "historique.json", "lectures_en_cours.json")

    private fun ZipOutputStream.text(name: String, s: String) {
        putNextEntry(ZipEntry(name)); write(s.toByteArray(Charsets.UTF_8)); closeEntry()
    }

    fun create(out: OutputStream, withChapters: Boolean, onProgress: (String) -> Unit = {}): Summary {
        Library.saveNow()
        val fics = Library.fics.value
        var chapters = 0
        ZipOutputStream(out.buffered()).use { z ->
            z.text("ohara.json", JSONObject().put("app", "Ohara").put("format", FORMAT).put("date", System.currentTimeMillis())
                .put("fics", fics.size).put("chapitres", withChapters).toString(2))
            z.text("fics.json", JSONArray().also { a -> fics.forEach { a.put(it.toJson()) } }.toString())
            z.text("journal.json", ReadingLog.toJson().toString())
            z.text("reglages.json", prefsToJson().toString())
            // favoris, historique et fics en cours de découverte (pas encore dans la bibliothèque)
            EXTRA_FILES.forEach { n -> File(Library.dataDir(), n).takeIf { it.exists() }?.let { z.text("extra/$n", it.readText()) } }
            if (withChapters) fics.forEachIndexed { i, f ->
                onProgress("Chapitres : ${i + 1}/${fics.size} — ${f.title}")
                val dir = Library.ficDir(f.id)
                dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                    z.putNextEntry(ZipEntry("chapitres/${dir.name}/${file.name}"))
                    file.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                    chapters++
                }
            }
        }
        return Summary(fics.size, chapters, System.currentTimeMillis())
    }

    /**
     * Fichier partiel : garde les chapitres déjà connus avant l'index [from], puis ajoute les nouveaux.
     * Un chapitre manquant avant [from] reçoit un titre provisoire, pour que les index restent alignés sur les fichiers.
     */
    private fun appendChapters(base: Fic, before: List<ChapterInfo>, added: List<ChapterInfo>, from: Int): Fic {
        val root = base.url.substringBeforeLast('/')
        val head = (0 until from).map { i -> before.getOrNull(i) ?: ChapterInfo("Chapitre ${i + 1}", "$root/$i") }
        val all = head + added
        return base.copy(chapters = all, chapterCount = all.size, words = all.sumOf { it.words.toLong() })
    }

    /**
     * Restauration.
     * @param replace true : la bibliothèque actuelle est remplacée ; false : les fics absentes sont ajoutées
     *   et, pour celles déjà présentes, on garde la progression la plus avancée.
     */
    fun restore(input: InputStream, replace: Boolean, onProgress: (String) -> Unit = {}): Summary {
        var manifest: JSONObject? = null
        var fics: List<Fic>? = null
        var journal: JSONObject? = null
        var prefs: JSONObject? = null
        val extras = HashMap<String, String>()
        // fics importées livrées par morceaux : « from » = index du premier chapitre contenu dans le fichier
        val starts = HashMap<String, Int>()
        val tmp = File(Library.dataDir(), "restauration").apply { deleteRecursively(); mkdirs() }
        var chapters = 0
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name
                if (name.contains("..")) continue
                when {
                    name == "ohara.json" -> manifest = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    name == "fics.json" -> {
                        val a = JSONArray(z.readBytes().toString(Charsets.UTF_8))
                        fics = List(a.length()) { Fic.fromJson(a.getJSONObject(it)) }
                        for (i in 0 until a.length()) a.getJSONObject(i).let { o ->
                            val from = o.optInt("from", 0)
                            if (from > 0) starts[o.optString("id")] = from
                        }
                    }
                    name == "journal.json" -> journal = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    name == "reglages.json" -> prefs = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    name.startsWith("extra/") && name.removePrefix("extra/") in EXTRA_FILES -> extras[name.removePrefix("extra/")] = z.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("chapitres/") && !e.isDirectory -> {
                        val f = File(tmp, name.removePrefix("chapitres/"))
                        f.parentFile?.mkdirs()
                        f.outputStream().use { z.copyTo(it) }
                        chapters++
                        if (chapters % 200 == 0) onProgress("Lecture des chapitres : $chapters")
                    }
                }
                z.closeEntry()
            }
        }
        if (manifest?.optString("app") != "Ohara" || fics == null) {
            tmp.deleteRecursively()
            throw IOException("Ce fichier n'est pas une sauvegarde Ohara.")
        }
        val list = fics!!
        onProgress("Mise en place de la bibliothèque…")
        if (replace) {
            File(Library.dataDir(), "fics").deleteRecursively()
            Library.replaceAll(list)
            journal?.let { ReadingLog.replace(it) }
            prefs?.let { Prefs.importAll(prefsFromJson(it)) }
        } else {
            val absentIds = list.filter { !Library.contains(it.id) }.map { it.id }.toSet()
            val absent = list.filter { it.id in absentIds }.map { f ->
                val from = starts[f.id] ?: 0
                if (from > 0) appendChapters(f, emptyList(), f.chapters, from) else f
            }
            if (absent.isNotEmpty()) Library.replaceAll(Library.fics.value + absent)
            for (f in list) {
                if (f.id in absentIds) continue
                Library.update(f.id) { cur ->
                    // fic importée : la nouvelle version du fichier apporte les nouveaux chapitres et la fiche à jour ;
                    // un fichier partiel (« from ») ajoute ses chapitres à la suite de ceux déjà présents
                    val from = starts[f.id] ?: 0
                    val it = if (f.site == com.thousandlabs.ohara.data.Site.LOCAL) {
                        if (from > 0) appendChapters(cur.mergeSiteData(f), cur.chapters, f.chapters, from)
                        else cur.mergeSiteData(f)
                    } else cur
                    if (f.maxChapterRead > it.maxChapterRead) it.copy(
                        progressChapter = f.progressChapter, progressPos = f.progressPos, maxChapterRead = f.maxChapterRead,
                        shelves = it.shelves + f.shelves,
                        highlights = (it.highlights + f.highlights).distinctBy { h -> h.chapter to h.text },
                        characterNotes = (it.characterNotes + f.characterNotes).distinctBy { c -> c.name.lowercase() }
                    ) else it.copy(
                        shelves = it.shelves + f.shelves,
                        highlights = (it.highlights + f.highlights).distinctBy { h -> h.chapter to h.text },
                        characterNotes = (it.characterNotes + f.characterNotes).distinctBy { c -> c.name.lowercase() }
                    )
                }
            }
        }
        // chapitres : déplacés en place (en fusion, seuls les chapitres absents sont ajoutés)
        val dest = File(Library.dataDir(), "fics").apply { mkdirs() }
        tmp.listFiles()?.forEach { dir ->
            val target = File(dest, dir.name).apply { mkdirs() }
            dir.listFiles()?.forEach { file ->
                val t = File(target, file.name)
                if (replace || !t.exists() || dir.name.startsWith("loc_")) { if (!file.renameTo(t)) { file.copyTo(t, overwrite = true) } }
            }
        }
        tmp.deleteRecursively()
        // favoris, historique : remplacés, ou ajoutés à ceux du téléphone en fusion
        extras["favoris.json"]?.let { txt ->
            if (replace) com.thousandlabs.ohara.data.SafeFile.write(File(Library.dataDir(), "favoris.json"), txt)
            else runCatching {
                val a = JSONObject(txt).getJSONArray("b")
                for (i in 0 until a.length()) a.getJSONObject(i).let { b ->
                    if (!com.thousandlabs.ohara.data.Bookmarks.contains(b.optString("u")))
                        com.thousandlabs.ohara.data.Bookmarks.add(b.optString("t"), b.optString("u"), b.optString("f", com.thousandlabs.ohara.data.Bookmarks.DEFAULT))
                }
            }
        }
        if (replace) {
            extras["historique.json"]?.let { com.thousandlabs.ohara.data.SafeFile.write(File(Library.dataDir(), "historique.json"), it) }
            extras["lectures_en_cours.json"]?.let { com.thousandlabs.ohara.data.SafeFile.write(File(Library.dataDir(), "lectures_en_cours.json"), it) }
        }
        com.thousandlabs.ohara.data.Bookmarks.init(Library.dataDir())
        com.thousandlabs.ohara.data.History.init(Library.dataDir())
        com.thousandlabs.ohara.ui.ReadTracker.init(Library.dataDir())
        Library.replaceAll(Library.fics.value) // relit les chapitres présents
        Library.saveNow()
        return Summary(list.size, chapters, manifest?.optLong("date") ?: 0L)
    }
}
