package com.thousandlabs.ohara.translate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Traduction en ligne (service de traduction de Google), bien plus naturelle que le modèle du téléphone :
 * elle voit le paragraphe entier, garde le sens des expressions et le ton des dialogues.
 * Plusieurs paragraphes partent dans une même demande (une ligne chacun) et plusieurs demandes partent en même temps.
 *
 * Solidité : chaque demande est retentée (deux adresses du service, trois essais) avant d'abandonner ;
 * après un refus, le service est laissé tranquille un moment (1, 3 puis 5 minutes), puis tout reprend dès qu'une demande passe.
 * Si le service ne répond vraiment pas, [Translate] repasse sur le modèle du téléphone, et revient au service dès qu'il répond de nouveau.
 */
internal object Online {
    private val ENDPOINTS = listOf(
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&ie=UTF-8&oe=UTF-8",
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=fr&dt=t&ie=UTF-8&oe=UTF-8"
    )
    /** Taille d'une demande : le service accepte environ 5 000 caractères. */
    const val MAX_CHARS = 4200
    private val gate = Semaphore(4)

    /** Après un refus du service, on le laisse tranquille un moment. */
    @Volatile private var pausedUntil = 0L
    @Volatile private var failures = 0
    val available: Boolean get() = System.currentTimeMillis() >= pausedUntil

    /** Temps restant avant que le service soit réessayé, en millisecondes (0 s'il est disponible). */
    val pausedFor: Long get() = (pausedUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun pause() {
        val now = System.currentTimeMillis()
        if (now < pausedUntil) return                       // déjà en pause : une seule pause pour une rafale d'échecs
        failures = (failures + 1).coerceAtMost(3)
        pausedUntil = now + when (failures) { 1 -> 60_000L; 2 -> 180_000L; else -> 300_000L }
    }

    private fun ok() { failures = 0 }

    private fun parse(body: String): String {
        val b = body.trim()
        if (b.startsWith("{")) {
            val s = JSONObject(b).optJSONArray("sentences") ?: throw IOException("réponse vide")
            val out = StringBuilder()
            for (i in 0 until s.length()) s.optJSONObject(i)?.let { out.append(it.optString("trans")) }
            return out.toString()
        }
        val top = JSONArray(b)
        val first = top.opt(0)
        if (first is String) return first
        val segs = top.optJSONArray(0) ?: throw IOException("réponse vide")
        val out = StringBuilder()
        for (i in 0 until segs.length()) {
            when (val e = segs.opt(i)) {
                is JSONArray -> if (!e.isNull(0)) out.append(e.optString(0))
                is String -> out.append(e)
            }
        }
        return out.toString()
    }

    private fun request(endpoint: String, text: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
        }
        try {
            conn.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) throw IOException("réponse $code du service de traduction")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return Typo.unescape(parse(body))
        } finally {
            conn.disconnect()
        }
    }

    /** Une demande, retentée sur l'autre adresse puis sur la première ; en cas d'échec général le service passe en pause. */
    private suspend fun call(text: String): String {
        var last: Exception? = null
        val order = intArrayOf(0, 1, 0)
        for (k in order.indices) {
            if (k > 0 && !available) break
            try {
                val r = withContext(Dispatchers.IO) { request(ENDPOINTS[order[k]], text) }
                ok()
                return r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (k < order.size - 1) delay(400L * (k + 1))
            }
        }
        pause()
        throw IOException("service de traduction indisponible" + (last?.message?.let { " ($it)" } ?: ""))
    }

    /** Une ligne = un texte. Rend null si le service n'a pas gardé le même nombre de lignes. */
    private suspend fun lines(texts: List<String>): List<String>? = gate.withPermit {
        val r = call(texts.joinToString("\n") { it.replace('\n', ' ') }).split('\n').map { it.trim() }.let { l ->
            // une ligne vide ajoutée en fin de réponse ne compte pas
            if (l.size > texts.size && l.drop(texts.size).all { it.isEmpty() }) l.take(texts.size) else l
        }
        r.takeIf { it.size == texts.size }
    }

    /**
     * Traduit une liste de textes courts (sans retour à la ligne), dans l'ordre.
     * Un texte que le service n'a pas su traduire revient vide (l'appelant le retraduit autrement).
     * Lève une exception si le service ne répond pas du tout : l'appelant repasse alors sur le téléphone.
     * [alone] : chaque texte part dans sa propre demande (seconde chance après un résultat douteux).
     */
    suspend fun translate(texts: List<String>, alone: Boolean = false): List<String> = coroutineScope {
        if (!available) throw IOException("service de traduction en pause")
        val out = arrayOfNulls<String>(texts.size)
        if (alone) {
            texts.mapIndexed { i, t -> async { out[i] = lines(listOf(t))?.single() ?: "" } }.awaitAll()
            return@coroutineScope out.map { it ?: "" }
        }
        // paquets de lignes sous la taille maximale d'une demande
        val groups = ArrayList<IntRange>()
        var start = 0; var size = 0
        texts.forEachIndexed { i, t ->
            if (i > start && size + t.length + 1 > MAX_CHARS) { groups += start until i; start = i; size = 0 }
            size += t.length + 1
        }
        if (start < texts.size) groups += start until texts.size
        groups.map { g ->
            async {
                val part = texts.subList(g.first, g.last + 1)
                val r = lines(part)
                    // lignes mélangées par le service : chaque texte repart seul
                    ?: part.map { t -> async { try { lines(listOf(t))?.single() } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: "" } }.awaitAll()
                r.forEachIndexed { k, s -> out[g.first + k] = s }
            }
        }.awaitAll()
        out.map { it ?: "" }
    }
}
