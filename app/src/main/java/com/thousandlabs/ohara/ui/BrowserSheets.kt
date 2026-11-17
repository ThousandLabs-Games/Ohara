package com.thousandlabs.ohara.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Bookmarks
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.sources.Sources

/** Ajout ou modification d'un favori, avec le choix du dossier. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkDialog(title: String, url: String, existing: Bookmarks.Bookmark? = Bookmarks.items.value.firstOrNull { it.url == url }, onDone: () -> Unit) {
    val folders by Bookmarks.folders.collectAsState()
    var name by remember { mutableStateOf(existing?.title ?: title) }
    var folder by remember { mutableStateOf(existing?.folder ?: Prefs.getString("bm_last", Bookmarks.DEFAULT).takeIf { it in folders } ?: Bookmarks.DEFAULT) }
    var newFolder by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Outlined.StarOutline, null) },
        title = { Text(if (existing != null) "Modifier le favori" else "Ajouter aux favoris") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Text("Dossier", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { f -> FilterChip(folder == f, { folder = f }, { Text(f) }, leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) }) }
                    AssistChip({ newFolder = "" }, { Text("Nouveau dossier") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(16.dp)) })
                }
                newFolder?.let { nf ->
                    OutlinedTextField(nf, { newFolder = it }, label = { Text("Nom du dossier") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { val n = nf.trim(); if (n.isNotEmpty()) { Bookmarks.addFolder(n); folder = n }; newFolder = null }) { Text("Créer") }
                        })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.putString("bm_last", folder)
                if (existing != null) Bookmarks.update(existing, existing.copy(title = name.ifBlank { url }, folder = folder))
                else { Bookmarks.add(name, url, folder); Toast.show("Ajouté aux favoris, dans « $folder ».") }
                onDone()
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            if (existing != null) TextButton(onClick = { Bookmarks.remove(url); Toast.show("Retiré des favoris."); onDone() }) { Text("Retirer") }
            else TextButton(onClick = onDone) { Text("Annuler") }
        }
    )
}

/** Menu d'un lien touché longuement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMenu(link: String, context: Context, onClose: () -> Unit) {
    val h = BrowserHolder
    val clipboard = LocalClipboardManager.current
    val fic = remember(link) { Sources.resolve(link)?.second }
    val inLib = fic != null && Library.contains(fic.ficId)
    val fromPrivate = h.tabs.getOrNull(h.current)?.private == true
    var bookmark by remember { mutableStateOf(false) }
    if (bookmark) { BookmarkDialog(link, link) { bookmark = false; onClose() }; return }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(link, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SheetRow(Icons.AutoMirrored.Outlined.OpenInNew, "Ouvrir dans un nouvel onglet") { onClose(); h.newTab(link, private = fromPrivate) }
            SheetRow(Icons.Outlined.Tab, "Ouvrir en arrière-plan") { onClose(); h.newTab(link, select = false, private = fromPrivate); Toast.show("Ouvert dans un onglet en arrière-plan.") }
            if (!fromPrivate) SheetRow(Icons.Outlined.VisibilityOff, "Ouvrir dans un onglet privé") { onClose(); h.newTab(link, private = true) }
            if (fic != null) {
                if (inLib) SheetRow(Icons.Outlined.LocalLibrary, "Voir la fiche dans la bibliothèque") { onClose(); Nav.push(Screen.FicDetail(fic.ficId)) }
                else SheetRow(Icons.Outlined.LibraryAdd, "Ajouter la fic à la bibliothèque") { onClose(); LinkFlow.open(link, Shelves.TO_READ, read = false) }
                SheetRow(Icons.Outlined.Download, "Télécharger la fic") { onClose(); LinkFlow.open(link, Shelves.TO_READ, read = false, download = true) }
            }
            SheetRow(Icons.Outlined.StarOutline, "Ajouter aux favoris") { bookmark = true }
            SheetRow(Icons.Outlined.ContentCopy, "Copier le lien") { clipboard.setText(AnnotatedString(link)); Toast.show("Lien copié."); onClose() }
            SheetRow(Icons.Outlined.Share, "Partager le lien") {
                onClose()
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Partager"))
            }
        }
    }
}

@Composable
fun SheetRow(icon: ImageVector, label: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Réglages d'affichage du mode lecture, appliqués tout de suite à la page. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderStyleSheet(t: Tab, onClose: () -> Unit) {
    val h = BrowserHolder
    var theme by remember { mutableIntStateOf(Prefs.readerTheme) }
    var font by remember { mutableIntStateOf(Prefs.readerFont.coerceIn(0, PageTools.FONTS.lastIndex)) }
    var size by remember { mutableFloatStateOf(Prefs.readerSize.toFloat()) }
    var line by remember { mutableFloatStateOf(Prefs.readerLine) }
    var margin by remember { mutableFloatStateOf(Prefs.readerMargin.toFloat()) }
    var justify by remember { mutableStateOf(Prefs.readerJustify) }
    var auto by remember { mutableStateOf(PageTools.autoReader) }
    fun apply() {
        Prefs.readerTheme = theme; Prefs.readerFont = font; Prefs.readerSize = size.toInt(); Prefs.readerLine = line
        Prefs.readerMargin = margin.toInt(); Prefs.readerJustify = justify
        h.applyReaderStyle(t)
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Affichage de lecture", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Thème", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageTools.THEMES.forEachIndexed { i, th ->
                    val bg = Color(android.graphics.Color.parseColor(th.bg)); val fg = Color(android.graphics.Color.parseColor(th.fg))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { theme = i; apply() }.padding(4.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(bg)
                            .then(if (theme == i) Modifier.background(Color.Transparent) else Modifier), contentAlignment = Alignment.Center) {
                            Text("Aa", color = fg)
                            if (theme == i) Box(Modifier.matchParentSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .22f)))
                        }
                        Text(th.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Police", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PageTools.FONTS.forEachIndexed { i, (n, _) ->
                    FilterChip(font == i, { font = i; apply() }, { Text(n, fontFamily = when (i) { 1 -> FontFamily.Serif; 3 -> FontFamily.Monospace; else -> FontFamily.SansSerif }) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Taille du texte : ${size.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(size, { size = it }, valueRange = 12f..30f, steps = 17, onValueChangeFinished = { apply() })
            Text("Interligne : ${"%.1f".format(line)}", style = MaterialTheme.typography.labelLarge)
            Slider(line, { line = it }, valueRange = 1.1f..2.2f, steps = 10, onValueChangeFinished = { apply() })
            Text("Marges : ${margin.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(margin, { margin = it }, valueRange = 4f..48f, steps = 10, onValueChangeFinished = { apply() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Texte justifié", Modifier.weight(1f)); Switch(justify, { justify = it; apply() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Toujours ouvrir les chapitres en mode lecture")
                    Text("Pour FanFiction.net, SpaceBattles et WebNovel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(auto, { auto = it; PageTools.autoReader = it })
            }
        }
    }
}

/** Écran des favoris : dossiers, modification, suppression. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen() {
    val items by Bookmarks.items.collectAsState()
    val folders by Bookmarks.folders.collectAsState()
    var folder by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmarks.Bookmark?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var dropFolder by remember { mutableStateOf<String?>(null) }
    val shown = items.filter { (folder == null || it.folder == folder) && (q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true)) }
    Scaffold(topBar = {
        BackTopBar("Favoris", Fmt.plural(items.size, "favori")) {
            IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "Nouveau dossier") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            item {
                if (items.isNotEmpty()) OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Chercher dans les favoris") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(folder == null, { folder = null }, { Text("Tout  ${items.size}") })
                    folders.forEach { f ->
                        FilterChip(folder == f, { folder = if (folder == f) null else f }, { Text("$f  ${items.count { it.folder == f }}") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) })
                    }
                }
                folder?.takeIf { it != Bookmarks.DEFAULT }?.let { f ->
                    TextButton(onClick = { dropFolder = f }, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Outlined.FolderDelete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Supprimer le dossier « $f »")
                    }
                }
                if (items.isEmpty()) EmptyState(Icons.Outlined.StarOutline, "Aucun favori",
                    "Dans le navigateur, ouvrez le menu puis « Ajouter aux favoris », ou touchez longuement un lien. Vos favoris s'affichent aussi sur la page d'accueil.",
                    Modifier.padding(top = 40.dp))
            }
            items(shown, key = { it.url }) { b ->
                Row(Modifier.fillMaxWidth().clickable { Nav.openBrowser(b.url, newTab = false) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.StarOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text((runCatching { java.net.URI(b.url).host?.removePrefix("www.") }.getOrNull() ?: b.url) + if (folder == null) " · ${b.folder}" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { editing = b }) { Icon(Icons.Outlined.Edit, "Modifier ou déplacer", tint = MaterialTheme.colorScheme.outline) }
                }
            }
        }
    }
    editing?.let { b -> BookmarkDialog(b.title, b.url, b) { editing = null } }
    if (newFolder) {
        var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newFolder = false }, title = { Text("Nouveau dossier") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true, label = { Text("Nom") }) },
            confirmButton = { TextButton(onClick = { Bookmarks.addFolder(n); folder = n.trim().ifEmpty { null }; newFolder = false }) { Text("Créer") } },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Annuler") } })
    }
    dropFolder?.let { f ->
        AlertDialog(onDismissRequest = { dropFolder = null }, title = { Text("Supprimer le dossier « $f » ?") },
            text = { Text("Ses favoris ne sont pas effacés : ils reviennent dans « ${Bookmarks.DEFAULT} ».") },
            confirmButton = { TextButton(onClick = { Bookmarks.removeFolder(f); folder = null; dropFolder = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { dropFolder = null }) { Text("Annuler") } })
    }
}
