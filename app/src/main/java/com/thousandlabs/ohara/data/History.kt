package com.thousandlabs.ohara.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Historique, désactivé par défaut (Réglages › Bloqueur et anti-pistage › Historique).
 * Il garde les pages visitées dans le navigateur, les recherches et les fics ouvertes, sur le téléphone seulement.
 */
object History {
    enum class Kind(val label: String) { PAGE("Pages"), SEARCH("Recherches"), FIC("Fics ouvertes") }

    /**
     * @param target adresse de la page ; pour une recherche avancée « q: » suivi de la recherche ;
     *   pour une fic « fic: » suivi de son identifiant et du chapitre (« #12 »).
     */
    data class Entry(val kind: Kind, val title: String, val target: String, val at: Long)

    private lateinit var file: File
    private const val MAX = 3000
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    var enabled: Boolean get() = Prefs.getBool("hist_on", false); set(v) = Prefs.putBool("hist_on", v)

    fun init(root: File) {
        file = File(root, "historique.json")
        runCatching {
            val a = JSONArray(file.readText())
            _entries.value = List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Entry(runCatching { Kind.valueOf(o.getString("k")) }.getOrDefault(Kind.PAGE), o.optString("t"), o.optString("u"), o.optLong("a"))
            }
        }
    }

    @Synchronized
    fun add(kind: Kind, title: String, target: String) {
        if (!enabled || target.isBlank()) return
        val e = Entry(kind, title.ifBlank { target }.take(300), target, System.currentTimeMillis())
        // la même page revisitée remonte en tête au lieu de se répéter
        _entries.value = (listOf(e) + _entries.value.filterNot { it.kind == kind && it.target == target }).take(MAX)
        save()
    }

    @Synchronized
    fun remove(e: Entry) { _entries.value = _entries.value - e; save() }

    @Synchronized
    fun clear() { _entries.value = emptyList(); save() }

    private var pending = false
    private fun save() {
        if (pending) return
        pending = true
        Thread {
            Thread.sleep(1000)
            val json = synchronized(this) {
                pending = false
                JSONArray().also { a ->
                    _entries.value.forEach { e -> a.put(JSONObject().put("k", e.kind.name).put("t", e.title).put("u", e.target).put("a", e.at)) }
                }.toString()
            }
            runCatching { file.writeText(json) }
        }.start()
    }
}
