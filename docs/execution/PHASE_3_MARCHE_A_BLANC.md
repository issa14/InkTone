# Phase 3 — Marche à blanc : résultats du test manuel (Tâche 3.7)

Ce document rapporte les résultats du test de bout en bout manuel exécuté
sur device réel (V2206, Android 14) pour la Tâche 3.7 de
`PHASE_3_TASKS_3.3-3.7.md`, et présente — sans la trancher — la question
que ce document demande explicitement de soumettre à Issa.

## Procédure exécutée

1. Installation de l'app avec `ReaderScreen` chargeant la phrase fixture
   « Bonjour, ceci est un test de synchronisation. » — OK.
2. Appui sur « Lire », vérification visuelle (captures d'écran) que le
   surlignage suit mot à mot la synthèse — OK, le surlignage progresse
   correctement de « Bonjour » jusqu'à « synchronisation ».
3. Arrêt forcé de l'app (`adb shell am force-stop com.inktone.app`, pas
   une simple mise en arrière-plan) après lecture complète de la phrase
   — OK.
4. Relance de l'app, vérification que `ReadingState` restaure la
   position exacte via log — OK, confirmé par capture logcat en direct
   (les tentatives par snapshot `adb logcat -d` ne montraient rien à
   cause d'une rotation de buffer sur ce device, pas d'un bug réel) :
   ```
   I/ReaderViewModel(15287): K3 - position restauree: resourceHref=OEBPS/chapter1.xhtml
   chapterIndex=0 charOffset=0 lastReadAt=1785103371493
   ```
   Valeur confirmée identique à celle persistée en base (vérifiée par
   requête `sqlite3` directe sur `inktone.db`/`inktone.db-wal` extraits
   du device après l'étape 2).
5. Qualité vocale perçue du Palier 1 (Android natif) — **non évaluable
   dans cette marche à blanc**. Le `ReaderViewModel` actuel ne joue pas
   l'audio réel : il rejoue les `WordTimestamp` renvoyés par
   `AndroidNativeTtsEngine.synthesize()` via un minuteur (`delay()`)
   pour piloter le surlignage, sans lecture du flux audio
   (`MediaPlayer`/`AudioTrack` sur `segment.audioData`). C'est un choix
   assumé de portée pour la marche à blanc (valider la chaîne
   `Locator → surlignage → reprise`, pas la lecture audio elle-même) —
   voir commentaire dans `ReaderViewModel.playCurrentSentence()`. La
   lecture audio réelle est prévue avec `AudioPlaybackService` en
   Phase 5. Aucune conclusion sur la qualité vocale du moteur natif
   Android ne peut donc être tirée de ce test.

## Bugs réels trouvés pendant l'exécution (pas supposés)

- `Publication.content()` renvoyait `null` : aucun `ContentService`
  n'était enregistré sur le `PublicationOpener`. Corrigé en ajoutant un
  bloc `onCreatePublication` avec
  `DefaultContentService.createFactory(listOf(HtmlResourceContentIterator.Factory()))`.
- Comparaison `link.href == element.locator.href` toujours fausse :
  `link.href` est du type `Href`, pas `Url`. Corrigé avec
  `link.href.resolve()`.
- `SQLiteConstraintException: FOREIGN KEY constraint failed` — trouvé
  deux fois (dans `ReadingResumeTest` et dans `ReaderViewModel` réel) :
  `ReadingStateEntity` a une FK vers `PublicationEntity`, jamais
  insérée en amont. Corrigé par un `publicationRepository.insert(...)`
  de bootstrap explicite avant la sauvegarde de l'état de lecture,
  documenté en commentaire comme scaffolding de marche à blanc à
  retirer en Phase 4.
- `error.NonExistentClass` (KSP) lors de l'injection par champ de
  `PublicationRepository` dans `MainActivity` — cause racine non
  identifiée (l'injection par constructeur du même type dans un
  `@HiltViewModel` fonctionne). Contourné en déplaçant la dépendance
  vers le constructeur de `ReaderViewModel`. À signaler si le problème
  ressurgit ailleurs, sans le considérer résolu en profondeur.
- Écart de configuration entre `InkToneFeatureConventionPlugin` et
  `InkToneAndroidLibraryConventionPlugin` : le premier ne configurait
  pas `testInstrumentationRunner`/`androidx.test:runner`, correctif déjà
  appliqué au second en Phase 2 mais oublié ici. Corrigé
  centralement dans le convention plugin.
- Emplacement de fichier du plan (Tâche 3.3) : le doc plaçait
  `ReadiumLocatorMapper.kt` dans `data/`, ce qui viole l'encapsulation
  Readium à `infrastructure/parser` (ADR-011). Placé dans
  `infrastructure/parser` à la place.

## Décision (Issa, 2026-07-27)

Palier 1 + Palier 2 tous deux dans le périmètre v1. Le Palier 1
(Android natif) reste la base de repli (ADR-021, détection au
runtime) ; le Palier 2 (Sherpa-ONNX + alignement forcé CTC) est
développé en Phase 5, pas différé en évolution future.

Ce qui est acquis avant cette décision :

- Validé empiriquement : la chaîne complète `TextToSpeech.onRangeStart
  → WordTimestamp → surlignage synchronisé → Locator → persistance K3
  → reprise` fonctionne de bout en bout avec le Palier 1 (moteur natif
  Android), sur device réel, lecture audio comprise (Tâche 3.8).
- Le Palier 2 (Sherpa-ONNX + alignement forcé CTC, ADR-021) reste non
  implémenté — c'est le travail que cette décision ouvre. Le contrat
  de domaine (`TtsEngine`, `WordTimestamp`, `AudioSegment`) ne change
  pas : seule l'infrastructure change (ADR-021, section Consequences).

## Checklist finale de sortie de Phase 3

- [x] Tâche 3.3 : mapping `Locator` → `Locator` Readium minimal, testé
      (androidTest, `Url(String)` nécessite `android.net.Uri`).
- [x] Tâche 3.4 : extraction du `DocumentModel` depuis une `Publication`
      Readium — `ContentService` enregistré, comparaison `Href`/`Url`
      corrigée, testé sur le fixture EPUB (Tâche 3.2).
- [x] Tâche 3.5 : squelette MVI `feature/reader` (état, intents,
      surlignage `buildAnnotatedString`), câblage Hilt (`TtsModule`,
      `UseCaseModule`).
- [x] Tâche 3.6 : test instrumenté `ReadingResumeTest` vérifiant que la
      position sauvegardée est restaurée à l'identique (bootstrap FK
      compris).
- [x] Tâche 3.7 : test manuel de bout en bout exécuté sur device réel,
      surlignage confirmé visuellement, reprise K3 confirmée par log
      + requête SQL directe, résultats documentés ci-dessus, décision
      Palier 1 vs 1+2 tranchée par Issa (voir section Décision
      ci-dessus) : Palier 1 + Palier 2 tous deux dans le périmètre v1.
- [x] Tâche 3.8 : lecture audio réelle câblée (`AudioSegmentPlayer`,
      AudioTrack sur PCM brut) et vérifiée sur device réel — qualité
      vocale jugée insuffisante seule (voir section Décision
      ci-dessus), Palier 2 requis en Phase 5.
