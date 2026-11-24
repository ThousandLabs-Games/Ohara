package com.thousandlabs.ohara.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.net.SiteError
import com.thousandlabs.ohara.net.VerificationNeeded
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Une fic dans la file de téléchargement. */
data class DownloadJob(
    val ficId: String,
    val title: String,
    val done: Int = 0,
    val total: Int = 0,
    val state: State = State.WAITING,
    val message: String = "",
    /** Adresse à ouvrir pour passer la vérification anti-robot. */
    val verifyUrl: String? = null,
    /** Chapitres précis à télécharger (null = tous ceux qui manquent). */
    val only: List<Int>? = null
) {
    enum class State { WAITING, RUNNING, PAUSED, VERIFY, ERROR }
}

/**
 * File de téléchargement : une fic après l'autre, un chapitre après l'autre, avec des pauses
 * pour ne pas se faire bloquer. Continue écran éteint grâce à un service de premier plan.
 */
object Downloads {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var worker: Job? = null

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    fun init(context: Context) { app = context.applicationContext }

    private fun edit(id: String, f: (DownloadJob) -> DownloadJob) {
        _jobs.update { l -> l.map { if (it.ficId == id) f(it) else it } }
        DownloadService.refresh(app)
    }

    /** Ajoute une fic à la file (tous les chapitres manquants, ou seulement ceux indiqués). */
    fun enqueue(fic: Fic, only: List<Int>? = null) {
        val cur = _jobs.value
        val existing = cur.firstOrNull { it.ficId == fic.id }
        if (existing != null) {
            if (existing.state != DownloadJob.State.RUNNING) edit(fic.id) {
                it.copy(state = DownloadJob.State.WAITING, message = "", verifyUrl = null,
                    only = if (only == null || it.only == null) null else (it.only + only).distinct())
            }
        } else {
            _jobs.update { l -> if (l.any { it.ficId == fic.id }) l else l + DownloadJob(fic.id, fic.title, only = only) }
        }
        start()
    }

    fun cancel(id: String) {
        _jobs.update { l -> l.filterNot { it.ficId == id } }
        if (_jobs.value.none { it.state == DownloadJob.State.RUNNING }) restartWorker()
        DownloadService.refresh(app)
    }

    fun pause(id: String) = edit(id) { it.copy(state = DownloadJob.State.PAUSED, message = "En pause") }

    fun resume(id: String) {
        edit(id) { it.copy(state = DownloadJob.State.WAITING, message = "", verifyUrl = null) }
        start()
    }

    /** Après une vérification réussie dans le navigateur : on relance tout ce qui attendait. */
    fun resumeVerified() {
        _jobs.update { l -> l.map {
            if (it.state == DownloadJob.State.VERIFY) it.copy(state = DownloadJob.State.WAITING, message = "", verifyUrl = null) else it
        } }
        start()
    }

    fun clearFinishedErrors() {
        _jobs.update { l -> l.filterNot { it.state == DownloadJob.State.ERROR } }
        DownloadService.refresh(app)
    }

    private fun restartWorker() {
        worker?.cancel()
        worker = null
        start()
    }

    private fun start() {
        if (worker?.isActive == true) return
        if (_jobs.value.none { it.state == DownloadJob.State.WAITING }) return
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        worker = scope.launch {
            while (isActive) {
                val next = _jobs.value.firstOrNull { it.state == DownloadJob.State.WAITING } ?: break
                runJob(next)
            }
            DownloadService.refresh(app)
        }
    }

