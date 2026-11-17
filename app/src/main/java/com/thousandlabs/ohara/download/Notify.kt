package com.thousandlabs.ohara.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thousandlabs.ohara.MainActivity
import com.thousandlabs.ohara.R

/** Notifications : téléchargements en cours, nouveaux chapitres, fics terminées. */
object Notify {
    const val CH_DOWNLOAD = "telechargements"
    const val CH_UPDATES = "nouveautes"
    const val ID_DOWNLOAD = 41
    private const val ID_UPDATES = 42
    private const val ID_VERIFY = 43
    private const val ID_CLAUDE = 44

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOAD, "Téléchargements", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progression des téléchargements de chapitres"
        })
        nm.createNotificationChannel(NotificationChannel(CH_UPDATES, "Nouveaux chapitres", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nouveaux chapitres et fics terminées"
        })
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, extra: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra != null) i.putExtra("ouvrir", extra)
        return PendingIntent.getActivity(context, extra.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun downloadNotification(context: Context, title: String, text: String, progress: Int, max: Int, open: String = "file") =
        NotificationCompat.Builder(context, CH_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .setContentIntent(openApp(context, open))
            .build()

    /** Fichiers prêts alors qu'Ohara était en arrière-plan : Android interdit d'ouvrir Claude sans un geste. */
    fun claudeReady(context: Context, title: String) {
        if (!allowed(context)) return
        val text = "« $title » : touchez pour ouvrir l'appli Claude avec les fichiers et la consigne."
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Prêt à envoyer à Claude")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "claude"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_CLAUDE, n) }
    }

    fun claudeFailed(context: Context, message: String) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Envoi à Claude impossible")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "claude"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_CLAUDE, n) }
    }

    fun cancelClaude(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_CLAUDE) }
    }

    fun updates(context: Context, title: String, text: String) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "bibliotheque"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_UPDATES, n) }
    }

    fun verification(context: Context, site: String) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Vérification demandée par $site")
            .setContentText("Touchez pour confirmer que vous n'êtes pas un robot ; le téléchargement reprendra ensuite.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "file"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_VERIFY, n) }
    }

    fun doneDownload(context: Context, text: String) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CH_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Téléchargement terminé")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "bibliotheque"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_DOWNLOAD + 1, n) }
    }
}
