# Audit de consolidation v1.0.0 — « la première utilisation ne déçoit pas »

**Date :** 2026-08-20 — **Base :** `main` (6b74b3b5) — **Device :** V2206
(Snapdragon 680, Android 14) branché, build debug installé.

**Périmètre demandé :** PAS un audit de fonctionnalités manquantes, mais
une consolidation de ce qui est en place, orientée **première
utilisation** : aucun crash, aucun cul-de-sac, aucun bouton qui ne fait
rien, aucune erreur muette, aucune promesse d'interface mensongère.

**Règle appliquée (Blueprint §17.2) :** le code fait foi. Chaque constat
ci-dessous cite le fichier et la ligne qui le prouve. Vérification
réelle sur appareil en plus de la lecture statique.

---

## 1. Méthode

Cinq axes, chacun audité par une passe dédiée (lecture seule, preuves
fichier:ligne) :

1. **Parcours premier lancement** — tracé dans le code + vérification
   réelle sur device : onboarding → bibliothèque vide → import → lecteur
   → TTS.
2. **Robustesse / crash** — gestion d'erreur, use cases morts, replis.
3. **Honnêteté d'interface** — états vide/chargement/erreur par écran,
   boutons sans effet, docs-vs-code.
4. **Performance premier usage** — travail sur thread principal, budgets
   Blueprint §11.2, téléchargement des modèles.
5. **Hygiène de release** — version, signature, R8, schéma Room,
   notices, CI, README.

Baseline : `./gradlew build` vert (27 modules, tests unitaires,
`checkArchitectureRules`, lint, `koverVerify`) — re-vérifié après
corrections.

---

## 2. Verdict global

Le **parcours complet de première utilisation fonctionne sur appareil**
(onboarding → bibliothèque vide avec CTA → sélecteur SAF → import réel
d'un EPUB → livre dans la bibliothèque → ouverture du lecteur → lecture
TTS via la voix du système). La robustesse de base est bonne : résultats
typés à l'import (DRM/corrompu/doublon), états d'erreur avec retry sur
les écrans principaux, aucun `runBlocking`/`Thread.sleep` en production,
K8/K2 respectés.

Mais l'audit a mis au jour **5 problèmes bloquants** pour une v1.0.0
dont la première impression doit être irréprochable — tous corrigés ou
tranchés dans cette passe — et plusieurs majeurs (dont 2 à traiter
avant release avec validation device).

---

## 3. Constats et décisions, par axe

### 3.1 Honnêteté d'interface (axe 3)

