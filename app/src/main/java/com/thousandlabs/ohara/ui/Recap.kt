package com.thousandlabs.ohara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.sources.Cleaner
import com.thousandlabs.ohara.translate.Translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * « Précédemment » : quand vous reprenez une fic laissée depuis un moment, les phrases clés
 * des derniers chapitres lus (résumé automatique par extraction, fait sur le téléphone).
 */
object Recap {
    /** Fic dont le résumé est à afficher. */
    var pending by mutableStateOf<String?>(null)
    var enabled: Boolean get() = Prefs.getBool("recap", true); set(v) = Prefs.putBool("recap", v)
    var afterDays: Int get() = Prefs.getInt("recap_days", 7); set(v) = Prefs.putInt("recap_days", v)

    data class Part(val chapter: Int, val title: String, val sentences: List<String>)

    /** Chapitres lus et gardés sur le téléphone, du plus récent au plus ancien. */
    private fun keptRead(f: Fic): List<Int> {
        val last = maxOf(f.maxChapterRead, f.progressChapter)
        return (last downTo 0).filter { Library.hasChapter(f.id, it) }
    }

    fun available(f: Fic) = keptRead(f).isNotEmpty()

    /** À appeler avant de mettre à jour la date d'ouverture. */
    fun maybeShow(f: Fic) {
        if (!enabled || f.lastOpened <= 0) return
        if (System.currentTimeMillis() - f.lastOpened < afterDays * 86_400_000L) return
        if (available(f)) pending = f.id
    }

    private val STOP = (
        "the a an and or but of to in on at for with from by as is was were be been are it its this that these those he she they them his her their " +
        "him i you we me my your our us not no so if then than there here what when where who which how all any just very can could would should will " +
        "had has have do did does into out up down over about after before again more most some such only own same too also back even still well now " +
        "le la les un une des et ou mais de du au aux en dans sur pour par avec sans ce cet cette ces il elle ils elles on je tu nous vous son sa ses leur " +
        "leurs lui y ne pas plus que qui quoi dont est était sont été être avoir a ai as avait comme tout tous toute toutes très bien aussi encore"
        ).split(' ').toSet()

    private val SPLIT = Regex("(?<=[.!?…])[\"”»’']?\\s+(?=[\"“«‘'A-ZÀ-Ý])")
    private val WORD = Regex("[\\p{L}']+")

    /** Phrases les plus porteuses d'un chapitre, dans leur ordre d'apparition. */
    fun keySentences(text: String, n: Int): List<String> {
        val sentences = text.split(Regex("\\n+")).flatMap { it.split(SPLIT) }.map { it.trim() }
            .filter { it.length in 40..320 && it.count { c -> c.isLetter() } > 25 }
        if (sentences.isEmpty()) return emptyList()
        val freq = HashMap<String, Int>()
        val names = HashMap<String, Int>()
        sentences.forEach { s ->
            WORD.findAll(s).forEachIndexed { i, m ->
                val w = m.value.lowercase().trim('\'')
                if (w.length > 3 && w !in STOP) freq[w] = (freq[w] ?: 0) + 1
                if (i > 0 && m.value.first().isUpperCase() && m.value.length > 2) names[w] = (names[w] ?: 0) + 1
            }
        }
        val maxF = (freq.values.maxOrNull() ?: 1).toDouble()
        val scored = sentences.mapIndexed { i, s ->
            val words = WORD.findAll(s).map { it.value.lowercase().trim('\'') }.filter { it.length > 3 && it !in STOP }.toList()
            if (words.isEmpty()) return@mapIndexed Triple(i, s, 0.0)
            var sc = words.sumOf { (freq[it] ?: 0) / maxF } / Math.sqrt(words.size.toDouble())
            sc += words.count { (names[it] ?: 0) >= 2 } * 0.35
            sc *= 0.7 + 0.6 * i / sentences.size.coerceAtLeast(1)   // la fin du chapitre compte plus
            val t = s.trimStart()
            if (t.startsWith("\"") || t.startsWith("“") || t.startsWith("«") || t.startsWith("'")) sc *= 0.65
            Triple(i, s, sc)
        }
        return scored.sortedByDescending { it.third }.take(n).sortedBy { it.first }.map { it.second }
    }

    suspend fun build(f: Fic): List<Part> = withContext(Dispatchers.Default) {
        val chapters = keptRead(f).take(2).reversed()
        chapters.mapIndexed { k, idx ->
            val raw = Library.readChapter(f.id, idx) ?: return@mapIndexed null
            val text = Cleaner.toText(Masking.applyToHtml(f, raw, remove = true).html)
            val picked = keySentences(text, if (k == chapters.lastIndex) 6 else 3)
            val title = f.chapters.getOrNull(idx)?.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${idx + 1}"
            Part(idx, title, picked)
        }.filterNotNull().filter { it.sentences.isNotEmpty() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(ficId: String, onDismiss: () -> Unit) {
    val fic = remember(ficId) { Library.get(ficId) } ?: run { onDismiss(); return }
    var parts by remember { mutableStateOf<List<Recap.Part>?>(null) }
    var translated by remember { mutableStateOf(false) }
    LaunchedEffect(ficId) {
        val p = runCatching { Recap.build(fic) }.getOrDefault(emptyList())
        parts = p
        if (Translate.enabled && p.isNotEmpty()) runCatching {
            parts = p.map { part -> part.copy(sentences = Translate.texts(part.sentences)) }
            translated = true
        }
    }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Précédemment", style = MaterialTheme.typography.titleLarge.merge(SerifTitle))
                    Text(fic.title, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            val p = parts
            when {
                p == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Lecture des derniers chapitres…")
                }
                p.isEmpty() -> Text("Pas assez de texte gardé sur le téléphone pour faire un résumé.", style = MaterialTheme.typography.bodyMedium)
                else -> p.forEach { part ->
                    Text("Chapitre ${part.chapter + 1} · ${part.title}", style = MaterialTheme.typography.labelLarge, color = cs.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    part.sentences.forEach { s ->
                        Row(Modifier.padding(bottom = 8.dp)) {
                            Box(Modifier.padding(top = 3.dp).width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(cs.outlineVariant))
                            Spacer(Modifier.width(10.dp))
                            Text(s, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Normal))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Résumé automatique : les phrases clés des derniers chapitres lus, choisies sur le téléphone" +
                if (translated) ", puis traduites." else ".",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Reprendre la lecture") }
            TextButton(onClick = { Recap.enabled = false; Toast.show("« Précédemment » désactivé. Réactivez-le dans Réglages › Lecture."); onDismiss() },
                modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ne plus proposer") }
        }
    }
}
