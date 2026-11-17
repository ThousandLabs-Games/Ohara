package com.thousandlabs.ohara.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.BuildConfig
import com.thousandlabs.ohara.IconSwitcher
import com.thousandlabs.ohara.MainActivity
import com.thousandlabs.ohara.R
import com.thousandlabs.ohara.backup.Backup
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.JunkFilter
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.download.UpdateChecker
import com.thousandlabs.ohara.privacy.AdBlocker
import com.thousandlabs.ohara.privacy.Privacy
import com.thousandlabs.ohara.sources.GenericSource
import com.thousandlabs.ohara.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab() {
    val context = LocalContext.current
    val pv by Prefs.version.collectAsState()
    val upd by AppUpdater.state.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var taps by remember { mutableIntStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("Réglages", style = MaterialTheme.typography.titleLarge.merge(SerifTitle)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (upd.available != null) NoteCard("Nouvelle version disponible : ${upd.available!!.name}", icon = Icons.Outlined.SystemUpdate, action = {
                TextButton(onClick = { Nav.push(Screen.Settings("maj")) }) { Text("Voir") }
            })
            SectionTitle("Lecture et apparence")
            SettingRow("Apparence", "Thème, couleur, icône de l'appli", Icons.Outlined.Palette, onClick = { Nav.push(Screen.Settings("apparence")) })
            SettingRow("Lecteur", "Police, taille, interligne, thèmes, lecture continue", Icons.AutoMirrored.Outlined.MenuBook, onClick = { Nav.push(Screen.Settings("lecture")) })
            SectionTitle("Découverte")
            SettingRow("Filtre anti-déchets", "Harem, fics « react », fics chinoises récentes, genres et auteurs", Icons.Outlined.FilterAlt, onClick = { Nav.push(Screen.Settings("filtre")) })
            SettingRow("Fics masquées", "Dossier « Masquées » et liste blanche", Icons.Outlined.VisibilityOff, onClick = { Nav.push(Screen.Hidden) })
            SettingRow("Passages masqués", "Promos Patreon, notes d'auteur, règles personnelles", Icons.Outlined.Block, onClick = { Nav.push(Screen.Settings("masquage")) })
            SettingRow("Score « Prometteuse »", "Voir et régler les critères", Icons.AutoMirrored.Outlined.TrendingUp, onClick = { Nav.push(Screen.Settings("score")) })
            SectionTitle("Bibliothèque")
            SettingRow("Téléchargements et nouveaux chapitres", "Vitesse, vérification quotidienne, Pack Claude", Icons.Outlined.Download, onClick = { Nav.push(Screen.Settings("telechargement")) })
            SettingRow("Sauvegarde et restauration", "Toute la bibliothèque dans un seul fichier", Icons.Outlined.Backup, onClick = { Nav.push(Screen.Settings("sauvegarde")) })
            SettingRow("Statistiques de lecture", null, Icons.Outlined.BarChart, onClick = { Nav.push(Screen.Stats) })
            SettingRow("Sites ajoutés", "${Fmt.plural(GenericSource.domains().size, "site")} en plus des trois sites principaux", Icons.Outlined.AddLink, onClick = { Nav.push(Screen.Settings("sources")) })
            SectionTitle("Confidentialité")
            SettingRow("Historique", if (com.thousandlabs.ohara.data.History.enabled) "Activé" else "Désactivé", Icons.Outlined.History, onClick = { Nav.push(Screen.HistoryScreen) })
            SettingRow("Bloqueur et anti-pistage", "${Fmt.number(AdBlocker.totalBlocked)} éléments bloqués depuis l'installation", Icons.Outlined.Shield, onClick = { Nav.push(Screen.Settings("confidentialite")) })
            if (Prefs.advancedUnlocked) SettingRow("Navigation libre", if (Prefs.advancedBrowsing) "Activée" else "Désactivée", Icons.Outlined.Public, onClick = { Nav.push(Screen.Settings("avance")) })
            SectionTitle("Aide")
            SettingRow("Comment faire", "Guide de toutes les fonctions", Icons.AutoMirrored.Outlined.HelpOutline, onClick = { Nav.push(Screen.Guide) })
            SettingRow("Nouveautés de l'appli", null, Icons.Outlined.NewReleases, onClick = { Nav.push(Screen.WhatsNew) })
            SettingRow("Mises à jour", if (upd.available != null) "Version ${upd.available!!.name} disponible" else "Version ${AppUpdater.currentVersion}", Icons.Outlined.SystemUpdate, onClick = { Nav.push(Screen.Settings("maj")) })
            SettingRow("Dépôt GitHub", AppUpdater.REPO_URL.removePrefix("https://"), Icons.AutoMirrored.Outlined.OpenInNew, onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.REPO_URL)))
            })
            SettingRow("Ohara ${BuildConfig.VERSION_NAME}", "Thousand Labs Games · usage personnel", Icons.Outlined.Info, onClick = {
                if (!Prefs.advancedUnlocked) {
                    taps++
                    if (taps >= 7) { Prefs.advancedUnlocked = true; Toast.show("Navigation libre débloquée : elle apparaît dans Confidentialité.") }
                    else if (taps >= 4) Toast.show("Encore ${7 - taps}…")
                }
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDetailScreen(section: String) {
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    val title = when (section) {
        "apparence" -> "Apparence"; "lecture" -> "Lecteur"; "filtre" -> "Filtre anti-déchets"; "masquage" -> "Passages masqués"
        "telechargement" -> "Téléchargements"; "confidentialite" -> "Bloqueur et anti-pistage"; "sauvegarde" -> "Sauvegarde"
        "sources" -> "Sites ajoutés"; "maj" -> "Mises à jour"; "avance" -> "Navigation libre"; "score" -> "Score « Prometteuse »"; else -> "Réglages"
    }
    Scaffold(topBar = { BackTopBar(title) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            when (section) {
                "apparence" -> AppearanceSection()
                "lecture" -> ReaderSettings()
                "filtre" -> FilterSection()
                "masquage" -> MaskingSection()
                "telechargement" -> DownloadSection()
                "confidentialite" -> PrivacySection()
                "sauvegarde" -> BackupSection()
                "sources" -> SourcesSection()
                "maj" -> UpdateSection()
                "avance" -> AdvancedSection()
                "score" -> ScoreSection()
            }
        }
    }
}

private val ICONS = listOf(
    Triple(R.drawable.ic_bg_a, R.drawable.ic_fg_a, IconSwitcher.Slot.MORNING),
    Triple(R.drawable.ic_bg_b, R.drawable.ic_fg_b, IconSwitcher.Slot.AFTERNOON),
    Triple(R.drawable.ic_bg_c, R.drawable.ic_fg_c, IconSwitcher.Slot.EVENING),
    Triple(R.drawable.ic_bg_d, R.drawable.ic_fg_d, IconSwitcher.Slot.NIGHT)
)

@Composable
fun AppIcon(bg: Int, fg: Int, size: Int = 64) {
    Box(Modifier.size(size.dp).clip(CircleShape)) {
        Image(painterResource(bg), null, Modifier.fillMaxSize())
        Image(painterResource(fg), null, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    SectionTitle("Thème de l'appli")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        THEMES.forEachIndexed { i, t -> FilterChip(selected = Prefs.appTheme == i, onClick = { Prefs.appTheme = i }, label = { Text(t) }) }
    }
    SectionTitle("Couleur")
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENTS.forEachIndexed { i, (name, c) ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(c)
                .then(if (Prefs.accent == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .clickable { Prefs.accent = i })
            @Suppress("UNUSED_EXPRESSION") name
        }
    }
    Text(ACCENTS.getOrElse(Prefs.accent) { ACCENTS[0] }.first, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    SectionTitle("Icône de l'appli")
    Text("L'icône suit le moment de la journée : matin, l'arbre-bibliothèque sur son île ; après-midi, la stèle gravée ; soir, l'intérieur de la bibliothèque ; nuit, l'île sous la lune. Vous pouvez aussi en garder une seule.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    FilterChip(selected = IconSwitcher.fixed < 0, onClick = { IconSwitcher.fixed = -1; Toast.show("L'icône suivra l'heure à partir de la prochaine fermeture d'Ohara.") },
        label = { Text("Automatique selon l'heure") }, modifier = Modifier.padding(horizontal = 16.dp))
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ICONS.forEach { (bg, fg, slot) ->
            val sel = IconSwitcher.fixed == slot.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                IconSwitcher.fixed = slot.ordinal; Toast.show("Icône « ${slot.label} » choisie : elle s'affichera à la prochaine fermeture d'Ohara.")
            }) {
                Box(Modifier.then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier).padding(3.dp)) { AppIcon(bg, fg) }
                Text(slot.label, style = MaterialTheme.typography.labelMedium)
                Text(slot.hours, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text("L'icône change seulement quand Ohara est fermée (bouton retour ou appli balayée), pour ne jamais interrompre votre lecture. Le lanceur peut mettre quelques secondes à l'afficher.", Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection() {
    var genre by remember { mutableStateOf("") }
    NoteCard("Le filtre s'applique aux recherches, à « Pour vous » et aux nouveautés, jamais à votre bibliothèque. Les fics écartées vont dans le dossier « Masquées », d'où vous pouvez les rétablir.")
    SectionTitle("Toujours exclus")
    SettingRow("Harem", "Sauf si le résumé précise « no harem »")
    SettingRow("Fics « react »", "Personnages qui regardent ou lisent leur futur, leur série, leurs livres")
    SectionTitle("Fics chinoises récentes")
    SliderRow("Masquées à partir de", JunkFilter.chineseFromYear.toFloat(), 2010f..2026f, 15, { "${it.toInt()}" }) { JunkFilter.chineseFromYear = it.toInt() }
    Text("Les fics publiées avant cette année restent visibles (les anciennes sont souvent excellentes). Ohara repère les tournures typiques des web-romans traduits.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle("System et transmigration")
    SliderRow("Gardées à partir du score", JunkFilter.systemMinScore.toFloat(), 30f..90f, 11, { "${it.toInt()}/100" }) { JunkFilter.systemMinScore = it.toInt() }
    Text("Acceptées quand elles sont bien faites : seules celles dont le score « Prometteuse » est sous ce seuil sont masquées.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle("Genres masqués")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        JunkFilter.hiddenGenres.forEach { g ->
            FilterChip(selected = true, onClick = { JunkFilter.removeHiddenGenre(g) }, label = { Text(g) }, trailingIcon = { Icon(Icons.Outlined.Close, "Retirer", Modifier.size(16.dp)) })
        }
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(genre, { genre = it }, label = { Text("Genre, étiquette ou mot") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { JunkFilter.addHiddenGenre(genre); genre = "" }, enabled = genre.isNotBlank()) { Text("Masquer") }
    }
    SectionTitle("Auteurs bloqués")
    if (JunkFilter.blockedAuthors.isEmpty()) Text("Aucun. Bloquez un auteur depuis la fiche d'une de ses fics.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    JunkFilter.blockedAuthors.forEach { k -> SettingRow(JunkFilter.authorLabel(k), null, Icons.Outlined.Block) { IconButton(onClick = { JunkFilter.forgetAuthor(k) }) { Icon(Icons.Outlined.Close, "Débloquer") } } }
    SectionTitle("Auteurs de confiance")
    if (JunkFilter.trustedAuthors.isEmpty()) Text("Aucun. Leurs fics passent les filtres de qualité (System, fics chinoises récentes).", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    JunkFilter.trustedAuthors.forEach { k -> SettingRow(JunkFilter.authorLabel(k), null, Icons.Outlined.Shield) { IconButton(onClick = { JunkFilter.forgetAuthor(k) }) { Icon(Icons.Outlined.Close, "Retirer") } } }
    SectionTitle("Liste blanche")
    SettingRow("Fics toujours affichées", Fmt.plural(JunkFilter.whitelist.size, "fic"), Icons.Outlined.VisibilityOff, onClick = { Nav.push(Screen.Hidden) })
}

@Composable
private fun MaskingSection() {
    SwitchRow("Replier les promos automatiquement", "Patreon, Ko-fi, Discord, chapitres en avance, votes WebNovel", Masking.autoPromo) { Masking.autoPromo = it }
    SwitchRow("Replier les notes d'auteur", "« A/N », « Author's note »… y compris sur plusieurs paragraphes", Masking.authorNotes) { Masking.authorNotes = it }
    SwitchRow("Retirer ces passages des exports", "EPUB, TXT et Pack Claude sans les promos", Masking.removeInExports) { Masking.removeInExports = it }
    SwitchRow("Laisser un repère", "Une ligne discrète « passage masqué » à toucher pour le lire ; sinon le passage disparaît complètement", Masking.showMarker) { Masking.showMarker = it }
    NoteCard("Une règle reconnaît le passage même si un nombre change (« 10 chapitres d'avance » / « 12 chapitres d'avance ») ou si quelques mots diffèrent. Les notes d'auteur sur plusieurs paragraphes, en début ou en fin de chapitre, sont masquées en entier.")
    SectionTitle("Vos règles")
    val rules = Masking.rules()
    if (rules.isEmpty()) Text("Dans le lecteur, faites un appui long sur un passage puis « Masquer ce genre de passage ».",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    rules.forEach { r ->
        val target = when (r.scope) {
            Masking.Scope.ALL -> "Toutes les fics"
            Masking.Scope.FIC -> "Fic : " + (Library.get(r.target)?.title ?: r.target)
            Masking.Scope.AUTHOR -> "Auteur : " + JunkFilter.authorLabel(r.target)
        }
        SettingRow("« ${r.phrase.take(90)} »", target) { IconButton(onClick = { Masking.remove(r) }) { Icon(Icons.Outlined.Delete, "Supprimer") } }
    }
}

@Composable
private fun DownloadSection() {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    SwitchRow("Vitesse douce", "Pauses plus longues entre deux chapitres : moins de risque d'être bloqué", Prefs.gentleSpeed) { Prefs.gentleSpeed = it }
    SwitchRow("Ajouter les fics lues dans le navigateur", "Remplace l'historique : la fic arrive dans « En cours », au bon chapitre", Prefs.autoAddVisited) { Prefs.autoAddVisited = it }
    SectionTitle("Nouveaux chapitres")
    SwitchRow("Vérifier chaque jour", "En arrière-plan, avec une notification", Prefs.checkDaily) { Prefs.checkDaily = it; UpdateChecker.schedule(context.applicationContext) }
    SwitchRow("Vérifier à l'ouverture", null, Prefs.checkOnLaunch) { Prefs.checkOnLaunch = it }
    SwitchRow("Télécharger les nouveaux chapitres", "Pour les fics déjà téléchargées en entier", Prefs.autoDownloadNew) { Prefs.autoDownloadNew = it }
    SliderRow("Fic « inactive » après", Prefs.inactiveMonths.toFloat(), 1f..24f, 22, { "${it.toInt()} mois" }) { Prefs.inactiveMonths = it.toInt() }
    SectionTitle("Pack Claude")
    SliderRow("Mots par partie", Prefs.packWordsPerPart.toFloat(), 20_000f..200_000f, 17, { "${(it / 1000).toInt()} k" }) { Prefs.packWordsPerPart = (it / 10_000).toInt() * 10_000 }
    Text("Le texte est découpé en parties de cette taille, sans couper un chapitre, pour pouvoir l'envoyer en plusieurs fois.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle("Stockage")
    val used = remember { Library.storageBytes() }
    SettingRow("Chapitres téléchargés", Fmt.bytes(used))
    TextButton(onClick = { confirm = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Effacer tous les chapitres téléchargés") }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Effacer tous les chapitres ?") },
        text = { Text("Vos fics, votre progression et vos notes restent. Les chapitres seront relus en ligne ou retéléchargés.") },
        confirmButton = { TextButton(onClick = { Library.fics.value.forEach { Library.deleteChapters(it.id) }; confirm = false; Toast.show("Chapitres effacés.") }) { Text("Effacer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )
}

@Composable
private fun PrivacySection() {
    val context = LocalContext.current
    val count by AdBlocker.ruleCount.collectAsState()
    val updating by AdBlocker.updating.collectAsState()
    SwitchRow("Bloqueur de publicités et de pisteurs", "${Fmt.number(count.toLong())} règles (EasyList, EasyPrivacy, Peter Lowe, Fanboy)", Prefs.adblock, Icons.Outlined.Shield) { Prefs.adblock = it }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Listes mises à jour " + (AdBlocker.lastUpdate().takeIf { it > 0 }?.let { Fmt.relative(it) } ?: "jamais (liste intégrée active)"),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { AdBlocker.updateLists { ok -> AppScope.launch { Toast.show(if (ok) "Listes mises à jour." else "Mise à jour impossible (pas de connexion ?).") } } }) { Text("Mettre à jour") }
    }
    SettingRow("Éléments bloqués", "${Fmt.number(AdBlocker.totalBlocked)} depuis l'installation")
    SectionTitle("Anti-pistage")
    SwitchRow("Ne pas me pister", "Cookies tiers refusés, paramètres de pistage retirés des liens, signal GPC envoyé", Prefs.antiTracking) { Prefs.antiTracking = it }
    SwitchRow("Connexions sécurisées uniquement", "Passe automatiquement en https", Prefs.httpsOnly) { Prefs.httpsOnly = it }
    SwitchRow("Protection contre les sites dangereux", "Navigation sécurisée d'Android", Prefs.safeBrowsing) { Prefs.safeBrowsing = it }
    SwitchRow("Tout effacer en quittant", "Cookies et cache du navigateur supprimés à la fermeture", Prefs.clearOnExit) { Prefs.clearOnExit = it }
    TextButton(onClick = { Privacy.clearBrowsingData(context.applicationContext); Toast.show("Données de navigation effacées.") }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Effacer maintenant les données de navigation")
    }
    SectionTitle("Historique")
    SwitchRow("Garder un historique", "Pages visitées, recherches et fics ouvertes, sur le téléphone seulement", com.thousandlabs.ohara.data.History.enabled, Icons.Outlined.History) { com.thousandlabs.ohara.data.History.enabled = it }
    SettingRow("Ouvrir l'historique", null, onClick = { Nav.push(Screen.HistoryScreen) })
    SectionTitle("Verrouillage")
    SwitchRow("Verrouiller Ohara par empreinte", "Demandée à l'ouverture et après 30 secondes en arrière-plan", Prefs.appLock, Icons.Outlined.Fingerprint) { on ->
        if (on) (context as? MainActivity)?.authenticate { Prefs.appLock = true } else Prefs.appLock = false
    }
    val off = Prefs.adblockDisabledSites
    if (off.isNotEmpty()) {
        SectionTitle("Sites sans bloqueur")
        off.forEach { s -> SettingRow(s, null) { IconButton(onClick = { Prefs.adblockDisabledSites = off - s }) { Icon(Icons.Outlined.Close, "Réactiver") } } }
    }
    val hidden = AdBlocker.UserHiding.sites()
    if (hidden.isNotEmpty()) {
        SectionTitle("Éléments masqués à la main")
        hidden.forEach { (s, n) -> SettingRow(s, Fmt.plural(n, "élément")) { IconButton(onClick = { AdBlocker.UserHiding.clear(s); Prefs.putInt("tmp_refresh", Prefs.getInt("tmp_refresh", 0) + 1) }) { Icon(Icons.Outlined.Delete, "Tout réafficher") } } }
    }
    if (Prefs.advancedUnlocked) {
        SectionTitle("Navigation libre")
        SettingRow("Navigation libre", if (Prefs.advancedBrowsing) "Activée" else "Désactivée", Icons.Outlined.Public, onClick = { Nav.push(Screen.Settings("avance")) })
    }
}

@Composable
private fun BackupSection() {
    val context = LocalContext.current
    var withChapters by remember { mutableStateOf(Prefs.getBool("bk_chapters", true)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Création de la sauvegarde…"
        AppScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> Backup.create(out, withChapters) { busy = it } } ?: error("Fichier inaccessible")
                }
            }.onSuccess { s ->
                Prefs.putLong("bk_last", System.currentTimeMillis())
                Toast.show("Sauvegarde créée : ${Fmt.plural(s.fics, "fic")}" + if (withChapters) ", ${Fmt.plural(s.chapters, "chapitre")}." else ".")
            }.onFailure { Toast.show("Sauvegarde impossible : ${it.message}") }
            busy = null
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    fun restore(uri: Uri, replace: Boolean) {
        busy = "Restauration…"
        AppScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { Backup.restore(it, replace) { m -> busy = m } } ?: error("Fichier inaccessible")
                }
            }.onSuccess { s -> Toast.show("Bibliothèque restaurée : ${Fmt.plural(s.fics, "fic")}.") }
                .onFailure { Toast.show("Restauration impossible : ${it.message}") }
            busy = null
        }
    }

    NoteCard("Un seul fichier « .ohara » contient tout : fics, étagères, progression, notes, surlignages, fiches personnages, statistiques, réglages et, si vous le voulez, le texte des chapitres. Rangez-le où vous voulez sur le téléphone ou une carte SD.")
    SwitchRow("Inclure le texte des chapitres", "Fichier plus lourd, mais tout fonctionne hors connexion après restauration", withChapters) { withChapters = it; Prefs.putBool("bk_chapters", it) }
    val last = Prefs.getLong("bk_last", 0L)
    SettingRow("Dernière sauvegarde", if (last > 0) "${Fmt.date(last)} (${Fmt.relative(last)})" else "Jamais")
    Spacer(Modifier.height(8.dp))
    if (busy != null) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(busy ?: "")
    } else Column(Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
            create.launch("Ohara-sauvegarde-$d.ohara")
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Backup, null); Spacer(Modifier.width(8.dp)); Text("Créer une sauvegarde") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restaurer une sauvegarde")
        }
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restaurer") },
            text = { Text("Remplacer : la bibliothèque actuelle est effacée et remplacée par la sauvegarde.\nFusionner : les fics de la sauvegarde sont ajoutées, la progression la plus avancée est gardée.") },
            confirmButton = { TextButton(onClick = { restoreUri = null; restore(uri, false) }) { Text("Fusionner") } },
            dismissButton = { TextButton(onClick = { restoreUri = null; restore(uri, true) }) { Text("Remplacer") } }
        )
    }
}

@Composable
private fun SourcesSection() {
    NoteCard("Pour lire un autre site de fics : activez la navigation libre (touchez sept fois la version dans Réglages), ouvrez le site, puis menu › « Ajouter ce site comme source ». Ohara repère le texte et le lien « chapitre suivant » tout seul.")
    val list = GenericSource.domains()
    if (list.isEmpty()) Text("Aucun site ajouté.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    list.forEach { d -> SettingRow(d, null, Icons.Outlined.Public) { IconButton(onClick = { GenericSource.removeDomain(d) }) { Icon(Icons.Outlined.Delete, "Retirer") } } }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val st by AppUpdater.state.collectAsState()
    SettingRow("Version installée", AppUpdater.currentVersion, Icons.Outlined.Info)
    SwitchRow("Chercher les mises à jour à l'ouverture", "Une fois par jour au plus", AppUpdater.autoCheck) { AppUpdater.autoCheck = it }
    val rel = st.available
    if (rel != null) {
        SectionTitle("Nouvelle version : ${rel.name}")
        if (rel.notes.isNotBlank()) Text(rel.notes, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (st.downloading) {
            LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        } else Button(onClick = {
            if (!AppUpdater.canInstall(context)) {
                Toast.show("Autorisez Ohara à installer des applis, puis revenez.")
                context.startActivity(AppUpdater.permissionIntent(context))
            } else AppUpdater.downloadAndInstall(context)
        }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Télécharger et installer") }
    } else {
        Spacer(Modifier.height(8.dp))
        if (st.checking) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Vérification…")
        } else OutlinedButton(onClick = { AppUpdater.check(manual = true) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Vérifier maintenant") }
    }
    st.message?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
    SettingRow("Toutes les versions", AppUpdater.RELEASES_URL.removePrefix("https://"), Icons.AutoMirrored.Outlined.OpenInNew, onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.RELEASES_URL)))
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSection() {
    NoteCard("La navigation libre ouvre n'importe quel site dans le navigateur d'Ohara, avec le même bloqueur et les mêmes protections. Les recherches passent par un moteur respectueux de la vie privée.")
    SwitchRow("Navigation libre", null, Prefs.advancedBrowsing, Icons.Outlined.Public) { Prefs.advancedBrowsing = it }
    SectionTitle("Moteur de recherche")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SEARCH_ENGINES.forEachIndexed { i, (name, _) -> FilterChip(selected = Prefs.searchEngine == i, onClick = { Prefs.searchEngine = i }, label = { Text(name) }) }
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { Prefs.advancedBrowsing = false; Prefs.advancedUnlocked = false; Nav.pop() }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Cacher à nouveau ce réglage")
    }
}

@Suppress("unused")
private val shape = RoundedCornerShape(8.dp)

@Composable
private fun ScoreSection() {
    val sc = com.thousandlabs.ohara.discover.Scoring
    NoteCard("Le score va de 0 à 100. Il additionne quatre critères. Chaque site est comparé à lui-même : 100 favoris sur FanFiction.net ne valent pas 100 vues sur WebNovel.")
    SectionTitle("1. Intérêt des lecteurs")
    Text("FanFiction.net : favoris et abonnés par mois depuis la publication, favoris pour 10 000 mots, avis par chapitre.\n" +
        "SpaceBattles : vues par mois, réponses et « likes » par chapitre.\n" +
        "WebNovel : vues et ajouts en bibliothèque par mois.\n" +
        "Le résultat passe sur une courbe de 0 à 100, puis compte pour la part choisie ci-dessous.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SliderRow("Part de l'intérêt", sc.wInterest.toFloat(), 40f..100f, 11, { "${it.toInt()} %" }) { sc.wInterest = it.toInt() }
    SectionTitle("2. Longueur")
    Text("De +8 points pour une très longue fic à −18 pour un texte très court.", Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SliderRow("Importance", sc.wLength.toFloat(), 0f..200f, 7, { "${it.toInt()} %" }) { sc.wLength = it.toInt() }
    SliderRow("Pénalité en dessous de", sc.minKWords.toFloat(), 0f..60f, 11, { "${it.toInt()} k mots" }) { sc.minKWords = it.toInt() }
    SliderRow("Bonus maximal dès", sc.bigKWords.toFloat(), 20f..400f, 18, { "${it.toInt()} k mots" }) { sc.bigKWords = it.toInt() }
    SectionTitle("3. Activité")
    Text("Terminée ou mise à jour ce mois-ci : +6. Mise à jour depuis moins de 4 mois : +2. Plus ancienne : −3, puis −12 une fois « inactive » (délai réglé dans Téléchargements, actuellement ${com.thousandlabs.ohara.data.Prefs.inactiveMonths} mois).",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SliderRow("Importance", sc.wActivity.toFloat(), 0f..200f, 7, { "${it.toInt()} %" }) { sc.wActivity = it.toInt() }
    SectionTitle("4. Démarrage fort")
    Text("Une fic récente qui attire déjà beaucoup de lecteurs (intérêt d'au moins 60) gagne +6.", Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SliderRow("Importance", sc.wRising.toFloat(), 0f..200f, 7, { "${it.toInt()} %" }) { sc.wRising = it.toInt() }
    SliderRow("Fic « récente » jusqu'à", sc.risingDays.toFloat(), 15f..365f, 0, { "${it.toInt()} jours" }) { sc.risingDays = it.toInt() }
    SectionTitle("Seuils")
    SliderRow("« Prometteuse » à partir de", sc.threshold.toFloat(), 30f..95f, 0, { "${it.toInt()}" }) { sc.threshold = it.toInt(); if (sc.veryThreshold <= it.toInt()) sc.veryThreshold = (it.toInt() + 1).coerceAtMost(100) }
    SliderRow("« Très prometteuse » à partir de", sc.veryThreshold.toFloat(), 31f..100f, 0, { "${it.toInt()}" }) { sc.veryThreshold = it.toInt().coerceAtLeast(sc.threshold + 1) }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { sc.reset(); Toast.show("Valeurs d'origine rétablies.") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Rétablir les valeurs d'origine") }
    Text("Sur la fiche d'une fic, touchez la ligne « Score » pour voir le détail du calcul.", Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
