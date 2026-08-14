# ADR-025 : Pipeline de lecture gapless — AudioTrack MODE_STREAM, surlignage reporté

**Status :** Accepted
**Date :** 2026-08-14

## Context

La lecture actuelle (`feature/reader/AudioSegmentPlayer.kt`) lit une phrase à
la fois en `AudioTrack.MODE_STATIC` : un `play()` **bloquant**, un track jetable
recréé à chaque phrase, et une notification de fin par callback natif
(`OnPlaybackPositionUpdateListener`). Deux défauts structurels en découlent :

1. **Course SIGSEGV latente** — le callback `onMarkerReached` (thread natif
   `AudioTrack`) libère le track pendant que la coroutine d'annulation peut
   aussi le libérer : double `release()` possible, crash natif intermittent,
   déjà observé sur device. Le legacy a résolu exactement ceci par un verrou
   (`ReentrantLock`) + un flag atomique (`willStop`) autour des écritures et de
   la libération.
2. **Pas de gapless** — la synthèse de la phrase n+1 ne chevauche pas la
   lecture de la phrase n de façon garantie, et le silence inter-phrases dépend
   d'un `delay()` après le `play()`, pas de l'écoulement réel de l'audio. Le
   Blueprint §8.7 exige un silence inter-phrases ≤ 150 ms perçues.

