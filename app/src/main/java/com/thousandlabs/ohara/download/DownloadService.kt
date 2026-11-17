package com.thousandlabs.ohara.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/** Garde l'appli active pendant les téléchargements, avec une notification de progression. */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notify.downloadNotification(this, "Ohara", "Préparation du téléchargement…", 0, 0)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, Notify.ID_DOWNLOAD, n, type) }
        update()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        // Android limite la durée des services de ce type : on s'arrête proprement, la file reste en attente
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    fun update() {
        val jobs = Downloads.jobs.value
        val running = jobs.firstOrNull { it.state == DownloadJob.State.RUNNING }
        val waiting = jobs.count { it.state == DownloadJob.State.WAITING }
        if (running == null && waiting == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val text = if (running != null) {
            val rest = if (waiting > 0) " · encore $waiting fic${if (waiting > 1) "s" else ""} en attente" else ""
            "${running.done}/${running.total} chapitres$rest"
        } else "En attente…"
        val n = Notify.downloadNotification(this, running?.title ?: "Ohara", text, running?.done ?: 0, running?.total ?: 0)
        if (Notify.allowed(this)) runCatching { NotificationManagerCompat.from(this).notify(Notify.ID_DOWNLOAD, n) }
    }

    companion object {
        @Volatile private var instance: DownloadService? = null

        fun refresh(context: Context) {
            instance?.let { s -> android.os.Handler(android.os.Looper.getMainLooper()).post { s.update() } }
        }
    }
}
