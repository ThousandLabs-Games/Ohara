package com.thousandlabs.ohara.sources

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Site
import java.io.IOException
import java.net.URI

/**
 * Fics gardées seulement sur le téléphone (importées d'un fichier) : aucun site à interroger.
 * Leurs chapitres s'ouvrent à l'adresse interne du lecteur hors ligne (https://hors-ligne.ohara/<fic>/<chapitre>),
 * ce qui garde le suivi de lecture, la traduction et les surlignages comme pour une fic d'un site.
 */
object LocalSource : Source {
    const val HOST = "hors-ligne.ohara"
    override val site = Site.LOCAL

    override fun matches(host: String) = host == HOST

    override fun parseLink(url: String): LinkInfo? {
        val parts = runCatching { URI(url).path.trim('/').split('/') }.getOrNull() ?: return null
        val id = parts.getOrNull(0)?.takeIf { it.startsWith("loc:") } ?: return null
        return LinkInfo(site, id.removePrefix("loc:"), chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    override fun storyUrl(storyId: String) = "https://$HOST/loc:$storyId/0"
    override fun chapterUrl(fic: Fic, index: Int) = "https://$HOST/${fic.id}/$index"

    override suspend fun fetchInfo(storyId: String): Fic =
        Library.get(Fic.makeId(site, storyId)) ?: throw IOException("Cette fic n'existe que dans le fichier importé.")

    override suspend fun fetchChapter(fic: Fic, index: Int): String =
        Library.readChapter(fic.id, index) ?: throw IOException("Ce chapitre n'est pas dans le fichier importé.")
}
