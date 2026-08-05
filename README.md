# Ma Bibliothèque

App Android (Kotlin/Compose) pour cataloguer sa collection de films (Blu-ray/4K) et de livres
par scan de code-barres, sans abonnement. MVP : scan, fiche film/livre, liste, stockage local.
Export PDF/CSV, prêt/emprunt et favoris viendront dans une V2.

## Avant de tester

1. **Clé TMDB (obligatoire pour les films)** — gratuite :
   - Créer un compte sur https://www.themoviedb.org
   - Réglages > API > Demander une clé API > usage "Développeur"
   - Copier la clé « API Key (v3 auth) »
   - La coller dans l'app : icône ⚙️ en haut à droite de l'écran Collection

2. **Livres** : aucune clé nécessaire (Google Books API, gratuit).

## Comment ça marche

- Un livre a son ISBN directement encodé dans son code-barres (préfixe 978/979) → recherche
  directe sur Google Books.
- Un film n'a qu'un code-barres UPC/EAN générique (aucun lien avec le film) → l'app traduit
  d'abord ce code en nom de produit via UPCitemdb (gratuit, ~100 requêtes/jour), puis cherche ce
  titre sur TMDB. Si un disque (surtout français ou ancien) n'est pas reconnu, l'app propose une
  recherche manuelle par titre — rien n'est jamais enregistré sans confirmation explicite.

## Build / install

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Prochaines étapes possibles (V2)

- Export PDF (avec jaquettes) et CSV, comme My Movies Pro
- Prêt / emprunt, favoris
- Téléchargement local des jaquettes pour un accès garanti hors-ligne (le MVP s'appuie sur le
  cache disque de Coil, pas une copie permanente)
