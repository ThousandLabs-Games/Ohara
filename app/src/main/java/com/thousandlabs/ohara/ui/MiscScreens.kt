package com.thousandlabs.ohara.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.discover.HiddenLog
import com.thousandlabs.ohara.discover.JunkFilter
import com.thousandlabs.ohara.privacy.AdBlocker
import java.util.Calendar

/** Vitesse de lecture mesurée (mots par minute), 230 par défaut tant qu'il n'y a pas assez de mesures. */
fun readingSpeed(): Int {
    val days = ReadingLog.snapshot().values
    val words = days.sumOf { it[0] }
    val secs = days.sumOf { it[1] }
    return if (secs > 900 && words > 2000) (words * 60 / secs).toInt().coerceIn(120, 700) else 230
}

// ---------- Dossier « Masquées » ----------

@Composable
fun HiddenScreen() {
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var refresh by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf<JunkFilter.Reason?>(null) }
    val entries = remember(refresh, pv) { HiddenLog.list() }
    val shown = if (reason == null) entries else entries.filter { it.reason == reason }
    Scaffold(topBar = {
        BackTopBar("Masquées", "Écartées par le filtre anti-déchets") {
            if (entries.isNotEmpty()) TextButton(onClick = { HiddenLog.clear(); refresh++ }) { Text("Vider") }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            item {
                NoteCard("Les dernières fics écartées des recherches et propositions, avec la raison. « Toujours afficher » les met sur la liste blanche : elles ne seront plus jamais filtrées.")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = reason == null, onClick = { reason = null }, label = { Text("Toutes  ${entries.size}") })
                    JunkFilter.Reason.entries.forEach { r ->
                        val n = entries.count { it.reason == r }
                        if (n > 0) FilterChip(selected = reason == r, onClick = { reason = if (reason == r) null else r }, label = { Text("${r.label}  $n") })
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Outlined.VisibilityOff, "Rien de masqué", "Les fics écartées apparaîtront ici.") }
            items(shown, key = { it.fic.id }) { e ->
                FicRow(e.fic, why = "Masquée : ${e.reason.label}" + if (e.detail.isNotBlank()) " (${e.detail})" else "", trailing = {
                    IconButton(onClick = { JunkFilter.alwaysShow(e.fic); refresh++; Toast.show("« ${e.fic.title} » sera toujours affichée.") }) {
                        Icon(Icons.Outlined.Visibility, "Toujours afficher", tint = MaterialTheme.colorScheme.primary)
                    }
                }) { Nav.push(Screen.FicDetail(e.fic.id, e.fic)) }
                Divider()
            }
            val white = JunkFilter.whitelistEntries()
            if (white.isNotEmpty()) {
                item { SectionTitle("Liste blanche (${white.size})") }
                items(white, key = { "w" + it.first }) { (id, title) ->
                    SettingRow(title, "Toujours affichée", Icons.Outlined.Visibility) {
                        IconButton(onClick = { JunkFilter.unWhitelist(id); refresh++ }) { Icon(Icons.Outlined.Close, "Retirer de la liste blanche") }
                    }
                }
            }
        }
    }
}

// ---------- Statistiques ----------

