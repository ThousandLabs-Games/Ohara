package com.thousandlabs.ohara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.LinkOpener
import com.thousandlabs.ohara.net.Fetcher
import com.thousandlabs.ohara.net.Http
import com.thousandlabs.ohara.net.SiteError
import com.thousandlabs.ohara.sources.Cleaner
import com.thousandlabs.ohara.sources.FanFictionNet
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup

/**
 * Ouvrir un lien de fic, de bout en bout.
 *
 * FanFiction.net demande souvent une vérification « Je ne suis pas un robot » que la lecture invisible ne peut pas passer.
 * Dans ce cas (ou si le site met trop de temps), la page s'ouvre dans le navigateur d'Ohara : dès qu'elle s'affiche
 * normalement, Ohara lit la fic directement sur la page et continue tout seul (lecture, ajout ou téléchargement).
 */
object LinkFlow {
    data class Pending(val url: String, val ficId: String, val shelf: String, val read: Boolean, val download: Boolean)

    /** Un lien est en cours d'ouverture (pour afficher un indicateur). */
    var working by mutableStateOf(false)
        private set
    /** Lien qui attend que la page soit affichée dans le navigateur. */
    var pending by mutableStateOf<Pending?>(null)

    fun open(text: String, shelf: String = Shelves.READING, read: Boolean = true, download: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val url = Sources.extractUrl(text)
        val resolved = url?.let { Sources.resolve(it) }
        if (url == null || resolved == null) {
            Toast.show(if (url == null) "Aucun lien trouvé dans le texte." else "Ce lien ne vient pas d'un site de fics pris en charge.")
            onDone(false); return
        }
        working = true
        Toast.show("Ouverture de la fic…")
        AppScope.launch {
            val r = runCatching { withTimeout(30_000) { LinkOpener.open(url, shelf) } }
            working = false
            r.onSuccess { o ->
                finish(o.fic.id, o.chapter, read, download, o.fic.title)
                onDone(true)
            }.onFailure { e ->
                if (e is SiteError) { Toast.show(e.message ?: "Page introuvable."); onDone(false); return@onFailure }
                // vérification demandée, délai dépassé ou site injoignable : on passe par le navigateur visible
                val why = when (e) {
                    is TimeoutCancellationException -> "Le site met du temps à répondre"
                    is com.thousandlabs.ohara.net.VerificationNeeded -> "Le site demande une vérification"
                    else -> e.message ?: "Lecture impossible"
                }
                pending = Pending(url, resolved.second.ficId, shelf, read, download)
                BrowserHolder.verifying = true
                Toast.show("$why : la page s'ouvre dans le navigateur. Cochez la case si elle apparaît, Ohara continue tout seul.")
                Nav.openBrowser(url)
                onDone(false)
            }
        }
    }

    /** Le lecteur n'a pas pu lire un chapitre : on le vérifie dans le navigateur, puis on revient lire. */
    fun verifyThenRead(ficId: String, chapterUrl: String) {
        pending = Pending(chapterUrl, ficId, Shelves.READING, read = true, download = false)
        BrowserHolder.verifying = true
        Nav.openBrowser(chapterUrl)
    }

    private fun finish(ficId: String, chapter: Int, read: Boolean, download: Boolean, title: String) {
        if (download) Library.get(ficId)?.let { Downloads.enqueue(it) }
        if (read) {
            Nav.home(Nav.TAB_LIBRARY)
            Nav.push(Screen.Reader(ficId, chapter))
        } else Toast.show("« $title » ajoutée." + if (download) " Téléchargement lancé." else "")
    }

    /**
     * Une page de fic vient de s'afficher normalement dans le navigateur (sans vérification en cours).
     * FanFiction.net : la fiche et le chapitre affiché sont lus directement sur la page, sans nouvelle requête.
     */
    fun onBrowserPage(url: String, html: String) {
        val (_, info) = Sources.resolve(url) ?: return
        Fetcher.verificationDone(url)
        if (Http.isChallengeHtml(html)) return
        val p = pending
        AppScope.launch {
            if (info.site == Site.FFN) runCatching {
                withContext(Dispatchers.Default) {
                    val doc = Jsoup.parse(html, url)
                    if (doc.selectFirst("#profile_top") == null) return@withContext
                    val fic = FanFictionNet.parseStory(doc, info.storyId)
                    val known = Library.contains(fic.id)
                    val chapterPage = info.chapterIndex != null
                    if (known || p?.ficId == fic.id || (chapterPage && com.thousandlabs.ohara.data.Prefs.autoAddVisited)) {
                        val saved = Library.upsert(fic, if (known) null else (p?.shelf ?: Shelves.READING))
                        val idx = info.chapterIndex ?: 0
                        doc.selectFirst("#storytext")?.let { el -> if (!Library.hasChapter(saved.id, idx)) Library.writeChapter(saved.id, idx, Cleaner.clean(el)) }
                    }
                }
            }
            if (p != null && p.ficId == info.ficId) {
                pending = null
                BrowserHolder.verifying = false
                runCatching { withTimeout(30_000) { LinkOpener.open(url, p.shelf) } }
                    .onSuccess { o -> finish(o.fic.id, o.chapter, p.read, p.download, o.fic.title) }
                    .onFailure { e -> Toast.show("La page est ouverte, mais Ohara n'a pas pu lire la fic : ${e.message ?: "erreur"}. Touchez « Lire dans Ohara » pour réessayer.") }
            } else if (p == null) BrowserHolder.maybeAutoAdd(url)
        }
    }
}