    private suspend fun runJob(job: DownloadJob) {
        val id = job.ficId
        edit(id) { it.copy(state = DownloadJob.State.RUNNING, message = "Lecture de la fiche…") }
        try {
            var fic = Library.get(id) ?: run { cancel(id); return }
            val src = Sources.of(fic.site)
            // fiche à jour (liste des chapitres)
            if (fic.chapters.isEmpty() || job.only == null) {
                val info = src.fetchInfo(fic.storyId)
                fic = Library.upsert(info)
            }
            val wanted = (job.only ?: fic.chapters.indices.toList())
                .filter { it in fic.chapters.indices && !Library.hasChapter(id, it) && !fic.chapters[it].locked }
            edit(id) { it.copy(total = wanted.size, done = 0, title = fic.title, message = "") }
            var done = 0
            for (i in wanted) {
                if (_jobs.value.firstOrNull { it.ficId == id }?.state != DownloadJob.State.RUNNING) return
                val html = fetchWithRetry(src, fic, i)
                Library.writeChapter(id, i, html)
                done++
                edit(id) { it.copy(done = done, message = fic.chapters[i].title) }
            }
            // résumé absent (SpaceBattles, sites ajoutés) : début du premier chapitre
            if (fic.summary.isBlank()) Library.readChapter(id, 0)?.let { first ->
                val text = com.thousandlabs.ohara.sources.Cleaner.toText(Masking.applyToHtml(fic, first).html)
                Library.update(id) { it.copy(summary = text.take(420).substringBeforeLast(' ') + "…") }
            }
            // nombre de mots calculé si le site ne le donne pas
            if (fic.words <= 0) {
                val total = fic.chapters.indices.sumOf { idx -> Library.readChapter(id, idx)?.let { Library.countWords(it) } ?: 0 }
                if (total > 0) Library.update(id) { it.copy(words = total.toLong()) }
            }
            _jobs.update { l -> l.filterNot { it.ficId == id } }
            if (done > 0) Notify.doneDownload(app, "${fic.title} : $done chapitre${if (done > 1) "s" else ""}")
        } catch (e: VerificationNeeded) {
            edit(id) { it.copy(state = DownloadJob.State.VERIFY, verifyUrl = e.url, message = "Vérification demandée par le site") }
            Notify.verification(app, Sources.forUrl(e.url)?.site?.label ?: "le site")
        } catch (e: SiteError) {
            if (e.code == 404) Library.update(id) { it.copy(deletedFromSite = true) }
            edit(id) { it.copy(state = DownloadJob.State.ERROR, message = if (e.code == 404) "Introuvable sur le site (supprimée ?)" else "Erreur du site (${e.code})") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            edit(id) { it.copy(state = DownloadJob.State.ERROR, message = e.message ?: "Erreur inconnue") }
        }
    }

    /** Une coupure de réseau passagère ne fait plus échouer toute la fic : deux nouveaux essais, après une pause. */
    private suspend fun fetchWithRetry(src: com.thousandlabs.ohara.sources.Source, fic: Fic, i: Int): String {
        repeat(2) { attempt ->
            try { return src.fetchChapter(fic, i) }
            catch (e: java.io.IOException) {
                if (e is SiteError || e is VerificationNeeded) throw e
                edit(fic.id) { it.copy(message = "Réseau instable, nouvel essai (${attempt + 1}/2)…") }
                kotlinx.coroutines.delay(4000L * (attempt + 1))
            }
        }
        return src.fetchChapter(fic, i)
    }

    /**
     * Texte d'un chapitre pour le lecteur : depuis le téléphone, ou téléchargé tout de suite s'il manque.
     */
    suspend fun ensureChapter(fic: Fic, index: Int): String = mutex.withLock {
        Library.readChapter(fic.id, index)?.let { return@withLock it }
        var f = fic
        if (index !in f.chapters.indices) {
            f = Library.upsert(Sources.of(f.site).fetchInfo(f.storyId))
        }
        val html = Sources.of(f.site).fetchChapter(f, index)
        if (Library.contains(f.id)) Library.writeChapter(f.id, index, html)
        html
    }

    /** Ajoute une fic à la bibliothèque à partir de ses informations (sans télécharger les chapitres). */
    suspend fun addFic(siteFic: Fic, shelf: String = Shelves.TO_READ, fetchFull: Boolean = true): Fic {
        val full = if (fetchFull) runCatching { Sources.of(siteFic.site).fetchInfo(siteFic.storyId) }.getOrElse { siteFic } else siteFic
        return Library.upsert(siteFic.mergeSiteData(full), shelf)
    }
}
