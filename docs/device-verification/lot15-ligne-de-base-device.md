# Vérification Device — Ligne de base TTS (avant pipeline gapless, Lot 15)

**Date** : ___ (à renseigner)
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

- [ ] App installée depuis `lot-15-gapless` (`./gradlew installDebug`)
- [ ] EPUB de test chargé : plusieurs chapitres, phrases longues, ponctuation variée
- [ ] Voix **Edge** (Vivienne ou Henri, 24 kHz) sélectionnable
- [ ] Voix **Sherpa-ONNX** (22 050 Hz) sélectionnable

## 1. Fluidité de lecture

- [ ] Lancement : premier son audible sans délai anormal (noter le délai perçu)
- [ ] Phrases enchaînées : fluides, pas de trou audible entre deux phrases
- [ ] Aucune coupure de mot/phrase en cours de lecture (l'audio va au bout)
- [ ] Silence inter-phrases conforme à la ponctuation (virgule ~150 ms, phrase ~650 ms)
- Obs. : ___

## 2. Surlignage mot-à-mot

- [ ] Le surlignage suit le mot en cours de lecture (pas de décalage visible)
- [ ] Moteur **Sherpa-ONNX** : surlignage mot-à-mot actif
- [ ] Moteur **Edge** : surlignage conforme à ses capacités (timestamps ou non)
- [ ] Fin de phrase : le surlignage s'efface proprement
- Obs. : ___

## 3. Stabilité (point critique — course SIGSEGV latente)

- [ ] **Stop** en pleine lecture : aucun crash
- [ ] **Tap lecture / stop répété** (≥ 10×) : aucun crash
- [ ] **Pause / reprise** répétées : aucun crash
- [ ] **Changement de chapitre** en pleine lecture : aucun crash
- Obs. : ___

## 4. Contrôles de lecture

- [ ] **Pause** : l'audio s'arrête immédiatement
- [ ] **Reprise** : repart au début de la phrase (comportement actuel MODE_STATIC)
- [ ] **Saut de phrase** avant / arrière : position correcte
- [ ] **Fin de chapitre** : avance automatique au chapitre suivant
- Obs. : ___

## 5. Vitesse

- [ ] Changement de vitesse : appliqué (relance depuis le début de la phrase en cours)
- Obs. : ___

## 6. Moteurs / sample rates

- [ ] **Edge 24 kHz** : lecture fluide
- [ ] **Sherpa 22 050 Hz** : lecture fluide
- Obs. : ___

## Verdict daté

- Date de capture : ___
- Signataire : ___
- [ ] Ligne de base consignée — le document ci-dessus fait foi pour la
      non-régression (Tâche 4.3 s'y référera).
- Remarques globales : ___
