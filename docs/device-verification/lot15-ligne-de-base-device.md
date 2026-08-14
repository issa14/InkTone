# Vérification Device — Ligne de base TTS (avant pipeline gapless, Lot 15)

**Date** : 2026-08-14
**Branche** : `lot-15-gapless` (Palier 1, `b32e2fc`) — le lecteur `AudioSegmentPlayer`
(MODE_STATIC) est **encore en service**, le pipeline gapless n'est **pas** branché.
**Appareil cible** : Snapdragon 680 (V2206), Android 14

> **But** : capturer le comportement ACTUEL de la lecture TTS, pour servir de
> référence de non-régression. La Tâche 4.3 comparera le comportement après
> branchement du pipeline gapless à **cette** ligne de base — en particulier le
> surlignage mot-à-mot, qui doit rester **identique** (ADR-025 : le surlignage
> n'est pas touché par le Lot 15).
>
> Pour chaque point, cocher **Oui / Non / Écart** et noter toute observation.
> Un point « Non » ici n'est pas bloquant : c'est la réalité du comportement
> actuel (ex. reprise à la phrase, pas au mot) — il documente le point de départ.

## 0. Préparation

- [x] App installée depuis `lot-15-gapless` (`./gradlew installDebug`)
- [x] EPUB de test chargé : plusieurs chapitres, phrases longues, ponctuation variée
- [x] Voix **Edge** (Vivienne ou Henri, 24 kHz) sélectionnable
- [x] Voix **Sherpa-ONNX** (22 050 Hz) sélectionnable

## 1. Fluidité de lecture

- [x] Lancement : premier son audible sans délai anormal (noter le délai perçu)
- [x] Phrases enchaînées : fluides, pas de trou audible entre deux phrases
- [x] Aucune coupure de mot/phrase en cours de lecture (l'audio va au bout)
- [x] Silence inter-phrases conforme à la ponctuation (virgule ~150 ms, phrase ~650 ms)
- Obs. : fluidité validée.

## 2. Surlignage mot-à-mot

- [x] Le surlignage suit le mot en cours de lecture (pas de décalage visible)
- [x] Moteur **Sherpa-ONNX** : surlignage mot-à-mot actif
- [x] Moteur **Edge** : surlignage conforme à ses capacités (timestamps ou non)
- [x] Fin de phrase : le surlignage s'efface proprement
- Obs. : surlignage mot-à-mot validé, aucun décalage signalé.

## 3. Stabilité (point critique — course SIGSEGV latente)

- [x] **Stop** en pleine lecture : aucun crash
- [x] **Tap lecture / stop répété** (≥ 10×) : aucun crash
- [x] **Pause / reprise** répétées : aucun crash
- [x] **Changement de chapitre** en pleine lecture : aucun crash
- Obs. : stop validé, pause/reprise validées, changement de chapitre en pleine
  lecture → début de lecture du nouveau chapitre (pas de crash).

## 4. Contrôles de lecture

- [x] **Pause** : l'audio s'arrête immédiatement
- [x] **Reprise** : repart au début de la phrase (comportement actuel MODE_STATIC)
- [x] **Saut de phrase** avant / arrière : position correcte
- [x] **Navigation chapitre précédent** : position correcte
- Obs. : pause validée, reprise à la phrase validée, phrase précédente/suivante
  validées, chapitre précédent validé.

## 5. Vitesse

- [x] Changement de vitesse : appliqué (relance depuis le début de la phrase en cours)
- Obs. : validé.

## 6. Moteurs / sample rates

- [x] **Edge 24 kHz** : lecture fluide
- [x] **Sherpa 22 050 Hz** : lecture fluide
- Obs. : validé.

## Verdict daté

- Date de capture : 2026-08-14
- Signataire : Issa
- [x] Ligne de base consignée — le document ci-dessus fait foi pour la
      non-régression (Tâche 4.3 s'y référera).
- Remarques globales : toutes les sections validées. Aucun crash observé
  (stop, pause/reprise, changement de chapitre en pleine lecture). Surlignage
  mot-à-mot conforme. Reprise à la phrase (MODE_STATIC), pas à mi-phrase —
  écart déjà déclaré dans le plan.
