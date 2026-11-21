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
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Traductions gardées sur le téléphone, d'une session à l'autre : un chapitre déjà traduit (même seulement en partie)
 * se réaffiche tout de suite, sans réseau et sans nouvelle demande. Au plus [MAX] paragraphes, les plus anciens sortent d'abord.
 */
internal object DiskMemo {
    private const val MAX = 9000
    private val map = object : LinkedHashMap<String, String>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX
    }
    private var loaded = false
    private var dirty = false
    private var lastFlush = 0L

    private fun file() = File(Library.dataDir(), "traductions/memo.tsv").apply { parentFile?.mkdirs() }

    private fun key(k: String): String =
        MessageDigest.getInstance("SHA-1").digest(k.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.substring(0, 24)

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file()
            if (f.exists()) f.forEachLine { l -> val i = l.indexOf('\t'); if (i > 0) map[l.substring(0, i)] = l.substring(i + 1) }
        }
    }

    @Synchronized fun get(k: String): String? { load(); return map[key(k)] }

    @Synchronized fun put(k: String, v: String) {
        if (v.isBlank() || '\n' in v || '\t' in v) return
        load(); map[key(k)] = v; dirty = true
    }

    /** Écrit le fichier, au plus toutes les quelques secondes (écriture complète puis remplacement : jamais de fichier à moitié écrit). */
    @Synchronized fun flush(force: Boolean = false) {
        if (!dirty) return
        val now = System.currentTimeMillis()
        if (!force && now - lastFlush < 4000) return
        runCatching {
            val f = file(); val tmp = File(f.parentFile, "memo.tsv.tmp")
            tmp.bufferedWriter().use { w -> for ((k, v) in map) { w.write(k); w.write("\t"); w.write(v); w.write("\n") } }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            dirty = false; lastFlush = now
        }
    }

    @Synchronized fun clear() { loaded = true; map.clear(); dirty = false; runCatching { file().delete() } }

    @Synchronized fun size(): Int { load(); return map.size }
}

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

    /** Efface toutes les traductions gardées (mémoire et téléphone). */
    fun clearSaved() { synchronized(memo) { memo.clear() }; DiskMemo.clear() }

    /** Nombre de paragraphes traduits gardés sur le téléphone. */
    fun savedCount(): Int = DiskMemo.size()

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

    private val END_RX = Regex("[.!?…]+[\"'”’»)\\]*_~\u27e7\u27eb]*\\s+")

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
    fun signature(): String = Names.signature() + (if (online) "|g3" else "|m2") + (if (Slang.enabled) "|a1" else "")

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

    // ---------- Contrôle du résultat ----------

    private val EN_WORDS = hashSetOf("the", "and", "of", "to", "in", "that", "is", "was", "he", "she", "it", "with", "for", "on", "as", "you", "his", "her",
        "they", "had", "have", "this", "but", "not", "be", "are", "at", "from", "what", "said", "were", "would", "could", "there", "their", "been", "him", "who")
    private val FR_WORDS = hashSetOf("le", "la", "les", "des", "est", "une", "que", "qui", "dans", "pour", "pas", "vous", "nous", "je", "il", "elle", "et",
        "un", "du", "au", "sur", "avec", "mais", "ce", "ne", "se", "sont", "ont", "fait", "très", "ils", "elles", "mon", "ton", "son", "sa", "ses", "cette")
    private val WORD_RX = Regex("[\\p{L}']+")

    private fun wordList(s: String): List<String> = WORD_RX.findAll(s.lowercase()).map { it.value }.toList()
    internal fun englishScore(s: String): Int = wordList(s).count { it in EN_WORDS }
    internal fun frenchScore(s: String): Int = wordList(s).count { it in FR_WORDS }

    /** Texte déjà en français (fic écrite en français, passage cité) : inutile de le traduire. */
    internal fun alreadyFrench(s: String): Boolean {
        val fr = frenchScore(s)
        return fr >= 2 && fr >= englishScore(s) * 3
    }

    /**
     * La traduction est-elle plausible ? Refuse : vide, trop courte ou trop longue par rapport au texte,
     * ou texte rendu tel quel alors qu'il est clairement en anglais.
     */
    internal fun good(src: String, tr: String): Boolean {
        val t = Marks.strip(tr).trim()
        if (t.isEmpty()) return false
        val sl = Marks.strip(src).count { it.isLetter() }
        if (sl < 3) return true
        val tl = t.count { it.isLetter() }
        if (sl >= 25 && (tl < sl * 0.3 || tl > sl * 3.5)) return false
        if (sl >= 12 && englishScore(src) >= 2 && t.equals(Marks.strip(src).trim(), ignoreCase = true)) return false
        return true
    }

    /** Un texte traduit, et s'il vient du téléphone alors que la traduction en ligne était voulue (qualité moindre). */
    class Out(val text: String, val fallback: Boolean)

    /** Combien de textes ont dû passer par le modèle du téléphone depuis le début : l'affichage peut prévenir. */
    @Volatile var fallbackCount = 0
        private set

    /** Dans combien de temps le service en ligne sera réessayé (0 s'il est disponible), en millisecondes. */
    val onlineRetryIn: Long get() = Online.pausedFor

    /**
     * Chaque texte est traduit en ligne ; ceux dont le résultat est douteux repartent un par un ;
     * ceux qui échouent encore (ou si le service est coupé) passent sur le téléphone.
     */
    private suspend fun engine(texts: List<String>): List<Out> {
        if (texts.isEmpty()) return emptyList()
        val res = arrayOfNulls<Out>(texts.size)
        if (online && Online.available) {
            try {
                Online.translate(texts).forEachIndexed { i, r -> if (good(texts[i], r)) res[i] = Out(r, false) }
                val bad = res.indices.filter { res[it] == null }
                if (bad.isNotEmpty() && Online.available) {
                    Online.translate(bad.map { texts[it] }, alone = true).forEachIndexed { k, r -> if (good(texts[bad[k]], r)) res[bad[k]] = Out(r, false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
        var used = 0
        for (i in res.indices) if (res[i] == null) { res[i] = Out(device(texts[i]), online); used++ }
        if (used > 0 && online) fallbackCount += used
        return res.map { it!! }
    }

    /**
     * Traduction de morceaux, avec les noms protégés (personnages, îles, techniques…) gardés tels quels
     * et les termes officiels mis en français. Si le traducteur abîme un repère, ce morceau seulement est retraduit sans protection.
     */
    private suspend fun translatePieces(raw: List<String>): List<Out> {
        // argot, mèmes et langage d'internet remplacés par leur sens courant (si la fonction est active)
        val src = raw.map { Slang.rewrite(it) }
        val word = online && Online.available
        val prot = src.map { Names.protect(it, word) }
        val res = arrayOfNulls<Out>(src.size)
        val send = ArrayList<Int>()
        prot.forEachIndexed { i, p ->
            // morceau fait seulement de noms (« Usopp ! ») : rien à traduire
            if (!p.isEmpty && !Regex("\\p{L}{2}").containsMatchIn(p.bare())) res[i] = Out(p.fill(), false) else send += i
        }
        val first = engine(send.map { prot[it].text })
        val retry = ArrayList<Int>()
        send.forEachIndexed { k, i -> prot[i].restore(first[k].text)?.let { res[i] = Out(it, first[k].fallback) } ?: retry.add(i) }
        if (retry.isNotEmpty()) engine(retry.map { src[it] }).forEachIndexed { k, r -> res[retry[k]] = r }
        return res.map { it ?: Out("", false) }
    }

    /** Traduit plusieurs paragraphes d'un coup (en ligne, ils partent ensemble). */
    private suspend fun rawMany(list: List<String>): List<Out> {
        val max = if (online && Online.available) Online.MAX_CHARS - 200 else 300
        // texte déjà en français : gardé tel quel
        val keep = list.map { alreadyFrench(Marks.strip(it)) }
        val split = list.mapIndexed { i, p -> if (keep[i]) emptyList() else pieces(p, max) }
        val tr = translatePieces(split.flatten().map { it.text })
        var k = 0
        return split.mapIndexed { n, ps ->
            if (keep[n]) return@mapIndexed Out(list[n], false)
            val out = StringBuilder()
            var fb = false
            for (pc in ps) {
                val o = tr[k++]
                if (o.fallback) fb = true
                var r = o.text.trim()
                if (pc.shout) r = r.uppercase(java.util.Locale.FRENCH)
                // Le traducteur met parfois une minuscule en tête (terme remplacé, phrase commençant par un nom…).
                val a = pc.text.firstOrNull { it.isLetter() }; val i = r.indexOfFirst { it.isLetter() }
                if (a != null && a.isUpperCase() && i >= 0 && r[i].isLowerCase()) r = r.substring(0, i) + r[i].uppercaseChar() + r.substring(i + 1)
                if (out.isNotEmpty()) out.append(' ')
                out.append(r)
            }
            Out(Marks.fix(list[n], Typo.french(out.toString())), fb)
        }
    }

    /**
     * Traduit des textes courts (des paragraphes) ; ceux déjà vus sont rendus aussitôt.
     * Le texte peut porter des repères d'italique et de gras ([Marks]) ; ils reviennent s'ils ont survécu à la traduction.
     * Les traductions de secours (téléphone) ne sont pas gardées : elles seront refaites dès que le service en ligne répond.
     */
    suspend fun textsEx(list: List<String>): List<Out> {
        val sig = signature()
        val out = arrayOfNulls<Out>(list.size)
        val miss = ArrayList<Int>()
        list.forEachIndexed { i, s -> synchronized(memo) { memo[sig + "\u0000" + s] }?.let { out[i] = Out(it, false) } ?: miss.add(i) }
        if (miss.isNotEmpty()) {
            // gardés sur le téléphone depuis une lecture précédente ?
            val disk = withContext(Dispatchers.IO) { miss.map { DiskMemo.get(sig + "\u0000" + list[it]) } }
            val todo = ArrayList<Int>()
            miss.forEachIndexed { k, i -> val d = disk[k]; if (d != null) { out[i] = Out(d, false); synchronized(memo) { memo[sig + "\u0000" + list[i]] = d } } else todo.add(i) }
            if (todo.isNotEmpty()) {
                val r = rawMany(todo.map { list[it] })
                synchronized(memo) { todo.forEachIndexed { k, i -> out[i] = r[k]; if (!r[k].fallback) memo[sig + "\u0000" + list[i]] = r[k].text } }
                withContext(Dispatchers.IO) {
                    todo.forEachIndexed { k, i -> if (!r[k].fallback) DiskMemo.put(sig + "\u0000" + list[i], r[k].text) }
                    DiskMemo.flush()
                }
            }
        }
        return out.map { it ?: Out("", false) }
    }

    suspend fun texts(list: List<String>): List<String> = textsEx(list).map { it.text }

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
            rawMany(src).forEachIndexed { k, fr -> cache.put(src[k], fr.text) }
            done += chunk.size
        }
        list.forEach { el -> cache.optString(el.text().trim()).takeIf { it.isNotEmpty() }?.let { el.text(it) } }
        if (todo.isNotEmpty()) withContext(Dispatchers.IO) { saveCache(ficId, ch, cache) }
        return doc.body().html()
    }
}
