package com.thousandlabs.ohara.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Traduction anglais → français.
 * Par défaut en ligne (qualité bien supérieure, paragraphes entiers, plusieurs demandes en même temps) ;
 * sans réseau, ou si l'on préfère que rien ne quitte le téléphone, le modèle du téléphone prend le relais
 * (une trentaine de Mo téléchargés une seule fois). Chaque paragraphe traduit est gardé : un chapitre déjà traduit s'affiche tout de suite.
 */
object Translate {
    /** Le lecteur affiche la traduction française. */
    var enabled: Boolean get() = Prefs.getBool("reader_fr", false); set(v) = Prefs.putBool("reader_fr", v)

    private val client: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.FRENCH)
                .build()
        )
    }
    private val modelLock = Mutex()
    @Volatile private var modelReady = false

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    /** Télécharge le modèle s'il manque (une seule fois). */
    suspend fun ensureModel(onProgress: (String) -> Unit = {}) = modelLock.withLock {
        if (modelReady) return@withLock
        if (!Prefs.getBool("fr_model_ok", false)) onProgress("Téléchargement du modèle de traduction (une seule fois, environ 30 Mo)…")
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        modelReady = true
        Prefs.putBool("fr_model_ok", true)
    }

    private fun dir(ficId: String) = File(Library.dataDir(), "traductions/" + ficId.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }

    private fun loadCache(ficId: String, ch: Int): JSONObject =
        runCatching { JSONObject(File(dir(ficId), "$ch.json").readText()) }.getOrDefault(JSONObject())

    private fun saveCache(ficId: String, ch: Int, o: JSONObject) = runCatching { File(dir(ficId), "$ch.json").writeText(o.toString()) }

    private const val NAMES_KEY = "\u0000noms"

    fun hasCache(ficId: String, ch: Int) = File(dir(ficId), "$ch.json").exists()

    fun deleteFor(ficId: String) { dir(ficId).deleteRecursively() }

    private val BLOCKS = setOf("p", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "td", "th", "center", "div", "dd", "dt", "pre")

    /** Les plus petits blocs de texte (un paragraphe, une ligne de tableau…), pour garder la mise en page. */
    private fun leaves(root: Element): List<Element> =
        root.getAllElements().filter { e ->
            e !== root && e.tagName() in BLOCKS && e.text().isNotBlank() &&
                e.children().none { it.tagName() in BLOCKS && it.text().isNotBlank() }
        }

    private val memo = object : LinkedHashMap<String, String>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 6000
    }

    // ---------- Découpage ----------

    /** Abréviations suivies d'un point qui ne terminent pas une phrase (« Mr. 3 », « Dr. Kureha », « Mt. Colubo »). */
    private val ABBR = setOf("mr", "mrs", "ms", "dr", "st", "mt", "lt", "sgt", "capt", "col", "gen", "jr", "sr", "vs", "prof", "no", "vol", "ch", "fig", "approx")

    private val END_RX = Regex("[.!?…]+[\"'”’»)\\]*_~]*\\s+")

    /** Coupe un paragraphe en phrases, sans couper « Mr. 3 », « Monkey D. Luffy » ni « … puis il partit ». */
    internal fun sentences(s: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        for (m in END_RX.findAll(s)) {
            val end = m.range.last + 1
            if (end >= s.length) break
            if (s[end].isLowerCase()) continue
            if (s[m.range.first] == '.' && m.value.trimEnd().count { it == '.' } == 1 && m.value.trimEnd().length == 1) {
                val w = Regex("[\\p{L}.]+$").find(s.substring(start, m.range.first))?.value ?: ""
                if (w.length == 1 && w[0].isUpperCase()) continue              // initiale : « D. Luffy »
                if (w.contains('.') || w.lowercase() in ABBR) continue         // « e.g. », « Mr. »
            }
            s.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { out += it }
            start = end
        }
        if (start < s.length) s.substring(start).trim().takeIf { it.isNotEmpty() }?.let { out += it }
        return out
    }

    /** Phrase criée, écrite en majuscules : le traducteur la comprend mal telle quelle. */
    internal fun isShout(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        if (letters.length < 4) return false
        return letters.count { it.isUpperCase() } >= letters.length * 0.85 && Regex("\\p{Lu}{3,}").containsMatchIn(s)
    }

    /** « WHERE IS LUFFY?! » → « Where is Luffy?! » (les noms connus gardent leur majuscule). */
    private fun unshout(s: String): String {
        var t = s.lowercase()
        t = Regex("(^|[.!?…]\\s+|[\"“«]\\s*)(\\p{Ll})").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }
        t = Regex("(?<![\\p{L}'’])i(?=[\\s'’,.!?…]|$)").replace(t, "I")
        return Names.recase(t)
    }

    private class Piece(val text: String, val shout: Boolean)

    /**
     * Le paragraphe en morceaux coupés entre deux phrases (environ 300 caractères pour le téléphone,
     * le paragraphe entier en ligne) : un texte court se traduit mieux sur le téléphone, et un numéro perdu ne gâche qu'un morceau.
     * Les phrases criées sont traduites à part puis remises en majuscules.
     */
    private fun pieces(s: String, max: Int): List<Piece> {
        val out = ArrayList<Piece>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += Piece(buf.toString(), false); buf.clear() } }
        for (x in sentences(s)) {
            if (isShout(x)) { flush(); out += Piece(unshout(x), true); continue }
            if (buf.isNotEmpty() && buf.length + x.length + 1 > max) flush()
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(x)
        }
        flush()
        return out
    }

    // ---------- Moteurs ----------

    /** Traduction en ligne (bien meilleure), avec repli automatique sur le téléphone. */
    var online: Boolean
        get() = Prefs.getBool("tr_online", true)
        set(v) { Prefs.putBool("tr_online", v); namesChanged() }

    /** Les traductions gardées dépendent des noms protégés et du moteur qui les a faites. */
    fun signature(): String = Names.signature() + if (online) "|g1" else "|m1"

    /** Prépare la traduction : rien à faire en ligne, le modèle du téléphone sinon. */
    suspend fun prepare(onProgress: (String) -> Unit = {}) { if (!online) ensureModel(onProgress) }

    /** Sur le téléphone, un long texte part en morceaux d'environ 300 caractères. */
    private suspend fun device(t: String): String {
        ensureModel()
        if (t.length <= 400) return client.translate(t).await()
        val parts = ArrayList<String>(); val buf = StringBuilder()
        for (x in sentences(t)) {
            if (buf.isNotEmpty() && buf.length + x.length + 1 > 300) { parts += buf.toString(); buf.clear() }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(x)
        }
        if (buf.isNotEmpty()) parts += buf.toString()
        return parts.map { client.translate(it).await().trim() }.joinToString(" ")
    }

    private suspend fun engine(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (online && Online.available) {
            try { return Online.translate(texts) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
        return texts.map { device(it) }
    }

    /**
     * Traduction de morceaux, avec les noms protégés (personnages, îles, techniques…) gardés tels quels
     * et les termes officiels mis en français. Si le traducteur abîme un repère, ce morceau seulement est retraduit sans protection.
     */
    private suspend fun translatePieces(src: List<String>): List<String> {
        val prot = src.map { Names.protect(it) }
        val res = arrayOfNulls<String>(src.size)
        val send = ArrayList<Int>()
        prot.forEachIndexed { i, p ->
            // morceau fait seulement de noms (« Usopp ! ») : rien à traduire
            if (!p.isEmpty && !Regex("\\p{L}{2}").containsMatchIn(p.bare())) res[i] = p.fill() else send += i
        }
        val first = engine(send.map { prot[it].text })
        val retry = ArrayList<Int>()
        send.forEachIndexed { k, i -> prot[i].restore(first[k])?.let { res[i] = it } ?: retry.add(i) }
        if (retry.isNotEmpty()) engine(retry.map { src[it] }).forEachIndexed { k, r -> res[retry[k]] = r }
        return res.map { it ?: "" }
    }

    /** Traduit plusieurs paragraphes d'un coup (en ligne, ils partent ensemble). */
    private suspend fun rawMany(list: List<String>): List<String> {
        val max = if (online && Online.available) Online.MAX_CHARS - 200 else 300
        val split = list.map { pieces(it, max) }
        val tr = translatePieces(split.flatten().map { it.text })
        var k = 0
        return split.map { ps ->
            val out = StringBuilder()
            for (pc in ps) {
                var r = tr[k++].trim()
                if (pc.shout) r = r.uppercase(java.util.Locale.FRENCH)
                // Le traducteur met parfois une minuscule en tête (terme remplacé, phrase commençant par un nom…).
                val a = pc.text.firstOrNull { it.isLetter() }; val i = r.indexOfFirst { it.isLetter() }
                if (a != null && a.isUpperCase() && i >= 0 && r[i].isLowerCase()) r = r.substring(0, i) + r[i].uppercaseChar() + r.substring(i + 1)
                if (out.isNotEmpty()) out.append(' ')
                out.append(r)
            }
            Typo.french(out.toString())
        }
    }

    /** Traduit des textes courts (des paragraphes) ; ceux déjà vus sont rendus aussitôt. */
    suspend fun texts(list: List<String>): List<String> {
        val sig = signature()
        val out = arrayOfNulls<String>(list.size)
        val miss = ArrayList<Int>()
        list.forEachIndexed { i, s -> synchronized(memo) { memo[sig + "\u0000" + s] }?.let { out[i] = it } ?: miss.add(i) }
        if (miss.isNotEmpty()) {
            val r = rawMany(miss.map { list[it] })
            synchronized(memo) { miss.forEachIndexed { k, i -> out[i] = r[k]; memo[sig + "\u0000" + list[i]] = r[k] } }
        }
        return out.map { it ?: "" }
    }

    /** Traduit un texte court (un paragraphe). */
    suspend fun text(s: String): String = texts(listOf(s)).first()

    /** La liste des noms a changé : les traductions gardées en mémoire sont refaites. */
    fun namesChanged() { synchronized(memo) { memo.clear() } }

    /** Le modèle est-il déjà sur le téléphone ? (sinon la première traduction le télécharge) */
    val modelOnPhone: Boolean get() = modelReady || Prefs.getBool("fr_model_ok", false)

    /**
     * Traduit un chapitre (HTML) paragraphe par paragraphe.
     * Les italiques et le gras à l'intérieur d'un paragraphe sont perdus ; les passages masqués restent masqués.
     */
    suspend fun chapter(ficId: String, ch: Int, html: String, onProgress: (String) -> Unit = {}): String {
        prepare(onProgress)
        // Les traductions gardées ne valent que pour la liste de noms et le moteur avec lesquels elles ont été faites.
        val sig = signature()
        val cache = withContext(Dispatchers.IO) { loadCache(ficId, ch) }.let { if (it.optString(NAMES_KEY) == sig) it else JSONObject().put(NAMES_KEY, sig) }
        val doc = withContext(Dispatchers.Default) { Jsoup.parseBodyFragment(html) }
        val list = leaves(doc.body()).ifEmpty { listOf(doc.body()) }
        val todo = list.map { it.text().trim() }.withIndex().filter { (_, t) -> t.any { it.isLetter() } && cache.optString(t).isEmpty() }
        // par paquets (quelques demandes en même temps en ligne), pour montrer l'avancement
        val step = if (online) 80 else 10
        var done = 0
        for (chunk in todo.chunked(step)) {
            onProgress("Traduction : ${done + 1}/${todo.size} paragraphes…")
            val src = chunk.map { it.value }
            rawMany(src).forEachIndexed { k, fr -> cache.put(src[k], fr) }
            done += chunk.size
        }
        list.forEach { el -> cache.optString(el.text().trim()).takeIf { it.isNotEmpty() }?.let { el.text(it) } }
        if (todo.isNotEmpty()) withContext(Dispatchers.IO) { saveCache(ficId, ch, cache) }
        return doc.body().html()
    }
}