| # | Sév. | Constat | Preuve | Action |
|---|---|---|---|---|
| B1 | **Bloquant** | Bouton « Écouter un extrait » (Réglages) **no-op** : `PlayPreview` n'est branché sur aucun moteur | `SettingsScreen.kt` (bouton), `SettingsViewModel.kt` (`/* Signalé : non branché */`) | **Corrigé — ré-implémenté** (décision utilisateur) : bouton recâblé sur une vraie synthèse + lecture d'une phrase d'exemple avec le profil vocal actif (`SettingsViewModel.togglePreview`, contrats `TtsEngine`/`AudioPlayer`), 3 tests unitaires, **vérifié sur device** (AudioTrack réel `state:started` + retour à l'état initial après la fin de la phrase) |
| B2 | **Bloquant** | Téléchargement de la voix neuronale : **faux succès**. L'UI affichait « Voix neuronale installée » après téléchargement d'une archive `.tar.bz2` jamais extraite ; `SherpaOnnxModelPaths.isReady` ne peut jamais passer → repli silencieux | `SherpaOnnxVoiceModelDownloadService.kt:33-46` (TODO extraction), `SherpaOnnxModelPaths.kt:44-46`, `SherpaOnnxTtsEngine.kt:92-93` (`check(isReady)`), `SettingsScreen.kt:777` (« installée »), `FallbackTtsEngine.kt:48-57` (repli silencieux) | **Tranché** : voix neuronale **différée de la v1.0.0** (voir §4.1) ; UI rendue honnête, prompt du Reader retiré, moteur par défaut → voix système |
| B3 | **Bloquant** | Écran d'erreur du Reader : bouton « Retour à la bibliothèque » qui **naviguait nulle part** (simple `DismissError`, laissant un écran vide) ; KDoc promettant « Réessayer et Retour » | `ReaderScreen.kt:1274-1292` (avant), `ReaderViewModel.kt:247`, KDoc `ReaderScreen.kt:342-343` | **Corrigé** : deux boutons réels — « Réessayer » (`RetryOpen`, relance l'ouverture) et « Retour à la bibliothèque » (`onBack` réel) |
| M1 | Majeur | Recherche sans résultat affichée comme **bibliothèque vide** (avec CTA d'import trompeur) | `LibraryScreen.kt:232-235` + `EmptyState` (l.863-888, sans `searchQuery`) | **Corrigé** : état « Aucun résultat pour votre recherche », sans CTA d'import |
| M2 | Majeur | Moteur Sherpa sélectionnable sans modèle, repli silencieux, « Voix active » affichant « ff_siwis · Kokoro » | `SettingsScreen.kt:515-518`, `:1160`, `FallbackTtsEngine.kt:48-57` | **Tranché avec B2** : libellés honnêtes (« à venir », « voix du système utilisée ») |
| M3 | Majeur | Détail Série/Tag : aucun état vide ni chargement (écran blanc) | `LibraryDetailScreen.kt:139-155`, `LibraryDetailViewModel.kt:73-87` | **Corrigé** : spinner + « Aucun livre dans cette sélection » |
| M4 | Majeur | OPDS : échec de chargement de flux **sans bouton Réessayer** | `OpdsFeedScreen.kt:67,86-99`, `OpdsViewModel.kt:183-186` | **Non corrigé** (rapide) — backlog, action recommandée : ajouter « Réessayer » |
| M5 | Majeur | Marque-pages & Notes : **écran blanc** pendant le chargement (`isLoading -> Unit`) | `LibraryItemsScreen.kt:134-136`, `LibraryItemsUiState.kt:20` | **Corrigé** : `CircularProgressIndicator` |
| M6 | Majeur | Bouton « Sommaire » actif pour PDF/TXT → feuille vide sans message (TOC vide) | `PdfPublicationParser.kt:115`, `TxtPublicationParser.kt:82`, `UnifiedControlPanel.kt:107` | **Corrigé** : bouton masqué hors EPUB (`showToc`) |

Mineurs relevés (backlog, non corrigés ici) : chaînes en dur (aucun
`strings.xml` — à externaliser au moins les écrans principaux), Récents
sans spinner, statistiques sans état « aucune donnée », FAB d'import
jamais câblé (slots vides), écritures Room (favori/épingle/suppression)
sans catch ni snackbar, `SearchViewModel` sans catch, couvertures sans
try/catch (`isRegeneratingCovers` bloqué si échec), OPDS `LoopDetected`
silencieux, pas de reprise de téléchargement après coupure, Récents vide
sans CTA.

### 3.2 Robustesse (axe 2)

| # | Sév. | Constat | Preuve | Action |
|---|---|---|---|---|
| R0 | **Bloquant** | **4 use cases morts** contenant `TODO(...)` qui lèveraient `NotImplementedError` s'ils étaient appelés — reliquats Phase 1 jamais branchés (le Reader passe par `ReaderViewModel.openPublication` + `GetReadingStateUseCase`/`UpdateReadingStateUseCase` réels) | `OpenPublicationUseCase.kt`, `StartAudioReadingUseCase.kt`, `PauseAudioReadingUseCase.kt`, `ResumeReadingUseCase.kt` (aucune référence nulle part, vérifié) | **Corrigé** : supprimés |
| OK | — | Import : résultats typés (DRM / corrompu / format non supporté / doublon), indexation best-effort documentée, permission SAF persistée avant insertion | `ImportPublicationUseCase.kt:58-122` | Conforme |
| OK | — | Repli TTS Palier 2→1 sans crash, capacités reflétant le moteur réel | `FallbackTtsEngine.kt` | Conforme (la transparence de l'état fait défaut — traité en B2/M2) |

### 3.3 Performance premier usage (axe 4)

| # | Sév. | Constat | Preuve | Action |
|---|---|---|---|---|
| P3 | Majeur | Backup export/import **sur le thread principal** : PBKDF2 120 000 itérations + AES-GCM + JSON → gel/ANR probable sur 680 | `BackupViewModel.kt:51-71` (avant), `BackupManager.kt` (aucun `withContext`), `BackupCrypto.kt:26` | **Corrigé** : `withContext(Dispatchers.IO)` |
| P5 | Majeur | Bibliothèque : `getAll()` + boucle O(n) de `computeProgressMap` **sur Main à chaque émission** du Flow Room (500 émissions pendant un import groupé) | `LibraryViewModel.kt:218`, `RecentsViewModel.kt:46`, `LibraryDetailViewModel.kt:80`, `ProgressMap.kt:12-22` | **Corrigé** : calcul déplacé sur `Dispatchers.Default` (3 ViewModels) |
| P2 | **Bloquant (release)** | Latence TTS Sherpa **~25 s pour ~4,8 s d'audio** (RTF ~4,7× sur 680) vs budget §11.2 de 1 500 ms — dépassement ×17, documenté dans le code comme « non viable en production » | `SherpaOnnxTtsEngine.kt:47-66`, Blueprint §11.2 | **Tranché avec B2** : Sherpa différé de la v1.0.0 — la voix système tient le budget |
| P4 | Majeur (release) | `TextMeasurer` Compose partagé utilisé **hors thread principal** (mesure de pagination sous `Dispatchers.Default`) — non thread-safe, risque de crash/rendu corrompu | `ChapterPaginationState.kt:167-168,225-251`, `ChapterTextMeasurer.kt:74-78` | **Non corrigé** — à traiter AVANT release avec validation device (mesure sur Main ou TextMeasurer dédié au thread de mesure) |
| OK | — | K8 (requête groupée, pas de N+1), K2 (une seule ouverture ZIP par import), premier chapitre (cache LRU 5 Mo, preload N±1/N+2), première pagination bornée, téléchargement vérifié SHA-256 + annulable, statistiques SQL ciblées, recherche debounce+FTS, ImportWorker Semaphore(4) | voir rapports de passe | Conforme (réserves P5/P4 ci-dessus) |

Mineurs (backlog) : N+1 borné du sélecteur de livre des stats, double
lecture du central directory ZIP à l'ouverture du Reader, une
`ZipInputStream` par image sur les EPUB à hrefs divergents, absence de
pagination SQL (déviation §11.2 à 1 000+ livres), 3 flux Room séparés
au cold start (négligeable), sérialisation JSON de la sync sur Main.

### 3.4 Hygiène de release (axe 5)

| # | Sév. | Constat | Preuve | Action |
|---|---|---|---|---|
| R1 | **Bloquant** | `versionName = "0.1.0"`, `versionCode = 1` | `InkToneApplicationConventionPlugin.kt:34-35` | **Corrigé** : `versionName = "1.0.0"`, `versionCode` reste 1 (rien n'a été distribué) |
| R2 | **Bloquant** | **Aucune configuration de signature release** : `keystore.properties` + `inktone-release.jks` existent mais ne sont référencés par aucun fichier de build → `assembleRelease` produit un artifact non signable | grep `signingConfig` → 0 résultat ; `app/build.gradle.kts` sans `signingConfigs`/`buildTypes` | **Corrigé** : `signingConfigs.release` + `buildTypes.release` câblés, lecture **conditionnelle** de `keystore.properties` (absent en CI → build vert, release non signée explicite) |
| R3 | Majeur | R8/minify/shrinkResources **jamais activés** : AAB mesuré à 196 Mo vs budget Blueprint §11.2 ≤ 60 Mo | aucun `isMinifyEnabled`/`shrinkResources` ; commentaire `app/build.gradle.kts` | **Différé déclaré** (§4.2) : activation risquée sans passe dédiée + validation device des règles proguard Readium/onnxruntime. `isMinifyEnabled=false` désormais **explicite** dans le buildType |
| R4 | Majeur | Migration **17→18 sans test `MigrationTestHelper`** — seule transition non couverte (violation K4, Blueprint §14.5) | `DatabaseMigrationTest.kt` (26 tests, aucun 17→18), `Migrations.kt:236-241` | **Corrigé** : test ajouté (index `startedAt` + correction des sessions AUDIO) — **83/83 tests de migration verts sur device** (V2206) |
| R4bis | Majeur | Découverte en exécution device : le test **16→17** encodait l'ANCIEN comportement (session AUDIO migrée vers `visualDurationMs`) contredit par la migration corrigée (AUDIO → `ttsDurationMs`, complétée par 17→18) — échec latent jamais vu (tests instrumentés hors CI) | `DatabaseMigrationTest.kt` (assertions s2), `Migrations.kt:225-226` | **Corrigé** : assertions alignées sur le comportement réel, re-exécuté sur device |
| OK | — | Schéma Room v26 == dernière migration ; pas de `fallbackToDestructiveMigration` ; WAL (K1) ; abiFilters arm64-v8a ; secrets gitignorés et non trackés ; CI = build + emoji + SAF + koverVerify ; tests instrumentés hors CI, limite honnête | `InkToneDatabase.kt:45`, `DatabaseModule.kt:47-50`, `ci.yml` | Conforme |
| m | Mineur | `THIRD_PARTY_NOTICES.md` non exhaustif (Coil, jsoup, kotlinx-serialization, WorkManager, security-crypto **alpha**, desugar absents) | `THIRD_PARTY_NOTICES.md` vs `gradle/libs.versions.toml` | Backlog — à compléter avant publication Play |
| m | Mineur | README : « 25 ADR » (26 réels), « 38 use cases » (37, dont un fichier non-use-case ; **33 après suppression des 4 morts**) | `README.md:205,137` | **Corrigé** (26 ADR, 33 use cases) |

### 3.5 Parcours premier lancement (axe 1) — vérifié sur device

| Étape | Résultat vérifié (V2206) | Preuve |
|---|---|---|
| Premier lancement → onboarding | ✅ « Bienvenue sur InkTone », « Passer » ; splash couvre le chargement de la préférence (pas de flash) | dump UI device ; `MainActivity.kt:49-75` |
| Onboarding → bibliothèque vide | ✅ « Votre bibliothèque est vide » + CTA « Importer votre premier livre » | dump UI device ; `LibraryScreen.kt:232-235` |
| Import (SAF) | ✅ Le CTA ouvre `documentsui` PickActivity (EPUB/PDF) | dump UI device |
| Import réel | ✅ « The Magicians » (EPUB réel du device) importé, couverture + « Non commencé » dans la bibliothèque, `ImportWorker` → SUCCESS | logcat WM ; dump UI |
| Ouverture du lecteur | ✅ « Chapitre 1 (1/1) », progression 0,0 %, chrome lisible (Sommaire, Marque-pages, Lire…) | dump UI device |
| Lecture TTS | ✅ Bouton « Lire » → état « Pause » (lecture engagée via repli voix système) | dump UI device ; `PlaybackOrchestrator` |

---

## 4. Décisions actées

### 4.1 Voix neuronale Sherpa-ONNX différée de la v1.0.0 (B2/P2)

> **Résolu par le Lot 20** (`LOT_20_RESTAURATION_SHERPA_UPMC.md`) : la
> voix neuronale est réintégrée à la v1.0.0 sur le modèle
> `vits-piper-fr_FR-upmc-medium` (2 voix jessica/pierre, RTF ~0,8 mesuré
> par le legacy — le facteur de latence Kokoro disparaît), avec
> **l'extraction tar.bz2 et la distribution du modèle CTC réellement
> câblées**. Les trois blocages ci-dessous restent le registre des faits
> qui ont motivé le différé, tous traités par le Lot 20.

Le moteur Sherpa-ONNX (Kokoro) a été validé sur device avec des modèles
placés manuellement (Phase 5), mais **trois conditions l'empêchaient
d'être livré en v1.0.0** :

1. **Extraction** : l'asset téléchargé est une archive `.tar.bz2` (~126
   Mo, SHA-256 vérifié) jamais extraite — `model.int8.onnx`,
   `voices.bin`, `tokens.txt`, `espeak-ng-data/` n'existent donc jamais
   sur l'appareil (`SherpaOnnxModelPaths.isReady` toujours faux).
2. **Modèle CTC** : le modèle d'alignement forcé (NeMo FastConformer
   CTC) n'a aucune distribution câblée (`CtcModelPaths.isReady` jamais
   satisfait) — le surlignage mot à mot du moteur ne peut pas tourner.
