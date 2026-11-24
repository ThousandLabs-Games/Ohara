package com.thousandlabs.ohara

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.download.LinkOpener
import com.thousandlabs.ohara.download.UpdateChecker
import com.thousandlabs.ohara.privacy.AdBlocker
import com.thousandlabs.ohara.privacy.Privacy
import com.thousandlabs.ohara.ui.AppScope
import com.thousandlabs.ohara.ui.Locker
import com.thousandlabs.ohara.ui.Nav
import com.thousandlabs.ohara.ui.OharaRoot
import com.thousandlabs.ohara.ui.Screen
import com.thousandlabs.ohara.ui.Toast
import com.thousandlabs.ohara.update.AppUpdater
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Locker.activity = this
        com.thousandlabs.ohara.net.HiddenHost.container = { if (isDestroyed) null else window.decorView as? android.view.ViewGroup }
        setContent { OharaRoot() }
        if (savedInstanceState == null) {
            handleIntent(intent)
            if (Build.VERSION.SDK_INT >= 33 && !Prefs.getBool("notif_asked", false) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Prefs.putBool("notif_asked", true)
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            AppUpdater.check(manual = false)
            AppScope.launch { kotlinx.coroutines.delay(4000); com.thousandlabs.ohara.ui.MonthReport.announce() }
            if (Prefs.checkOnLaunch && Library.fics.value.isNotEmpty()) UpdateChecker.checkAll(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Lien partagé depuis Brave, lien ouvert avec Ohara, ou notification touchée. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.getStringExtra("ouvrir")) {
            "file" -> { Nav.home(); Nav.push(Screen.Downloads); return }
            "bibliotheque" -> { Nav.home(Nav.TAB_LIBRARY); return }
            // « Prêt à envoyer à Claude » : l'envoi part au retour à l'écran (onStart)
            "claude" -> return
        }
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        if (!LinkOpener.isFicLink(text)) {
            if (com.thousandlabs.ohara.sources.Sources.extractUrl(text) != null) {
                Nav.openBrowser(com.thousandlabs.ohara.sources.Sources.extractUrl(text)!!)
                Toast.show("Ce lien n'est pas une fic reconnue : il est ouvert dans le navigateur.")
            } else Toast.show("Aucun lien de fic dans le texte partagé.")
            return
        }
        com.thousandlabs.ohara.ui.LinkFlow.open(text)
    }

    override fun onStart() {
        super.onStart()
        com.thousandlabs.ohara.ui.ReadTracker.foreground = true
        Locker.onStart()
        // si l'appli est verrouillée, l'envoi attend l'empreinte (voir Locker.unlock)
        if (Locker.locked) com.thousandlabs.ohara.ui.ClaudePrep.markVisible() else com.thousandlabs.ohara.ui.ClaudePrep.onVisible(this)
    }

    override fun onStop() {
        super.onStop()
        com.thousandlabs.ohara.ui.ReadTracker.foreground = false
        com.thousandlabs.ohara.ui.ReadTracker.flushSeconds()
        com.thousandlabs.ohara.data.ReadingLog.flush()
        Locker.onStop()
        com.thousandlabs.ohara.ui.ClaudePrep.onHidden()
        AdBlocker.flushCount()
        Library.saveNow()
        com.thousandlabs.ohara.ui.BrowserHolder.saveTabs()
        if (!isChangingConfigurations && IconSwitcher.needsChange(this)) runCatching { IconSwitcher.scheduleSoon(applicationContext) }
    }

    override fun onDestroy() {
        if (isFinishing && Prefs.clearOnExit) Privacy.clearBrowsingData(applicationContext)
        if (Locker.activity === this) {
            Locker.activity = null
            com.thousandlabs.ohara.net.HiddenHost.container = null
        }
        // l'icône ne change que quand l'appli est vraiment fermée (sinon Android ferme la tâche en cours)
        if (isFinishing && !isChangingConfigurations) IconSwitcher.apply(applicationContext)
        super.onDestroy()
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Demande l'empreinte (ou le code du téléphone). */
    fun authenticate(onOk: () -> Unit) {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(auth)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.show("Aucune empreinte ni code configuré sur ce téléphone : verrouillage ignoré.")
            onOk(); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Ohara est verrouillée")
                .setSubtitle("Posez votre doigt pour ouvrir votre bibliothèque")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}
