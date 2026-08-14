# Lot 15 — Pipeline audio gapless (lecture fluide + fix crash), surlignage intact

**Base :** `main`. Références : `ADR-008` (Reader = composant cœur),
`ADR-021` (architecture à paliers du timing mot), `ADR-004` (abstraction TTS
capability-aware), `ADR-003` (Offline First), Blueprint §8 (en particulier
§8.7 Audio Pipeline, §8.8 Playback Control, §8.12 Error Handling),
`CLAUDE.md` §13.5 (legacy = référence de comportement, jamais copié tel quel).
Implémentation legacy de référence : `legacy/monolith` →
`app/src/main/java/com/inktone/service/audio/` (`GaplessAudioPlayer.kt`,
`PlaybackOrchestrator.kt`) et leurs tests (`GaplessAudioPlayerTest.kt`,
`PlaybackOrchestratorTest.kt`).

> **Le surlignage mot-à-mot n'est PAS touché par ce lot.** Il reste piloté par
> les `wordTimestamps` comme aujourd'hui (mécanisme `delay()`). Sa
> synchronisation « par position réelle » est un chantier séparé, plus risqué
> et non prouvé par le legacy : **LOT 16** (spike d'abord).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu
vérifier, la clôture se fait sur appareil.

## Motif (pourquoi ce lot existe)

L'état actuel (`feature/reader/AudioSegmentPlayer.kt`, corrigé au Lot 14) lit
une phrase à la fois en `AudioTrack.MODE_STATIC`, avec un `play()` **bloquant**
et une notification de fin par callback natif (`OnPlaybackPositionUpdateListener`).
Deux défauts structurels en découlent :

1. **Course SIGSEGV latente** : le callback `onMarkerReached` (thread natif
   AudioTrack) libère le track pendant que la coroutine d'annulation peut
   aussi le libérer — double `release()` possible, crash natif intermittent
   (déjà observé). Le legacy a résolu ceci avec un verrou (`ReentrantLock`)
   + un flag atomique (`willStop`) autour des écritures et de la libération.
2. **Pas de gapless** : une phrase = un track jetable recréé à chaque fois ;
   la synthèse de la phrase n+1 ne peut pas chevaucher la lecture de la phrase
   n de façon garantie, et le silence inter-phrases dépend d'un `delay()` à
   l'issue du `play()`, pas de l'écoulement réel de l'audio.

Ce lot remplace `AudioSegmentPlayer` (et le préchargement ad hoc
`SentenceAudioBuffer`) par un **pipeline gapless** : un lecteur `AudioTrack`
`MODE_STREAM` unique alimenté par une file non-bloquante, et un ordonnanceur
producteur/consommateur minimal. Le surlignage actuel est **conservé tel quel**,
simplement déclenché par le début de phrase de l'ordonnanceur.

## Périmètre hors de ce lot (déclaré maintenant, pas « on essaie »)

- **Reprise au mot exact** (§8.9 règle 3) : non fiable en `AudioTrack`
  `MODE_STREAM` intra-segment. Reprise **à la phrase**, comme le legacy et
  l'actuel. La reprise au mot est un écart déclaré.
- **Changement de vitesse en cours de lecture** : les timestamps des segments
  déjà bufferisés deviennent périmés. Politique conservée : la vitesse est
  lue au début (profil vocal) ; un changement en cours = stop + relance. Écart
  déclaré.
- **Surlignage par position réelle** : reporté au **LOT 16** (spike d'abord).
- **PreWarm du chapitre suivant**, **timeouts/seuils adaptatifs par moteur**,
  **sleep timer dans l'ordonnanceur** : non portés. La politique d'erreur
  réseau reste portée par `SelectiveTtsEngine`/`FallbackTtsEngine` (Lot 14).

## Écarts délibérés par rapport au legacy

Le code legacy est une référence de comportement, pas un patron à copier.
Cinq écarts sont actés — à ne pas « corriger en sens inverse » en exécution :

1. **PCM16 `ByteArray`, pas `FloatArray`.** Le legacy consomme
   `SynthesisResult.samples: FloatArray` et convertit en `Short` avec un gain
   3× (compensation du volume natif faible du modèle Piper VITS). Le contrat
   actuel est `AudioSegment.audioData: ByteArray` PCM16 signé little-endian
   (voir `domain/service/TtsEngine.kt`) : le lecteur gapless écrit ce PCM16
   directement. **Aucune conversion Float→Short, aucun gain.**
2. **Surlignage conservé, pas de portage du polling.** Le legacy pollait
   `completedCount` au niveau phrase. Le courant fait du mot-à-mot par `delay()`
   sur les `wordTimestamps` — **conservé tel quel**. Le lot ne branche pas le
   surlignage sur la position `AudioTrack` (reporté au LOT 16).
3. **Placement en `infrastructure/media` via une interface domaine.** Le legacy
   regroupe lecteur + orchestration dans un package `service/audio` du monolithe.
   Ici, le lecteur (`GaplessAudioPlayer`) appartient à `infrastructure/media`
   (Blueprint §5.2), et `feature/reader` ne le consomme qu'à travers une
   interface `domain` (`AudioPlayer`) — sens des dépendances
   `Presentation → Application → Domain ← Data ← Infrastructure` non négociable.
4. **Ordonnanceur borné, pas de duplication de la politique d'erreur.** Le
   legacy adapte timeouts et seuils d'erreurs par moteur (ONNX 4 s/3, Edge
   20 s/8) et porte preWarm + sleep timer. Ici, le repli moteur est déjà porté
   par `SelectiveTtsEngine` (Lot 14) et `FallbackTtsEngine` : l'ordonnanceur du
   Lot 15 ne **réimplémente pas** cette politique, ni le preWarm, ni le sleep
   timer (voir §Périmètre). Timeout de synthèse unique + signalisation simple.
5. **Audio focus : à arbitrer, pas reconduit tel quel.** Le legacy a un
   `AudioFocusManager` maison. Le code actuel embarque déjà Media3
   (`AudioPlaybackService`, `MediaSession`) qui gère le focus. Le lot tranche
   entre « réutiliser le focus Media3 » et « écart déclaré », sans réécrire un
   gestionnaire de focus.

## Décisions actées (à valider en Palier 0)

1. **Lecteur = `AudioTrack` `MODE_STREAM`, pas ExoPlayer.** Le gapless
   byte-à-byte exige le contrôle direct du flux PCM qu'ExoPlayer n'expose pas.
   Media3 (`AudioPlaybackService`) reste l'enveloppe service/notification
   (§8.8) — l'intégration lecteur↔MediaSession est hors périmètre, signalée.
2. **Contrat domaine `AudioPlayer`** (nouveau, `domain/service/AudioPlayer.kt`) :
   `enqueue(AudioSegment)`, `play()`, `pause()`, `resume()`, `stop()`,
   `release()`, `setVolume(Float)`, `sampleRate: Int` mutable, `state:
   StateFlow<PlayerState>`, `pendingCount: Int`. **Pas de flux de position**
   (inutile tant que le surlignage n'est pas rebranché — LOT 16). Implémenté
   par `GaplessAudioPlayer` (`infrastructure/media`), lié en Hilt.
   `feature/reader` ne dépend que du contrat.
3. **Ordonnanceur = `PlaybackOrchestrator`** (`feature/reader`, `@Singleton`
   injecté dans `ReaderViewModel`) : producteur de synthèse (`Channel` à
   `LOOKAHEAD=3`, timeout unique) + consommateur qui enfile segments et
   silences ponctués puis `play()` au premier. Il ne connaît que `TtsEngine`
   et `AudioPlayer` (contrats domaine) — jamais `AudioTrack`, jamais un moteur
   concret.
4. **Surlignage inchangé, déclenché par le début de phrase.** L'ordonnanceur
   expose l'index de phrase courante (`StateFlow`) ; `ReaderViewModel` lance le
   même `highlightJob` `delay()` qu'aujourd'hui à chaque changement. Aucune
   modification de la mécanique de surlignage dans ce lot.
5. **Silences ponctués conservés** (legacy, validé device) : virgule 150 ms,
   fin de phrase 650 ms, paragraphe 1000 ms — injectés **dans la file**, pour
   rester gapless.

## Défaut préalable à corriger (hors code, à signaler)

Aucun défaut documentaire bloquant. Le risque SIGSEGV de l'`AudioSegmentPlayer`
actuel est **la raison d'être du lot**, pas un écart : il disparaît avec la
suppression du composant (Tâche 3.3).

## Tâche 0.1 — ADR du pipeline playback

Rédiger `docs/adr/ADR-025-playback-gapless.md` : lecteur `AudioTrack`
`MODE_STREAM` vs ExoPlayer, placement `infrastructure/media` + contrat
`AudioPlayer`, **périmètre sans surlignage** (reporté au LOT 16), gestion du
focus (Media3 vs écart). Le faire accepter par Issa avant toute ligne de code.

## Tâche 0.2 — Ligne de base device (avant tout code)

Capturer le comportement ACTUEL sur device, pour détecter toute régression :
lecture fluide ou trous, surlignage correct ou décalé, absence de crash au
stop/tap répété. Consigner le résultat (checklist datée) comme référence de
non-régression. Commit : `Consigne la ligne de base device avant le pipeline
gapless`.

## Tâche 1.1 — Contrat `AudioPlayer` en domain

`domain/service/AudioPlayer.kt` : `sealed interface PlayerState` (Idle,
Playing, Paused, Stopped), `enqueue`, `play`, `pause`, `resume`, `stop`,
`release`, `setVolume`, `sampleRate`, `state`, `pendingCount`. KDoc de contrat
complet. Aucune dépendance Android, **aucun flux de position** (LOT 16).
Commit : `Ajoute le contrat AudioPlayer (domain)`.

## Tâche 1.2 — `GaplessAudioPlayer` (file + verrou + PCM16)

`infrastructure/media/.../GaplessAudioPlayer.kt` : `AudioTrack` `MODE_STREAM`
unique, file non-bloquante (`ConcurrentLinkedQueue` + `Semaphore`), verrou
d'écriture (`ReentrantLock`) + flag `willStop` autour des écritures et de la
libération (anti-SIGSEGV), chunk buffer réutilisable, `sampleRate` dynamique,
écriture directe du PCM16 (écart 1). États Idle/Playing/Paused/Stopped en
`StateFlow`. Commit : `Ajoute le lecteur gapless (AudioTrack MODE_STREAM, file
+ verrou)`.

## Tâche 1.3 — Logique de file/état extraite (JVM)

Extraire machine d'états + file + synchronisation dans une classe pure (sans
`AudioTrack`) testable en JVM ; l'`AudioTrack` reste une couche I/O fine
(instrumentée). Tests : enqueue non-bloquant, transitions d'état, stop pendant
écriture. **NB : ce test ne prouve pas l'absence de SIGSEGV — la preuve du
crash est instrumentée (Tâche 2.1).** Commit : `Extrait la logique de file du
lecteur gapless (testable JVM)`.

## Tâche 1.4 — Câblage DI (Hilt)

`infrastructure/media/di/` : lier `AudioPlayer` → `GaplessAudioPlayer`
(`@Singleton`). Vérifier `checkArchitectureRules`. Commit : `Câble le lecteur
gapless dans la DI`.

## Tâche 2.1 — Test instrumenté de stress (gate avant l'ordonnanceur)

Test instrumenté de `GaplessAudioPlayer` : enchaînement de 2+ segments sans
silence audible, **stop pendant écriture** (pas de SIGSEGV), **tap/stop
répétés** (course release), pause/reprise phrase, changement de `sampleRate` à
chaud. **C'est la porte d'entrée du lot** : pas d'ordonnanceur avant que ce
test passe sur device. Commit : `Teste le lecteur gapless sur device (stress
anti-SIGSEGV)`.

## Tâche 2.2 — Checklist device lecteur seul

Vérifier sur appareil le lecteur isolé (2+ phrases fluides, pause/reprise,
volume, sampleRate Edge + Sherpa). Résultat consigné. La clôture de cette
étape est un acte d'Issa.

## Tâche 3.1 — `PlaybackOrchestrator` borné (producteur/consommateur)

`feature/reader/.../PlaybackOrchestrator.kt` (`@Singleton`) : `play(sentences,
voiceProfile, startFrom)` lance un producteur (`Channel` à `LOOKAHEAD=3`,
timeout de synthèse unique) + un consommateur qui enfile chaque `AudioSegment`
+ silence ponctué puis `play()` au premier. `playGeneration` (AtomicLong)
contre les courses d'arrêt/relance. État Idle/Buffering/Playing/Paused/Error en
`StateFlow`. **Pas de preWarm, pas de seuils adaptatifs.** Commit : `Ajoute
l'ordonnanceur de lecture (producteur/consommateur)`.

## Tâche 3.2 — Pause, reprise, stop, progression (phrase)

`pause()`/`resume()`/`stop()` sérialisés par un verrou, **reprise à la phrase**
(pas au mot — écart déclaré), sauvegarde de progression via
`UpdateReadingStateUseCase` au changement de phrase, Idle en fin de chapitre.
Commit : `Ajoute pause/reprise/stop et progression à l'ordonnanceur`.

## Tâche 3.3 — Suppression du code mort (surlignage conservé)

Supprimer `AudioSegmentPlayer` et `SentenceAudioBuffer`. Le `highlightJob`
`delay()` actuel de `ReaderViewModel` est **conservé**, simplement déclenché par
l'index de phrase de l'ordonnanceur. Commit : `Supprime AudioSegmentPlayer et
SentenceAudioBuffer`.

## Tâche 3.4 — Tests de l'ordonnanceur (JVM)

Fakes `TtsEngine` + `AudioPlayer` : enchaînement avec silences dans le bon
ordre, pause/reprise conserve l'index, stop annule le producteur, erreur de
synthèse → silence court + poursuite, fin de chapitre → Idle + progression
sauvegardée. Commit : `Teste l'ordonnanceur de lecture`.

## Tâche 4.1 — Brancher `ReaderViewModel` sur l'ordonnanceur

Remplacer la boucle inline (preloadAhead + play bloquant) par
`PlaybackOrchestrator.play(…)` ; `isPlaying`/`isAudioActive`/
`currentSentenceIndex` dérivent du `StateFlow` de l'ordonnanceur. Le surlignage
reste le même `highlightJob` `delay()`. Commit : `Branche le Reader sur
l'ordonnanceur gapless`.

## Tâche 4.2 — Préserver le SleepTimer

Vérifier l'interaction existante entre le `sleepTimer` (`ReaderUiState`) et la
boucle de lecture, et conserver le comportement (arrêt en douceur à zéro).
Aucun nouveau mécanisme. Commit : `Préserve le sleep timer à travers
l'ordonnanceur`.

## Tâche 4.3 — Vérification sur device réel

Checklist Issa : lecture fluide sans trou (≤ 150 ms perçues, §8.7), **surlignage
mot IDENTIQUE à la ligne de base (0.2)**, pause/reprise phrase, saut de phrase,
stop propre sans crash, changement de vitesse, Edge (24 kHz) et Sherpa
(22 050 Hz) fluides, pas de SIGSEGV au stop/tap répété.

## Tâche 4.4 — Build vert et garde-fous

`./gradlew build` vert (inclut `checkArchitectureRules`), `bash
scripts/check-no-emoji.sh` (K12), `bash
scripts/check-no-manage-external-storage.sh` (K5). Aucune régression des tests
existants. Commit : `Assure le build vert et les garde-fous du pipeline
gapless`.
