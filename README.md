# Ohara

Lecteur de fanfictions pour Android, de **Thousand Labs Games**. Usage personnel.

Ohara réunit au même endroit la recherche, la lecture, le téléchargement et l'export des fanfictions de **FanFiction.net**, **SpaceBattles** et **WebNovel** (et des sites que vous ajoutez), avec un filtre qui écarte d'office ce qui ne vaut pas le détour.

- Dépôt : https://github.com/ThousandLabs-Games/Ohara
- Télécharger la dernière version : https://github.com/ThousandLabs-Games/Ohara/releases/latest (fichier `Ohara.apk`)

## Fonctions

### Bibliothèque
- Étagères : À lire, En cours, Favoris, Pour Claude, Déjà lues, Abandonnées, plus les vôtres.
- Note sur 10, commentaire, raison d'abandon, fiches personnages.
- Badges : nouveaux chapitres, fic enfin terminée, fic inactive, fic supprimée du site.
- Doublons FanFiction.net / SpaceBattles repérés et fusionnés.
- Sélection multiple (appui long) pour ranger, télécharger ou supprimer plusieurs fics.

### Ajout de fics
- Coller un lien de chapitre : la fic s'ouvre exactement à ce chapitre.
- Partager depuis Brave (ou toute autre appli) vers Ohara.
- Ajout par titre, ajout en lot (plusieurs liens d'un coup).
- Les fics lues dans le navigateur intégré s'ajoutent toutes seules, au bon chapitre.

### Lecteur
- Polices, taille, interligne, marges, texte justifié.
- Thèmes clair, sépia, sombre et noir.
- Traduction en français, faite sur le téléphone (modèle téléchargé une seule fois, puis hors connexion).
- Lecture continue ou chapitre par chapitre, touches de volume, écran toujours allumé.
- Surlignage avec notes, temps de lecture restant (selon votre vitesse réelle), reprise à la position exacte.
- Appui long sur un passage : « Masquer ce genre de passage » (promos Patreon, notes d'auteur…), pour une fic, un auteur ou toutes les fics.

### Découverte
- Recherche avancée multi-sites : site, fandom, personnages, type (self-insert, crossover, AU, fix-it, isekai, time travel, System, transmigration, OC), mots, chapitres, statut, dates, nouveautés, tri.
- Score « Prometteuse » (intérêt des lecteurs rapporté à l'âge et à la longueur, longueur, activité, démarrage fort) : critères visibles et réglables, détail du calcul sur chaque fiche.
- Onglet « Pour vous » appris de votre bibliothèque, onglet « Nouveautés » de vos fandoms, « Trouver une fic similaire ».

### Filtre anti-déchets (toujours actif)
- Harem et fics « react » exclus.
- Fics chinoises récentes masquées à partir d'une année réglable ; les anciennes restent.
- System et transmigration acceptés quand la qualité est bonne.
- « Masquer ce genre », auteurs bloqués ou de confiance, dossier « Masquées » avec liste blanche.

### Téléchargement et exports
- Tous les chapitres d'un coup, file d'attente, écran éteint, pauses pour ne pas être bloqué, vérification anti-robot guidée.
- Vérification quotidienne des nouveaux chapitres, avec notification.
- Exports EPUB, TXT et **Pack Claude** (fiche, notes, personnages, surlignages, modèles de consignes, texte découpé en parties).

### Navigateur et confidentialité
- Navigateur limité aux sites de fics, bloqueur de publicités et de pisteurs (EasyList, EasyPrivacy, liste de Peter Lowe, Fanboy), anti-pistage proche de Brave.
- « Masquer un élément » : l'élément reste masqué sur tout le site.
- Traduction des pages en un bouton (sur le téléphone, rien n'est envoyé).
- FanFiction.net et WebNovel ouverts en version ordinateur puis remis à la largeur du téléphone.
- Historique activable (pages, recherches, fics ouvertes).
- Navigation libre cachée dans les réglages (touchez sept fois la version), avec un moteur de recherche respectueux de la vie privée.
- Verrouillage par empreinte.

### Le reste
- Sauvegarde et restauration de toute la bibliothèque dans un seul fichier `.ohara`.
- Statistiques de lecture.
- Quatre icônes qui suivent le moment de la journée : matin, l'arbre-bibliothèque sur son île ; après-midi, la stèle de pierre gravée ; soir, l'intérieur de la bibliothèque ; nuit, l'île sous la lune.
- Mises à jour de l'appli depuis les versions GitHub, guide « Comment faire » intégré.

## Installation

1. Ouvrez https://github.com/ThousandLabs-Games/Ohara/releases/latest sur le téléphone.
2. Téléchargez `Ohara.apk` et ouvrez-le ; autorisez l'installation depuis le navigateur si Android le demande.
3. Les mises à jour suivantes se font depuis l'appli : Réglages › Mises à jour.

## Compilation

L'APK est compilé et signé automatiquement par GitHub Actions à chaque envoi sur `main`, puis publié dans les versions. Le mot de passe de la clé de signature est le secret `OHARA_SIGNING_PASSWORD` du dépôt ; il n'apparaît jamais dans le code.

Pour compiler à la main : JDK 17 et SDK Android 35, puis `./gradlew assembleRelease`.

## Technique

Kotlin, Jetpack Compose (Material 3), WebView pour le lecteur et le navigateur, jsoup pour lire les pages, WorkManager pour les vérifications en arrière-plan. Toutes les données restent sur le téléphone.