3. **Latence** : ~25 s de synthèse pour ~4,8 s d'audio sur le Snapdragon
   680 (RTF ~4,7×) — dépassement ×17 du budget §11.2, documenté dans le
   code comme « non viable en production ».

Conséquence : la v1.0.0 livre la **voix du système** comme voix réelle
(hors ligne, surlignage mot à mot via `onRangeStart`). Les changements
de cette passe : moteur par défaut → `ANDROID_NATIVE`, libellés
honnêtes, suppression du bouton de téléchargement inexploitable et du
dialogue de proposition du Reader, README réécrit. La mécanique de
téléchargement (downloader + vérification + tests) reste en place comme
infrastructure pour le câblage futur. **Recommandation** : avant toute
ré-introduction, résoudre l'extraction (ex. Apache Commons Compress), la
distribution du modèle CTC, et ré-arbitrer le budget de latence par ADR
(le code l'exige déjà, `SherpaOnnxTtsEngine.kt:62-66`).

### 4.2 R8/minify différé (R3)

L'AAB (196 Mo) dépasse le budget interne (60 Mo) mais reste sous la
limite Play. Activer R8 sans passe dédiée (règles proguard pour Readium,
onnxruntime, AppAuth) et sans validation device complète serait un
risque de régression pire que la taille. `isMinifyEnabled=false` est
désormais **explicite** ; la tâche de mise en conformité est
documentée comme blocker technique post-v1.0.0.

### 4.3 À traiter AVANT release (validés sur device)

- **P4** — `TextMeasurer` utilisé hors thread principal (pagination) :
  risque de crash cross-thread. Fix recommandé : mesure de la première
  page et élargissements sur Main (coût borné par le batching 10 000
  caractères), ou `TextMeasurer` instancié sur le thread de mesure ;
  valider ensuite sur device.
- **M4** — OPDS : ajouter « Réessayer » sur l'état d'erreur de flux.
- **R3** — décision formelle sur R8 (ADR) si la taille AAB devient
  gênante.
- Compléter `THIRD_PARTY_NOTICES.md` (notamment `security-crypto`
  alpha, jsoup, Coil) avant publication.

---

## 5. Corrections effectuées dans cette passe

1. `build-logic/.../InkToneApplicationConventionPlugin.kt` — version
   1.0.0 + `signingConfigs.release`/`buildTypes.release` conditionnels.
2. Suppression des 4 use cases morts (`domain/usecase`).
3. `feature/settings` — bouton « Écouter un extrait » **ré-implémenté**
   (`PlayPreview` recâblé : synthèse + lecture d'une phrase d'exemple
   via les contrats `TtsEngine`/`AudioPlayer`, état `isPreviewing`/
   `previewError`, 3 tests) ; UI vocale honnête (note « à venir »,
   libellés Sherpa, description moteur) ; `formatMegabytes` mort retiré.
4. `feature/reader` — prompt de téléchargement de voix retiré (état,
   intent, dialogue) ; écran d'erreur : « Réessayer » (`RetryOpen`) +
   « Retour à la bibliothèque » réel ; bouton Sommaire masqué hors
   EPUB.
5. `domain/UserPreferences` — moteur TTS par défaut `ANDROID_NATIVE`.
6. `app/BackupViewModel` — export/import sur `Dispatchers.IO`.
7. `feature/library` — états vide/chargement (recherche, détail
   Série/Tag, Marque-pages & Notes) ; `computeProgressMap` hors Main
   (3 ViewModels).
8. `infrastructure/database` — test de migration 17→18 ajouté (K4) +
   test 16→17 corrigé (il encodait le comportement pré-correction).
9. `README.md` — 26 ADR, 33 use cases, section Narration vocale honnête.
10. `UX_FLOW_DESIGN.md` — note de consolidation sur « Écouter un
    extrait » (ré-implémenté).

**Vérification finale :** `./gradlew build` vert après corrections ;
**83/83 tests de migration exécutés et verts sur device** (V2206) ;
UI vocale honnête vérifiée sur device (Réglages → Lecture).

---

## 6. Points conformes (non retouchés, vérifiés)

- Onboarding (Passer/Commencer câblés), bibliothèque (shimmer, erreur
  avec Réessayer, bannière d'import, résumé par fichier), recherche
  (spinner + « Aucun résultat »), OPDS (dashboard + erreurs en
  snackbar), sync (AlertDialog d'échec, conflits, auto-sync), K8/K2/K1,
  erreurs d'import typées, SHA-256 avant usage, stats SQL, WAL, SAF
  exclusif, absence d'emoji (K12), architecture rules vérifiées par le
  build.
