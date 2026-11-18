# Ohara

Lecteur de fanfictions pour Android, de **Thousand Labs Games**. Usage personnel.

Ohara réunit au même endroit la recherche, la lecture, le téléchargement et l'export des fanfictions de **FanFiction.net**, **SpaceBattles** et **WebNovel** (et des sites que vous ajoutez), avec un filtre qui écarte d'office ce qui ne vaut pas le détour.

- Dépôt : https://github.com/ThousandLabs-Games/Ohara
- Télécharger la dernière version : https://github.com/ThousandLabs-Games/Ohara/releases/latest (fichier `Ohara.apk`)

## Fonctions

### Un navigateur avant tout
- Ohara s'ouvre sur le navigateur, en version téléphone ; « Version ordinateur » au choix, onglet par onglet.
- Onglets avec aperçus, groupes d'onglets par fic ou par site, onglets privés aux barres sombres qui ne laissent aucune trace.
- Page d'accueil (recherche, reprise de lecture, favoris, sites, raccourcis), barre d'outils en bas, recherche dans la page, propositions dans la barre d'adresse.
- Favoris rangés en dossiers ; appui long sur un lien pour l'ouvrir en arrière-plan ou en privé, ajouter ou télécharger la fic, copier, partager.
- Lecture : barres cachées en descendant, chapitre suivant en continuant à faire défiler tout en bas (une seconde), précédent tout en haut, mode lecture (thème, police, taille, interligne, marges).
- Bloqueur de publicités et de pisteurs (EasyList, EasyPrivacy, liste de Peter Lowe, Fanboy), anti-pistage proche de Brave.
- Traduction en français en un bouton, sur le téléphone (rien n'est envoyé), pour toutes les pages tant qu'elle est activée.
- Noms à ne pas traduire : personnages, îles, villages et techniques de One Piece et de Naruto gardés tels quels pendant la traduction (« Drum Island », « Usopp »), avec vos propres noms en plus (appui long sur un nom › « Ne pas traduire »).
- Appui long sur une phrase › « Masquer » : elle disparaît tout de suite et dans les chapitres suivants. Promos et notes d'auteur masquées d'office.
- « Masquer un élément » d'un site (bannières, encarts), comme sur Brave.
- Réglages par site : chaque fonction (glissement entre chapitres, barres cachées, mode lecture, masquage, menu des liens, traduction) peut être coupée sur un site seulement.
- Historique activable, navigation libre cachée dans les réglages.

### La bibliothèque à côté
- Remplie toute seule : une fic y entre après 10 000 mots lus (réglable), et se rouvre à la dernière page lue, avec « Précédemment » (phrases clés des derniers chapitres lus) après une semaine sans l'ouvrir.
- Collections automatiques : mises à jour ce mois-ci, actives et prometteuses, presque à jour, à rattraper, longues, courtes, en pause, par fandom et par type.
- Étagères (À lire, En cours, Favoris, Pour Claude, Déjà lues, Abandonnées et les vôtres), note sur 10, commentaire, raison d'abandon, fiches personnages.
- Badges : nouveaux chapitres, fic terminée, inactive, supprimée du site ; doublons FanFiction.net / SpaceBattles fusionnés.
- Chapitres lus gardés sur le téléphone ; téléchargement complet au choix ; « Téléchargements » affiche les chapitres comme des pages, sans connexion.
- « Envoyer à Claude » en un geste (fichiers et consigne choisie vers l'appli Claude), avec compteur, annulation et préparation qui continue en arrière-plan (notification « Prêt à envoyer à Claude ») ; exports EPUB, TXT et Pack Claude.

### Découverte
- Recherche avancée multi-sites, score « Prometteuse » aux critères réglables, « Pour vous », « Nouveautés », « Trouver une fic similaire ».
- Filtre anti-déchets toujours actif (harem, fics « react », fics chinoises récentes, System et transmigration de mauvaise qualité), dossier « Masquées ».

### Le reste
- Sauvegarde et restauration dans un seul fichier `.ohara`, statistiques de lecture, verrouillage par empreinte.
- Quatre icônes qui suivent le moment de la journée, mises à jour depuis les versions GitHub, guide « Comment faire » intégré.

## Installation

1. Ouvrez https://github.com/ThousandLabs-Games/Ohara/releases/latest sur le téléphone.
2. Téléchargez `Ohara.apk` et ouvrez-le ; autorisez l'installation depuis le navigateur si Android le demande.
3. Les mises à jour suivantes se font depuis l'appli : Réglages › Mises à jour.

## Compilation

L'APK est compilé et signé automatiquement par GitHub Actions à chaque envoi sur `main`, puis publié dans les versions. Le mot de passe de la clé de signature est le secret `OHARA_SIGNING_PASSWORD` du dépôt ; il n'apparaît jamais dans le code.

Pour compiler à la main : JDK 17 et SDK Android 35, puis `./gradlew assembleRelease`.

## Technique

Kotlin, Jetpack Compose (Material 3), WebView pour le lecteur et le navigateur, jsoup pour lire les pages, WorkManager pour les vérifications en arrière-plan. Toutes les données restent sur le téléphone.
