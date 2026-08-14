# SPIKE_SURLIGNAGE_POSITION.md — Protocole de validation

**Objet :** la position `AudioTrack` peut-elle piloter un surlignage mot fiable ?
**Lot :** 16 (surlignage synchronisé sur la position réelle). **Sans preuve legacy.**
**Appareil cible :** Snapdragon 680 (V2206), Android 14.

## 1. Objectif

Mesurer, sur device, la fiabilité des deux API de position d'`AudioTrack` en
`MODE_STREAM` :

- `getPlaybackHeadPosition()` — position en frames (unsigned 32-bit, **wrap sur
  2³²**), réputée rapporter les frames *écrits* (pas *joués*) en MODE_STREAM.
- `getTimestamp(AudioTimestamp)` — position **corrigée de la latence** : le
  frame `framePosition` a été présenté à la sortie audio à l'instant
  `nanoTime`. C'est l'API candidate pour un surlignage « position réelle ».

La question n'est pas « laquelle est exacte dans l'absolu », mais : **peut-on en
déduire le mot courant, avec un décalage perçu ≤ 80 ms et sans dérive
cumulative**, sur les deux sample rates de production (Edge 24 kHz, Sherpa
22 050 Hz) ?

## 2. Conditions et prérequis

- `V2206` (Android 14), build debug de `infrastructure/media`.
- PCM de test **connu** : 5 sinus de 1 s chacun, fréquences distinctes
  (440/550/660/770/880 Hz) → 5 « mots » de 1 s, horodatage théorique connu.
- `AudioTrack` `MODE_STREAM` **brut** (pas `GaplessAudioPlayer`, qui n'expose
  volontairement aucune position — c'est ce que ce spike décide d'ajouter ou
  non au contrat `AudioPlayer`).

## 3. Protocole exact (à ne pas improviser)

1. Construire un `AudioTrack` `MODE_STREAM`, buffer = 1 s, usage MEDIA/SPEECH.
2. `track.play()` **avant** l'écriture, puis noter `startNanos = System.nanoTime()`.
3. Écrire le PCM complet (5 s) dans un thread séparé (`write` bloquant — il se
   cale naturellement sur la vitesse de lecture).
4. Pendant la lecture, échantillonner **toutes les 100 ms** :
   - `elapsedMs = (now - startNanos) / 1_000_000` ;
   - `head = track.playbackHeadPosition` ;
   - `hasTs = track.getTimestamp(ts)` (framePosition, nanoTime) ;
   - `expectedFrames = elapsedMs * sampleRate / 1000` ;
   - `headErrorMs = (head - expectedFrames) * 1000 / sampleRate` ;
   - `tsErrorMs = (ts.framePosition - expectedFrames) * 1000 / sampleRate` (si `hasTs`).
5. Exécuter le protocole **deux fois** : 22 050 Hz puis 24 000 Hz.
6. Consigner chaque échantillon en logcat (tag `HeadPosSpike`).

## 4. Partie A — `getPlaybackHeadPosition()`

Répondre : l'erreur `headErrorMs` est-elle bornée et stable ? (Attendu du
risque ADR-025 : elle reflète les frames *écrits*, donc **avance** sur le son,
avec une latence de buffer.)

## 5. Partie B — `getTimestamp()`

Répondre : `getTimestamp()` retourne-t-il `true` en MODE_STREAM sur ce device ?
Si oui, `tsErrorMs` est-elle **≤ 80 ms en valeur absolue** et **sans dérive
cumulative** sur 5 s ?

## 6. Critère de verdict (chiffré)

La métrique **rédhibitoire** est la **dérive cumulative** (l'erreur qui grandit
avec le temps) — pas l'offset initial, qui est une latence de démarrage
constante et compensable.

- **Positif** : `getTimestamp()` valide **et** dérive ≤ 80 ms sur 5 s aux deux
  sample rates (offset initial constant toléré). → On ajoute un flux de position
  (basé `getTimestamp()`) au contrat `AudioPlayer`, surlignage rebranché, repli
  `delay()` conservé.
- **Négatif** : `getTimestamp()` invalide, ou dérive > 80 ms. → **Écart
  déclaré** : surlignage `delay()`-based conservé, documenté.

## 7. Résultats (device V2206, 2026-08-14)

| sampleRate | getTimestamp valide ? | erreur max (ms) | dérive ? |
|---|---|---|---|
| 22 050 Hz | Oui (48/50) | 144 ms (offset constant) | Non (~1 ms) |
| 24 000 Hz | Oui (49/50) | 128 ms (offset constant) | Non (stable à -127 ms) |

`getPlaybackHeadPosition()` : ~±20 ms sur 24 kHz, ~100 ms max sur 22 050 Hz,
sans dérive — **contrairement au risque ADR-025**, sur ce device la tête suit
bien les frames *joués*, pas les frames écrits.

## 8. Verdict

`getTimestamp()` **valide** sur V2206 : **OUI** (48-49/50 échantillons, aux deux
sample rates).

`getPlaybackHeadPosition()` comportement observé : précis (~±20 ms), sans
dérive — utilisable en repli.

Décision : **POSITIF** — `getTimestamp()` fournit une position jouée **sans
**dérive** (offset initial constant ~130 ms = latence de démarrage,
compensable). La position `AudioTrack` peut piloter le surlignage mot ; la
bascule se fait sur `getTimestamp()`, avec `getPlaybackHeadPosition()` en repli
et le `delay()` actuel en dernier recours.
