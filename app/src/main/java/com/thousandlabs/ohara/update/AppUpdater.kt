package com.thousandlabs.ohara.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.thousandlabs.ohara.BuildConfig
import com.thousandlabs.ohara.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mises à jour de l'appli depuis les versions publiées sur GitHub (dépôt ThousandLabs-Games/Ohara).
 * Ohara télécharge le nouvel APK et demande à Android de l'installer : vous confirmez d'une touche.
 */
object AppUpdater {
    const val REPO = "ThousandLabs-Games/Ohara"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

    data class State(
        val checking: Boolean = false,
        val available: Release? = null,
        val upToDate: Boolean = false,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val message: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    var autoCheck: Boolean get() = Prefs.getBool("upd_auto", true); set(v) = Prefs.putBool("upd_auto", v)

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000
        setRequestProperty("User-Agent", "Ohara/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    /** Vérifie s'il existe une version plus récente (au plus une fois par jour en automatique). */
    fun check(manual: Boolean = true) {
        if (_state.value.checking || _state.value.downloading) return
        if (!manual) {
            if (!autoCheck) return
            if (System.currentTimeMillis() - Prefs.getLong("upd_last_check", 0L) < 20L * 3600 * 1000) return
        }
        _state.value = _state.value.copy(checking = true, message = null, upToDate = false)
        scope.launch {
            val result = runCatching {
                val c = open("https://api.github.com/repos/$REPO/releases/latest")
                try {
                    if (c.responseCode == 404) return@runCatching null
                    if (c.responseCode != 200) throw IllegalStateException("GitHub a répondu ${c.responseCode}.")
                    val o = JSONObject(c.inputStream.bufferedReader().readText())
                    val tag = o.optString("tag_name")
                    val code = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val assets = o.optJSONArray("assets")
                    var apk = ""; var size = 0L
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); size = a.optLong("size"); break }
                    }
                    if (apk.isEmpty()) null else Release(code, o.optString("name").ifBlank { tag }, o.optString("body"), apk, size)
                } finally { c.disconnect() }
            }
            Prefs.putLong("upd_last_check", System.currentTimeMillis())
            result.fold(
                { rel ->
                    _state.value = if (rel != null && rel.versionCode > BuildConfig.VERSION_CODE) State(available = rel)
                    else State(upToDate = true, message = if (manual) "Vous avez la dernière version (${BuildConfig.VERSION_NAME})." else null)
                },
                { e -> _state.value = State(message = if (manual) "Vérification impossible : ${e.message ?: "pas de connexion"}" else null) }
            )
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Télécharge puis lance l'installation. */
    fun downloadAndInstall(context: Context) {
        val rel = _state.value.available ?: return
        if (_state.value.downloading) return
        val app = context.applicationContext
        _state.value = _state.value.copy(downloading = true, progress = 0f, message = "Téléchargement de la mise à jour…")
        scope.launch {
            runCatching {
                val dir = File(app.cacheDir, "maj").apply { mkdirs() }
                val file = File(dir, "Ohara.apk")
                var url = rel.apkUrl
                var conn = open(url)
                var hops = 0
                conn.instanceFollowRedirects = false
                while (conn.responseCode in 300..399 && hops < 6) {
                    url = conn.getHeaderField("Location") ?: break
                    conn.disconnect(); conn = open(url); conn.instanceFollowRedirects = false; hops++
                }
                if (conn.responseCode != 200) throw IllegalStateException("Téléchargement refusé (${conn.responseCode}).")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.size
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.value = _state.value.copy(progress = (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                conn.disconnect()
                install(app, file)
            }.onFailure { e ->
                _state.value = _state.value.copy(downloading = false, message = "Échec : ${e.message ?: "erreur inconnue"}")
            }
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("Ohara.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            s.commit(pi.intentSender)
        }
        _state.value = _state.value.copy(downloading = false, progress = 1f, message = "Installation en cours : confirmez dans la fenêtre d'Android.")
    }

    internal fun onResult(message: String) {
        _state.value = _state.value.copy(downloading = false, message = message)
    }
}

/** Reçoit le résultat de l'installation et affiche la confirmation d'Android si besoin. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdater.onResult("Mise à jour installée.")
            else -> AppUpdater.onResult("Installation annulée ou impossible : " +
                (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "raison inconnue"))
        }
    }
}
