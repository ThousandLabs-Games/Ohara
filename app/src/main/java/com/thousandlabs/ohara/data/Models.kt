package com.thousandlabs.ohara.data

import org.json.JSONArray
import org.json.JSONObject

/** Les sites pris en charge. */
enum class Site(val code: String, val label: String, val short: String) {
    FFN("ffn", "FanFiction.net", "FFN"),
    SB("sb", "SpaceBattles", "SB"),
    WN("wn", "WebNovel", "WN"),
    OTHER("gen", "Autre site", "Web"),
    /** Fic gardée seulement sur le téléphone (importée d'un fichier), sans site à interroger. */
    LOCAL("loc", "Sur le téléphone", "Local");

    companion object {
        fun of(code: String): Site = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class FicStatus(val code: String, val label: String) {
    COMPLETE("complete", "Terminée"),
    ONGOING("ongoing", "En cours"),
    UNKNOWN("unknown", "Statut inconnu");

    companion object {
        fun of(code: String?): FicStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Un chapitre tel qu'annoncé par le site. */
data class ChapterInfo(
    val title: String,
    val url: String,
    /** Identifiant propre au site (numéro de message SpaceBattles, chapitre WebNovel…). */
    val key: String = "",
    val words: Int = 0,
    val date: Long = 0L,
    /** Chapitre payant ou verrouillé (WebNovel). */
    val locked: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("t", title); put("u", url)
        if (key.isNotEmpty()) put("k", key)
        if (words > 0) put("w", words)
        if (date > 0) put("d", date)
        if (locked) put("l", true)
    }

    companion object {
        fun fromJson(o: JSONObject) = ChapterInfo(
            title = o.optString("t"), url = o.optString("u"), key = o.optString("k"),
            words = o.optInt("w"), date = o.optLong("d"), locked = o.optBoolean("l")
        )
    }
}

/** Un passage surligné, avec une note éventuelle. */
data class Highlight(
    val chapter: Int,
    val text: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().put("c", chapter).put("t", text).put("n", note).put("a", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Highlight(o.optInt("c"), o.optString("t"), o.optString("n"), o.optLong("a"))
    }
}

/** Fiche personnage, remplie par le lecteur. */
data class CharacterNote(val name: String, val note: String) {
    fun toJson() = JSONObject().put("n", name).put("d", note)

    companion object {
        fun fromJson(o: JSONObject) = CharacterNote(o.optString("n"), o.optString("d"))
    }
}

/**
 * Une fic : ce que le site annonce (titre, auteur, statistiques…) et ce que le lecteur en fait
 * (étagères, progression, note, surlignages…). Les résultats de recherche utilisent la même forme.
 */
data class Fic(
    val id: String,
    val site: Site,
    val storyId: String,
    val url: String,
    val title: String,
    val author: String = "",
    val authorUrl: String = "",
    val fandoms: List<String> = emptyList(),
    val summary: String = "",
    val rating: String = "",
    val language: String = "",
    val genres: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: FicStatus = FicStatus.UNKNOWN,
    val words: Long = 0,
    val chapterCount: Int = 0,
    val chapters: List<ChapterInfo> = emptyList(),
    val published: Long = 0,
    val updated: Long = 0,
    val favs: Int = 0,
    val follows: Int = 0,
    val reviews: Int = 0,
    val likes: Int = 0,
    val views: Int = 0,
    val replies: Int = 0,
    val cover: String = "",
    val crossover: Boolean = false,
    /** Page du fandom sur le site (FanFiction.net), pour chercher des fics voisines. */
    val fandomUrl: String = "",
    // ----- côté lecteur -----
    val shelves: Set<String> = emptySet(),
    val addedAt: Long = 0,
    val lastOpened: Long = 0,
    /** Adresse exacte de la dernière page lue dans le navigateur (pour reprendre au bon endroit). */
    val resumeUrl: String = "",
    val progressChapter: Int = 0,
    val progressPos: Float = 0f,
    val maxChapterRead: Int = -1,
    val note: Int = -1,
    val comment: String = "",
    val abandonReason: String = "",
    val characterNotes: List<CharacterNote> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val newChapters: Int = 0,
    val completedAlert: Boolean = false,
    val deletedFromSite: Boolean = false,
    val altUrls: List<String> = emptyList(),
    val lastChecked: Long = 0,
    val secondsRead: Long = 0,
    val wordsRead: Long = 0
) {
    val fandomLabel: String get() = fandoms.joinToString(" × ").ifEmpty { "Fandom inconnu" }

    /** Nombre de chapitres connus (liste détaillée, sinon chiffre annoncé). */
    val totalChapters: Int get() = if (chapters.isNotEmpty()) chapters.size else chapterCount

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("site", site.code); put("sid", storyId); put("url", url); put("title", title)
        put("author", author); put("authorUrl", authorUrl); put("fandoms", JSONArray(fandoms))
        put("summary", summary); put("rating", rating); put("lang", language)
        put("genres", JSONArray(genres)); put("chars", JSONArray(characters)); put("tags", JSONArray(tags))
        put("status", status.code); put("words", words); put("chCount", chapterCount)
        put("chapters", JSONArray().also { a -> chapters.forEach { a.put(it.toJson()) } })
        put("pub", published); put("upd", updated); put("favs", favs); put("follows", follows)
        put("reviews", reviews); put("likes", likes); put("views", views); put("replies", replies)
        put("cover", cover); put("xover", crossover); if (fandomUrl.isNotEmpty()) put("fUrl", fandomUrl)
        put("shelves", JSONArray(shelves.toList())); put("added", addedAt); put("opened", lastOpened); if (resumeUrl.isNotEmpty()) put("rUrl", resumeUrl)
        put("pCh", progressChapter); put("pPos", progressPos.toDouble()); put("maxRead", maxChapterRead)
        put("note", note); put("comment", comment); put("abandon", abandonReason)
        put("charNotes", JSONArray().also { a -> characterNotes.forEach { a.put(it.toJson()) } })
        put("hl", JSONArray().also { a -> highlights.forEach { a.put(it.toJson()) } })
        put("newCh", newChapters); put("doneAlert", completedAlert); put("deleted", deletedFromSite)
        put("alt", JSONArray(altUrls)); put("checked", lastChecked)
        put("secRead", secondsRead); put("wRead", wordsRead)
    }

    companion object {
        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

        fun fromJson(o: JSONObject): Fic = Fic(
            id = o.optString("id"), site = Site.of(o.optString("site")), storyId = o.optString("sid"),
            url = o.optString("url"), title = o.optString("title"), author = o.optString("author"),
            authorUrl = o.optString("authorUrl"), fandoms = o.optJSONArray("fandoms").strings(),
            summary = o.optString("summary"), rating = o.optString("rating"), language = o.optString("lang"),
            genres = o.optJSONArray("genres").strings(), characters = o.optJSONArray("chars").strings(),
            tags = o.optJSONArray("tags").strings(), status = FicStatus.of(o.optString("status")),
            words = o.optLong("words"), chapterCount = o.optInt("chCount"),
            chapters = o.optJSONArray("chapters")?.let { a -> List(a.length()) { ChapterInfo.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            published = o.optLong("pub"), updated = o.optLong("upd"), favs = o.optInt("favs"),
            follows = o.optInt("follows"), reviews = o.optInt("reviews"), likes = o.optInt("likes"),
            views = o.optInt("views"), replies = o.optInt("replies"), cover = o.optString("cover"),
            crossover = o.optBoolean("xover"), fandomUrl = o.optString("fUrl"),
            shelves = o.optJSONArray("shelves").strings().toSet(), addedAt = o.optLong("added"),
            lastOpened = o.optLong("opened"), resumeUrl = o.optString("rUrl"), progressChapter = o.optInt("pCh"),
            progressPos = o.optDouble("pPos", 0.0).toFloat(), maxChapterRead = o.optInt("maxRead", -1),
            note = o.optInt("note", -1), comment = o.optString("comment"), abandonReason = o.optString("abandon"),
            characterNotes = o.optJSONArray("charNotes")?.let { a -> List(a.length()) { CharacterNote.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            highlights = o.optJSONArray("hl")?.let { a -> List(a.length()) { Highlight.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            newChapters = o.optInt("newCh"), completedAlert = o.optBoolean("doneAlert"),
            deletedFromSite = o.optBoolean("deleted"), altUrls = o.optJSONArray("alt").strings(),
            lastChecked = o.optLong("checked"), secondsRead = o.optLong("secRead"), wordsRead = o.optLong("wRead")
        )

        fun makeId(site: Site, storyId: String) = "${site.code}:$storyId"
    }

    /**
     * Met à jour les informations venant du site sans toucher à ce que le lecteur a fait
     * (étagères, progression, notes…). Les valeurs vides du nouveau relevé ne remplacent pas les anciennes.
     */
    fun mergeSiteData(n: Fic): Fic = copy(
        url = n.url.ifEmpty { url }, title = n.title.ifEmpty { title }, author = n.author.ifEmpty { author },
        authorUrl = n.authorUrl.ifEmpty { authorUrl }, fandoms = n.fandoms.ifEmpty { fandoms },
        summary = n.summary.ifEmpty { summary }, rating = n.rating.ifEmpty { rating },
        language = n.language.ifEmpty { language }, genres = n.genres.ifEmpty { genres },
        characters = n.characters.ifEmpty { characters }, tags = n.tags.ifEmpty { tags },
        status = if (n.status != FicStatus.UNKNOWN) n.status else status,
        words = if (n.words > 0) n.words else words,
        chapterCount = if (n.totalChapters > 0) n.totalChapters else chapterCount,
        chapters = n.chapters.ifEmpty { chapters },
        published = if (n.published > 0) n.published else published,
        updated = if (n.updated > 0) n.updated else updated,
        favs = if (n.favs > 0) n.favs else favs, follows = if (n.follows > 0) n.follows else follows,
        reviews = if (n.reviews > 0) n.reviews else reviews, likes = if (n.likes > 0) n.likes else likes,
        views = if (n.views > 0) n.views else views, replies = if (n.replies > 0) n.replies else replies,
        cover = n.cover.ifEmpty { cover }, crossover = n.crossover || crossover,
        fandomUrl = n.fandomUrl.ifEmpty { fandomUrl }
    )
}

/** Les étagères proposées d'office ; le lecteur peut en ajouter d'autres. */
object Shelves {
    const val TO_READ = "a_lire"
    const val READING = "en_cours"
    const val FAVORITES = "favoris"
    const val CLAUDE = "claude"
    const val DONE = "lues"
    const val ABANDONED = "abandonnees"

    val builtIn = linkedMapOf(
        TO_READ to "À lire",
        READING to "En cours",
        FAVORITES to "Favoris",
        CLAUDE to "Pour Claude",
        DONE to "Déjà lues",
        ABANDONED to "Abandonnées"
    )

    fun custom(): List<String> = Prefs.getList("custom_shelves")

    fun all(): List<Pair<String, String>> =
        builtIn.map { it.key to it.value } + custom().map { "c:$it" to it }

    fun label(id: String): String = builtIn[id] ?: id.removePrefix("c:")

    fun addCustom(name: String) {
        val n = name.trim()
        if (n.isEmpty() || custom().contains(n)) return
        Prefs.putList("custom_shelves", custom() + n)
    }

    fun removeCustom(name: String) = Prefs.putList("custom_shelves", custom() - name)
}
