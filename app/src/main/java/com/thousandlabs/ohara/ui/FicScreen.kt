package com.thousandlabs.ohara.ui

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Send
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thousandlabs.ohara.data.CharacterNote
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.discover.JunkFilter
import com.thousandlabs.ohara.discover.Scoring
import com.thousandlabs.ohara.discover.Types
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.UpdateChecker
import com.thousandlabs.ohara.export.Exporter
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FicScreen(ficId: String, preview: Fic?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by Library.fics.collectAsState()
    val dv by Library.downloadVersion.collectAsState()
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var loaded by remember { mutableStateOf<Fic?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val inLib = lib.firstOrNull { it.id == ficId }
    val fic = inLib ?: loaded ?: preview

    LaunchedEffect(ficId) {
        if (inLib == null && preview != null && (preview.chapters.isEmpty() || preview.summary.isBlank())) {
            loading = true
            runCatching { withContext(Dispatchers.IO) { Sources.of(preview.site).fetchInfo(preview.storyId) } }
                .onSuccess { loaded = preview.mergeSiteData(it) }
                .onFailure { loadError = it.message }
            loading = false
        }
    }

    if (fic == null) {
        Scaffold(topBar = { BackTopBar("Fic introuvable") }) { pad ->
            EmptyState(Icons.Outlined.Block, "Cette fic n'est plus dans la bibliothèque", "", Modifier.padding(pad))
        }
        return
    }

    var menu by remember { mutableStateOf(false) }
    var exportSheet by remember { mutableStateOf(false) }
    var claudeSheet by remember { mutableStateOf(false) }
    var hideGenre by remember { mutableStateOf(false) }
    var charDialog by remember { mutableStateOf<CharacterNote?>(null) }
    var charNew by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var showAllChapters by remember { mutableStateOf(false) }
    val types = remember(fic) { Types.detect(fic) }
    val detail = remember(fic) { Scoring.detail(fic) }

    fun ensureInLibrary(shelf: String = Shelves.TO_READ): Fic = Library.get(fic.id) ?: Library.upsert(fic, shelf)

    Scaffold(topBar = {
        BackTopBar(fic.title, fic.site.label) {
            IconButton(onClick = { Nav.openBrowser(fic.url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Ouvrir sur le site") }
            Column {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Plus") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Masquer ce genre…") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) }, onClick = { menu = false; hideGenre = true })
                    if (fic.author.isNotBlank()) {
                        if (JunkFilter.isTrusted(fic)) DropdownMenuItem(text = { Text("Retirer la confiance à ${fic.author}") }, leadingIcon = { Icon(Icons.Outlined.Person, null) },
                            onClick = { menu = false; JunkFilter.forgetAuthor(JunkFilter.authorKey(fic)) })
                        else DropdownMenuItem(text = { Text("Auteur de confiance") }, leadingIcon = { Icon(Icons.Outlined.Verified, null) },
                            onClick = { menu = false; JunkFilter.trustAuthor(fic); Toast.show("${fic.author} : auteur de confiance. Ses fics passent les filtres de qualité.") })
                        if (JunkFilter.isBlocked(fic)) DropdownMenuItem(text = { Text("Débloquer ${fic.author}") }, leadingIcon = { Icon(Icons.Outlined.Person, null) },
                            onClick = { menu = false; JunkFilter.forgetAuthor(JunkFilter.authorKey(fic)) })
                        else DropdownMenuItem(text = { Text("Bloquer l'auteur") }, leadingIcon = { Icon(Icons.Outlined.Block, null) },
                            onClick = { menu = false; JunkFilter.blockAuthor(fic); Toast.show("${fic.author} est bloqué : ses fics n'apparaîtront plus dans les recherches.") })
                    }
                    if (inLib == null) DropdownMenuItem(text = { Text("Masquer cette fic") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                        onClick = { menu = false; JunkFilter.hideFic(fic); Toast.show("Fic masquée (dossier « Masquées »)."); Nav.pop() })
                    if (inLib != null) DropdownMenuItem(text = { Text("Rechercher dans cette fic") }, leadingIcon = { Icon(Icons.Outlined.FindInPage, null) }, onClick = { menu = false; Nav.push(Screen.ChapterSearch(fic.id)) })
                    DropdownMenuItem(text = { Text("Partager le lien") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                        menu = false
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fic.url), "Partager"))
                    })
                    if (inLib != null) {
                        DropdownMenuItem(text = { Text("Vérifier les nouveaux chapitres") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = {
                            menu = false; UpdateChecker.checkAll(context.applicationContext, listOf(fic.id)); Toast.show("Vérification en cours…")
                        })
                        if (Library.downloadedCount(fic.id) > 0) DropdownMenuItem(text = { Text("Effacer les chapitres téléchargés") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; Library.deleteChapters(fic.id); Toast.show("Chapitres effacés du téléphone.") })
                        DropdownMenuItem(text = { Text("Retirer de la bibliothèque") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmRemove = true })
                    }
                }
            }
        }
    }) { pad ->
        @Suppress("UNUSED_EXPRESSION") dv
        val downloaded = if (inLib != null) Library.downloadedCount(fic.id) else 0
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        CoverTile(fic, 76.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fic.title, style = MaterialTheme.typography.headlineSmall)
                            if (fic.author.isNotBlank()) Text("par ${fic.author}" + when {
                                JunkFilter.isTrusted(fic) -> " · auteur de confiance"
                                JunkFilter.isBlocked(fic) -> " · auteur bloqué"
                                else -> ""
                            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fic.fandomLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SitePill(fic.site)
                        Pill(fic.status.label, outlined = true, color = MaterialTheme.colorScheme.secondary)
                        ScorePill(detail.score)
                        if (fic.newChapters > 0) Pill(Fmt.plural(fic.newChapters, "nouveau chapitre", "nouveaux chapitres"), color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
                        if (fic.deletedFromSite) Pill("Supprimée du site", color = MaterialTheme.colorScheme.error, outlined = true)
                        else if (Scoring.isInactive(fic)) Pill("Inactive", outlined = true, color = MaterialTheme.colorScheme.outline)
                        types.forEach { Pill(it.label, outlined = true, color = MaterialTheme.colorScheme.tertiary) }
                    }
                    if (loading) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                        Text("Lecture de la fiche sur le site…", style = MaterialTheme.typography.bodySmall)
                    }
                    loadError?.let { Text("Fiche incomplète : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
            // Actions principales
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val started = inLib != null && (inLib.maxChapterRead >= 0 || inLib.progressChapter > 0 || inLib.progressPos > 0f)
                    Button(onClick = {
                        val f = ensureInLibrary(Shelves.READING)
                        Library.update(f.id) { it.copy(shelves = it.shelves + Shelves.READING - Shelves.TO_READ) }
                        Nav.openFic(f.id)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null); Spacer(Modifier.width(8.dp))
                        Text(if (started) "Reprendre ch. ${inLib!!.progressChapter + 1}" else "Lire")
                    }
                    if (inLib == null) OutlinedButton(onClick = { ensureInLibrary(); Toast.show("Ajoutée à « À lire ».") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.LibraryAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter")
                    }
                    else OutlinedButton(onClick = {
                        Downloads.enqueue(inLib); Toast.show("Téléchargement lancé.")
                    }, modifier = Modifier.weight(1f), enabled = downloaded < fic.totalChapters || fic.totalChapters == 0) {
                        Icon(if (downloaded >= fic.totalChapters && fic.totalChapters > 0) Icons.Outlined.DownloadDone else Icons.Outlined.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (downloaded >= fic.totalChapters && fic.totalChapters > 0) "Téléchargée" else "Télécharger")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportSheet = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Exporter")
                    }
                    OutlinedButton(onClick = { Nav.push(Screen.Similar(fic)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Similaires")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(onClick = { claudeSheet = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Envoyer à Claude")
                    }
                    if (inLib != null && Recap.available(inLib)) OutlinedButton(onClick = { Recap.pending = inLib.id }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.History, null); Spacer(Modifier.width(8.dp)); Text("Précédemment")
                    }
                }
            }
            // Statistiques
            item {
                SectionTitle("Informations")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val rows = buildList {
                        add("Longueur" to "${Fmt.words(fic.words)} · ${Fmt.plural(fic.totalChapters, "chapitre")}")
                        if (inLib != null) add("Sur le téléphone" to "$downloaded/${fic.totalChapters} chapitres" + if (downloaded > 0) "" else " (lecture en ligne)")
                        if (inLib != null && (inLib.secondsRead >= 60 || inLib.wordsRead > 0)) add("Votre lecture" to listOf(
                            Stats.hours(inLib.secondsRead), Fmt.words(inLib.wordsRead).replace("?", "0") + " lus",
                            "chapitre ${(inLib.maxChapterRead + 1).coerceAtLeast(0)}/${fic.totalChapters}").joinToString(" · "))
                        if (inLib != null) Stats.remaining(inLib)?.let { r ->
                            add("Reste à lire" to if (r <= 0) "Rien, vous êtes à jour" else "environ ${Stats.hours(r)} à votre vitesse (${Stats.speed()} mots/min)")
                        }
                        add("Publiée" to Fmt.date(fic.published))
                        add("Mise à jour" to "${Fmt.date(fic.updated)} (${Fmt.relative(fic.updated)})")
                        if (fic.favs > 0 || fic.follows > 0) add("Lecteurs" to listOfNotNull(
                            fic.favs.takeIf { it > 0 }?.let { "${Fmt.number(it.toLong())} favoris" },
                            fic.follows.takeIf { it > 0 }?.let { "${Fmt.number(it.toLong())} abonnés" },
                            fic.reviews.takeIf { it > 0 }?.let { "${Fmt.number(it.toLong())} avis" }).joinToString(" · "))
                        if (fic.views > 0 || fic.replies > 0) add("Audience" to listOfNotNull(
                            fic.views.takeIf { it > 0 }?.let { "${Fmt.number(it.toLong())} vues" },
                            fic.replies.takeIf { it > 0 }?.let { "${Fmt.number(it.toLong())} réponses" }).joinToString(" · "))
                        add("Score" to "${detail.score}/100 — " + detail.parts.joinToString(" · ") { (k, v) -> "$k ${if (v >= 0) "+" else ""}$v" } + " (toucher pour les critères)")
                        if (fic.rating.isNotBlank()) add("Classement" to fic.rating)
                        if (fic.language.isNotBlank()) add("Langue" to fic.language)
                        if (fic.genres.isNotEmpty()) add("Genres" to fic.genres.joinToString(", "))
                        if (fic.characters.isNotEmpty()) add("Personnages" to fic.characters.joinToString(", "))
                        if (fic.tags.isNotEmpty()) add("Étiquettes" to fic.tags.take(25).joinToString(", "))
                        if (fic.altUrls.isNotEmpty()) add("Autres adresses" to fic.altUrls.joinToString("\n"))
                    }
                    rows.forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 3.dp).then(if (k == "Score") Modifier.clickable { Nav.push(Screen.Settings("score")) } else Modifier)) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(118.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (fic.summary.isNotBlank()) {
                    SectionTitle("Résumé")
                    Text(fic.summary, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Partie personnelle
            if (inLib != null) {
                item {
                    SectionTitle("Étagères")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Shelves.all().forEach { (id, label) ->
                            FilterChip(selected = id in inLib.shelves, onClick = { Library.setShelf(fic.id, id, id !in inLib.shelves) }, label = { Text(label) })
                        }
                    }
                    SectionTitle("Ma note")
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(value = inLib.note.coerceAtLeast(0).toFloat(), onValueChange = { v -> Library.update(fic.id) { it.copy(note = v.toInt()) } },
                            valueRange = 0f..10f, steps = 9, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(if (inLib.note >= 0) "${inLib.note}/10" else "—", style = MaterialTheme.typography.titleMedium)
                        if (inLib.note >= 0) TextButton(onClick = { Library.update(fic.id) { it.copy(note = -1) } }) { Text("Effacer") }
                    }
                    var comment by remember(fic.id) { mutableStateOf(inLib.comment) }
                    OutlinedTextField(comment, { comment = it; Library.update(fic.id) { f -> f.copy(comment = it) } },
                        label = { Text("Commentaire") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), minLines = 2)
                    if (Shelves.ABANDONED in inLib.shelves) {
                        var reason by remember(fic.id) { mutableStateOf(inLib.abandonReason) }
                        OutlinedTextField(reason, { reason = it; Library.update(fic.id) { f -> f.copy(abandonReason = it) } },
                            label = { Text("Raison de l'abandon") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Rythme trop lent", "Personnage principal décevant", "Écriture faible", "Abandonnée par l'auteur", "Harem déguisé", "Trop de clichés").forEach { r ->
                                FilterChip(selected = reason == r, onClick = { reason = r; Library.update(fic.id) { f -> f.copy(abandonReason = r) } }, label = { Text(r) })
                            }
                        }
                    }
                }
                item {
                    SectionTitle("Fiche personnages")
                    if (inLib.characterNotes.isEmpty()) Text("Notez qui est qui, les pouvoirs, les relations : utile pour suivre une longue fic et pour le Pack Claude.",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inLib.characterNotes.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { charDialog = c }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            }
                            Icon(Icons.Outlined.Edit, "Modifier", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    TextButton(onClick = { charNew = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Ajouter un personnage") }
                }
                if (inLib.highlights.isNotEmpty()) item {
                    SectionTitle("Surlignages (${inLib.highlights.size})")
                    inLib.highlights.sortedWith(compareBy({ it.chapter }, { it.createdAt })).forEach { h ->
                        Row(Modifier.fillMaxWidth().clickable { Nav.openFic(fic.id, h.chapter) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(Icons.Outlined.Highlight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Chapitre ${h.chapter + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("« ${h.text.take(280)} »", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                                if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { Library.update(fic.id) { f -> f.copy(highlights = f.highlights - h) } }) { Icon(Icons.Outlined.Delete, "Supprimer", tint = MaterialTheme.colorScheme.outline) }
                        }
                    }
                }
            }
            // Chapitres
            if (fic.chapters.isNotEmpty()) {
                item { SectionTitle("Chapitres (${fic.chapters.size})") }
                val list = if (showAllChapters || fic.chapters.size <= 40) fic.chapters else fic.chapters.take(15) + fic.chapters.takeLast(5)
                itemsIndexed(list, key = { i, c -> "ch$i${c.url}" }) { i, c ->
                    val idx = if (showAllChapters || fic.chapters.size <= 40) i else if (i < 15) i else fic.chapters.size - (list.size - i)
                    val read = inLib != null && idx <= inLib.maxChapterRead
                    val here = inLib != null && idx == inLib.progressChapter
                    Row(Modifier.fillMaxWidth().clickable {
                        val f = ensureInLibrary(Shelves.READING)
                        if (Library.hasChapter(f.id, idx)) BrowserHolder.openOffline(f.id, idx) else Nav.openFic(f.id, idx)
                    }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(36.dp))
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1,
                            color = when { here -> MaterialTheme.colorScheme.primary; read -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurface })
                        if (c.locked) Pill("Verrouillé", outlined = true, color = MaterialTheme.colorScheme.outline)
                        if (inLib != null && Library.hasChapter(fic.id, idx)) Icon(Icons.Outlined.CheckCircle, "Téléchargé", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    if (!showAllChapters && fic.chapters.size > 40 && i == 14) {
                        TextButton(onClick = { showAllChapters = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Afficher les ${fic.chapters.size} chapitres") }
                    }
                }
            }
        }
    }

    if (exportSheet) ExportSheet(fic, inLib != null) { exportSheet = false }
    if (claudeSheet) ClaudeSheet(inLib ?: fic) { claudeSheet = false }

    if (hideGenre) {
        val options = (types.map { it.label } + fic.genres + fic.tags.take(20)).distinctBy { it.lowercase() }
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { hideGenre = false },
            title = { Text("Masquer ce genre") },
            text = {
                Column {
                    Text("Les fics de ce genre n'apparaîtront plus dans les recherches, « Pour vous » et les nouveautés.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { o ->
                            FilterChip(selected = o.lowercase() in JunkFilter.hiddenGenres, onClick = {
                                if (o.lowercase() in JunkFilter.hiddenGenres) JunkFilter.removeHiddenGenre(o.lowercase()) else JunkFilter.addHiddenGenre(o)
                            }, label = { Text(o) })
                        }
                    }
                    OutlinedTextField(custom, { custom = it }, label = { Text("Autre mot (ex. : yaoi, reincarnation)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { if (custom.isNotBlank()) JunkFilter.addHiddenGenre(custom); hideGenre = false }) { Text("Terminé") } }
        )
    }

    if (charNew || charDialog != null) {
        val editing = charDialog
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        var note by remember(editing) { mutableStateOf(editing?.note ?: "") }
        AlertDialog(
            onDismissRequest = { charNew = false; charDialog = null },
            title = { Text(if (editing == null) "Nouveau personnage" else "Modifier la fiche") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                    if (editing == null && fic.characters.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fic.characters.filter { c -> inLib?.characterNotes?.none { it.name.equals(c, true) } != false }.take(8).forEach { c ->
                            FilterChip(selected = name == c, onClick = { name = c }, label = { Text(c) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Notes (rôle, pouvoirs, relations…)") }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val f = ensureInLibrary()
                    Library.update(f.id) { cur ->
                        val list = cur.characterNotes.filterNot { it == editing || it.name.equals(name.trim(), true) && editing == null }
                        cur.copy(characterNotes = list + CharacterNote(name.trim(), note.trim()))
                    }
                    charNew = false; charDialog = null
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                Row {
                    if (editing != null) TextButton(onClick = { Library.update(fic.id) { it.copy(characterNotes = it.characterNotes - editing) }; charDialog = null }) { Text("Supprimer") }
                    TextButton(onClick = { charNew = false; charDialog = null }) { Text("Annuler") }
                }
            }
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Retirer cette fic ?") },
            text = { Text("Sa progression, vos notes, surlignages et chapitres téléchargés seront effacés.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; Library.remove(fic.id); Nav.pop() }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler") } }
        )
    }
}

/** Partage un fichier exporté (EPUB, TXT, Pack Claude) vers une autre appli. */
fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", file)
    val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(i, "Envoyer ${file.name}"))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(fic: Fic, inLibrary: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(Exporter.Format.PACK) }
    val total = fic.totalChapters.coerceAtLeast(1)
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf(total.toString()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val f = pending
        if (uri != null && f != null) AppScope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } } }
                .onSuccess { Toast.show("Fichier enregistré.") }.onFailure { Toast.show("Enregistrement impossible : ${it.message}") }
        }
    }

    fun run(save: Boolean) {
        val a = (from.toIntOrNull() ?: 1).coerceIn(1, total) - 1
        val b = (to.toIntOrNull() ?: total).coerceIn(a + 1, total) - 1
        val f = Library.get(fic.id) ?: Library.upsert(fic)
        busy = "Préparation…"
        AppScope.launch {
            runCatching { withContext(Dispatchers.IO) { Exporter.export(context.applicationContext, f, format, Exporter.Range(a, b)) { msg -> busy = msg } } }
                .onSuccess { file ->
                    busy = null
                    if (format == Exporter.Format.PACK) Library.setShelf(f.id, Shelves.CLAUDE, true)
                    if (save) { pending = file; saver.launch(file.name) } else shareFile(context, file, format.mime)
                }
                .onFailure { busy = null; Toast.show("Export impossible : ${it.message}") }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (busy == null) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Exporter", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Exporter.Format.entries.forEach { f -> FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) }) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (format) {
                    Exporter.Format.EPUB -> "Un livre numérique avec sommaire, lisible dans n'importe quelle liseuse."
                    Exporter.Format.TXT -> "Tout le texte dans un seul fichier, chapitres séparés."
                    Exporter.Format.PACK -> "Une archive prête pour Claude : fiche, vos notes, personnages, surlignages, modèles de consignes et texte découpé en parties d'environ ${Prefs.packWordsPerPart / 1000} k mots."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(from, { from = it.filter(Char::isDigit) }, label = { Text("Du chapitre") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(to, { to = it.filter(Char::isDigit) }, label = { Text("au chapitre") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (!inLibrary) Text("La fic sera ajoutée à votre bibliothèque.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
                    Text(busy!!, style = MaterialTheme.typography.bodyMedium)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { run(false) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Partager") }
                OutlinedButton(onClick = { run(true) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer") }
            }
        }
    }
}
