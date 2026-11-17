package com.thousandlabs.ohara.privacy

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.net.Http
import android.net.Uri

/** Réglages de confidentialité appliqués au navigateur intégré. */
object Privacy {

    /** Sites de fanfictions pris en charge : jamais de brouillage d'empreinte (leur protection anti-robots le refuserait). */
    val SOURCE_HOSTS = listOf("fanfiction.net", "spacebattles.com", "webnovel.com")

    fun isSourceHost(host: String?): Boolean = host != null && SOURCE_HOSTS.any { host == it || host.endsWith(".$it") }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(web: WebView, private: Boolean = false) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = Http.userAgent
            @Suppress("DEPRECATION")
            saveFormData = false
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = Prefs.safeBrowsing
            cacheMode = if (private) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !Prefs.antiTracking)
        // n'annonce pas aux sites qu'il s'agit de l'appli Ohara (en-tête X-Requested-With)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet()) }
        }
    }

    /** En-têtes ajoutés aux pages ouvertes : « ne me pistez pas ». */
    fun headers(): Map<String, String> =
        if (Prefs.antiTracking) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    private val TRACKING_PARAMS = Regex(
        "^(utm_[a-z_]+|fbclid|gclid|gclsrc|dclid|gbraid|wbraid|msclkid|mc_eid|mc_cid|igshid|yclid|_hsenc|_hsmi|" +
            "ref_src|ref_url|oly_anon_id|oly_enc_id|vero_id|wickedid|twclid|ttclid|s_cid|mkt_tok|trk|trkCampaign|sc_cid|__s|_openstat)$",
        RegexOption.IGNORE_CASE
    )

    /** Retire les paramètres de pistage d'une adresse (utm_source, fbclid…). */
    fun cleanUrl(url: String): String {
        if (!Prefs.antiTracking) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val names = runCatching { uri.queryParameterNames }.getOrNull() ?: return url
        if (names.none { TRACKING_PARAMS.matches(it) }) return url
        val b = uri.buildUpon().clearQuery()
        for (n in names) if (!TRACKING_PARAMS.matches(n)) uri.getQueryParameters(n).forEach { b.appendQueryParameter(n, it) }
        return b.build().toString()
    }

    /** Passe en connexion sécurisée (https) quand c'est possible. */
    fun upgrade(url: String): String =
        if (Prefs.httpsOnly && url.startsWith("http://") && !url.startsWith("http://localhost") && !url.startsWith("http://127.")) {
            "https://" + url.removePrefix("http://")
        } else url

    /** Efface cookies, cache, stockage des sites et historique de formulaires. */
    fun clearBrowsingData(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        runCatching { WebView(context).apply { clearCache(true); clearFormData(); clearHistory(); destroy() } }
        @Suppress("DEPRECATION")
        runCatching { WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword() }
    }
}
