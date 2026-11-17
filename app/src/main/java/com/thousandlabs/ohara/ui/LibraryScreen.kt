package com.thousandlabs.ohara.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.download.DownloadJob
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.UpdateChecker
import com.thousandlabs.ohara.discover.Scoring

/** Doublons : la même histoire publiée sur FanFiction.net et SpaceBattles (ou deux fois ajoutée). */
object Dupes {
    private fun key(t: String) = t.lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("\\b(complete|completed|ongoing|si|oc|story only|thread|fic|discussion)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    var ignored: List<String> get() = Prefs.getList("dupes_ignored"); set(v) = Prefs.putList("dupes_ignored", v)

    fun pairKey(a: Fic, b: Fic) = listOf(a.id, b.id).sorted().joinToString("|")

    fun find(list: List<Fic> = Library.fics.value): List<Pair<Fic, Fic>> {
        val ign = ignored.toSet()
        return list.filter { key(it.title).length >= 4 }.groupBy { key(it.title) }.values
            .filter { it.size >= 2 }
            .flatMap { g -> g.indices.flatMap { i -> (i + 1 until g.size).map { j -> g[i] to g[j] } } }
            .filter { (a, b) -> a.site != b.site || a.author.equals(b.author, true) }
            .filter { (a, b) -> pairKey(a, b) !in ign }
    }
}

private enum class LibSort(val label: String) {
    OPENED("Ouvertes récemment"), ADDED("Ajoutées récemment"), UPDATED("Mises à jour sur le site"),
    TITLE("Titre"), NOTE("Ma note"), PROGRESS("Progression"), WORDS("Longueur")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTab() {
    val context = LocalContext.current
    val fics by Library.fics.collectAsState()
    val pv by Prefs.version.collectAsState()
    val jobs by Downloads.jobs.collectAsState()
    val check by UpdateChecker.progress.collectAsState()
    var shelf by rememberSaveable { mutableStateOf("") }
    var sortIdx by rememberSaveable { mutableStateOf(Prefs.getInt("lib_sort", 0)) }
    var onlyNew by rememberSaveable { mutableStateOf(false) }
    var siteFilter by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var shelfMenu by remember { mutableStateOf(false) }
    var newShelf by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    @Suppress("UNUSED_EXPRESSION") pv
    val sort = LibSort.entries[sortIdx.coerceIn(0, LibSort.entries.lastIndex)]

    val shown = remember(fics, shelf, sort, onlyNew, query, siteFilter, pv) {
        var l = fics
        if (shelf.isNotEmpty()) l = l.filter { shelf in it.shelves }
        if (onlyNew) l = l.filter { it.newChapters > 0 || it.completedAlert }
        if (siteFilter.isNotEmpty()) l = l.filter { it.site.code == siteFilter }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            l = l.filter { f -> f.title.lowercase().contains(q) || f.author.lowercase().contains(q) || f.fandoms.any { it.lowercase().contains(q) } || f.comment.lowercase().contains(q) }
        }
        when (sort) {
            LibSort.OPENED -> l.sortedByDescending { maxOf(it.lastOpened, it.addedAt) }
            LibSort.ADDED -> l.sortedByDescending { it.addedAt }
            LibSort.UPDATED -> l.sortedByDescending { it.updated }
            LibSort.TITLE -> l.sortedBy { it.title.lowercase() }
            LibSort.NOTE -> l.sortedByDescending { it.note }
            LibSort.PROGRESS -> l.sortedByDescending { if (it.totalChapters > 0) (it.maxChapterRead + 1).toFloat() / it.totalChapters else 0f }
            LibSort.WORDS -> l.sortedByDescending { it.words }
        }
    }
    val dupes = remember(fics, pv) { Dupes.find(fics) }
    val newCount = fics.count { it.newChapters > 0 || it.completedAlert }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selection.size} sélectionnée${if (selection.size > 1) "s" else ""}") },
                    navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Outlined.Close, "Annuler la sélection") } },
                    actions = {
                        IconButton(onClick = { selection = shown.map { it.id }.toSet() }) { Icon(Icons.Outlined.SelectAll, "Tout sélectionner") }
                        Column {
                            IconButton(onClick = { shelfMenu = true }) { Icon(Icons.Outlined.Bookmarks, "Étagère") }
                            DropdownMenu(shelfMenu, onDismissRequest = { shelfMenu = false }) {
                                Shelves.all().forEach { (id, label) ->
                                    DropdownMenuItem(text = { Text("Ranger dans « $label »") }, onClick = {
                                        selection.forEach { Library.setShelf(it, id, true) }
                                        if (id == Shelves.READING || id == Shelves.DONE || id == Shelves.ABANDONED) selection.forEach { fid ->
                                            Library.update(fid) { f -> f.copy(shelves = f.shelves - Shelves.TO_READ) }
                                        }
                                        shelfMenu = false; Toast.show("Rangées dans « $label »."); selection = emptySet()
                                    })
                                }
                                if (shelf.isNotEmpty()) DropdownMenuItem(text = { Text("Retirer de « ${Shelves.label(shelf)} »") }, onClick = {
                                    selection.forEach { Library.setShelf(it, shelf, false) }; shelfMenu = false; selection = emptySet()
                                })
                            }
                        }
                        IconButton(onClick = {
                            selection.mapNotNull { Library.get(it) }.forEach { Downloads.enqueue(it) }
                            Toast.show("Ajoutées à la file de téléchargement."); selection = emptySet()
                        }) { Icon(Icons.Outlined.Download, "Télécharger") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                TopAppBar(
                    title = {
                        if (searching) OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Titre, auteur, fandom…") }, modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                        ) else Column {
                            Text("Bibliothèque", style = MaterialTheme.typography.titleLarge.merge(SerifTitle))
                            Text(Fmt.plural(fics.size, "fic"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "Chercher dans la bibliothèque")
                        }
                        if (!searching) {
                            IconButton(onClick = { Nav.push(Screen.Add) }) { Icon(Icons.Outlined.Add, "Ajouter des fics") }
                            IconButton(onClick = { UpdateChecker.checkAll(context.applicationContext); Toast.show("Recherche de nouveaux chapitres…") }) {
                                Icon(Icons.Outlined.Refresh, "Vérifier les nouveaux chapitres")
                            }
                            Column {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Plus") }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("File de téléchargement") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { menu = false; Nav.push(Screen.Downloads) })
                                    DropdownMenuItem(text = { Text("Statistiques de lecture") }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { menu = false; Nav.push(Screen.Stats) })
                                    DropdownMenuItem(text = { Text("Doublons") }, leadingIcon = { Icon(Icons.Outlined.MergeType, null) }, onClick = { menu = false; Nav.push(Screen.Duplicates) })
                                    DropdownMenuItem(text = { Text("Nouvelle étagère") }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) }, onClick = { menu = false; newShelf = true })
                                    DropdownMenuItem(text = { Text("Comment faire") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) }, onClick = { menu = false; Nav.push(Screen.Guide) })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // étagères
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = shelf.isEmpty(), onClick = { shelf = "" }, label = { Text("Toutes") })
                Shelves.all().forEach { (id, label) ->
                    val n = fics.count { id in it.shelves }
                    FilterChip(selected = shelf == id, onClick = { shelf = if (shelf == id) "" else id }, label = { Text(if (n > 0) "$label  $n" else label) })
                }
                FilterChip(selected = false, onClick = { newShelf = true }, label = { Text("Nouvelle") }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    FilterChip(selected = false, onClick = { sortMenu = true }, label = { Text(sort.label) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) })
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibSort.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.label) }, onClick = { sortIdx = s.ordinal; Prefs.putInt("lib_sort", s.ordinal); sortMenu = false })
                        }
                    }
                }
                FilterChip(selected = onlyNew, onClick = { onlyNew = !onlyNew }, label = { Text(if (newCount > 0) "Nouveautés  $newCount" else "Nouveautés") })
                listOf(Site.FFN, Site.SB, Site.WN, Site.OTHER).filter { s -> fics.any { it.site == s } }.forEach { s ->
                    FilterChip(selected = siteFilter == s.code, onClick = { siteFilter = if (siteFilter == s.code) "" else s.code }, label = { Text(s.short) })
                }
            }
            if (check.running) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Vérification ${check.done + 1}/${check.total} : ${check.current}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { if (check.total > 0) check.done.toFloat() / check.total else 0f }, modifier = Modifier.fillMaxWidth())
                }
            } else if (check.summary.isNotEmpty()) {
                NoteCard(check.summary, icon = Icons.Outlined.Verified, action = {
                    TextButton(onClick = { onlyNew = true }) { Text("Voir") }
                })
            }
            val active = jobs.filter { it.state != DownloadJob.State.ERROR }
            if (active.isNotEmpty()) {
                val run = jobs.firstOrNull { it.state == DownloadJob.State.RUNNING }
                val verify = jobs.any { it.state == DownloadJob.State.VERIFY }
                NoteCard(
                    when {
                        verify -> "Un site demande une vérification avant de continuer le téléchargement."
                        run != null -> "Téléchargement : ${run.title} (${run.done}/${run.total})" + if (active.size > 1) " · ${active.size - 1} en attente" else ""
                        else -> "${Fmt.plural(active.size, "fic")} en attente de téléchargement"
                    },
                    icon = if (verify) Icons.Outlined.ErrorOutline else Icons.Outlined.Download,
                    action = { TextButton(onClick = { Nav.push(Screen.Downloads) }) { Text("Ouvrir") } }
                )
            }
            if (dupes.isNotEmpty() && shelf.isEmpty() && query.isBlank()) {
                NoteCard("${Fmt.plural(dupes.size, "doublon possible", "doublons possibles")} (même fic sur deux sites).", icon = Icons.Outlined.MergeType,
                    action = { TextButton(onClick = { Nav.push(Screen.Duplicates) }) { Text("Fusionner") } })
            }
            if (fics.isEmpty()) {
                EmptyState(Icons.Outlined.LocalLibrary, "Votre bibliothèque est vide",
                    "Collez un lien copié depuis Brave, partagez une fic vers Ohara, ou ouvrez un site dans le navigateur intégré.") {
                    OutlinedButton(onClick = { Nav.push(Screen.Add) }) { Icon(Icons.Outlined.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text("Coller un lien") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { Nav.tab = Nav.TAB_BROWSER }) { Icon(Icons.Outlined.Public, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir le navigateur") }
                }
            } else if (shown.isEmpty()) {
                EmptyState(Icons.Outlined.Search, "Aucune fic ici", "Changez d'étagère ou de filtre.")
            } else {
                val resume = if (shelf.isEmpty() && query.isBlank() && !onlyNew && selection.isEmpty())
                    fics.filter { it.lastOpened > 0 && Shelves.DONE !in it.shelves && Shelves.ABANDONED !in it.shelves }.maxByOrNull { it.lastOpened } else null
                LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    if (resume != null) item(key = "reprendre") { ResumeCard(resume) }
                    items(shown, key = { it.id }) { f ->
                        FicRow(
                            f, showProgress = true, selected = f.id in selection,
                            onLongClick = { selection = if (f.id in selection) selection - f.id else selection + f.id },
                            trailingText = Library.downloadedCount(f.id).let { n -> if (n in 1 until f.totalChapters) "$n/${f.totalChapters}" else null }
                        ) {
                            if (selection.isNotEmpty()) selection = if (f.id in selection) selection - f.id else selection + f.id
                            else Nav.push(Screen.FicDetail(f.id))
                        }
                        Divider()
                    }
                }
            }
        }
    }

    if (newShelf) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newShelf = false },
            title = { Text("Nouvelle étagère") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom") }) },
            confirmButton = { TextButton(onClick = { Shelves.addCustom(name); newShelf = false }, enabled = name.isNotBlank()) { Text("Créer") } },
            dismissButton = { TextButton(onClick = { newShelf = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${Fmt.plural(selection.size, "fic")} ?") },
            text = { Text("Les fics, leur progression, vos notes et les chapitres téléchargés seront effacés du téléphone.") },
            confirmButton = { TextButton(onClick = { selection.forEach { Library.remove(it) }; selection = emptySet(); confirmDelete = false }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun DuplicatesScreen() {
    val fics by Library.fics.collectAsState()
    val pv by Prefs.version.collectAsState()
    val pairs = remember(fics, pv) { Dupes.find(fics) }
    Scaffold(topBar = { BackTopBar("Doublons", "Même histoire sur deux sites") }) { pad ->
        if (pairs.isEmpty()) {
            EmptyState(Icons.Outlined.MergeType, "Aucun doublon", "Ohara compare les titres de votre bibliothèque. Rien à fusionner pour l'instant.", Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = ListPadding) {
            item {
                NoteCard("Fusionner garde la fiche la plus avancée comme principale ; l'autre adresse reste connue (un lien collé ouvrira la bonne fic). Vos notes, surlignages et étagères sont réunis.")
            }
            items(pairs, key = { Dupes.pairKey(it.first, it.second) }) { (a, b) ->
                val keep = if (b.totalChapters > a.totalChapters) b else a
                val drop = if (keep === a) b else a
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(keep.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    for (f in listOf(keep, drop)) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        SitePill(f.site); Spacer(Modifier.width(8.dp))
                        Text("${f.author} · ${Fmt.plural(f.totalChapters, "chapitre")} · ${Fmt.words(f.words)}" + if (f === keep) " · principale" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { Library.mergeDuplicates(keep.id, drop.id); Toast.show("Fiches fusionnées.") }) { Text("Fusionner") }
                        TextButton(onClick = { Dupes.ignored = Dupes.ignored + Dupes.pairKey(a, b) }) { Text("Ce ne sont pas les mêmes") }
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    val jobs by Downloads.jobs.collectAsState()
    Scaffold(topBar = {
        BackTopBar("File de téléchargement", if (jobs.isEmpty()) null else Fmt.plural(jobs.size, "fic")) {
            if (jobs.any { it.state == DownloadJob.State.ERROR }) TextButton(onClick = { Downloads.clearFinishedErrors() }) { Text("Effacer les erreurs") }
        }
    }) { pad ->
        if (jobs.isEmpty()) {
            EmptyState(Icons.Outlined.Download, "Rien en cours", "Les chapitres se téléchargent un par un, avec des pauses pour ne pas être bloqué par les sites. Le téléchargement continue écran éteint.", Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = ListPadding) {
            if (jobs.any { it.state == DownloadJob.State.VERIFY }) item {
                NoteCard("Le site demande de confirmer que vous n'êtes pas un robot. Touchez « Vérifier », cochez la case sur la page, puis revenez : le téléchargement reprendra.", icon = Icons.Outlined.ErrorOutline)
            }
            items(jobs, key = { it.ficId }) { j ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(j.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                when (j.state) {
                                    DownloadJob.State.WAITING -> "En attente"
                                    DownloadJob.State.RUNNING -> "${j.done}/${j.total} · ${j.message}"
                                    DownloadJob.State.PAUSED -> "En pause"
                                    DownloadJob.State.VERIFY -> "Vérification demandée par le site"
                                    DownloadJob.State.ERROR -> j.message
                                },
                                style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                color = if (j.state == DownloadJob.State.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when (j.state) {
                            DownloadJob.State.RUNNING, DownloadJob.State.WAITING -> IconButton(onClick = { Downloads.pause(j.ficId) }) { Icon(Icons.Outlined.Pause, "Pause") }
                            DownloadJob.State.PAUSED, DownloadJob.State.ERROR -> IconButton(onClick = { Downloads.resume(j.ficId) }) { Icon(Icons.Outlined.PlayArrow, "Reprendre") }
                            DownloadJob.State.VERIFY -> TextButton(onClick = { j.verifyUrl?.let { u -> BrowserHolder.verifying = true; Nav.openBrowser(u) } }) { Text("Vérifier") }
                        }
                        IconButton(onClick = { Downloads.cancel(j.ficId) }) { Icon(Icons.Outlined.Close, "Retirer de la file") }
                    }
                    if (j.state == DownloadJob.State.RUNNING && j.total > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { j.done.toFloat() / j.total }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Divider()
            }
        }
    }
}

/** Petit utilitaire : score d'une fic de la bibliothèque (pour l'afficher sur la fiche). */
fun libraryScore(f: Fic) = Scoring.score(f)

/** Carte « Reprendre la lecture » en haut de la bibliothèque. */
@Composable
private fun ResumeCard(f: Fic) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        color = cs.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.clickable { Nav.push(Screen.Reader(f.id, null)) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverTile(f, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Reprendre la lecture", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Text(f.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = cs.onPrimaryContainer)
                val total = f.totalChapters
                Text("Chapitre ${f.progressChapter + 1}" + (if (total > 0) " sur $total" else "") + " · ouverte ${Fmt.relative(f.lastOpened)}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.75f))
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { ((f.maxChapterRead + 1).coerceAtLeast(f.progressChapter)).toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(4.dp), color = cs.primary, trackColor = cs.surface.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.FilledIconButton(onClick = { Nav.push(Screen.Reader(f.id, null)) }) {
                Icon(Icons.Outlined.PlayArrow, "Reprendre")
            }
        }
    }
}
