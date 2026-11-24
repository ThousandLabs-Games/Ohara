package com.thousandlabs.ohara.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.privacy.AdBlocker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Statistiques de lecture : journal par jour (mots, temps, chapitres) et compteurs de chaque fic. */
object Stats {
    private val fr = Locale.FRANCE

    /** Premier jour mesuré avec le nouveau suivi du temps (les jours d'avant sous-comptaient le temps). */
    val measuredSince: String
        get() = Prefs.getString("stats_since", "").ifBlank {
            ReadingLog.dayKey(Calendar.getInstance()).also { Prefs.putString("stats_since", it) }
        }

    /** Vitesse de lecture mesurée (mots par minute), 230 par défaut tant qu'il n'y a pas assez de mesures. */
    fun speed(days: Map<String, LongArray> = ReadingLog.snapshot()): Int {
        val since = measuredSince
        // seuls les jours où l'on a à la fois lu des chapitres et du temps compté servent au calcul
        val ok = days.filter { (k, v) -> k >= since && v[0] > 0 && v[1] > 0 }.values
        val words = ok.sumOf { it[0] }
        val secs = ok.sumOf { it[1] }
        return if (secs > 900 && words > 2000) (words * 60 / secs).toInt().coerceIn(80, 900) else 230
    }

    /** Temps restant estimé pour finir une fic, d'après votre vitesse. */
    fun remaining(f: Fic): Long? {
        if (f.words <= 0 || f.totalChapters <= 0) return null
        val done = ((f.maxChapterRead + 1).coerceAtLeast(0).toDouble() / f.totalChapters).coerceIn(0.0, 1.0)
        val left = (f.words * (1 - done)).toLong()
        return if (left <= 0) 0 else left * 60 / speed()
    }

    fun day(c: Calendar): String = ReadingLog.dayKey(c)
    fun shortDay(c: Calendar): String = SimpleDateFormat("EEE d", fr).format(c.time).replace(".", "")
    fun longDay(key: String): String = runCatching {
        SimpleDateFormat("EEEE d MMMM yyyy", fr).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(key)!!)
    }.getOrDefault(key)
    fun month(c: Calendar): String = SimpleDateFormat("MMM yy", fr).format(c.time).replace(".", "")

    /** Durée en heures et minutes, même au-delà d'une journée (« 31 h 20 min »). */
    fun hours(seconds: Long): String {
        if (seconds < 60) return if (seconds <= 0) "0 min" else "< 1 min"
        val h = seconds / 3600; val m = (seconds % 3600) / 60
        return if (h == 0L) "$m min" else if (m == 0L) "$h h" else "$h h $m min"
    }

    fun active(v: LongArray?) = v != null && (v[0] > 0 || v[1] >= 60 || v[2] > 0)
}

private enum class Period(val label: String) { WEEK("7 jours"), MONTH("30 jours"), YEAR("12 mois") }
private enum class Measure(val label: String) { TIME("Temps"), WORDS("Mots"), CHAPTERS("Chapitres") }

