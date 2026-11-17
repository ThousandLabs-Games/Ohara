package com.thousandlabs.ohara.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Favoris du navigateur, rangés dans des dossiers (« Favoris » par défaut). */
object Bookmarks {
    const val DEFAULT = "Favoris"

    data class Bookmark(val title: String, val url: String, val folder: String = DEFAULT, val at: Long = System.currentTimeMillis())

    private lateinit var file: File
    private val _items = MutableStateFlow<List<Bookmark>>(emptyList())
    val items: StateFlow<List<Bookmark>> = _items.asStateFlow()
    private val _folders = MutableStateFlow(listOf(DEFAULT))
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    fun init(root: File) {
        file = File(root, "favoris.json")
        runCatching {
            val o = JSONObject(file.readText())
            val a = o.getJSONArray("b")
            _items.value = List(a.length()) { i ->
                val b = a.getJSONObject(i)
                Bookmark(b.optString("t"), b.optString("u"), b.optString("f", DEFAULT), b.optLong("a"))
            }
            val f = o.optJSONArray("f")
            if (f != null) _folders.value = (listOf(DEFAULT) + List(f.length()) { f.getString(it) }).distinct()
        }
    }

    private fun save() {
        val a = JSONArray()
        _items.value.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("f", it.folder).put("a", it.at)) }
        SafeFile.write(file, JSONObject().put("b", a).put("f", JSONArray(_folders.value)).toString())
    }

    fun contains(url: String) = _items.value.any { it.url == url }

    @Synchronized
    fun add(title: String, url: String, folder: String = DEFAULT) {
        if (url.isBlank()) return
        _items.value = listOf(Bookmark(title.ifBlank { url }, url, folder)) + _items.value.filterNot { it.url == url }
        if (folder !in _folders.value) _folders.value = _folders.value + folder
        save()
    }

    @Synchronized
    fun remove(url: String) { _items.value = _items.value.filterNot { it.url == url }; save() }

    @Synchronized
    fun update(old: Bookmark, new: Bookmark) {
        _items.value = _items.value.map { if (it.url == old.url) new else it }
        if (new.folder !in _folders.value) _folders.value = _folders.value + new.folder
        save()
    }

    @Synchronized
    fun addFolder(name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && n !in _folders.value) { _folders.value = _folders.value + n; save() }
    }

    /** Supprime un dossier : ses favoris reviennent dans « Favoris ». */
    @Synchronized
    fun removeFolder(name: String) {
        if (name == DEFAULT) return
        _folders.value = _folders.value - name
        _items.value = _items.value.map { if (it.folder == name) it.copy(folder = DEFAULT) else it }
        save()
    }
}
