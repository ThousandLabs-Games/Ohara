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
        val info = resolved.second
        val known = Library.get(info.ficId)?.chapters?.isNotEmpty() == true
        if (info.site == Site.FFN && !known) {
            // FanFiction.net bloque la lecture invisible : la page s'ouvre, et la fic est lue directement dessus
            pending = Pending(url, info.ficId, shelf, read, download)
            Toast.show("Ouverture de la page : la fic s'ouvrira dans Ohara dès qu'elle sera affichée.")
            Nav.openBrowser(url)
            onDone(false)
            return
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
     * Lit la fiche (et le chapitre affiché) directement dans le HTML d'une page déjà ouverte.
     * FanFiction.net seulement : c'est le site qui bloque la lecture invisible.
     * @param force ajoute la fic même si elle n'est pas encore dans la bibliothèque.
     */
    private suspend fun importPage(url: String, html: String, force: Boolean, shelf: String?): Boolean {
        val (_, info) = Sources.resolve(url) ?: return false
        if (info.site != Site.FFN || Http.isChallengeHtml(html)) return false
        return runCatching {
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html, url)
                if (doc.selectFirst("#profile_top") == null) return@withContext false
                val fic = FanFictionNet.parseStory(doc, info.storyId)
                if (!force && !Library.contains(fic.id)) return@withContext false
                val saved = Library.upsert(fic, if (Library.contains(fic.id)) null else (shelf ?: Shelves.READING))
                val idx = info.chapterIndex ?: 0
                doc.selectFirst("#storytext")?.let { el -> if (!Library.hasChapter(saved.id, idx)) Library.writeChapter(saved.id, idx, Cleaner.clean(el)) }
                true
            }
        }.getOrDefault(false)
    }

    /** Boutons du navigateur (« Ajouter », « Lire dans Ohara », « Télécharger ») : la fic est lue sur la page affichée. */
    fun openFromPage(web: android.webkit.WebView, url: String, shelf: String = Shelves.READING, read: Boolean = true, download: Boolean = false) {
        if (working) { Toast.show("Un instant, Ohara termine l'opération en cours…"); return }
        working = true
        Toast.show(when { read -> "Ouverture dans Ohara…"; download -> "Préparation du téléchargement…"; else -> "Ajout à la bibliothèque…" })
        web.evaluateJavascript("document.documentElement.outerHTML") { raw ->
            val html = runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrDefault("")
            AppScope.launch {
                if (html.isNotEmpty()) importPage(url, html, force = true, shelf = shelf)
                Fetcher.verificationDone(url)
                val r = runCatching { withTimeout(30_000) { LinkOpener.open(url, shelf) } }
                working = false
                r.onSuccess { o ->
                    // WebNovel : le chapitre affiché est gardé tel quel (pas besoin de le redemander au site)
                    if (o.fic.site == Site.WN && html.isNotEmpty() && !Library.hasChapter(o.fic.id, o.chapter)) runCatching {
                        withContext(Dispatchers.Default) {
                            val doc = Jsoup.parse(html, url)
                            val el = doc.selectFirst(".cha-words") ?: doc.selectFirst(".cha-content") ?: doc.selectFirst(".chapter_content")
                            if (el != null && el.text().length > 200) {
                                el.select(".para-comment, .j_open_para_comment, .cha-bts, script, style").remove()
                                Library.writeChapter(o.fic.id, o.chapter, Cleaner.clean(el))
                            }
                        }
                    }
                    finish(o.fic.id, o.chapter, read, download, o.fic.title)
                }
                    .onFailure { e ->
                        Toast.show("Ohara n'a pas pu lire cette fic : " + when (e) {
                            is TimeoutCancellationException -> "le site ne répond pas. Réessayez dans un instant."
                            else -> e.message ?: "erreur inconnue."
                        })
                    }
            }
        }
    }

    /** Une page de fic vient de s'afficher normalement dans le navigateur. */
    fun onBrowserPage(url: String, html: String) {
        val (_, info) = Sources.resolve(url) ?: return
        if (Http.isChallengeHtml(html)) return
        Fetcher.verificationDone(url)
        val p = pending
        AppScope.launch {
            val autoAdd = info.chapterIndex != null && com.thousandlabs.ohara.data.Prefs.autoAddVisited
            importPage(url, html, force = p?.ficId == info.ficId || autoAdd, shelf = p?.shelf)
            if (p != null && p.ficId == info.ficId) {
                pending = null
                BrowserHolder.verifying = false
                working = true
                runCatching { withTimeout(30_000) { LinkOpener.open(url, p.shelf) } }
                    .onSuccess { o -> working = false; finish(o.fic.id, o.chapter, p.read, p.download, o.fic.title) }
                    .onFailure { e -> working = false; Toast.show("La page est ouverte, mais Ohara n'a pas pu lire la fic : ${e.message ?: "erreur"}. Touchez « Lire dans Ohara » pour réessayer.") }
            } else if (p == null) BrowserHolder.maybeAutoAdd(url)
        }
    }
}
