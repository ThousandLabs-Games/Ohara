package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.translate.Translate

/**
 * Réglages par site : chaque fonction ajoutée aux pages peut être coupée partout (réglage général)
 * ou seulement sur certains sites, quand elle gêne une fonction du site lui-même.
 */
object SiteRules {
    enum class Feature(val key: String, val label: String, val sub: String) {
        PULL("pull", "Glisser pour changer de chapitre", "Tout en bas ou tout en haut d'un chapitre"),
        BARS("bars", "Cacher les barres en lisant", "Les barres disparaissent quand vous descendez"),
        READER("reader", "Mode lecture d'office", "Les chapitres s'ouvrent en mode lecture"),
        MASK("mask", "Masquage des passages", "Vos règles, les promos et les notes d'auteur"),
        SELECT("select", "Masquer en touchant une phrase", "La barre « Masquer » après un appui long sur du texte"),
        LINKS("links", "Menu des liens", "Appui long sur un lien : nouvel onglet, favoris…"),
        TRANSLATE("translate", "Traduction", "Quand la traduction est activée");

        /** Valeur générale (tous les sites). */
        var global: Boolean
            get() = when (this) {
                PULL -> PageTools.pullNav
                BARS -> BrowserHolder.autoHideBars
                READER -> PageTools.autoReader
                TRANSLATE -> Translate.enabled
                else -> Prefs.getBool("feat_$key", true)
            }
            set(v) {
                when (this) {
                    PULL -> PageTools.pullNav = v
                    BARS -> BrowserHolder.autoHideBars = v
                    READER -> PageTools.autoReader = v
                    TRANSLATE -> Translate.enabled = v
                    else -> Prefs.putBool("feat_$key", v)
                }
            }
    }

    /** Coupé d'office sur ces sites, sauf si vous le rallumez : WebNovel enchaîne déjà les chapitres en descendant. */
    private val DEFAULT_OFF = mapOf(Feature.PULL to setOf("webnovel.com"))

    fun host(url: String?): String =
        runCatching { java.net.URI(url ?: "").host?.lowercase() }.getOrNull().orEmpty()
            .removePrefix("www.").removePrefix("m.")

    private fun key(f: Feature, host: String) = "site_${f.key}_$host"

    /** Réglage propre au site : true, false, ou null (suit le réglage général). */
    fun override(f: Feature, host: String): Boolean? = when (Prefs.getString(key(f, host), "")) {
        "1" -> true; "0" -> false; else -> null
    }

    private fun defaultFor(f: Feature, host: String) =
        f.global && DEFAULT_OFF[f]?.none { host == it || host.endsWith(".$it") } != false

    fun on(f: Feature, url: String?): Boolean {
        val h = host(url)
        if (h.isEmpty() || h == "hors-ligne.ohara") return f.global
        return override(f, h) ?: defaultFor(f, h)
    }

    /** Fixe la valeur pour ce site (enregistrée seulement si elle diffère de ce qui s'appliquerait sans réglage). */
    fun set(f: Feature, url: String?, value: Boolean) {
        val h = host(url)
        if (h.isEmpty()) return
        if (value == defaultFor(f, h)) Prefs.remove(key(f, h)) else Prefs.putString(key(f, h), if (value) "1" else "0")
        val list = Prefs.getList("site_hosts").toMutableSet()
        if (Feature.entries.any { override(it, h) != null }) list += h else list -= h
        Prefs.putList("site_hosts", list.toList())
    }

    fun reset(host: String) {
        Feature.entries.forEach { Prefs.remove(key(it, host)) }
        Prefs.putList("site_hosts", Prefs.getList("site_hosts") - host)
    }

    /** Sites qui ont des réglages particuliers. */
    fun hosts(): List<String> = Prefs.getList("site_hosts").filter { h -> Feature.entries.any { override(it, h) != null } }
}
