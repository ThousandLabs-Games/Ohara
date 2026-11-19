package com.thousandlabs.ohara.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
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
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Vérifie si les fics de la bibliothèque ont de nouveaux chapitres ou sont terminées. */
object UpdateChecker {
    data class Progress(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val current: String = "", val summary: String = "")

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Fics à surveiller : tout sauf les fics lues jusqu'au bout et terminées, et les fics abandonnées. */
    private fun watched() = Library.fics.value.filter { f ->
        f.site != com.thousandlabs.ohara.data.Site.LOCAL && !f.deletedFromSite && Shelves.ABANDONED !in f.shelves &&
            !(f.status == FicStatus.COMPLETE && f.maxChapterRead >= f.totalChapters - 1 && f.totalChapters > 0)
    }

    fun checkAll(context: Context, ids: List<String>? = null) {
        if (job?.isActive == true) return
        job = scope.launch { run(context, ids) }
    }

    fun cancel() { job?.cancel(); _progress.value = Progress(summary = "Vérification interrompue.") }

    suspend fun run(context: Context, ids: List<String>? = null): String {
        val list = if (ids != null) Library.fics.value.filter { it.id in ids } else watched()
        var newTotal = 0
        val finished = mutableListOf<String>()
        val updatedTitles = mutableListOf<String>()
        var errors = 0
        // un site qui demande une vérification n'est plus interrogé pendant cette vérification (inutile, et risque de blocage)
        val blockedSites = HashSet<com.thousandlabs.ohara.data.Site>()
        list.forEachIndexed { i, old ->
            _progress.value = Progress(true, i, list.size, old.title)
            if (old.site in blockedSites) { errors++; return@forEachIndexed }
            if (i > 0) kotlinx.coroutines.delay(if (Prefs.gentleSpeed) 2500 else 900)
            try {
                val info = Sources.of(old.site).fetchInfo(old.storyId)
                // fiche jamais complétée (fic entrée par la lecture) : le premier compte n'est pas une nouveauté
                val diff = if (old.chapters.isEmpty()) 0 else info.totalChapters - old.totalChapters
                val becameComplete = info.status == FicStatus.COMPLETE && old.status != FicStatus.COMPLETE
                val hadAll = old.totalChapters > 0 && Library.downloadedCount(old.id) >= old.totalChapters
                Library.upsert(info)
                Library.update(old.id) {
                    it.copy(
                        newChapters = it.newChapters + diff.coerceAtLeast(0),
                        completedAlert = it.completedAlert || becameComplete,
                        lastChecked = System.currentTimeMillis(),
                        deletedFromSite = false
                    )
                }
                if (diff > 0) { newTotal += diff; updatedTitles += old.title }
                if (becameComplete) finished += old.title
                if (diff > 0 && hadAll && Prefs.autoDownloadNew) {
                    Library.get(old.id)?.let { f -> Downloads.enqueue(f, (old.totalChapters until f.totalChapters).toList()) }
                }
            } catch (e: SiteError) {
                if (e.code == 404) Library.update(old.id) { it.copy(deletedFromSite = true, lastChecked = System.currentTimeMillis()) }
                else errors++
            } catch (e: VerificationNeeded) {
                errors++
                blockedSites += old.site
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errors++
            }
        }
        val parts = mutableListOf<String>()
        if (newTotal > 0) parts += "$newTotal nouveau${if (newTotal > 1) "x" else ""} chapitre${if (newTotal > 1) "s" else ""} dans ${updatedTitles.size} fic${if (updatedTitles.size > 1) "s" else ""}"
        if (finished.isNotEmpty()) parts += "terminée${if (finished.size > 1) "s" else ""} : " + finished.joinToString(", ")
        val summary = when {
            parts.isNotEmpty() -> parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
            list.isEmpty() -> "Aucune fic à vérifier."
            else -> "Pas de nouveau chapitre."
        } + if (errors > 0) " ($errors fic${if (errors > 1) "s" else ""} non vérifiée${if (errors > 1) "s" else ""})" else ""
        _progress.value = Progress(false, list.size, list.size, "", summary)
        if (newTotal > 0 || finished.isNotEmpty()) {
            val title = if (finished.isNotEmpty() && newTotal == 0) "Fic terminée" else "Nouveaux chapitres"
            val text = buildString {
                if (updatedTitles.isNotEmpty()) append(updatedTitles.take(5).joinToString(", ")).append(if (updatedTitles.size > 5) "…" else "")
                if (finished.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("Enfin complète : ").append(finished.take(3).joinToString(", ")) }
            }
            Notify.updates(context, title, text)
        }
        return summary
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!Prefs.checkDaily) { wm.cancelUniqueWork("verif_fics"); return }
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork("verif_fics", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Prefs.checkDaily) return Result.success()
        runCatching { UpdateChecker.run(applicationContext) }
        return Result.success()
    }
}
