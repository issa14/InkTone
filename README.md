# InkTone

[![CI](https://github.com/issa14/InkTone/actions/workflows/ci.yml/badge.svg)](https://github.com/issa14/InkTone/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Lecteur EPUB et PDF pour Android, avec narration vocale neuronale
surlignée mot à mot.** Francophone-first, offline-first, accessible dès
la v1.

InkTone traite l'écoute comme un mode de lecture à part entière, et non
comme une fonctionnalité annexe : la voix est neuronale, le surlignage
suit le mot réellement prononcé, et la position de lecture reste la même
que l'on lise des yeux ou que l'on écoute.

---

## Ce que fait l'application

### Lecture

- **EPUB** via [Readium](https://github.com/readium/kotlin-toolkit) 3.0
  et **PDF** via Pdfium — même bibliothèque, même lecteur, même modèle de
  position.
- Moteur de pagination Compose maison : chapitres reflowables paginés,
  pages fixes pour le PDF, table des matières, barre de progression du
  livre.
- Chrome de lecture immersif : masquage automatique, luminosité locale,
  ligne d'état, règle de lecture, rappel de repos oculaire, minuterie de
  sommeil.
- Signets, annotations colorées, sélection de texte avec actions
  contextuelles, recherche plein texte dans la publication.
- **Une seule notion de position** : le value object `Locator`
  ([domain/valueobject/Locator.kt](domain/src/main/kotlin/com/inktone/domain/valueobject/Locator.kt))
  adresse la reprise de lecture, les signets, les annotations et les
  résultats de recherche. Aucun numéro de page, aucun second système
  d'adressage.

### Narration vocale

- Trois moteurs derrière un contrat unique `TtsEngine`, chacun déclarant
  honnêtement ses capacités :

  | Moteur | Hors ligne | Timing mot | Notes |
  |---|---|---|---|
  | **Sherpa-ONNX / Kokoro** (référence) | oui | oui, par alignement forcé CTC | ~290 Mo de modèles, qualité premium |
  | **Android natif** (`TextToSpeech`) | oui | oui, via `onRangeStart` | aucun téléchargement, palier de repli |
  | **Edge-TTS** (optionnel, opt-in) | non | oui, métadonnées du service | voix cloud, désactivé par défaut |

- **Le surlignage mot à mot n'est jamais simulé.** Il n'est actif que si
  `TtsCapabilities.wordTimestamps` est vrai pour le moteur en cours
  ([TtsCapabilities.kt](domain/src/main/kotlin/com/inktone/domain/service/TtsCapabilities.kt)).
  Aucune interpolation de caractères ne vient combler un moteur qui ne
  sait pas donner de frontières de mots.
- Sherpa-ONNX n'exposant pas de timestamps natifs — vérifié
  empiriquement, voir [ADR-021](docs/adr/ADR-021-tts-word-timing-tiered-architecture.md) —
  InkTone exécute un **second passage d'alignement forcé sur l'appareil**
  (modèle CTC léger, décodage de Viterbi contraint par le texte connu)
  pour produire de vraies frontières temporelles.
- Lecture continue sans blanc entre phrases (pipeline gapless,
  [ADR-025](docs/adr/ADR-025-playback-gapless.md)), session média
  Media3 : contrôles depuis l'écran verrouillé, casque, Android Auto.
- Règles de prononciation personnalisables, profils de voix, contrôle de
  vitesse.

### Bibliothèque et contenu

- Import par Storage Access Framework exclusivement — aucune permission
  de stockage large
  ([ADR-015](docs/adr/ADR-015-storage-access-framework-only.md)), détection
  des EPUB protégés par DRM à l'import avec message explicite.
- Séries, favoris, sujets, filtres, tri, écran de détail, reprises
  récentes ; la progression de toute la bibliothèque est calculée par une
  seule requête groupée, jamais en N+1.
- **Catalogues OPDS** : navigation de flux distants, couvertures,
  téléchargement ([ADR-023](docs/adr/ADR-023-opds-scope-reintegration.md)).

### Le reste

- **Synchronisation** de la position et des annotations entre appareils,
  chiffrée de bout en bout, via **Google Drive** (`appDataFolder`, OAuth
  AppAuth) ou **WebDAV** — les deux optionnelles, résolution de conflits
  explicite dans l'interface.
- **Statistiques** : temps de lecture visuelle et d'écoute distingués,
  mots lus, carte de chaleur, statistiques par livre — agrégation SQL
  native, aucun chargement en mémoire.
- **Thèmes** : galerie de thèmes de lecture et studio de personnalisation
  avec vérification de contraste.
- **Accessibilité** de premier rang : cibles tactiles, libellés
  sémantiques, respect de la réduction de mouvement, tests d'accessibilité
  Compose instrumentés.
- **Confidentialité** : tout fonctionne hors ligne par défaut ; le crash
  reporting est opt-in et se dégrade en no-op sans secret embarqué
  ([ADR-014](docs/adr/ADR-014-crash-reporting-opt-in.md)). Les seuls
  échanges réseau sont ceux que vous activez : OPDS, synchronisation,
  Edge-TTS, téléchargement de modèles de voix.

---

## Stack technique

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin 2.0.20, Jetpack Compose, Material 3 |
| Lecture | Readium 3.0 (EPUB), Pdfium (PDF) |
| TTS | Sherpa-ONNX + onnxruntime 1.27 via JNI, `android.speech.tts`, Edge-TTS |
| Audio | Media3 / ExoPlayer 1.4.1, MediaSession |
| Persistance | Room (journal mode WAL), schéma en version 26 |
| Réseau / auth | OkHttp 4.12, AppAuth 0.11 |
| DI | Hilt |
| Architecture | Clean Architecture, présentation MVI |

Cibles : `compileSdk` 35, `minSdk` 26, `targetSdk` 34, AGP 8.6.0.
**JDK 17 (Temurin)** est requis pour builder, comme en CI. Matériel de
référence pour les budgets de performance : Snapdragon 680.

---

## Architecture

Sens de dépendance strict, vérifié par le build et non par la relecture :

```
Presentation → Application → Domain ← Data ← Infrastructure
```

`domain/` ne dépend ni d'Android, ni de Room, ni de Compose, ni d'aucun
autre module du projet. Chaque module applique un convention plugin
`inktone.*` qui câble la tâche `checkArchitectureRules` : **une dépendance
interdite fait échouer `./gradlew build`.**

Le dépôt compte 27 modules Gradle, découpés par responsabilité :

- **`app`** — coquille applicative, câblage DI, `MainActivity`
- **`core:*`** — `designsystem` (couleurs, typo, `AppIcons`, contraste),
  `ui`, `common`, `testing`
- **`domain`** — entités, value objects, contrats de repository,
  38 use cases ; zéro dépendance de framework
- **`data`** — implémentations de repository, mappers
- **`infrastructure:*`** — `database`, `storage` (SAF), `parser`
  (EPUB/PDF), `tts`, `media`, `worker`, `crashreporting`, `sync`, `opds`
- **`feature:*`** — `library`, `reader`, `player`, `search`, `import`,
  `settings`, `statistics`, `onboarding`, `sync`, `opds`
- **`benchmark`** — macrobenchmarks, aucun code métier

Chaque écran suit MVI ([ADR-012](docs/adr/ADR-012-mvi-presentation-pattern.md))
: un état unique immuable, des intents explicites, les effets ponctuels
sur un canal dédié.

Détail complet des modules et de leurs responsabilités : chapitre 5 du
[Blueprint d'architecture](docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md).

---

## Construire

```bash
./gradlew build          # build + tests unitaires + règles d'architecture
./gradlew :app:assembleDebug
```

Vérifications ciblées :

```bash
./gradlew :domain:test                              # tests du domaine
./gradlew :<module>:checkArchitectureRules          # un module isolément
./gradlew koverVerify                               # seuil de couverture
bash scripts/check-no-emoji.sh                      # aucun emoji en production
bash scripts/check-no-manage-external-storage.sh    # SAF exclusivement
```

Les modèles de voix neuronaux ne sont pas embarqués dans l'APK : ils sont
téléchargés à la demande depuis l'application
([ADR-018](docs/adr/ADR-018-voice-model-distribution.md)).

---

## Tests

Environ 100 classes de tests unitaires JVM et 79 classes de tests
instrumentés.

- **CI** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) sur
  chaque PR et chaque push sur `main` : `./gradlew build` (donc tests
  unitaires + `checkArchitectureRules`), garde-fous emoji et
  `MANAGE_EXTERNAL_STORAGE`, puis `koverVerify`.
- **Migrations Room** : toute migration est accompagnée, *dans le même
  commit*, d'un test `MigrationTestHelper`
  ([DatabaseMigrationTest.kt](infrastructure/database/src/androidTest/kotlin/com/inktone/infrastructure/database/DatabaseMigrationTest.kt)).
  Il n'existe aucun `fallbackToDestructiveMigration` global : une
  migration manquante fait planter l'application plutôt qu'effacer les
  données d'un lecteur.
- **Tests instrumentés** (DAO, migrations, accessibilité Compose) : hors
  CI, exécutés sur appareil physique avant tout merge sensible.
- **Garde-fous de régression** : chaque bug structurel du passé a laissé
  un test derrière lui (position de lecture, hrefs percent-encodés, DRM,
  progression N+1). Ils ne se contournent pas pour accélérer un build.

---

## Documentation

| Besoin | Emplacement |
|---|---|
| Architecture cible, tous les chapitres | [`docs/blueprint/`](docs/blueprint/) |
| Décisions d'architecture et alternatives écartées (25 ADR) | [`docs/adr/`](docs/adr/) |
| Plans d'exécution détaillés et avancement réel | [`docs/execution/`](docs/execution/) |
| Conventions de contribution | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| Contexte permanent pour Claude Code | [`CLAUDE.md`](CLAUDE.md) |

> **L'avancement réel du projet ne se lit pas dans ce README.** Il se
> vérifie dans `docs/execution/` et dans `git log --oneline main`. Aucun
> document de statut n'affirme ici qu'une fonctionnalité est terminée
> sans que le code puisse le prouver.

---

## Contribuer

Voir [`CONTRIBUTING.md`](CONTRIBUTING.md). En résumé : toute décision
d'architecture donne lieu à un ADR, les messages de commit sont en
français à l'impératif (`Corrige…`, `Ajoute…`), `./gradlew build` doit
rester vert avant tout commit, et une affirmation de complétude cite le
commit, le fichier ou le test qui la démontre.

---

## Licence

Le code source d'InkTone est publié sous licence **MIT** — voir
[`LICENSE`](LICENSE).

Cette licence couvre le code écrit pour ce projet, et lui seul. Elle ne
s'étend ni au code tiers présent dans le dépôt, ni aux bibliothèques
liées, ni aux modèles de synthèse vocale téléchargés à l'exécution — dont
le modèle d'alignement NeMo FastConformer CTC, sous **CC-BY-4.0, qui
impose une attribution dans toute application distribuée**. L'inventaire
complet est dans [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

---

## Historique

Le dépôt a été réécrit intégralement le 26 juillet 2026
([ADR-019](docs/adr/ADR-019-full-rewrite-orphan-branch.md)).
L'implémentation précédente est archivée en lecture seule sur la branche
`legacy/monolith` : elle sert de référence de comportement, jamais de
source à fusionner dans `main`.

## Secrets

`keystore.properties`, `*.jks` et `google-services.json` sont gitignorés
et doivent le rester. Ils ne sont ni committés, ni affichés, ni
supprimés — en particulier le keystore de signature, dont la perte
rendrait impossible toute mise à jour ultérieure sur le Play Store.
