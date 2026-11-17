package com.thousandlabs.ohara.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * La bibliothèque : la liste des fics (un fichier JSON) et le texte des chapitres téléchargés
 * (un fichier par chapitre). Tout reste dans la mémoire privée de l'appli.
 */
object Library {
    private lateinit var root: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _fics = MutableStateFlow<List<Fic>>(emptyList())
    val fics: StateFlow<List<Fic>> = _fics.asStateFlow()

    /** Chapitres présents sur le téléphone, par fic. */
    private val downloaded = ConcurrentHashMap<String, MutableSet<Int>>()
    private val _downloadVersion = MutableStateFlow(0)
    val downloadVersion: StateFlow<Int> = _downloadVersion.asStateFlow()

    private var saveJob: Job? = null

    fun init(context: Context) {
        root = File(context.filesDir, "bibliotheque").apply { mkdirs() }
        val f = File(root, "fics.json")
        val list = runCatching {
            val a = JSONArray(f.readText())
            List(a.length()) { Fic.fromJson(a.getJSONObject(it)) }
        }.getOrElse {
            // fichier abîmé : on le met de côté (jamais écrasé), puis on tente la copie de secours
            if (f.exists() && f.length() > 0) runCatching { f.copyTo(File(root, "fics.abime-${System.currentTimeMillis()}.json"), overwrite = false) }
            runCatching {
                val a = JSONArray(File(root, "fics.bak.json").readText())
                List(a.length()) { Fic.fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
        _fics.value = list
        ReadingLog.init(root)
    }

    fun dataDir(): File = root

    // ---------- Fics ----------

    @Volatile private var index: Pair<List<Fic>, Map<String, Fic>>? = null

    /** Accès direct par identifiant (recalculé seulement quand la bibliothèque change). */
    private fun byId(): Map<String, Fic> {
        val l = _fics.value
        index?.let { (list, map) -> if (list === l) return map }
        val m = HashMap<String, Fic>(l.size * 2)
        for (f in l) m[f.id] = f
        index = l to m
        return m
    }

    fun get(id: String): Fic? = byId()[id]
    fun contains(id: String): Boolean = byId().containsKey(id)

    /** Retrouve une fic par une de ses adresses (y compris les adresses des doublons fusionnés). */
    fun findByUrl(url: String): Fic? = _fics.value.firstOrNull { it.url == url || url in it.altUrls }

    /** Ajoute la fic, ou met à jour ses informations si elle est déjà là. */
    fun upsert(fic: Fic, shelf: String? = null): Fic {
        synchronized(lock) {
            val cur = _fics.value
            val i = cur.indexOfFirst { it.id == fic.id }
            val result = if (i >= 0) {
                val merged = cur[i].mergeSiteData(fic).let { if (shelf != null) it.copy(shelves = it.shelves + shelf) else it }
                _fics.value = cur.toMutableList().also { it[i] = merged }
                merged
            } else {
                val added = fic.copy(
                    addedAt = System.currentTimeMillis(),
                    shelves = fic.shelves + (shelf ?: Shelves.TO_READ)
                )
                _fics.value = listOf(added) + cur
                added
            }
            scheduleSave()
            return result
        }
    }

    fun update(id: String, change: (Fic) -> Fic) {
        synchronized(lock) {
            val cur = _fics.value
            val i = cur.indexOfFirst { it.id == id }
            if (i < 0) return
            _fics.value = cur.toMutableList().also { it[i] = change(cur[i]) }
            scheduleSave()
        }
    }

    fun remove(id: String, deleteChapters: Boolean = true) {
        synchronized(lock) {
            _fics.value = _fics.value.filterNot { it.id == id }
            scheduleSave()
        }
        if (deleteChapters) {
            runCatching { ficDir(id).deleteRecursively() }
            downloaded.remove(id)
            _downloadVersion.value++
        }
    }

    fun replaceAll(list: List<Fic>) {
        synchronized(lock) { _fics.value = list; scheduleSave() }
        downloaded.clear()
        _downloadVersion.value++
    }

    fun setShelf(id: String, shelf: String, on: Boolean) = update(id) {
        it.copy(shelves = if (on) it.shelves + shelf else it.shelves - shelf)
    }

    /** Fusionne deux fiches de la même histoire (publiée sur deux sites) : la plus avancée devient la principale. */
    fun mergeDuplicates(keepId: String, dropId: String) {
        val keep = get(keepId) ?: return
        val drop = get(dropId) ?: return
        update(keepId) {
            it.copy(
                altUrls = (it.altUrls + drop.url + drop.altUrls).distinct() - it.url,
                shelves = it.shelves + drop.shelves,
                note = if (it.note >= 0) it.note else drop.note,
                comment = it.comment.ifEmpty { drop.comment },
                characterNotes = (it.characterNotes + drop.characterNotes).distinctBy { c -> c.name.lowercase() },
                highlights = it.highlights + drop.highlights.filter { h -> h.chapter < it.totalChapters },
                secondsRead = it.secondsRead + drop.secondsRead,
                wordsRead = it.wordsRead + drop.wordsRead,
                maxChapterRead = maxOf(it.maxChapterRead, drop.maxChapterRead.coerceAtMost(keep.totalChapters - 1))
            )
        }
        remove(dropId)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            saveNow()
        }
    }

    fun saveNow() {
        val json = JSONArray().also { a -> _fics.value.forEach { a.put(it.toJson()) } }.toString()
        synchronized(this) {
            val f = File(root, "fics.json")
            val tmp = File(root, "fics.tmp")
            tmp.writeText(json)
            if (f.exists()) f.copyTo(File(root, "fics.bak.json"), overwrite = true)
            if (!tmp.renameTo(f)) { f.writeText(json); tmp.delete() }
        }
    }

    // ---------- Texte des chapitres ----------

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    fun ficDir(id: String) = File(root, "fics/${safe(id)}")
    private fun chapterFile(id: String, index: Int) = File(ficDir(id), "$index.html")

    fun hasChapter(id: String, index: Int): Boolean = downloadedSet(id).contains(index)

    fun downloadedSet(id: String): Set<Int> = downloaded.getOrPut(id) {
        val set = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
        ficDir(id).listFiles()?.forEach { f -> f.name.removeSuffix(".html").toIntOrNull()?.let { set.add(it) } }
        set
    }

    fun downloadedCount(id: String): Int = downloadedSet(id).size

    fun readChapter(id: String, index: Int): String? =
        runCatching { chapterFile(id, index).takeIf { it.exists() }?.readText() }.getOrNull()

    fun writeChapter(id: String, index: Int, html: String) {
        SafeFile.write(chapterFile(id, index), html)
        (downloadedSet(id) as MutableSet<Int>).add(index)
        _downloadVersion.value++
    }

    fun deleteChapters(id: String) {
        runCatching { ficDir(id).deleteRecursively() }
        downloaded.remove(id)
        _downloadVersion.value++
    }

    /** Nombre de mots d'un texte HTML de chapitre. */
    fun countWords(html: String): Int {
        val text = html.replace(Regex("<[^>]+>"), " ")
        return Regex("[\\p{L}\\p{N}'’-]+").findAll(text).count()
    }

    fun storageBytes(): Long = File(root, "fics").walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** Journal de lecture par jour (mots lus et temps passé), pour les statistiques. */
object ReadingLog {
    private lateinit var file: File
    private val days = linkedMapOf<String, LongArray>() // "2026-11-17" → [mots, secondes]

    fun init(root: File) {
        file = File(root, "journal.json")
        runCatching {
            val o = JSONObject(file.readText())
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                days[k] = longArrayOf(a.optLong(0), a.optLong(1))
            }
        }
    }

    private fun today(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Synchronized
    fun add(words: Long, seconds: Long) {
        if (words <= 0 && seconds <= 0) return
        val k = today()
        val cur = days.getOrPut(k) { longArrayOf(0, 0) }
        cur[0] += words; cur[1] += seconds
        save()
    }

    @Synchronized
    fun snapshot(): Map<String, LongArray> = days.mapValues { it.value.clone() }

    @Synchronized
    fun replace(o: JSONObject) {
        days.clear()
        for (k in o.keys()) { val a = o.getJSONArray(k); days[k] = longArrayOf(a.optLong(0), a.optLong(1)) }
        save()
    }

    @Synchronized
    fun toJson(): JSONObject = JSONObject().also { o -> days.forEach { (k, v) -> o.put(k, JSONArray().put(v[0]).put(v[1])) } }

    private fun save() { SafeFile.write(file, toJson().toString()) }
}
