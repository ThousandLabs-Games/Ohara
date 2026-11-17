package com.thousandlabs.ohara.net

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Une page reçue d'un site. */
class Page(val code: Int, val body: String, val url: String)

/** Le site demande une vérification (« Je ne suis pas un robot ») que seul le lecteur peut faire. */
class VerificationNeeded(val url: String) : IOException("Le site demande une vérification.")

/** Le site a répondu par une erreur (404 : page introuvable, la fic a sans doute été supprimée). */
class SiteError(val code: Int, val url: String) : IOException(
    when (code) {
        404, 410 -> "Page introuvable sur le site."
        429 -> "Le site demande de ralentir. Réessayez dans quelques minutes."
        in 500..599 -> "Le site rencontre un problème ($code)."
        else -> "Le site a refusé la demande ($code)."
    }
)

/**
 * Requêtes « légères », sans afficher de page. Elles utilisent le même identifiant de navigateur
 * et les mêmes cookies que le navigateur intégré, pour que les sites reconnaissent le lecteur.
 */
object Http {
    @Volatile private var ua: String? = null

    fun init(context: Context) {
        ua = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
    }

    val userAgent: String
        get() = ua ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    fun get(
        url: String,
        referer: String? = null,
        accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    ): Page {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9,fr;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Sec-GPC", "1")
                if (referer != null) setRequestProperty("Referer", referer)
                val cookies = runCatching { CookieManager.getInstance().getCookie(current) }.getOrNull()
                if (!cookies.isNullOrEmpty()) setRequestProperty("Cookie", cookies)
            }
            try {
                val code = conn.responseCode
                // garde les cookies posés par le site (session, protection anti-robots…)
                conn.headerFields?.entries?.filter { it.key.equals("Set-Cookie", true) }?.forEach { e ->
                    e.value.forEach { c -> runCatching { CookieManager.getInstance().setCookie(current, c) } }
                }
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return Page(code, "", current)
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val body = stream?.let { s ->
                    val input = if ("gzip".equals(conn.contentEncoding, true)) GZIPInputStream(s) else s
                    input.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: ""
                return Page(code, body, current)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Trop de redirections.")
    }

    /** Reconnaît les pages de protection anti-robots (Cloudflare et compagnie). */
    fun isChallenge(page: Page): Boolean {
        if (page.code == 429) return true
        if (page.code == 403 || page.code == 503) {
            val b = page.body.take(30000)
            if (b.contains("cf-chl") || b.contains("challenge-platform") || b.contains("Just a moment") ||
                b.contains("cf_chl_opt") || b.contains("Attention Required") || b.contains("cf-turnstile") ||
                b.contains("Checking your browser") || b.contains("DDoS protection")
            ) return true
        }
        return isChallengeHtml(page.body)
    }

    fun isChallengeHtml(body: String): Boolean {
        val head = body.take(6000)
        return head.contains("<title>Just a moment") || head.contains("challenge-platform") ||
            head.contains("cf-turnstile") || head.contains("<title>Attention Required")
    }
}
