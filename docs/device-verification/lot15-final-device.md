# Vérification Device — Pipeline gapless branché (Lot 15, Tâche 4.3)

**Date** : ___ (à renseigner)
**Branche** : `lot-15-gapless` (Palier 4, `fb73812`) — lecteur gapless branché au
Reader (`PlaybackOrchestrator`), `AudioSegmentPlayer`/`SentenceAudioBuffer` supprimés.
**Appareil cible** : Snapdragon 680 (V2206), Android 14
**Référence** : ligne de base `docs/device-verification/lot15-ligne-de-base-device.md` (0.2)

> **But** : vérifier que le pipeline gapless ne **régresse rien** par rapport à
> la ligne de base 0.2 — en particulier le **surlignage mot-à-mot, qui doit être
> identique** (ADR-025 : mécanique `delay()` inchangée, seul le déclencheur a
> changé). C'est la dernière porte avant clôture du lot.

## 1. Fluidité de lecture

- [ ] Phrases enchaînées **sans trou audible** (≤ 150 ms perçues, Blueprint §8.7)
- [ ] Aucune coupure de mot/phrase en cours de lecture
- [ ] Silence inter-phrases conforme à la ponctuation (virgule ~150 ms, phrase ~650 ms)
- Obs. : ___

## 2. Surlignage mot-à-mot (comparer à la ligne de base 0.2)

- [ ] Le surlignage suit le mot lu, **identique** à la ligne de base (aucun décalage nouveau)
- [ ] Moteur **Sherpa-ONNX** : surlignage mot-à-mot actif
- [ ] Moteur **Edge** : surlignage conforme à ses capacités
- [ ] Fin de phrase : le surlignage s'efface proprement
- Obs. : ___

## 3. Contrôles de lecture

- [ ] **Pause** : l'audio s'arrête immédiatement (arrêt complet, comportement conservé)
- [ ] **Reprise** : repart à la phrase courante (pas au mot — écart déclaré)
- [ ] **Saut de phrase** avant / arrière : position correcte
- [ ] **Fin de chapitre** : avance automatique au chapitre suivant
- [ ] **Changement de vitesse** : appliqué
- Obs. : ___

## 4. Stabilité (course SIGSEGV éliminée)

- [ ] **Stop** en pleine lecture : aucun crash
- [ ] **Tap lecture / stop répété** (≥ 10×) : aucun crash
- [ ] **Pause / reprise** répétées : aucun crash
- [ ] **Changement de chapitre** en pleine lecture : aucun crash
- Obs. : ___

## 5. Moteurs / sample rates

- [ ] **Edge 24 kHz** : lecture fluide
- [ ] **Sherpa 22 050 Hz** : lecture fluide
- Obs. : ___

## Verdict daté

- Date de capture : ___
- Signataire : ___
- [ ] Pipeline gapless validé sur device — le lot 15 peut être fusionné (sur
      confirmation explicite).
- [ ] Écart signalé (détailler) : ___
- Remarques globales : ___