@Composable
fun StatsScreen() {
    val fics by Library.fics.collectAsState()
    val days = remember { ReadingLog.snapshot() }
    val cal = Calendar.getInstance()
    fun key(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    val last30 = (29 downTo 0).map { back ->
        val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
        days[key(c)]?.get(1) ?: 0L
    }
    var streak = 0
    run {
        val c = cal.clone() as Calendar
        if ((days[key(c)]?.get(1) ?: 0L) == 0L) c.add(Calendar.DAY_OF_YEAR, -1)
        while ((days[key(c)]?.get(1) ?: 0L) > 0L) { streak++; c.add(Calendar.DAY_OF_YEAR, -1) }
    }
    val totalSec = days.values.sumOf { it[1] }
    val totalWords = days.values.sumOf { it[0] }
    val downloaded = fics.sumOf { Library.downloadedCount(it.id) }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Scaffold(topBar = { BackTopBar("Statistiques de lecture") }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Temps de lecture", Fmt.duration(totalSec), Modifier.weight(1f))
                StatBox("Mots lus", Fmt.words(totalWords).removeSuffix(" mots"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Jours d'affilée", "$streak", Modifier.weight(1f))
                StatBox("Vitesse", "${readingSpeed()} mots/min", Modifier.weight(1f))
            }
            SectionTitle("30 derniers jours")
            val max = (last30.maxOrNull() ?: 0L).coerceAtLeast(60L)
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp)) {
                val w = size.width / 30f
                last30.forEachIndexed { i, v ->
                    val h = (v.toFloat() / max) * size.height
                    drawRoundRect(track, Offset(i * w + w * 0.15f, 0f), Size(w * 0.7f, size.height), CornerRadius(4f, 4f))
                    if (v > 0) drawRoundRect(primary, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), CornerRadius(4f, 4f))
                }
            }
            Text("Temps de lecture par jour (le plus haut : ${Fmt.duration(max)})", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Bibliothèque")
            SettingRow("Fics", "${fics.size} au total · ${fics.count { Shelves.DONE in it.shelves }} lues · ${fics.count { Shelves.READING in it.shelves }} en cours · ${fics.count { Shelves.ABANDONED in it.shelves }} abandonnées")
            SettingRow("Chapitres sur le téléphone", "${Fmt.number(downloaded.toLong())} · ${Fmt.bytes(remember { Library.storageBytes() })}")
            SettingRow("Notes données", fics.filter { it.note >= 0 }.let { l -> if (l.isEmpty()) "Aucune" else "${l.size} · moyenne ${"%.1f".format(l.map { it.note }.average())}/10" })
            SettingRow("Surlignages", "${fics.sumOf { it.highlights.size }}")
            SettingRow("Publicités et pisteurs bloqués", Fmt.number(AdBlocker.totalBlocked))
            val topFandoms = fics.flatMap { f -> f.fandoms.map { it to f.secondsRead.coerceAtLeast(1) } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(6)
            if (topFandoms.isNotEmpty()) {
                SectionTitle("Fandoms les plus lus")
                topFandoms.forEach { (fd, s) -> SettingRow(fd, if (s > 60) Fmt.duration(s) else "—") }
            }
            val topFics = fics.filter { it.secondsRead > 60 }.sortedByDescending { it.secondsRead }.take(5)
            if (topFics.isNotEmpty()) {
                SectionTitle("Fics les plus lues")
                topFics.forEach { f -> SettingRow(f.title, Fmt.duration(f.secondsRead), onClick = { Nav.push(Screen.FicDetail(f.id)) }) }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") track
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).padding(0.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(Modifier.padding(0.dp))) {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- Guide « Comment faire » ----------

private val GUIDE = listOf(
    "Naviguer" to "Ohara s'ouvre sur le navigateur. La barre du haut sert à taper une adresse ou une recherche ; la barre du bas : page précédente, page suivante, accueil, onglets (le chiffre), bibliothèque et menu. Les pages s'affichent comme sur le téléphone ; menu › « Version ordinateur » pour passer en version ordinateur sur un onglet.",
    "Onglets" to "Touchez le carré chiffré en bas : chaque onglet montre un aperçu de sa page et l'icône du site. Cherchez un onglet par son titre, ouvrez-en un nouveau avec le bouton +, fermez-en un avec la croix. Les onglets sont gardés quand vous fermez l'appli.",
    "Lire en plein écran" to "Quand vous descendez dans une page, les barres du haut et du bas disparaissent pour laisser tout l'écran au chapitre ; remontez un peu pour les faire revenir. Réglages › Apparence › « Cacher les barres en lisant » pour désactiver.",
    "La bibliothèque se remplit toute seule" to "Lisez une fic dans le navigateur : dès que vous en avez lu 10 000 mots (réglable), elle entre dans la bibliothèque, étagère « En cours ». Un chapitre compte comme lu quand vous en avez parcouru 60 %. Sous la barre d'adresse, une ligne indique où vous en êtes. Pour l'ajouter tout de suite : menu › « Ajouter à la bibliothèque ».",
    "Reprendre là où vous vous êtes arrêté" to "Dans la bibliothèque, ou dans « Reprendre la lecture » sur la page d'accueil, touchez une fic : elle s'ouvre exactement à la dernière page lue. Le bouton « i » ouvre sa fiche (étagères, note, commentaire, personnages, chapitres, exports).",
    "Masquer une phrase ou un passage" to "Appui long sur une phrase, puis « Masquer » : le paragraphe disparaît tout de suite, et aussi dans les chapitres suivants. Choisissez la portée : cette fic, cet auteur, ou partout. La règle reconnaît le passage même si un nombre ou quelques mots changent. Les promos (Patreon, Discord, chapitres d'avance…) et les notes d'auteur sont masquées d'office. Tout se gère dans Réglages › Passages masqués.",
    "Masquer un élément d'un site" to "Menu › « Masquer un élément » : touchez une bannière ou un encart gênant, « Agrandir » si besoin, puis « Masquer ». Il reste masqué sur tout le site.",
    "Traduire" to "Touchez l'icône de traduction à côté de l'adresse : la page se traduit en français sur place, en commençant par ce qui est à l'écran, et toutes les pages suivantes aussi jusqu'à ce que vous la touchiez à nouveau. La toute première fois, Ohara prépare la traduction sur le téléphone ; ensuite tout se fait sans connexion et rien n'est envoyé à personne.",
    "Chapitres gardés et lecture sans connexion" to "Chaque chapitre que vous lisez d'une fic de la bibliothèque est gardé sur le téléphone. Menu › « Télécharger la fic » (ou le bouton de la fiche) récupère tous les chapitres. Menu › « Téléchargements » : les fics gardées s'ouvrent comme des pages, avec Précédent et Suivant, même sans connexion.",
    "Rechercher une fic précise" to "Menu › « Découvrir » › Recherche : site, fandom, personnages, type (self-insert, crossover, AU, fix-it, isekai, time travel, System, transmigration, OC), longueur, chapitres, statut, dates et tri. Une touche sur un type l'inclut, deux touches l'excluent. « Pour vous » propose des fics proches de vos lectures, « Nouveautés » les nouvelles fics de vos fandoms.",
    "Le score « Prometteuse »" to "Il compare l'intérêt des lecteurs (favoris, abonnés, vues, réponses) à l'âge et à la longueur de la fic, tient compte de l'activité récente et repère les jeunes fics qui démarrent fort. Tous les critères sont détaillés et réglables dans Réglages › Score « Prometteuse » ; sur la fiche d'une fic, touchez la ligne du score pour voir le calcul.",
    "Le filtre anti-déchets" to "Toujours actif sur les recherches et propositions : pas de harem ni de fics « react », pas de fics chinoises récentes (l'année se règle), System et transmigration seulement quand elles sont bonnes. Depuis une fiche : « Masquer ce genre », « Bloquer l'auteur » ou « Auteur de confiance ». Tout ce qui est écarté va dans « Masquées ».",
    "Étagères, notes et fiches personnages" to "Chaque fic peut être sur plusieurs étagères : À lire, En cours, Favoris, Pour Claude, Déjà lues, Abandonnées, et les vôtres. Sur la fiche : note sur 10, commentaire, raison d'abandon et fiches personnages. Appui long dans la bibliothèque pour sélectionner plusieurs fics.",
    "Nouveaux chapitres" to "Ohara vérifie chaque jour vos fics et vous prévient. Un point sur l'icône de bibliothèque signale des nouveautés. Les badges indiquent les nouveaux chapitres, les fics terminées, inactives ou supprimées du site.",
    "Envoyer une fic à Claude" to "Fiche de la fic › Exporter › Pack Claude : fiche, vos notes, personnages, modèles de consignes et texte découpé en parties, à partir des chapitres gardés sur le téléphone. EPUB et TXT sont aussi disponibles.",
    "Rechercher dans la page, partager" to "Menu › « Rechercher dans la page » pour trouver un mot dans un chapitre ; menu › « Partager » pour envoyer le lien.",
    "Historique" to "Désactivé par défaut. Réglages › Historique (ou menu › Historique) pour l'activer : pages, recherches et fics ouvertes, retrouvées par jour, par type ou par mot. La barre d'adresse propose aussi les pages déjà visitées.",
    "Sites FanFiction.net et WebNovel" to "Si un site demande « Je ne suis pas un robot », cochez la case : c'est lui qui vérifie. Si un site affiche une erreur 504 ou semblable, son serveur ne répond pas : « Réessayer » un peu plus tard.",
    "Navigation libre" to "Le navigateur est limité aux sites de fics. Pour tous les sites : Réglages › touchez sept fois la ligne de version, puis Réglages › Navigation libre, avec un moteur de recherche respectueux de la vie privée.",
    "Sauvegarder la bibliothèque" to "Réglages › Sauvegarde : un seul fichier contient tout. Pour changer de téléphone, copiez ce fichier puis « Restaurer » sur le nouveau.",
    "Verrouiller l'appli et mettre à jour" to "Réglages › Bloqueur et anti-pistage › Verrouiller Ohara par empreinte. Réglages › Mises à jour › « Télécharger et installer » pour la dernière version publiée sur GitHub."
)

@Composable
fun GuideScreen() {
    var open by remember { mutableStateOf(setOf<Int>()) }
    Scaffold(topBar = { BackTopBar("Comment faire") }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            items(GUIDE.size) { i ->
                val (q, a) = GUIDE[i]
                val isOpen = i in open
                Column(Modifier.fillMaxWidth().clickable { open = if (isOpen) open - i else open + i }.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (isOpen) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.rotate(0f))
                    }
                    if (isOpen) { Spacer(Modifier.height(8.dp)); Text(a, style = MaterialTheme.typography.bodyMedium) }
                }
                Divider()
            }
        }
    }
}

// ---------- Nouveautés de l'appli ----------

private val VERSIONS = listOf(
    "Onglets et plein écran — 17 novembre 2026" to listOf(
        "Liste des onglets avec un aperçu de chaque page, l'icône du site et une recherche dans les onglets.",
        "Les barres se cachent quand vous descendez dans un chapitre et reviennent quand vous remontez."
    ),
    "Ohara devient un navigateur — 17 novembre 2026" to listOf(
        "Ohara s'ouvre directement sur le navigateur, en version téléphone ; « Version ordinateur » au choix dans le menu, onglet par onglet.",
        "Onglets, page d'accueil (recherche, reprise de lecture, sites, raccourcis), barre d'outils en bas, menu complet, recherche dans la page, propositions dans la barre d'adresse.",
        "La bibliothèque se remplit toute seule : une fic y entre après 10 000 mots lus (réglable), et s'ouvre ensuite à la dernière page lue.",
        "Appui long sur une phrase › « Masquer » : elle disparaît tout de suite et dans les chapitres suivants.",
        "Les chapitres lus sont gardés ; « Téléchargements » les affiche comme des pages, sans connexion.",
        "Le lecteur séparé est retiré : tout se lit dans le navigateur, avec la traduction en un bouton."
    ),
    "Lecture plus rapide — 17 novembre 2026 (fin de matinée)" to listOf(
        "Le chapitre suivant s'affiche tout de suite ; la traduction se fait ensuite sur place, en commençant par l'écran visible, comme dans un navigateur.",
        "Menu de traduction dans le lecteur : « Traduire ce chapitre », « Afficher le texte original », « Toujours traduire l'anglais ».",
        "Masquage plus efficace : passages retirés complètement, règles tolérantes aux nombres et aux petites différences, notes d'auteur sur plusieurs paragraphes, plus de formules de promo reconnues.",
        "Quand le serveur d'un site ne répond pas (erreur 504…), le navigateur l'explique et propose « Réessayer » ou la version mobile du site.",
        "Appli plus fluide : la position de lecture n'est plus enregistrée à chaque mouvement, bibliothèque plus rapide, transitions entre les écrans."
    ),
    "Améliorations — 17 novembre 2026 (après-midi)" to listOf(
        "WebNovel : si son interface de données refuse, Ohara lit directement le sommaire et les pages du site ; le chapitre affiché dans le navigateur est gardé tel quel.",
        "Le navigateur invisible d'Ohara tourne désormais derrière l'écran de l'appli, ce qui lui permet de passer les protections anti-robot automatiques.",
        "Les messages d'erreur indiquent la page qui a posé problème.",
        "WebNovel fonctionne : les demandes au site partent désormais d'une vraie page du site, et Ohara ne confond plus les pages normales avec une vérification anti-robot (même correction pour SpaceBattles).",
        "Les pages FanFiction.net et WebNovel, ouvertes en version ordinateur, sont remises à la largeur du téléphone.",
        "Traduction en un bouton dans le navigateur : toutes les pages s'affichent en français tant qu'elle est activée.",
        "Lecteur réglé comme sur le site (police simple, texte aligné à gauche), avec un bouton « Comme sur le site » dans l'affichage.",
        "Historique activable : pages, recherches et fics ouvertes.",
        "Score « Prometteuse » : critères détaillés et réglables, détail du calcul sur chaque fiche.",
        "Couvertures dessinées aux couleurs du fandom et carte « Reprendre la lecture » dans la bibliothèque."
    ),
    "Correctifs — 17 novembre 2026" to listOf(
        "Coller un lien ouvre maintenant la fic à coup sûr : si le site demande une vérification ou tarde à répondre, la page s'ouvre dans le navigateur d'Ohara et la fic s'ouvre toute seule dès que la page s'affiche.",
        "Sur FanFiction.net, la fiche et le chapitre affiché sont lus directement sur la page, sans attendre.",
        "Ohara ne se ferme plus toute seule en arrière-plan : l'icône du moment change désormais seulement quand l'appli est fermée.",
        "Le bouton « Vérifier » du lecteur ramène directement au chapitre une fois la vérification passée.",
        "Navigateur : « Ajouter », « Télécharger » et « Lire » lisent la fic directement sur la page affichée, avec un indicateur pendant l'opération.",
        "Nouveau : traduction en français dans le lecteur, faite sur le téléphone.",
        "FanFiction.net et WebNovel s'ouvrent dans leur version ordinateur : c'est elle qu'Ohara sait lire, et la vérification anti-robot reste valable pour les téléchargements."
    ),
    "Première version — novembre 2026" to listOf(
        "Bibliothèque avec étagères (À lire, En cours, Favoris, Pour Claude, Déjà lues, Abandonnées et étagères personnelles), notes sur 10, commentaires, raisons d'abandon et fiches personnages.",
        "Lecteur : polices, taille, interligne, marges, thèmes clair, sépia, sombre et noir, lecture continue ou par chapitre, surlignages avec notes, temps restant, reprise exacte.",
        "« Masquer ce genre de passage » par appui long ; promos Patreon repliées d'office.",
        "Téléchargement de tous les chapitres de FanFiction.net, SpaceBattles et WebNovel, file d'attente, écran éteint, vérification anti-robot.",
        "Recherche avancée multi-sites, score « Prometteuse », onglets « Pour vous » et « Nouveautés », « Trouver une fic similaire ».",
        "Filtre anti-déchets toujours actif, dossier « Masquées » avec liste blanche, auteurs bloqués ou de confiance.",
        "Exports EPUB, TXT et Pack Claude.",
        "Navigateur intégré avec bloqueur de publicités, anti-pistage et « Masquer un élément » ; navigation libre cachée dans les réglages.",
        "Sauvegarde et restauration dans un seul fichier, statistiques de lecture, verrouillage par empreinte.",
        "Quatre icônes qui changent selon le moment de la journée, mises à jour depuis GitHub."
    )
)

@Composable
fun WhatsNewScreen() {
    Scaffold(topBar = { BackTopBar("Nouveautés de l'appli") }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            VERSIONS.forEach { (title, items) ->
                SectionTitle(title)
                items.forEach { t ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("—", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
