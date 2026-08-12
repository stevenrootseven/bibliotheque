# Fiche Google Play — Ma Bibliothèque

## Titre (30 caractères max)
Ma Bibliothèque

## Description courte (80 caractères max)
Cataloguez vos films, livres et séries : scan, jaquettes, export PDF.

## Description complète (4000 caractères max)

Ma Bibliothèque est une application gratuite et sans publicité pour cataloguer
toute votre collection personnelle : films, livres et séries, physiques comme
numériques.

**🎬 Films**
- Scannez le code-barres d'un Blu-ray, DVD ou 4K pour l'identifier automatiquement
  (titre, année, réalisateur, acteurs, jaquette, synopsis).
- Suivez plusieurs exemplaires d'un même film (Blu-ray + 4K + copie numérique).
- Cataloguez vos films numériques stockés sur un NAS (partage réseau SMB) : scan
  automatique du dossier, taille de fichier pour comparer la qualité entre deux
  versions, et lecture directe dans votre lecteur vidéo préféré (VLC, MX Player...)
  d'un simple tap.
- Photographiez ou importez une jaquette manuellement si besoin.

**📚 Livres**
- Scannez l'ISBN pour une identification quasi instantanée, y compris pour les
  livres en français (via la Bibliothèque nationale de France).
- Si un livre reste introuvable, créez sa fiche à la main (titre, auteur, année,
  ISBN) — l'ISBN permet de relancer une recherche plus tard pour compléter la fiche
  automatiquement.
- Prenez en photo la couverture (avec recadrage intégré) ou cherchez-la en ligne
  directement depuis l'application.

**📺 Séries**
- Cataloguez vos séries stockées sur votre NAS : liste de tous les épisodes,
  lecture directe, et jaquette (à ajouter manuellement).

**🔍 Et aussi**
- Recherche, tri (titre, année, ajout récent) et bascule grille/liste sur toutes
  les collections.
- Visionneuse de jaquettes en plein écran, avec zoom.
- Import en un clic depuis une collection exportée de My Movies Pro.
- **Exportez toute votre collection en CSV (tableur) ou en catalogue PDF illustré**,
  à partager facilement avec vos proches qui n'ont pas l'application.
- Interface disponible en français et en anglais.

**🔒 Respect de la vie privée**
Aucune publicité, aucun compte à créer, aucun suivi ni statistiques d'usage.
Vos données restent sur votre appareil ; les seuls échanges réseau sont vos
propres recherches auprès de bases de données publiques de films et de livres
(TMDB, BnF, Open Library), et la connexion à votre propre NAS si vous choisissez
de l'utiliser.

---

## Catégorie suggérée
Outils (Tools) — ou Productivité, selon la nomenclature Play Console au moment
de la publication.

## Classification du contenu
Tout public — aucun contenu sensible, pas de fonctions sociales ni d'achats
intégrés.

## Section « Sécurité des données » (Data safety)
Aucune donnée personnelle n'est collectée ni transmise au développeur.
- Pas de compte utilisateur.
- Les identifiants du partage réseau (NAS), s'ils sont saisis, sont stockés
  chiffrés sur l'appareil (EncryptedSharedPreferences) et ne sont envoyés qu'à
  ce NAS, jamais à un tiers.
- Les recherches de films/livres (titre ou code-barres scanné) sont envoyées aux
  API publiques TMDB, BnF et Open Library pour identifier l'œuvre — aucune donnée
  personnelle identifiable n'y est associée.
- Aucun SDK de publicité, d'analytics ou de suivi n'est intégré à l'application.
- Permission caméra : utilisée uniquement pour scanner les codes-barres et
  photographier les jaquettes, localement — aucune image n'est envoyée où que
  ce soit sans action explicite de l'utilisateur (recherche en ligne de jaquette).

## URL de politique de confidentialité
Voir `privacy_policy.html` dans ce dossier — à héberger par exemple via GitHub
Pages sur ce même dépôt (Réglages → Pages → dossier `/play_store` ou
`/docs`), comme pour TWP Inventaire.
