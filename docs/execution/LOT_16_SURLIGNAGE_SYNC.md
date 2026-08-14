# Lot 16 — Surlignage mot synchronisé sur la position réelle

**Base :** `main` (après LOT 15). Références : Blueprint §8.9 (Word-Level
Synchronization), `ADR-021` (architecture à paliers du timing mot), `ADR-025`
(gapless). Legacy : **aucune** — le legacy n'a jamais fait de surlignage mot par
position ; il pollait `completedCount` au niveau phrase.

> **Verdict du spike : POSITIF** (`docs/execution/SPIKE_SURLIGNAGE_POSITION.md`,
> device V2206, 2026-08-14). `AudioTrack.getTimestamp()` est valide (48-49/50
> échantillons) et fournit une position jouée **sans dérive** sur 22 050 Hz et
> 24 000 Hz (offset initial constant ~130 ms = latence de démarrage,
> compensable). `getPlaybackHeadPosition()` est lui aussi précis (~±20 ms) sur
> ce device — utilisable en repli. Le lot suit donc le chemin **positif** ; le
> repli `delay()` actuel reste le dernier recours.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu
vérifier, la clôture se fait sur appareil.

## Périmètre

Surlignage mot piloté par la position réelle du lecteur (`getTimestamp()`), avec
**deux replis** : `getPlaybackHeadPosition()` puis le mécanisme `delay()` actuel
si la position est invalide. Le mécanisme `delay()` n'est **pas supprimé** — il
devient le filet de sécurité.

Hors périmètre (écarts déclarés) :
- Reprise au mot exact (non fiable en MODE_STREAM intra-segment, inchangé).
- Synchronisation des **annotations/signets** sur la position (non demandée).

## Palier 1 — Contrat position dans `AudioPlayer`

### Tâche 1.1 — `PlaybackPosition` + flux dans le contrat (domain)

`domain/service/AudioPlayer.kt` : ajouter un flux de position —
`val playbackPosition: StateFlow<PlaybackPosition>` — où `PlaybackPosition` est
un value object (`playedFrame: Long`, `sampleRate: Int`, `timestampNanos: Long?`,
`valid: Boolean`). KDoc de contrat complet. C'est l'extension du contrat décidée
par le spike (remplace la note « aucun flux de position » d'ADR-025).
Commit : `Ajoute le flux de position au contrat AudioPlayer`.

### Tâche 1.2 — Implémentation `getTimestamp()` (infrastructure/media)

`GaplessAudioPlayer` : émettre `playbackPosition` depuis le cœur de lecture
(`GaplessPlaybackCore`) — un échantillonneur lit `AudioTrack.getTimestamp()`
(frame présenté + horodatage) et calcule la frame jouée courante ; repli sur
`getPlaybackHeadPosition()` si `getTimestamp()` retourne `false` ; `valid=false`
si le track n'existe pas ou est arrêté. **La couche I/O `AudioTrack` reste fine
et instrumentée.**
Commit : `Émet la position jouée (getTimestamp) dans le lecteur gapless`.

### Tâche 1.3 — Tests

JVM : logique de calcul de la frame jouée (à partir d'un `getTimestamp()` mocké)
extraite et testée. Instrumenté : la position émise ne dérive pas (5 s, deux
sample rates) — même protocole que le spike, en assertion.
Commit : `Teste le flux de position du lecteur gapless`.

## Palier 2 — Surlignage rebranché sur la position

### Tâche 2.1 — Ordonnanceur : mot courant déduit de la position

`PlaybackOrchestrator` : exposer le mot courant (ou l'intervalle de caractères à
surligner) déduit de `audioPlayer.playbackPosition` + `wordTimestamps` de la
phrase courante. `delay()`-based reste en repli quand `valid == false`.
Commit : `Déduit le mot courant de la position du lecteur`.

### Tâche 2.2 — Reader : surlignage par position (repli conservé)

`ReaderViewModel` : remplacer le `highlightJob` `delay()` par la collecte du mot
courant de l'ordonnanceur. Le repli `delay()` actuel reste déclenché si la
position est invalide (le `startWordHighlight` actuel devient le repli).
Commit : `Branche le surlignage sur la position réelle`.

### Tâche 2.3 — Tests

JVM (fakes) : position → mot courant correct ; position invalide → repli `delay()`.
Commit : `Teste le surlignage par position et son repli`.

## Palier 3 — Vérification device

Checklist Issa (à consigner) :
- Surlignage mot **synchronisé** (pas d'avance/retard, pas de dérive) sur un
  chapitre complet.
- Edge 24 kHz **et** Sherpa 22 050 Hz.
- Repli `delay()` : si la position devient invalide (ex. pause), le surlignage
  reste lisible.
- Stop/tap répétés : pas de crash (non-régression LOT 15).
- `./gradlew build` vert + garde-fous K5/K12.

## Écarts déclarés (d'emblée)

- La **reprise au mot exact** reste non fiable en MODE_STREAM intra-segment
  (inchangé depuis ADR-025).
- La position est un estimateur de la frame jouée, pas une vérité absolue : le
  critère de réussite device est « synchronisé à l'oreille, sans dérive », pas
  « exact au frame ».
