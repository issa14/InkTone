# Lot 16 — Surlignage mot synchronisé sur la position réelle (spike d'abord)

**Base :** après LOT 15. Références : Blueprint §8.9 (Word-Level
Synchronization), `ADR-021` (architecture à paliers du timing mot). Legacy :
**aucune** — le legacy n'a jamais fait de surlignage mot par position ; il
pollait `completedCount` au niveau phrase. Ce lot est donc **sans preuve
legacy** : c'est pourquoi il démarre par un spike.

## Statut conditionnel

Ce lot est **conditionnel au verdict du spike** (Tâche 1.1). Si le spike conclut
que la position `AudioTrack` ne peut pas piloter un surlignage mot fiable (cas
probable : `getPlaybackHeadPosition()` rapporte les frames *écrits*, pas
*joués*, et wrap sur 2³²), le lot se réduit à **l'écart déclaré** : le
surlignage reste `delay()`-based, documenté comme tel.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu
vérifier, la clôture se fait sur appareil.

## Périmètre

Surlignage mot piloté par la position réelle du lecteur, avec **repli** sur le
mécanisme `delay()` actuel en cas d'échec. Le mécanisme `delay()` actuel reste
le filet de sécurité tant que la position n'est pas prouvée fiable.

## Tâche 1.1 — Spike : la position AudioTrack peut-elle piloter le surlignage ?

Prototype sur device : mesurer la fiabilité de `getPlaybackHeadPosition()` (ou
de la tête d'écriture) en `MODE_STREAM` pour déduire le mot courant à partir des
`wordTimestamps`. Critère de sortie chiffré : **décalage perçu ≤ 80 ms stable**
sur plusieurs phrases, **sans dérive cumulative**, sur Edge (24 kHz) et Sherpa
(22 050 Hz). Consigner le verdict (positif/négatif) avec les mesures.

## Tâche 1.2 — Décision

- **Positif** : basculer le surlignage sur la position (ajout d'un contrat
  position/événements à `AudioPlayer`, surlignage rebranché, **repli `delay()`
  conservé**).
- **Négatif** : **écart déclaré** — le surlignage reste `delay()`-based,
  documenté, et le lot se termine sans code.

## Tâche 2.x — (conditionnel) bascule + device

Uniquement si la Tâche 1.2 est positive : implémenter la bascule, vérifier sur
device que le surlignage est synchronisé (pas d'avance/retard, pas de dérive),
et conserver le repli `delay()`.