Le legacy (`legacy/monolith`, archivé) contient la solution : un lecteur
`GaplessAudioPlayer` (`AudioTrack` `MODE_STREAM`, file non-bloquante
`ConcurrentLinkedQueue` + `Semaphore`, verrou d'écriture) et un
`PlaybackOrchestrator` producteur/consommateur. Ce code est une **référence de
comportement**, pas un patron à copier : il consomme du `FloatArray` (incompatible
avec le `ByteArray` PCM16 du contrat `AudioSegment`), et son surlignage se limite
au niveau phrase par polling de `completedCount` — il n'a **jamais** fait de
surlignage mot par position.

Un point de fait à ne pas éluder : le surlignage mot actuel fonctionne (validé
device) par `delay()` sur les `wordTimestamps`, et n'est pas la source du crash.
Le rebrancher sur une « position réelle » du lecteur est un chantier **sans
preuve legacy** : `AudioTrack.getPlaybackHeadPosition()` en `MODE_STREAM`
rapporte des frames *écrits* (pas *joués*) et wrap sur 2³² — une source de
dérive classique.

## Decision

La lecture de publication est confiée à un **pipeline gapless**, avec un
périmètre strict qui **ne touche pas au surlignage** :

1. **Lecteur = `AudioTrack` `MODE_STREAM`, pas ExoPlayer.** Le gapless
   byte-à-byte et la synchronisation mot (§8.9) exigent le contrôle direct du
   flux PCM qu'ExoPlayer n'expose pas à ce niveau. Media3 (`AudioPlaybackService`)
   reste l'enveloppe service/notification (§8.8) ; l'intégration
   lecteur↔MediaSession est hors périmètre, signalée.

2. **Placement : `infrastructure/media`, consommé via un contrat domaine.**
   Nouvelle interface `domain/service/AudioPlayer.kt` (`enqueue(AudioSegment)`,
   `play()`, `pause()`, `resume()`, `stop()`, `release()`, `setVolume(Float)`,
   `sampleRate: Int`, `state: StateFlow<PlayerState>`, `pendingCount: Int`).
   `feature/reader` ne dépend que du contrat — sens des dépendances
   `Presentation → Application → Domain ← Data ← Infrastructure` non négociable.
   **Aucun flux de position dans ce contrat** (inutile tant que le surlignage
   n'est pas rebranché).

3. **PCM16 direct, jamais de conversion.** Le lecteur écrit le
   `AudioSegment.audioData: ByteArray` PCM16 signé little-endian directement.
   Aucune conversion Float→Short, aucun gain (le gain 3× du legacy compensait
   le volume du modèle Piper VITS, écarté par ADR-022).

4. **Ordonnanceur borné (`feature/reader`).** `PlaybackOrchestrator`
   (`@Singleton`) : producteur de synthèse (`Channel` à `LOOKAHEAD=3`, timeout
   de synthèse unique) + consommateur qui enfile segments et silences ponctués
   (virgule 150 ms, phrase 650 ms, paragraphe 1000 ms — valeurs validées device)
   puis `play()` au premier segment. Il ne connaît que `TtsEngine` et
   `AudioPlayer` (contrats domaine). **Hors périmètre** : preWarm du chapitre
   suivant, timeouts/seuils adaptatifs par moteur (la politique d'erreur reste
   portée par `SelectiveTtsEngine`/`FallbackTtsEngine`, Lot 14), sleep timer.

5. **Surlignage inchangé, reporté.** Le surlignage mot reste piloté par
   `delay()` sur les `wordTimestamps`, déclenché par l'index de phrase de
   l'ordonnanceur. Sa synchronisation « par position réelle » est **reportée au
   LOT 16**, conditionné à un spike avec critère chiffré (décalage ≤ 80 ms sans
   dérive). Aucune prétention de position réelle dans ce lot.

6. **Reprise à la phrase, pas au mot.** La reprise après pause repositionne à
   la phrase courante (comme le legacy et l'actuel). La reprise intra-segment
   « au mot exact » (§8.9 règle 3) est un **écart déclaré** — non fiable en
   `MODE_STREAM`, elle n'est pas simulée.

7. **Preuve du fix crash = test instrumenté.** La race SIGSEGV vit dans la
   couche I/O `AudioTrack` (intestable en JVM). Sa preuve est un test
   instrumenté de stress (stop pendant écriture, tap/stop répétés) passé sur
   device **avant** toute construction de l'ordonnanceur.

## Rationale

Le crash et l'absence de gapless sont deux défauts réels, prouvés, et leur
solution est prouvée par le legacy. À l'inverse, le surlignage « par position
réelle » est une ambition sans preuve : le legacy ne l'a jamais fait, et la
tête de lecture `AudioTrack` est une source de dérive documentée. Mélanger les
deux dans un même lot ferait porter au chantier le plus risqué le poids du
chantier le plus sûr — et exposerait la fonctionnalité signature (le surlignage
mot) à une régression pendant qu'on répare l'audio. Le découpage (gapless ici,
surlignage au LOT 16 après spike) est la traduction directe de cette asymétrie
de risque.

Le choix `AudioTrack` plutôt qu'ExoPlayer suit la même logique que l'ADR-021
pour le timing mot : quand l'exigence est un contrôle fin du signal, on reste au
niveau du signal, pas d'un lecteur qui abstrait le flux. ExoPlayer reste présent
pour ce qu'il sait faire (session média, notification, écran verrouillé), mais
n'est pas le moteur du gapless.

Le test instrumenté comme porte d'entrée (§7) évite de bâtir l'ordonnanceur sur
un lecteur dont la sûreté n'est pas démontrée — l'inverse (construire puis
tester) est exactement le scénario des debugs interminables que ce découpage
vise à prévenir.

## Consequences

- Nouveau contrat `domain/service/AudioPlayer.kt` + implémentation
  `infrastructure/media/GaplessAudioPlayer.kt` (Hilt, `@Singleton`).
- Nouveau `feature/reader/PlaybackOrchestrator.kt` ; suppression de
  `AudioSegmentPlayer` et `SentenceAudioBuffer` (remplacés).
- `feature/reader` cesse de dépendre d'`AudioTrack` directement (il passait par
  une classe concrète du même module) et passe par le contrat domaine.
- Aucun changement de comportement du surlignage ; le sleep timer existant est
  préservé à travers la boucle de l'ordonnanceur.
- La bascule du surlignage sur la position réelle est conditionnée au spike du
  LOT 16 — si le spike est négatif, le surlignage reste `delay()`-based et
  documenté comme tel.

## Alternatives Considered

- **ExoPlayer pour le gapless.** Rejeté : pas de contrôle byte-à-byte du flux
  PCM, indispensable à la synchronisation mot (§8.9). Media3 reste pour la
  session, pas pour le cœur de lecture.
- **Portage intégral du `PlaybackOrchestrator` legacy.** Rejeté : 824 lignes
  dont chaque section est un bug corrigé dans le temps (deadlock `Channel`,
  use-after-free, courses de génération). Porter d'un bloc = rejouer cet
  historique. On ne porte que la structure (producteur/consommateur, verrou,
  file) en périmètre borné.
- **Rebrancher le surlignage sur la position dans ce même lot.** Rejeté :
  mécanisme non prouvé par le legacy, source de dérive connue. Reporté au
  LOT 16 avec spike préalable et repli `delay()` conservé.
- **Reprise au mot exact.** Rejetée pour ce lot : non fiable en `MODE_STREAM`
  intra-segment. Écart déclaré, pas simulé.
