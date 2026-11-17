package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.sources.FanFictionNet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Index des fandoms de FanFiction.net (nom, page, nombre d'histoires), préparé une fois puis gardé un mois.
 * Il sert à retrouver la page d'un fandom à partir de son nom, et à proposer des noms pendant la saisie.
 */
object FandomIndex {
    data class Entry(val name: String, val url: String, val count: Int, val category: String)

    private lateinit var file: File
    @Volatile private var entries: List<Entry> = emptyList()
    private val mutex = Mutex()

    private val _building = MutableStateFlow<String?>(null)
    /** Message pendant la préparation (null quand rien n'est en cours). */
    val building: StateFlow<String?> = _building.asStateFlow()

    fun init(root: File) {
        file = File(root, "fandoms_ffn.json")
        runCatching {
            val o = JSONObject(file.readText())
            val a = o.getJSONArray("e")
            entries = List(a.length()) { i ->
                val e = a.getJSONArray(i)
                Entry(e.getString(0), e.getString(1), e.optInt(2), e.optString(3))
            }
        }
    }

    val ready: Boolean get() = entries.isNotEmpty()
    private fun age(): Long = if (::file.isInitialized && file.exists()) System.currentTimeMillis() - file.lastModified() else Long.MAX_VALUE

    /** Prépare l'index s'il manque ou date de plus d'un mois. */
    suspend fun ensure() = mutex.withLock {
        if (entries.isNotEmpty() && age() < 30L * 24 * 3600 * 1000) return@withLock
        val all = ArrayList<Entry>()
        val cats = FanFictionNet.CATEGORIES.entries.toList()
        for ((i, c) in cats.withIndex()) {
            _building.value = "Préparation de l'index des fandoms (${i + 1}/${cats.size}) : ${c.value}…"
            runCatching {
                val page = Fetcher.get("${FanFictionNet.BASE}/${c.key}/", readyJs = "!!document.querySelector('#list_output')")
                FanFictionNet.parseFandomIndex(page.body).forEach { (n, u, k) -> all += Entry(n, u, k, c.value) }
            }
        }
        _building.value = null
        if (all.size > 100) {
            entries = all
            runCatching {
                val a = JSONArray()
                all.forEach { a.put(JSONArray().put(it.name).put(it.url).put(it.count).put(it.category)) }
                file.writeText(JSONObject().put("e", a).toString())
            }
        }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Page d'un fandom, d'après les fics de la bibliothèque puis l'index. */
    fun urlFor(name: String): String? {
        val n = norm(name)
        if (n.isEmpty()) return null
        Library.fics.value.firstOrNull { f ->
            f.site == Site.FFN && f.fandomUrl.isNotEmpty() && f.fandoms.size == 1 && norm(f.fandoms[0]) == n
        }?.let { return it.fandomUrl }
        val list = entries
        return (list.firstOrNull { norm(it.name) == n }
            ?: list.filter { norm(it.name).startsWith(n) }.maxByOrNull { it.count }
            ?: list.filter { norm(it.name).contains(n) }.maxByOrNull { it.count })?.url
    }

    /** Propositions pendant la saisie : fandoms de la bibliothèque d'abord, puis les plus fournis de l'index. */
    fun suggest(prefix: String, limit: Int = 8): List<String> {
        val n = norm(prefix)
        if (n.length < 2) return emptyList()
        val mine = Library.fics.value.flatMap { it.fandoms }.filter { norm(it).contains(n) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val idx = entries.asSequence().filter { norm(it.name).contains(n) }
            .sortedWith(compareByDescending<Entry> { norm(it.name).startsWith(n) }.thenByDescending { it.count })
            .map { it.name }.take(limit * 2).toList()
        return (mine + idx).distinctBy { it.lowercase() }.take(limit)
    }
}
