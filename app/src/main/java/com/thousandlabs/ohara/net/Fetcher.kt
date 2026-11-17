package com.thousandlabs.ohara.net

import com.thousandlabs.ohara.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Point d'entrée unique pour lire une page d'un site :
 * - respecte une pause entre deux requêtes vers le même site (pour ne pas se faire bloquer) ;
 * - essaie d'abord une requête directe, puis le navigateur invisible si le site exige une vérification.
 */
object Fetcher {
    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val lastRequest = ConcurrentHashMap<String, Long>()
    /** Sites qui ont demandé une vérification récemment : on passe directement par le navigateur invisible. */
    private val browserOnlyUntil = ConcurrentHashMap<String, Long>()

    fun host(url: String): String = runCatching { URI(url).host?.lowercase() ?: "" }.getOrDefault("")

    private fun baseHost(h: String) = h.removePrefix("www.").removePrefix("m.")

    /** Pause minimale entre deux pages d'un même site, en millisecondes. */
    private fun minGap(host: String): Long {
        val gentle = Prefs.gentleSpeed
        return when {
            host.endsWith("fanfiction.net") -> if (gentle) 3200L else 1800L
            host.endsWith("spacebattles.com") -> if (gentle) 1600L else 900L
            host.endsWith("webnovel.com") -> if (gentle) 1200L else 600L
            else -> if (gentle) 1500L else 800L
        }
    }

    private suspend fun politeWait(host: String) {
        val now = System.currentTimeMillis()
        val last = lastRequest[host] ?: 0L
        val gap = minGap(host) + Random.nextLong(0, minGap(host) / 2 + 1)
        val wait = last + gap - now
        if (wait > 0) delay(wait)
        lastRequest[host] = System.currentTimeMillis()
    }

    /**
     * Lit une page.
     * @param needsJs le site n'affiche son contenu qu'avec JavaScript : navigateur invisible obligatoire.
     * @param readyJs condition JavaScript qui indique que le contenu est affiché (navigateur invisible).
     */
    suspend fun get(url: String, needsJs: Boolean = false, readyJs: String? = null, polite: Boolean = true): Page {
        val h = baseHost(host(url))
        val lock = hostLocks.getOrPut(h) { Mutex() }
        return lock.withLock {
            if (polite) politeWait(h)
            val viaBrowser = needsJs || (browserOnlyUntil[h] ?: 0L) > System.currentTimeMillis()
            if (!viaBrowser) {
                val page = withContext(Dispatchers.IO) { Http.get(url) }
                if (!Http.isChallenge(page)) {
                    if (page.code == 404 || page.code == 410) throw SiteError(page.code, url)
                    if (page.code >= 400) throw SiteError(page.code, url)
                    return@withLock page
                }
                browserOnlyUntil[h] = System.currentTimeMillis() + 15 * 60_000L
            }
            val page = WebFetcher.fetch(url, readyJs)
            if (Http.isChallengeHtml(page.body)) throw VerificationNeeded(url)
            page
        }
    }

    /** Requête JSON (API d'un site), avec les cookies du navigateur intégré. */
    suspend fun getJson(url: String, referer: String? = null): String {
        val h = baseHost(host(url))
        val lock = hostLocks.getOrPut(h) { Mutex() }
        return lock.withLock {
            politeWait(h)
            if ((browserOnlyUntil[h] ?: 0L) <= System.currentTimeMillis()) {
                val page = withContext(Dispatchers.IO) { Http.get(url, referer, accept = "application/json, text/plain, */*") }
                if (!Http.isChallenge(page) && page.code != 403) {
                    if (page.code >= 400) throw SiteError(page.code, url)
                    return@withLock page.body
                }
                browserOnlyUntil[h] = System.currentTimeMillis() + 15 * 60_000L
            }
            // requête refusée hors navigateur : on l'envoie depuis une page du site
            ApiBrowser.fetch(url, referer ?: "https://${host(url)}/")
        }
    }

    /** Après une vérification réussie dans le navigateur, on retente les requêtes directes. */
    fun verificationDone(url: String) {
        browserOnlyUntil.remove(baseHost(host(url)))
    }
}
