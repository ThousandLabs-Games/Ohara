package com.thousandlabs.ohara

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thousandlabs.ohara.data.Prefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Les quatre icônes d'Ohara, qui suivent le moment de la journée :
 * matin, le grand arbre-bibliothèque sur son île ; après-midi, la stèle de pierre gravée ;
 * soir, l'intérieur de la bibliothèque ; nuit, l'île sous la lune.
 */
object IconSwitcher {
    enum class Slot(val alias: String, val label: String, val hours: String) {
        MORNING("IconeA", "Matin", "6 h – 12 h"),
        AFTERNOON("IconeB", "Après-midi", "12 h – 18 h"),
        EVENING("IconeC", "Soir", "18 h – 21 h"),
        NIGHT("IconeD", "Nuit", "21 h – 6 h")
    }

    /** -1 : automatique selon l'heure ; sinon l'icône choisie reste fixe. */
    var fixed: Int get() = Prefs.getInt("icon_fixed", -1); set(v) = Prefs.putInt("icon_fixed", v)

    fun slotFor(hour: Int): Slot = when (hour) {
        in 6..11 -> Slot.MORNING
        in 12..17 -> Slot.AFTERNOON
        in 18..20 -> Slot.EVENING
        else -> Slot.NIGHT
    }

    fun wanted(): Slot = Slot.entries.getOrNull(fixed) ?: slotFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    fun current(context: Context): Slot? {
        val pm = context.packageManager
        return Slot.entries.firstOrNull { s ->
            val st = pm.getComponentEnabledSetting(ComponentName(context, "${context.packageName}.${s.alias}"))
            st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && s == Slot.MORNING)
        }
    }

    /** Active la bonne icône (la nouvelle d'abord, puis on éteint les autres : il y en a toujours une). */
    fun apply(context: Context) {
        val target = wanted()
        if (current(context) == target) return
        val pm = context.packageManager
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${target.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            for (s in Slot.entries) if (s != target) pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${s.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<IconWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("icone_du_moment", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class IconWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Changer d'icône pendant que l'appli est ouverte (même en arrière-plan) ferme sa tâche :
        // on attend qu'elle soit vraiment fermée.
        if (com.thousandlabs.ohara.ui.Locker.activity == null) IconSwitcher.apply(applicationContext)
        return Result.success()
    }
}
