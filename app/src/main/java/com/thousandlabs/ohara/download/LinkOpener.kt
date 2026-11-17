package com.thousandlabs.ohara.download

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.sources.GenericSource
import com.thousandlabs.ohara.sources.LinkInfo
import com.thousandlabs.ohara.sources.SpaceBattles
import com.thousandlabs.ohara.sources.Sources
import java.io.IOException

/**
 * Un lien collé ou partagé → la fic dans la bibliothèque, positionnée au bon chapitre.
 */
object LinkOpener {

    data class Opened(val fic: Fic, val chapter: Int)

    fun isFicLink(text: String): Boolean = Sources.resolve(text) != null

    suspend fun open(text: String, shelf: String = Shelves.READING): Opened {
        val url = Sources.extractUrl(text) ?: throw IOException("Aucun lien trouvé.")
        // Lien déjà connu (chapitre d'une fic de la bibliothèque, ou ancienne adresse fusionnée)
        Library.fics.value.forEach { f ->
            val idx = f.chapters.indexOfFirst { it.url.substringBefore("#") == url.substringBefore("#") }
            if (idx >= 0) return Opened(f, idx).also { setProgress(f, idx) }
            if (f.url == url || url in f.altUrls) return Opened(f, f.progressChapter)
        }
        var (src, info) = Sources.resolve(url) ?: throw IOException("Ce lien ne vient pas d'un site pris en charge.")
        if (info.site == Site.SB && info.storyId.isEmpty()) {
            info = SpaceBattles.resolvePost(info.chapterKey ?: "") ?: throw IOException("Message SpaceBattles introuvable.")
        }
        if (info.site == Site.OTHER) GenericSource.rememberStart(info.storyId, url)
        val existing = Library.get(info.ficId)
        val fic = if (existing != null && existing.chapters.isNotEmpty() && chapterOf(existing, info) != null) existing
        else Library.upsert(src.fetchInfo(info.storyId), if (existing == null) shelf else null)
        val ch = chapterOf(fic, info) ?: fic.progressChapter.takeIf { existing != null } ?: 0
        setProgress(fic, ch)
        if (existing == null) Library.setShelf(fic.id, shelf, true)
        return Opened(Library.get(fic.id) ?: fic, ch)
    }

    private fun chapterOf(fic: Fic, info: LinkInfo): Int? {
        info.chapterIndex?.let { return it.coerceIn(0, (fic.totalChapters - 1).coerceAtLeast(0)) }
        info.chapterKey?.let { k -> return fic.chapters.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
        return null
    }

    private fun setProgress(fic: Fic, ch: Int) {
        Library.update(fic.id) {
            if (it.progressChapter == ch) it.copy(lastOpened = System.currentTimeMillis())
            else it.copy(progressChapter = ch, progressPos = 0f, lastOpened = System.currentTimeMillis(),
                maxChapterRead = maxOf(it.maxChapterRead, ch - 1))
        }
    }
}
