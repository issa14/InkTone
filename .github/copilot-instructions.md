# InkTone — Contexte Permanent GitHub Copilot

Ce fichier fournit les règles et le contexte essentiels pour toute
session Copilot sur ce dépôt. Il est concis par nécessité (contexte
limité). **Pour les détails complets, consultez `CLAUDE.md` et les
documents de référence listés ci-dessous.**

## Résumé du Projet

**InkTone** — Lecteur EPUB Android premium avec narration TTS neuronale
synchronisée mot à mot.

- **Stack** : Kotlin, Jetpack Compose (Material 3), Readium 3.0.0,
  Sherpa-ONNX/onnxruntime via JNI, Room 2.6.1, Hilt 2.52, Clean
  Architecture, MVI
- **Cible** : Snapdragon 680, Android 8+ (minSdk 26), arm64-v8a uniquement
- **Philosophie** : Francophone-first, Offline-first, Accessible from Day One
- **Historique** : Réécriture intégrale le 2026-07-26 (ADR-019).
  Legacy archivé sur `legacy/monolith` — **jamais fusionné dans `main`**.

## Documents de Référence (à consulter pour les détails)

| Besoin | Emplacement |
|---|---|
| Architecture complète | `docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md` |
| Décisions d'architecture | `docs/adr/ADR-XXX-*.md` |
| Plan d'exécution phase en cours | `docs/execution/PHASE_9_HARDENING.md` et `PHASE_9BIS_UI_REBUILD.md` |
| Conventions de contribution | `CONTRIBUTING.md` |

## Architecture — Règles Non Négociables

### Sens des Dépendances
```
Presentation → Application → Domain ← Data ← Infrastructure
```
- **`domain/` ne dépend JAMAIS d'Android, Room, Compose, ni d'aucun module.**
- `data/` dépend de tous les modules `infrastructure/`.
- Les règles sont encodées dans `build-logic/` → `./gradlew build` échoue si violées.

### Pattern de Présentation : MVI (ADR-012)
- **UN** état immuable par écran (`XxxUiState`).
- Intents explicites en entrée.
- Effets ponctuels par canal dédié (`SharedFlow`/`Channel`).
- **Pas de MVVM libre à états multiples.**
- Test : `intent entrant → état attendu`, sans instrumentation Android.

### Adressage Universel : `Locator` (domain/valueobject/Locator.kt)
- **SEUL** value object de position dans toute l'app.
- Utilisé pour : reprise de lecture, signets, annotations, recherche.
- **Jamais** de numéro de page, **jamais** de second système d'adressage.

### Distinction Critique
- **`ReadingState`** = état de reprise (1 par publication, source de vérité).
- **`ReadingSession`** = historique statistique (N par publication).
- **Ne jamais les confondre ou les conflater.**

### Capacités TTS (ADR-021)
- Surlignage mot-à-mot **uniquement** si `TtsCapabilities.wordTimestamps == true`.
- **Jamais simulé** par interpolation de caractères.
- Deux paliers : Palier 1 (Android natif `onRangeStart`), Palier 2 (Sherpa-ONNX + alignement forcé CTC).
- Sherpa-ONNX = qualité vocale ; Piper = **écarté** (licence GPL-3.0, archivé).

## Structure des Modules

```
app/                          # Application shell, DI, navigation
core/
  common/                     # Utilitaires partagés
  designsystem/               # Design system (Material 3 tokens, icônes)
  ui/                         # Composants UI réutilisables
  testing/                    # Fakes et helpers de test
domain/                       # Modèles, repos (interfaces), services, use cases
data/                         # Implémentations Room des repos, mappers, backup
infrastructure/
  database/                   # Room DB, DAOs, entités, migrations
  parser/                     # Readium + TXT parsers
  storage/                    # SAF file storage
  tts/                        # Sherpa-ONNX JNI, CTC aligner, Android TTS
  media/                      # AudioTrack playback, Media3 session
  worker/                     # WorkManager (import)
feature/
  library/                    # Bibliothèque
  reader/                     # Lecteur (écran principal, thèmes, surlignage)
  player/                     # Contrôles audio
  search/                     # Recherche plein texte
  import/                     # Import de fichiers
  settings/                   # Préférences, règles de prononciation
  statistics/                 # Statistiques de lecture
  onboarding/                 # Premier lancement
benchmark/                    # Macrobenchmarks
```

## Conventions de Code

### Kotlin
- **Pas d'emoji** dans le code de production — icônes via `AppIcons` (Material Symbols).
- Nommage par concepts métier : `PublicationRepository`, pas `PublicationDataSourceImplV2`.
- Invariants du domaine via `require()` dans les constructeurs.
- Use cases : verbe à l'infinitif (`GetReadingStateUseCase`, `ImportPublicationUseCase`).
- Use case non implémentable : signature complète + KDoc + `TODO("raison, phase cible")`.
- `StateFlow` + `collectAsStateWithLifecycle()` dans Compose.
- Aucune validation silencieusement absente.

### Commits
- **En français, impératif** : `Corrige…`, `Ajoute…`, `Initialise…`.

### Fichiers
- PascalCase pour les classes, camelCase pour les fonctions.
- Messages de commit en français, à l'impératif.

## Acquis Capitalisés du Legacy (K1–K12) — Jamais Perdre

