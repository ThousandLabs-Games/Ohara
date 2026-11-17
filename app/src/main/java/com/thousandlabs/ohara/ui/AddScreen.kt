package com.thousandlabs.ohara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.discover.Scoring
import com.thousandlabs.ohara.discover.Search
import com.thousandlabs.ohara.discover.SearchQuery
import com.thousandlabs.ohara.discover.SiteNote
import com.thousandlabs.ohara.discover.Types
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.LinkOpener
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScreen() {
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = {
        Column {
            BackTopBar("Ajouter des fics")
            TabRow(selectedTabIndex = tab) {
                listOf("Lien", "Par titre", "En lot").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> {
                    var link by rememberSaveable { mutableStateOf("") }
                    var busy by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(link, { link = it }, label = { Text("Lien d'un chapitre ou d'une fic") }, minLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        trailingIcon = { IconButton(onClick = { link = clipboard.getText()?.text.orEmpty() }) { Icon(Icons.Outlined.ContentPaste, "Coller") } })
                    Text("Un lien de chapitre ouvre la fic à ce chapitre précis : copiez l'adresse dans Brave là où vous vous êtes arrêté.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val busyNow = busy
                        Button(enabled = !busyNow && Sources.extractUrl(link) != null, onClick = {
                            busy = true
                            LinkFlow.open(link) { busy = false }
                        }) {
                            if (busyNow) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                            Spacer(Modifier.width(8.dp)); Text("Ouvrir à ce chapitre")
                        }
                        OutlinedButton(enabled = !busyNow && Sources.extractUrl(link) != null, onClick = {
                            busy = true
                            LinkFlow.open(link, Shelves.TO_READ, read = false) { ok -> busy = false; if (ok) link = "" }
                        }) { Text("Ajouter seulement") }
                    }
                    Spacer(Modifier.height(24.dp))
                    NoteCard("Depuis Brave ou une autre appli : bouton Partager, puis Ohara. La fic s'ouvre directement au bon chapitre.", icon = Icons.Outlined.Share)
                }
                1 -> {
                    var title by rememberSaveable { mutableStateOf("") }
                    var sites by rememberSaveable { mutableStateOf(setOf("ffn", "sb", "wn")) }
                    var busy by remember { mutableStateOf(false) }
                    var results by remember { mutableStateOf<List<Fic>>(emptyList()) }
                    var notes by remember { mutableStateOf<List<SiteNote>>(emptyList()) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(title, { title = it }, label = { Text("Titre de la fic") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(Site.FFN, Site.SB, Site.WN).forEach { s ->
                                    FilterChip(selected = s.code in sites, onClick = { sites = if (s.code in sites) sites - s.code else sites + s.code }, label = { Text(s.label) })
                                }
                            }
                            Button(enabled = !busy && title.isNotBlank() && sites.isNotEmpty(), modifier = Modifier.padding(horizontal = 16.dp), onClick = {
                                busy = true; results = emptyList()
                                AppScope.launch {
                                    val n = mutableListOf<SiteNote>()
                                    val q = SearchQuery(sites = sites.map { Site.of(it) }.toSet(), keywords = title.trim(), englishOnly = false)
                                    val found = runCatching { Search.collect(q, 1, n) }.getOrDefault(emptyList())
                                    val t = title.trim().lowercase()
                                    results = found.sortedByDescending { if (it.title.lowercase() == t) 2 else if (it.title.lowercase().contains(t)) 1 else 0 }
                                    notes = n; busy = false
                                    if (found.isEmpty()) Toast.show("Aucune fic trouvée avec ce titre.")
                                }
                            }) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Search, null)
                                Spacer(Modifier.width(8.dp)); Text("Chercher")
                            }
                            notes.forEach { NoteCard(it.text) }
                        }
                        items(results, key = { it.id }) { f ->
                            FicRow(f, score = Scoring.score(f), types = Types.detect(f), trailing = { QuickAdd(f) }) { Nav.push(Screen.FicDetail(f.id, f)) }
                            Divider()
                        }
                    }
                }
                else -> {
                    var text by rememberSaveable { mutableStateOf("") }
                    var download by rememberSaveable { mutableStateOf(false) }
                    var progress by remember { mutableStateOf<String?>(null) }
                    val urls = Regex("https?://[^\\s\"'<>]+").findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().toList()
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(text, { text = it }, label = { Text("Un lien par ligne") }, minLines = 6,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        trailingIcon = { IconButton(onClick = { text = (text + "\n" + clipboard.getText()?.text.orEmpty()).trim() }) { Icon(Icons.Outlined.ContentPaste, "Coller") } })
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(download, { download = it }); Text("Télécharger aussi tous les chapitres")
                    }
                    Button(enabled = urls.isNotEmpty() && progress == null, modifier = Modifier.padding(horizontal = 16.dp), onClick = {
                        AppScope.launch {
                            var ok = 0; var ko = 0
                            urls.forEachIndexed { i, u ->
                                progress = "Ajout ${i + 1}/${urls.size}…"
                                runCatching { LinkOpener.open(u, Shelves.TO_READ) }
                                    .onSuccess { o -> ok++; if (download) Downloads.enqueue(o.fic) }
                                    .onFailure { ko++ }
                            }
                            progress = null
                            Toast.show("${Fmt.plural(ok, "fic ajoutée", "fics ajoutées")}" + if (ko > 0) " · $ko lien${if (ko > 1) "s" else ""} non reconnu${if (ko > 1) "s" else ""}" else "")
                            if (ko == 0) text = ""
                        }
                    }) { Text(if (urls.isEmpty()) "Ajouter" else "Ajouter ${Fmt.plural(urls.size, "fic")}") }
                    progress?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
