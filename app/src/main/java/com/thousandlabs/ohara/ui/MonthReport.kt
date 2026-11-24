package com.thousandlabs.ohara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Bilan du mois : ce que vous avez lu, comparé au mois d'avant. */
object MonthReport {
    fun prefix(c: Calendar) = "%04d-%02d".format(Locale.ROOT, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    fun name(c: Calendar): String = SimpleDateFormat("MMMM yyyy", Locale.FRANCE).format(c.time).replaceFirstChar { it.uppercase() }

    fun totals(days: Map<String, LongArray>, prefix: String): LongArray =
        LongArray(4).also { t -> days.filterKeys { it.startsWith(prefix) }.values.forEach { v -> t[0] += v[0]; t[1] += v[1]; t[2] += v[2]; if (Stats.active(v)) t[3]++ } }

    /** Au début d'un mois, signale une fois que le bilan du mois précédent est prêt. */
    fun announce() {
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_MONTH) > 7) return
        val prev = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1) }
        val key = prefix(prev)
        if (Prefs.getString("report_seen", "") == key) return
        if (totals(ReadingLog.snapshot(), key)[3] == 0L) return
        Prefs.putString("report_seen", key)
        Toast.show("Votre bilan de ${SimpleDateFormat("MMMM", Locale.FRANCE).format(prev.time)} est prêt : Bibliothèque › menu › Statistiques › Bilan du mois.")
    }
}

@Composable
fun MonthReportScreen() {
    val fics by Library.fics.collectAsState()
    val days = remember { ReadingLog.snapshot() }
    val now = Calendar.getInstance()
    // au début du mois, on ouvre sur le mois qui vient de finir
    var back by remember { mutableIntStateOf(if (now.get(Calendar.DAY_OF_MONTH) <= 7) 1 else 0) }
    val month = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
    val prevMonth = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val p = MonthReport.prefix(month)
    val t = MonthReport.totals(days, p)
    val before = MonthReport.totals(days, MonthReport.prefix(prevMonth))
    val perFic = remember(p) { ReadingLog.ficsFor(p) }
    val byId = fics.associateBy { it.id }
    val topFics = perFic.entries.filter { it.value[1] >= 60 || it.value[0] > 0 }
        .sortedWith(compareByDescending<Map.Entry<String, LongArray>> { it.value[1] }.thenByDescending { it.value[0] }).take(5)
    val fandoms = perFic.entries.flatMap { (id, a) -> (byId[id]?.fandoms ?: emptyList()).map { it to a[1] } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(3)
    val bestDay = days.filterKeys { it.startsWith(p) }.maxByOrNull { it.value[1] }?.takeIf { it.value[1] >= 60 }
    val oldest = days.keys.minOrNull()

    fun trend(a: Long, b: Long): String? = when {
        b <= 0L -> null
        else -> { val d = ((a - b) * 100 / b).toInt(); if (d >= 0) "+$d % par rapport à ${SimpleDateFormat("MMMM", Locale.FRANCE).format(prevMonth.time)}" else "$d % par rapport à ${SimpleDateFormat("MMMM", Locale.FRANCE).format(prevMonth.time)}" }
    }

    Scaffold(topBar = { BackTopBar("Bilan du mois") }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back++ }, enabled = oldest != null && MonthReport.prefix(prevMonth) >= oldest.take(7)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Mois précédent")
                }
                Text(MonthReport.name(month), Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { back-- }, enabled = back > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Mois suivant") }
            }
            if (t[3] == 0L) {
                NoteCard("Aucune lecture comptée ce mois-ci.", Modifier.padding(16.dp))
                return@Column
            }
            Surface(Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Stats.hours(t[1]), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text("de lecture", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    trend(t[1], before[1])?.let { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mini("mots lus", Fmt.number(t[0]), Modifier.weight(1f))
                Mini("chapitres", Fmt.number(t[2]), Modifier.weight(1f))
                Mini("jours de lecture", "${t[3]}", Modifier.weight(1f))
            }
            val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
            SectionTitle("En chiffres")
            SettingRow("Moyenne", "${Stats.hours(t[1] / daysInMonth)} par jour · ${Stats.hours(t[1] / t[3])} par jour de lecture")
            bestDay?.let { (k, a) -> SettingRow("Meilleur jour", "${Stats.longDay(k)} : ${Stats.hours(a[1])}, ${Fmt.number(a[0])} mots") }
            if (t[1] >= 600 && t[0] > 0) SettingRow("Vitesse du mois", "${(t[0] * 60 / t[1]).coerceIn(80, 900)} mots par minute")
            // équivalences parlantes : un roman de poche compte environ 90 000 mots
            if (t[0] >= 45_000) SettingRow("Équivalent", "environ ${"%.1f".format(Locale.FRANCE, t[0] / 90_000.0).removeSuffix(",0")} romans de poche")
            if (topFics.isNotEmpty()) {
                SectionTitle("Fics du mois")
                topFics.forEachIndexed { i, (id, a) ->
                    val f = byId[id]
                    SettingRow(
                        (if (i == 0) "Fic du mois : " else "") + (f?.title ?: "Fic pas encore dans la bibliothèque"),
                        "${Stats.hours(a[1])} · ${Fmt.number(a[0])} mots" + (f?.fandoms?.firstOrNull()?.let { " · $it" } ?: ""),
                        if (i == 0) Icons.Outlined.EmojiEvents else null,
                        onClick = f?.let { { Nav.push(Screen.FicDetail(it.id)) } }
                    )
                }
            }
            if (fandoms.isNotEmpty()) {
                SectionTitle("Fandoms du mois")
                fandoms.forEach { (fd, s) -> SettingRow(fd, Stats.hours(s)) }
            }
            if (perFic.isEmpty()) NoteCard("Le détail par fic est compté à partir de cette version d'Ohara : il apparaîtra dans les prochains bilans.", Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