1. Room journal mode **WAL** obligatoire, jamais TRUNCATE.
2. **Une seule ouverture ZIP** par import EPUB.
3. Position de lecture : **source de vérité unique**, pas de départ implicite à zéro.
4. **Aucun `fallbackToDestructiveMigration`** — toute migration a son test.
5. **Storage Access Framework exclusivement** — `MANAGE_EXTERNAL_STORAGE` interdit.
6. Normaliser les hrefs EPUB percent-encodés.
7. Détecter les EPUB protégés par DRM à l'import.
8. Progression de bibliothèque : **une requête groupée**, jamais de N+1.
9. Sherpa-ONNX = moteur référence pour timestamps par mot.
10. Crash reporting **opt-in**, no-op gracieux sans secrets.
11. `seriesName`/`seriesIndex`/`isFavorite`/`subjects` dans le modèle **dès la v1**.
12. **Aucun emoji** dans le code — icônes Material Symbols.
13. **Toujours vérifier le bon artefact Compose** : `LocalTextToolbar`/`TextToolbar`
    sont dans `androidx.compose.ui.platform` (module `ui-android`), pas dans
    `foundation.text.selection`. Avant de conclure qu'une API est absente, chercher
    dans TOUS les artefacts Compose (ui-android, foundation-android, ui-text, etc.)
    — jamais s'arrêter au premier résultat négatif.

## Tests Obligatoires

- Toute migration Room → test `MigrationTestHelper` **dans le même commit**.
- Garde-fous de régression K3/K6/K7/K8 : **jamais supprimés**.
- `./gradlew build` doit rester vert — inclut `checkArchitectureRules`.

## Secrets — Interdits

`keystore.properties`, `*.jks`, `*.jks.bak`, `google-services.json` :
- **Ne jamais lire, afficher, committer ou supprimer.**
- Le keystore de signature : perte = mise à jour Play Store impossible.

## DI (Hilt)

- `@Singleton` pour les services globaux.
- `@ViewModelScoped` pour les états d'écran.
- Modules Hilt dans des packages `di/` par module.
- `data/di/RepositoryModule.kt` et `UseCaseModule.kt` centralisent les bindings.

## Règles Spécifiques UI (Phase 9bis)

- **Material You / Dynamic Color** : uniquement sur le **chrome** (TopBar, Drawer, Dialogs).
  **JAMAIS** sur les thèmes de lecture (`LIGHT`, `DARK`, `SEPIA`).
- **Navigation** : Compose Navigation 2.8+ typée (`@Serializable`), pas Navigation 3 alpha.
- **Sélection de texte** : le spike `SelectableSentence` (Tâche 1.1.1) utilise
  `LocalTextToolbar`/`TextToolbar` de `androidx.compose.ui.platform` (module
  `ui-android`) — PAS de `foundation.text.selection`. `SelectionContainer`
  reste dans `foundation.text.selection`.
- Badges de progression : arrondir ≥ 1% dès qu'une lecture a commencé.
- Pas d'arrondi à 0% si une progression existe.

## Rigueur & Vérification Empirique (K13)

**Ne jamais faire confiance aux imports cités dans un document sans les vérifier
contre le classpath réel du projet.** Un import peut pointer vers le mauvais
module/package — chercher dans un seul artefact et conclure est une erreur.

### Vérification d'une API Compose

1. **Identifier TOUS les artefacts** : `./gradlew :<module>:dependencies` pour
   lister les dépendances transitives. Compose est divisé en ~7 artefacts
   (`ui-android`, `ui-text`, `foundation-android`, `runtime`, etc.).
2. **Chercher dans CHAQUE artefact** : localiser le `.aar` dans le cache Gradle,
   extraire `classes.jar`, lister avec `jar -tf`.
3. **Décompiler si trouvé** : `javap -p` sur la classe pour confirmer les
   signatures exactes (méthodes, paramètres, nullabilité).
4. **Compiler avant de conclure** : un `./gradlew :module:compileDebugKotlin`
   est la seule preuve irréfutable.

### Liste des artefacts Compose (BOM 2024.09.02 → 1.7.2)

| Artefact | Package principal | Exemples de classes |
|---|---|---|
| `foundation-android` | `androidx.compose.foundation.text.selection` | `SelectionContainer`, `SelectionManager` |
| `ui-android` | `androidx.compose.ui.platform` | `LocalTextToolbar`, `TextToolbar`, `TextToolbarStatus` |
| `ui-text` | `androidx.compose.ui.text` | `TextStyle`, `AnnotatedString` |
| `ui-graphics` | `androidx.compose.ui.graphics` | `Color`, `Canvas` |

## Workflow

- Ne pas compiler après chaque modification — build final UNIQUEMENT quand toutes les
  modifications de la session sont terminées.
- Vérifier l'avancement réel : `docs/execution/` + `git log --oneline main`.
- Avant une tâche non triviale : lire le chapitre Blueprint concerné.
- Le code fait foi — ne pas déclarer une tâche finie sans citer le commit/preuve.

## Commandes Essentielles

```bash
./gradlew build                                    # build + tests + règles d'architecture
./gradlew :domain:test                             # tests du domaine uniquement
./gradlew :<module>:checkArchitectureRules         # vérifier un module isolément
bash scripts/check-no-emoji.sh                     # K12
bash scripts/check-no-manage-external-storage.sh   # K5
```
