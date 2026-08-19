# ADR-021 : Architecture à paliers pour le timing mot du TTS

**Status :** Accepted
**Date :** 2026-07-26

> **Note (ADR-026, 2026-08-20)** — la prémisse « application commerciale à
> code source fermé » invoquée ici n'est plus vraie : le code est publié sous
> licence MIT. La décision de cet ADR reste valide pour des raisons
> indépendantes de cette prémisse — voir
> [ADR-026](ADR-026-licence-mit-ouverture-du-code.md).

## Context

ADR-013 supposait que Sherpa-ONNX fournissait des timestamps natifs
pour le TTS. La vérification empirique en Phase 3 l'a infirmé :
`GeneratedAudio` (Kotlin et Python, même cœur C++) n'expose que
`samples`/`sample_rate`, pour tous les backends de modèle (VITS, Matcha,
Kokoro, Pocket, Supertonic, ZipVoice). Une recherche comparative a
confirmé qu'il s'agit d'un manque connu et documenté côté Sherpa-ONNX
(issue #3536, alignement forcé demandé mais non implémenté), et a
établi qu'aucun moteur TTS offline neuronal évalué n'expose de timing
mot natif de bout en bout sur Android sans travail supplémentaire.

## Decision

InkTone adopte une architecture à paliers pour le surlignage mot-à-mot,
au lieu de parier sur un unique moteur :

- **Palier 1 (livré en premier) :** `android.speech.tts.TextToSpeech`
  natif + `UtteranceProgressListener.onRangeStart` (API 26+), qui
  fournit de vraies frontières de mots (plage de caractères + frame
  audio), entièrement hors ligne avec les voix Google embarquées.
- **Palier 2 (expérience premium) :** synthèse neuronale (Sherpa-ONNX,
  modèle Kokoro ou VITS) pour la qualité vocale, augmentée d'un second
  passage d'alignement forcé sur device (modèle CTC léger + décodage de
  Viterbi contraint par le texte connu) pour produire de vrais
  timestamps — jamais estimés par interpolation.
- `TtsCapabilities.wordTimestamps` reste le contrat exposé à
  l'application (Blueprint §8.4) ; la façon dont un adaptateur
  `TtsEngine` le satisfait (callback natif ou passage d'alignement) est
  un détail interne à cet adaptateur, jamais exposé au Reader.
- Nouveau membre d'énumération `TtsEngineId.ANDROID_NATIVE` pour
  l'adaptateur du Palier 1 (ajout non cassant, Tâche 3.0 de la Phase 3).
- **Piper est écarté des moteurs candidats** : son dépôt a été archivé
  le 6 octobre 2025 et le projet relicencié en GPL-3.0 (`piper1-gpl`) —
  incompatible avec une application commerciale à code source fermé,
  indépendamment même de la question des timestamps.

## Rationale

Le Palier 1 est un filet de sécurité quasi gratuit (Kotlin pur, zéro
JNI, zéro modèle supplémentaire) qui valide toute la chaîne Locator →
surlignage → reprise (K3) dès la marche à blanc, avant d'avoir résolu
le Palier 2. Le Palier 2 préserve la promesse de qualité vocale et
d'usage hors ligne simultanément, via un mécanisme (alignement forcé)
déjà éprouvé en production dans l'écosystème Sherpa-ONNX
(`react-native-sherpa-onnx`, mode `timingMode: 'aligned'`).

## Consequences

Deux chemins TTS à construire au lieu d'un ; la fiabilité du Palier 1
dépend du moteur OS installé (Google TTS confirmé, moteurs constructeur
incertains — détection au runtime nécessaire) ; le Palier 2 ajoute un
modèle CTC et une latence de traitement à mesurer (§11.2, nouveau
budget à fixer en Phase 5) ; le contrat de domaine (`TtsEngine`,
`WordTimestamp`, Tâche 1.7) reste inchangé — seule l'infrastructure
change, preuve que la conception du domaine a bien isolé cette
incertitude.

## Alternatives Considered

- **Simuler le timing par interpolation de caractères** : rejeté —
  malhonnête, §8.9.
- **Edge TTS comme mécanisme principal de timing** : rejeté — en ligne,
  contraire à Offline First.
- **Portage natif de l'extracteur de durée interne de Kokoro vers
  Kotlin/C++**, qui éliminerait le second passage : retenu comme piste
  d'optimisation future si le Palier 2 s'avère trop coûteux, pas comme
  décision v1 — effort de portage élevé, non justifié sans mesure
  préalable.

## Addendum (Tâche 3.1, device V2206)

Certains moteurs TTS constructeur ne respectent pas la sémantique
documentée par Android pour `onRangeStart(utteranceId, start, end,
frame)`. Sur le device de test (voix embarquée
`fr-fr-x-frb-seanet-embedded`), les paramètres portent en réalité
`(audioPosition, charStart, charEnd)` au lieu de
`(charStart, charEnd, audioFrame)` — décodage vérifié sur 7 mots
consécutifs. `AndroidNativeTtsEngine.resolveWordBoundary()` teste les
deux interprétations et ignore (avec avertissement) tout évènement qui
ne correspond à aucune des deux, plutôt que de supposer laquelle est
active. **Ne jamais retirer cette double interprétation en pensant
simplifier** : elle encode un comportement réel observé, pas une
précaution excessive.
