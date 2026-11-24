package com.thousandlabs.ohara.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Recherche dans le texte des chapitres gardés sur le téléphone : retrouver une scène, une réplique, un nom.
 * Sans réseau. Les majuscules, les accents et les apostrophes courbes ne comptent pas.
 */
object ChapterSearch {
    data class Hit(val ficId: String, val ficTitle: String, val index: Int, val chapterTitle: String,
                   val before: String, val match: String, val after: String)

    private val RX_TAG = Regex("<[^>]+>")
    private val RX_BLOCK = Regex("(?i)</?(p|div|br|li|h\\d|blockquote|hr)[^>]*>")
    private val RX_SPACE = Regex("\\s+")
    private val RX_MARK = Regex("\\p{M}+")

    /** Texte simplifié caractère par caractère (même longueur que l'original, pour retrouver la position). */
    fun fold(s: String): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            val f = when (c) {
                '’', '‘', 'ʼ', '`' -> '\''
                '“', '”', '«', '»' -> '"'
                '–', '—' -> '-'
                else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(RX_MARK, "").firstOrNull() ?: c
            }
            b.append(f.lowercaseChar())
        }
        return b.toString()
    }

    fun plain(html: String): String =
        org.jsoup.parser.Parser.unescapeEntities(html.replace(RX_BLOCK, " ").replace(RX_TAG, ""), false).replace(RX_SPACE, " ").trim()

    /** Cherche [query] dans les chapitres gardés (d'une seule fic si [ficId] est donné). */
    suspend fun search(query: String, ficId: String?, onProgress: (Int, Int) -> Unit, onHit: (Hit) -> Unit): Int = withContext(Dispatchers.IO) {
        val q = fold(query.trim().replace(RX_SPACE, " "))
        if (q.length < 2) return@withContext 0
        val fics = Library.fics.value.filter { ficId == null || it.id == ficId }
        val jobs = fics.flatMap { f -> Library.downloadedSet(f.id).sorted().map { f to it } }
        var hits = 0
        jobs.forEachIndexed { n, (f, idx) ->
            ensureActive()
            if (n % 10 == 0) withContext(Dispatchers.Main) { onProgress(n, jobs.size) }
            val html = Library.readChapter(f.id, idx) ?: return@forEachIndexed
            val text = plain(html)
            val folded = fold(text)
            var from = 0
            var inChapter = 0
            while (inChapter < 5 && hits < 500) {
                val at = folded.indexOf(q, from)
                if (at < 0) break
                val start = (at - 90).coerceAtLeast(0).let { s -> if (s == 0) 0 else text.indexOf(' ', s).takeIf { it in s until at }?.plus(1) ?: s }
                val end = (at + q.length + 110).coerceAtMost(text.length).let { e -> if (e == text.length) e else text.lastIndexOf(' ', e).takeIf { it > at + q.length } ?: e }
                val hit = Hit(f.id, f.title, idx, f.chapters.getOrNull(idx)?.title ?: "Chapitre ${idx + 1}",
                    (if (start > 0) "…" else "") + text.substring(start, at), text.substring(at, at + q.length),
                    text.substring(at + q.length, end) + if (end < text.length) "…" else "")
                withContext(Dispatchers.Main) { onHit(hit) }
                hits++; inChapter++; from = at + q.length
            }
            if (hits >= 500) return@withContext hits
        }
        withContext(Dispatchers.Main) { onProgress(jobs.size, jobs.size) }
        hits
    }
}

@Composable
fun ChapterSearchScreen(ficId: String?) {
    val fic = ficId?.let { Library.get(it) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyThis by rememberSaveable { mutableStateOf(ficId != null) }
    val hits = remember { mutableStateListOf<ChapterSearch.Hit>() }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var done by remember { mutableStateOf<Int?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val totalChapters = remember(onlyThis) { Library.fics.value.filter { !onlyThis || it.id == ficId }.sumOf { Library.downloadedCount(it.id) } }

    fun run() {
        job?.cancel(); hits.clear(); done = null
        if (query.trim().length < 2) return
        job = scope.launch {
            val n = ChapterSearch.search(query, if (onlyThis) ficId else null, { a, b -> progress = a to b }) { hits += it }
            progress = null; done = n
        }
    }

    Scaffold(topBar = { BackTopBar("Rechercher dans les chapitres", if (onlyThis && fic != null) fic.title else "${Fmt.number(totalChapters.toLong())} chapitres sur le téléphone") }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Une phrase, une réplique, un nom…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; job?.cancel(); hits.clear(); done = null }) { Icon(Icons.Outlined.Close, "Effacer") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus)
            )
            if (fic != null) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(onlyThis, { onlyThis = true; run() }, { Text("Cette fic") })
                FilterChip(!onlyThis, { onlyThis = false; run() }, { Text("Toutes les fics") })
            }
            progress?.let { (a, b) ->
                LinearProgressIndicator(progress = { if (b == 0) 0f else a.toFloat() / b }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val status = when {
                done == null && progress == null && hits.isEmpty() -> if (totalChapters == 0) "Aucun chapitre n'est encore gardé sur le téléphone. Les chapitres lus d'une fic de la bibliothèque y sont gardés d'office ; « Télécharger » garde toute la fic." else "Majuscules, accents et apostrophes n'ont pas d'importance. Touchez un résultat pour ouvrir le chapitre à ce passage."
                done == 0 -> "Aucun passage trouvé."
                done != null -> "${Fmt.plural(done!!, "passage trouvé", "passages trouvés")}" + if (done!! >= 500) " (les 500 premiers)" else ""
                else -> "${hits.size} trouvés…"
            }
            Text(status, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits) { h ->
                    Column(Modifier.fillMaxWidth().clickable {
                        BrowserHolder.findSeed = h.match
                        BrowserHolder.openOffline(h.ficId, h.index)
                    }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(if (onlyThis && fic != null) h.chapterTitle else "${h.ficTitle} · ${h.chapterTitle}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Text(buildAnnotatedString {
                            append(h.before)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.primary.copy(alpha = .18f))) { append(h.match) }
                            append(h.after)
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (query.isBlank()) runCatching { focus.requestFocus() } else run() }
}
