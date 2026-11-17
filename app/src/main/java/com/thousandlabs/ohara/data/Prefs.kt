package com.thousandlabs.ohara.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Réglages de l'appli. Tout reste sur le téléphone. */
object Prefs {
    private lateinit var sp: SharedPreferences

    /** Incrémenté à chaque changement de réglage, pour que l'interface se mette à jour. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("ohara", Context.MODE_PRIVATE)
    }

    private fun bump() { _version.value = _version.value + 1 }

    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply(); bump() }
    fun getInt(key: String, def: Int) = sp.getInt(key, def)
    fun putInt(key: String, v: Int) { sp.edit().putInt(key, v).apply(); bump() }
    fun getLong(key: String, def: Long) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) { sp.edit().putLong(key, v).apply(); bump() }
    fun getFloat(key: String, def: Float) = sp.getFloat(key, def)
    fun putFloat(key: String, v: Float) { sp.edit().putFloat(key, v).apply(); bump() }
    fun getString(key: String, def: String) = sp.getString(key, def) ?: def
    fun putString(key: String, v: String) { sp.edit().putString(key, v).apply(); bump() }

    fun remove(key: String) { sp.edit().remove(key).apply(); bump() }

    fun getList(key: String): List<String> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    fun putList(key: String, list: List<String>) {
        sp.edit().putString(key, JSONArray(list).toString()).apply(); bump()
    }

    /** Tous les réglages, pour la sauvegarde. */
    fun exportAll(): Map<String, Any?> = sp.all

    fun importAll(map: Map<String, Any?>) {
        val e = sp.edit()
        for ((k, v) in map) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is Double -> e.putFloat(k, v.toFloat())
            is String -> e.putString(k, v)
        }
        e.apply(); bump()
    }

    // ---------- Réglages nommés ----------

    // Apparence
    var appTheme: Int get() = getInt("app_theme", 0); set(v) = putInt("app_theme", v)
    var accent: Int get() = getInt("accent", 0); set(v) = putInt("accent", v)

    // Lecture
    var readerFont: Int get() = getInt("r_font", 1); set(v) = putInt("r_font", v)
    var readerSize: Int get() = getInt("r_size", 17); set(v) = putInt("r_size", v)
    var readerLine: Float get() = getFloat("r_line", 1.5f); set(v) = putFloat("r_line", v)
    var readerMargin: Int get() = getInt("r_margin", 16); set(v) = putInt("r_margin", v)
    var readerTheme: Int get() = getInt("r_theme", 0); set(v) = putInt("r_theme", v)
    var readerJustify: Boolean get() = getBool("r_justify", false); set(v) = putBool("r_justify", v)
    var readerContinuous: Boolean get() = getBool("r_cont", false); set(v) = putBool("r_cont", v)
    var keepScreenOn: Boolean get() = getBool("r_awake", true); set(v) = putBool("r_awake", v)
    var volumeKeys: Boolean get() = getBool("r_volume", false); set(v) = putBool("r_volume", v)

    // Téléchargement et mises à jour des fics
    var gentleSpeed: Boolean get() = getBool("dl_gentle", true); set(v) = putBool("dl_gentle", v)
    var autoAddVisited: Boolean get() = getBool("auto_add", true); set(v) = putBool("auto_add", v)
    var checkOnLaunch: Boolean get() = getBool("check_launch", false); set(v) = putBool("check_launch", v)
    var checkDaily: Boolean get() = getBool("check_daily", true); set(v) = putBool("check_daily", v)
    var autoDownloadNew: Boolean get() = getBool("auto_dl_new", true); set(v) = putBool("auto_dl_new", v)
    var inactiveMonths: Int get() = getInt("inactive_months", 6); set(v) = putInt("inactive_months", v)
    var packWordsPerPart: Int get() = getInt("pack_words", 80000); set(v) = putInt("pack_words", v)

    // Confidentialité
    var adblock: Boolean get() = getBool("adblock", true); set(v) = putBool("adblock", v)
    var antiTracking: Boolean get() = getBool("antitrack", true); set(v) = putBool("antitrack", v)
    var httpsOnly: Boolean get() = getBool("https_only", true); set(v) = putBool("https_only", v)
    var safeBrowsing: Boolean get() = getBool("safe_browsing", true); set(v) = putBool("safe_browsing", v)
    var clearOnExit: Boolean get() = getBool("clear_exit", false); set(v) = putBool("clear_exit", v)
    var appLock: Boolean get() = getBool("app_lock", false); set(v) = putBool("app_lock", v)
    var adblockDisabledSites: List<String> get() = getList("adblock_off"); set(v) = putList("adblock_off", v)

    // Navigation avancée (cachée)
    var advancedUnlocked: Boolean get() = getBool("adv_unlocked", false); set(v) = putBool("adv_unlocked", v)
    var advancedBrowsing: Boolean get() = getBool("adv_on", false); set(v) = putBool("adv_on", v)
    var searchEngine: Int get() = getInt("engine", 0); set(v) = putInt("engine", v)

    // Découverte
    var pinnedFandoms: List<String> get() = getList("pinned_fandoms"); set(v) = putList("pinned_fandoms", v)
    var lastSeenNews: Long get() = getLong("news_seen", 0L); set(v) = putLong("news_seen", v)
}