@Composable
fun StatsScreen() {
    val fics by Library.fics.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { AdBlocker.flushCount() }
    val days = remember(refresh) { ReadingLog.snapshot() }
    var period by remember { mutableStateOf(Period.MONTH) }
    var measure by remember { mutableStateOf(Measure.TIME) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val now = Calendar.getInstance()
    fun at(back: Int) = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
    fun v(a: LongArray?, m: Measure) = when (m) { Measure.TIME -> a?.get(1) ?: 0L; Measure.WORDS -> a?.get(0) ?: 0L; Measure.CHAPTERS -> a?.get(2) ?: 0L }
    fun fmt(x: Long, m: Measure) = when (m) {
        Measure.TIME -> Stats.hours(x)
        Measure.WORDS -> Fmt.number(x) + " mots"
        Measure.CHAPTERS -> Fmt.plural(x.toInt(), "chapitre")
    }
    fun sumDays(n: Int): LongArray = LongArray(3).also { acc -> for (b in 0 until n) days[Stats.day(at(b))]?.let { for (i in 0..2) acc[i] += it[i] } }

    // séries du graphique
    val bars: List<Pair<String, Long>> = when (period) {
        Period.WEEK, Period.MONTH -> {
            val n = if (period == Period.WEEK) 7 else 30
            (n - 1 downTo 0).map { b -> val c = at(b); Stats.shortDay(c) to v(days[Stats.day(c)], measure) }
        }
        Period.YEAR -> (11 downTo 0).map { back ->
            val c = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
            val prefix = "%04d-%02d".format(Locale.ROOT, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            Stats.month(c) to days.filterKeys { it.startsWith(prefix) }.values.sumOf { v(it, measure) }
        }
    }

    // séries de jours
    var streak = 0
    run {
        val c = now.clone() as Calendar
        if (!Stats.active(days[Stats.day(c)])) c.add(Calendar.DAY_OF_YEAR, -1)
        while (Stats.active(days[Stats.day(c)])) { streak++; c.add(Calendar.DAY_OF_YEAR, -1) }
    }
    var best = 0
    run {
        var cur = 0; var prev: Calendar? = null
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        days.filterValues { Stats.active(it) }.keys.sorted().forEach { k ->
            val c = Calendar.getInstance().apply { time = df.parse(k)!! }
            val p = prev
            cur = if (p != null && (p.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.let { Stats.day(it) == k }) cur + 1 else 1
            best = maxOf(best, cur); prev = c
        }
    }

    val totalSec = days.values.sumOf { it[1] }
    val totalWords = days.values.sumOf { it[0] }
    val totalChapters = days.values.sumOf { it[2] }
    val activeDays = days.values.count { Stats.active(it) }
    val bestDay = days.maxByOrNull { it.value[1] }?.takeIf { it.value[1] >= 60 }
    val downloaded = remember(fics) { fics.sumOf { Library.downloadedCount(it.id) } }
    val today = sumDays(1); val week = sumDays(7); val month = sumDays(30)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.tertiary

    Scaffold(topBar = { BackTopBar("Statistiques de lecture", if (activeDays > 0) "${Fmt.plural(activeDays, "jour")} de lecture" else null) {
        IconButton(onClick = { confirmReset = true }) { Icon(Icons.Outlined.DeleteOutline, "Remettre le journal à zéro") }
    } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Temps de lecture", Stats.hours(totalSec), Modifier.weight(1f))
                StatBox("Mots lus", Fmt.words(totalWords).removeSuffix(" mots").replace("?", "0"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Chapitres lus", Fmt.number(totalChapters), Modifier.weight(1f))
                StatBox("Vitesse", "${Stats.speed(days)} mots/min", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Jours d'affilée", "$streak", Modifier.weight(1f))
                StatBox("Record d'affilée", "$best", Modifier.weight(1f))
            }

            SettingRow("Bilan du mois", "Temps, mots, fic et fandoms du mois, comparés au mois précédent", Icons.Outlined.CalendarMonth, onClick = { Nav.push(Screen.MonthReport) })

            SectionTitle("Évolution")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> FilterChip(period == p, { period = p; picked = null }, { Text(p.label) }) }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Measure.entries.forEach { m -> FilterChip(measure == m, { measure = m }, { Text(m.label) }) }
            }
            val max = (bars.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
            Canvas(
                Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(bars.size) {
                        detectTapGestures { o -> val i = (o.x / (size.width / bars.size.toFloat())).toInt().coerceIn(0, bars.size - 1); picked = if (picked == i) null else i }
                    }
            ) {
                val w = size.width / bars.size
                bars.forEachIndexed { i, (_, value) ->
                    val h = (value.toFloat() / max) * size.height
                    drawRoundRect(track, Offset(i * w + w * 0.15f, 0f), Size(w * 0.7f, size.height), CornerRadius(4f, 4f))
                    if (value > 0) drawRoundRect(if (picked == i) accent else primary, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), CornerRadius(4f, 4f))
                }
            }
            val sel = picked?.let { bars.getOrNull(it) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bars.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bars.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (sel != null) "${sel.first} : ${fmt(sel.second, measure)}"
                else "Touchez une barre pour son détail · le plus haut : ${fmt(bars.maxOfOrNull { it.second } ?: 0L, measure)}",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = if (sel != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Résumé")
            fun line(a: LongArray) = listOf(Stats.hours(a[1]), Fmt.number(a[0]) + " mots", Fmt.plural(a[2].toInt(), "chapitre")).joinToString(" · ")
            SettingRow("Aujourd'hui", line(today))
            SettingRow("7 derniers jours", line(week))
            SettingRow("30 derniers jours", line(month) + if (month[1] > 0) " · ${Stats.hours(month[1] / 30)} par jour en moyenne" else "")
            if (activeDays > 0) SettingRow("Par jour de lecture", "${Stats.hours(totalSec / activeDays)} · ${Fmt.number(totalWords / activeDays)} mots en moyenne")
            bestDay?.let { (k, a) -> SettingRow("Meilleur jour", "${Stats.longDay(k)} : ${line(a)}") }

            SectionTitle("Bibliothèque")
            SettingRow("Fics", "${fics.size} au total · ${fics.count { Shelves.DONE in it.shelves }} lues · ${fics.count { Shelves.READING in it.shelves }} en cours · ${fics.count { Shelves.ABANDONED in it.shelves }} abandonnées")
            SettingRow("Chapitres sur le téléphone", "${Fmt.number(downloaded.toLong())} · ${Fmt.bytes(remember { Library.storageBytes() })}")
            SettingRow("Notes données", fics.filter { it.note >= 0 }.let { l -> if (l.isEmpty()) "Aucune" else "${l.size} · moyenne ${"%.1f".format(Locale.FRANCE, l.map { it.note }.average())}/10" })
            SettingRow("Surlignages", "${fics.sumOf { it.highlights.size }}")
            SettingRow("Publicités et pisteurs bloqués", Fmt.number(AdBlocker.totalBlocked))

            val topFandoms = fics.flatMap { f -> f.fandoms.map { it to f } }.groupBy({ it.first }, { it.second })
                .mapValues { (_, l) -> l.sumOf { it.secondsRead } to l.sumOf { it.wordsRead } }
                .entries.filter { it.value.first >= 60 || it.value.second > 0 }
                .sortedWith(compareByDescending<Map.Entry<String, Pair<Long, Long>>> { it.value.first }.thenByDescending { it.value.second }).take(6)
            if (topFandoms.isNotEmpty()) {
                SectionTitle("Fandoms les plus lus")
                topFandoms.forEach { (fd, p) -> SettingRow(fd, "${Stats.hours(p.first)} · ${Fmt.words(p.second).replace("?", "0")}") }
            }
            val topFics = fics.filter { it.secondsRead >= 60 || it.wordsRead > 0 }
                .sortedWith(compareByDescending<Fic> { it.secondsRead }.thenByDescending { it.wordsRead }).take(8)
            if (topFics.isNotEmpty()) {
                SectionTitle("Fics les plus lues")
                topFics.forEach { f -> SettingRow(f.title, "${Stats.hours(f.secondsRead)} · ${Fmt.words(f.wordsRead).replace("?", "0")}", onClick = { Nav.push(Screen.FicDetail(f.id)) }) }
            }
            NoteCard(
                "Le temps est compté quand Ohara est à l'écran et que vous lisez un chapitre : les pauses de lecture sans toucher l'écran comptent, " +
                    "mais plus rien n'est compté après 2 min 30 sans faire défiler, appli fermée ou écran éteint. Un chapitre compte comme lu à 60 % parcourus, " +
                    "une seule fois (une relecture ne s'ajoute pas). Les onglets privés ne comptent pas. La vitesse est calculée à partir du ${Stats.longDay(Stats.measuredSince)}.",
                Modifier.padding(16.dp), icon = Icons.Outlined.Info
            )
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text("Remettre le journal à zéro ?") },
        text = { Text("Le temps, les mots et les chapitres lus jour par jour sont effacés, ainsi que les séries de jours. Les fics, leur progression et le temps passé sur chacune restent.") },
        confirmButton = { TextButton({
            ReadingLog.clear(); Prefs.putString("stats_since", Stats.day(Calendar.getInstance()))
            confirmReset = false; refresh++; Toast.show("Journal de lecture remis à zéro.")
        }) { Text("Effacer") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Annuler") } }
    )
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
