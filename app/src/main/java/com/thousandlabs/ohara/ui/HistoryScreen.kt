package com.thousandlabs.ohara.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.History
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.SearchQuery
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun dayLabel(t: Long): String {
    val c = Calendar.getInstance()
    val today = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    c.timeInMillis = t
    val d = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    return when (today - d) {
        0 -> "Aujourd'hui"
        1 -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(t)).replaceFirstChar { it.uppercase() }
    }
}

/** Ouvre ce qu'une ligne d'historique désigne : page, recherche ou fic. */
fun openHistory(e: History.Entry) {
    val t = e.target
    when {
        t.startsWith("q:") -> runCatching { SearchQuery.fromJson(JSONObject(t.removePrefix("q:"))) }.getOrNull()?.let { Nav.push(Screen.Results(it)) }
        t.startsWith("fic:") -> {
            val id = t.removePrefix("fic:").substringBeforeLast('#')
            val ch = t.substringAfterLast('#', "").toIntOrNull()
            if (com.thousandlabs.ohara.data.Library.contains(id)) Nav.push(Screen.Reader(id, ch)) else Toast.show("Cette fic n'est plus dans la bibliothèque.")
        }
        else -> Nav.openBrowser(t)
    }
}

@Composable
fun HistoryScreen() {
    val pv by Prefs.version.collectAsState()
    val all by History.entries.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var kind by remember { mutableStateOf<History.Kind?>(null) }
    var q by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val shown = remember(all, kind, q) {
        all.filter { (kind == null || it.kind == kind) && (q.isBlank() || it.title.contains(q, true) || it.target.contains(q, true)) }
    }
    Scaffold(topBar = {
        BackTopBar("Historique", if (History.enabled) Fmt.plural(all.size, "entrée") else "Désactivé") {
            if (all.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Tout effacer") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            item {
                SwitchRow("Garder un historique", "Pages visitées, recherches et fics ouvertes, sur le téléphone seulement", History.enabled, Icons.Outlined.History) { History.enabled = it }
                if (!History.enabled && all.isEmpty()) NoteCard("L'historique est désactivé : rien n'est enregistré. Activez-le pour retrouver les pages et recherches déjà faites.")
                if (all.isNotEmpty()) {
                    OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Chercher dans l'historique") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("Tout") })
                        History.Kind.entries.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text("${k.label}  ${all.count { it.kind == k }}") })
                        }
                    }
                }
            }
            var lastDay = ""
            shown.forEach { e ->
                val d = dayLabel(e.at)
                if (d != lastDay) { lastDay = d; item(key = "d$d") { SectionTitle(d) } }
                item(key = "${e.kind}${e.target}${e.at}") { HistoryRow(e) }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Effacer tout l'historique ?") },
        confirmButton = { TextButton(onClick = { History.clear(); confirm = false }) { Text("Effacer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )
}

@Composable
private fun HistoryRow(e: History.Entry) {
    Row(Modifier.fillMaxWidth().clickable { openHistory(e) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (e.kind) { History.Kind.PAGE -> Icons.Outlined.Public; History.Kind.SEARCH -> Icons.Outlined.Search; History.Kind.FIC -> Icons.AutoMirrored.Outlined.MenuBook },
                        null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            e.target.startsWith("q:") -> "Recherche avancée"
                            e.target.startsWith("fic:") -> "Lecteur"
                            else -> runCatching { java.net.URI(e.target).host }.getOrNull() ?: e.target
                        }
                        Text("$sub · ${SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(e.at))}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { History.remove(e) }) { Icon(Icons.Outlined.Close, "Effacer", tint = MaterialTheme.colorScheme.outline) }
                }
}
