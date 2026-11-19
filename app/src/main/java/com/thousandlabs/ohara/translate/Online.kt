package com.thousandlabs.ohara.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Traduction en ligne (service de traduction de Google), bien plus naturelle que le modèle du téléphone :
 * elle voit le paragraphe entier, garde le sens des expressions et le ton des dialogues.
 * Plusieurs paragraphes partent dans une même demande (une ligne chacun) et plusieurs demandes partent en même temps.
 * En cas de souci (pas de réseau, service saturé), [Translate] repasse sur le modèle du téléphone.
 */
internal object Online {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&ie=UTF-8&oe=UTF-8"
    /** Taille d'une demande : le service accepte environ 5 000 caractères. */
    const val MAX_CHARS = 4200
    private val gate = Semaphore(4)

    /** Après un refus du service, on le laisse tranquille quelques minutes. */
    @Volatile private var pausedUntil = 0L
    val available: Boolean get() = System.currentTimeMillis() >= pausedUntil

    private fun pause() { pausedUntil = System.currentTimeMillis() + 5 * 60_000L }

    private fun request(text: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
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
            if (code == 429 || code == 403 || code >= 500) { pause(); throw IOException("service de traduction saturé ($code)") }
            if (code != 200) throw IOException("réponse $code du service de traduction")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val segs = JSONArray(body).optJSONArray(0) ?: throw IOException("réponse vide")
            val out = StringBuilder()
            for (i in 0 until segs.length()) segs.optJSONArray(i)?.let { s -> if (!s.isNull(0)) out.append(s.optString(0)) }
            return out.toString()
        } finally {
            conn.disconnect()
        }
    }

    /** Une ligne = un texte. Rend null si le service n'a pas gardé le même nombre de lignes. */
    private suspend fun lines(texts: List<String>): List<String>? = gate.withPermit {
        withContext(Dispatchers.IO) {
            val r = request(texts.joinToString("\n") { it.replace('\n', ' ') }).split('\n').map { it.trim() }.let { l ->
                // une ligne vide ajoutée en fin de réponse ne compte pas
                if (l.size > texts.size && l.drop(texts.size).all { it.isEmpty() }) l.take(texts.size) else l
            }
            r.takeIf { it.size == texts.size }
        }
    }

    /**
     * Traduit une liste de textes courts (sans retour à la ligne), dans l'ordre.
     * Lève une exception si le service ne répond pas : l'appelant repasse alors sur le téléphone.
     */
    suspend fun translate(texts: List<String>): List<String> = coroutineScope {
        if (!available) throw IOException("service de traduction en pause")
        // paquets de lignes sous la taille maximale d'une demande
        val groups = ArrayList<IntRange>()
        var start = 0; var size = 0
        texts.forEachIndexed { i, t ->
            if (i > start && size + t.length + 1 > MAX_CHARS) { groups += start until i; start = i; size = 0 }
            size += t.length + 1
        }
        if (start < texts.size) groups += start until texts.size
        val out = arrayOfNulls<String>(texts.size)
        groups.map { g ->
            async {
                val part = texts.subList(g.first, g.last + 1)
                val r = lines(part)
                    // lignes mélangées par le service : chaque texte repart seul
                    ?: part.map { t -> async { lines(listOf(t))?.single() ?: t } }.awaitAll()
                r.forEachIndexed { k, s -> out[g.first + k] = s }
            }
        }.awaitAll()
        out.map { it ?: "" }
    }
}
