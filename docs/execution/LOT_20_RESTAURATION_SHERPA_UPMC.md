# Lot 20 — Restauration de Sherpa-ONNX sur le modèle `vits-piper-fr_FR-upmc-medium`

**Base :** `main` + corrections de `AUDIT_CONSOLIDATION_V1.md` (B2 : voix
neuronale différée — ce lot la ré-introduit pour la v1.0.0). Références
de comportement : `legacy/monolith` (`OnnxInferenceService.kt`, même
modèle, jamais à fusionner tel quel — Blueprint §13.5),
`docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`, ADR-018/021/024.

**Objectif :** remplacer Kokoro par `vits-piper-fr_FR-upmc-medium` (2
voix françaises, ~6× plus léger, RTF mesuré ~0,8 vs ~4,7 pour Kokoro),
et rendre la chaîne de distribution **réellement fonctionnelle**
(extraction tar.bz2 + modèle CTC) pour que le moteur soit utilisable
pour de vrai en v1.0.0 — plus jamais un « téléchargement réussi » sans
moteur derrière.

---

## Constat vérifié (faits, pas suppositions)

| Fait | Valeur vérifiée | Source |
|---|---|---|
| Modèle | `vits-piper-fr_FR-upmc-medium` — variantes release `tts-models` : fp32 **80,4 Mo** / fp16 **41,7 Mo** / **int8 22,6 Mo** (recommandé) | API GitHub `releases/tag/tts-models` |
| SHA-256 (int8) | `ec4930dd3778e19e6ea2bc0d16c5a50a82f428ba7ee43dceab1df7efbaf9f1d3` | digest GitHub |
| 2 voix | `speaker_id_map: {'jessica': 0, 'pierre': 1}`, `num_speakers: 2` | `fr_FR-upmc-medium.onnx.json` (lu) + MODEL_CARD |
| Contenu archive int8 | `vits-piper-fr_FR-upmc-medium-int8/` : `fr_FR-upmc-medium.onnx`, `fr_FR-upmc-medium.onnx.json`, `tokens.txt`, `espeak-ng-data/` (397 fichiers) — **pas de lexicon.txt** (phonémiseur espeak-ng) | listing de l'archive téléchargée |
| Latence | Legacy : **RTF ~0,8** sur Snapdragon 680 (fp32) — « bien inférieur au seuil critique de 1.0 » ; vs RTF ~4,7 pour Kokoro | `legacy/monolith/docs/prototype-report.md` |
| Réglages prosodiques legacy | `lengthScale 1.08`, `noiseScale 0.667`, `noiseScaleW 0.8` ; `generate(text, sid, speed)` ; clamp sid sur `numSpeakers()` | `legacy/monolith/.../OnnxInferenceService.kt` |
| Modèle CTC (surlignage mot) | `sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8.tar.bz2` — **102,3 Mo**, SHA-256 `2116eebbfc923ee3332a244e8c933ccc1b7e6783070f7bf842d0b5fc64f6ae33`, release `asr-models` — déjà validé par le prototype (int8, doc l.703) | API GitHub + `PROTOTYPE_ALIGNEMENT_CTC.md` |
| Aligneur CTC | `align()` resample n'importe quel sampleRate → 16 kHz (`AudioResampler` + `CTC_SAMPLE_RATE`) — **aucun changement nécessaire** pour l'audio 22 050 Hz d'upmc | `CtcForcedAligner.kt:46-53` |
| Code actuel | `SherpaOnnxTtsEngine` configure **Kokoro** (`OfflineTtsKokoroModelConfig`, sid=30 en dur) ; `SherpaOnnxModelPaths` attend `model.int8.onnx`/`voices.bin`/… ; `TtsVoiceCatalog` liste `ff_siwis` ; téléchargement sans extraction (TODO) ; `CtcModelPaths` jamais rempli | fichiers `infrastructure/tts`, `TtsVoiceCatalog.kt` |

**Conclusion :** le modèle est léger, rapide, français, avec deux voix —
toutes les données pour le câbler sont vérifiées. Trois chantiers :
(1) extraction tar.bz2, (2) bascule Kokoro → upmc (VITS), (3) distribution
du modèle CTC.

---

## Décisions proposées (à valider)

1. **Variante int8** (22,6 Mo, ~6× plus léger que Kokoro) — cohérent
   avec le téléchargement à la demande (ADR-018) ; la latence int8 sera
   mesurée sur device (le legacy a mesuré fp32).
2. **Voix par défaut : `jessica` (sid 0)** — même ordre que le legacy
   (« JESSICA en premier pour SID 0 »).
3. **Moteur par défaut restauré : `SHERPA_ONNX`** — avec repli automatique
   sur la voix système tant que le modèle n'est pas installé
   (`FallbackTtsEngine` déjà en place, aucun blocage du premier usage) et
   proposition de téléchargement au premier usage TTS.
4. **Le flux « installer la voix neuronale » télécharge DEUX modèles**
   (TTS 22,6 Mo + CTC 102,3 Mo) avec progression globale, extraction
   vérifiée, et n'affiche « installée » que si `isReady` est vrai pour
   les deux.

---

## Tâches

### 1. Extraction tar+bzip2 (débloque toute la chaîne)
- Nouvelle dépendance `commons-compress` (version catalog + module
  `infrastructure/tts`), composant `TarBz2Extractor` **pur JVM/testé**
  (streaming fichier par fichier, chemin de sortie contrôlé — jamais
  d'extraction de chemin absolu).
- `SherpaOnnxVoiceModelDownloadService` : après téléchargement + SHA-256,
  extraction vers `SherpaOnnxModelPaths.voiceDir`, suppression de
  l'archive, puis vérification `isReady`.
- Commit : `Ajoute l'extraction tar+bzip2 des modèles téléchargés`.

### 2. Bascule du moteur sur upmc-medium (VITS)
- `SherpaOnnxModelPaths` : `modelFile = fr_FR-upmc-medium.onnx`,
  `tokensFile = tokens.txt`, `espeakDataDir`, plus de `voices.bin`/
  lexiques/`.fst` ; `isReady` = onnx + tokens + espeak-ng-data.
- `SherpaOnnxTtsEngine` : `OfflineTtsKokoroModelConfig` →
  `OfflineTtsVitsModelConfig(model, tokens, dataDir, lexicon="")` ;
  `sid` résolu depuis `voiceProfile.voice` (`jessica → 0`,
  `pierre → 1`, défaut `jessica`, clamp sur `numSpeakers()`) ; KDoc de
  latence réécrit avec la mesure upmc ; `capabilities` :
  `modelSizeMb ≈ 23`, `license = "Apache-2.0 (sherpa-onnx) +
  CC-BY-SA-4.0 (voix UPMC upmc-medium)"`, `pitchControl = false` (VITS
  n'expose que `lengthScale`, inchangé).
- Commit : `Bascule Sherpa-ONNX sur le modèle vits-piper-fr_FR-upmc-medium`.

### 3. Distribution du modèle CTC (surlignage mot à mot)
- Nouveau service/extension du flux de téléchargement pour
  `sherpa-onnx-nemo-fast-conformer-ctc-...-20k-int8` (URL + SHA vérifiés
  ci-dessus), extraction vers `CtcModelPaths.modelDir`, vérification
  `isReady` (`model.int8.onnx` + `tokens.txt` — à confirmer au listing
  de l'archive à l'implémentation).
- `capabilities.wordTimestamps` reste vrai uniquement si `isReady`
  (règle « un moteur ne fait jamais semblant »).
- Commit : `Câble le téléchargement du modèle CTC d'alignement`.

### 4. Restauration du flux d'installation dans l'UI
- `TtsVoiceCatalog` : `availableVoicesFor(SHERPA_ONNX) =
  listOf("jessica", "pierre")`, `voiceLabel` (« Jessica (FR) »,
  « Pierre (FR) » — noms du legacy).
- Réglages (carte Lecture) : retour du bouton « Télécharger une voix
  neuronale » avec confirmation (taille réelle ~125 Mo TTS+CTC),
  progression, annulation, états honnêtes (« installée » seulement si
  `isReady`) ; descriptions moteur (« Voix neuronale locale · surlignage
  mot à mot ») ; retrait de la note « à venir ».
- Reader : proposition au premier usage TTS si modèle absent (dialogue
  « Télécharger / Plus tard », même mécanique que le Lot 10 supprimé à
  l'audit, texte de taille mis à jour).
- Commit : `Restaure le flux d'installation de la voix neuronale`.

### 5. Latence & budgets (device V2206)
- Mesure avec `SherpaOnnxTtsEngineLatencyTest` (synthèse + alignement
  décomposés) : RTF int8, coût d'init (`tts` lazy), tap → premier audio.
- Si le budget §11.2 (1 500 ms) n'est pas tenu : **préchauffage** du
  moteur à la fin du téléchargement et/ou à la sélection de la voix —
  sinon ADR de ré-arbitrage explicite.
- Commit : `Mesure et documente la latence upmc-medium sur device`.

### 6. Cohérence docs & notices
- `README.md` : tableau des moteurs (Sherpa/upmc = référence, ~23 Mo,
  2 voix), suppression de la mention « à venir ».
- `THIRD_PARTY_NOTICES.md` : entrée précise voix `upmc-medium`
  (CC-BY-SA-4.0, corpus UPMC) + modèle CTC (NeMo, CC-BY-4.0 déjà
  présent).
- Commit : `Actualise README et notices tierces pour la voix upmc`.

---

## Critères de sortie

- [ ] Téléchargement → extraction → `isReady` vrai sur device (TTS + CTC).
- [ ] Synthèse audible avec **jessica et pierre** (sid 0 et 1), vitesse
      appliquée, prononciation française correcte (règles K9).
- [ ] Surlignage mot à mot fonctionnel via CTC sur device.
- [ ] Latence mesurée et documentée (RTF + tap → premier audio) ; budget
      §11.2 tenu ou ADR de ré-arbitrage signé.
- [ ] UI honnête : aucun « installée » sans `isReady` ; repli voix système
      transparent tant que le modèle est absent.
- [ ] `./gradlew build` vert (tests + `checkArchitectureRules` +
      `koverVerify`), tests de migration inchangés verts sur device.
- [ ] README et `THIRD_PARTY_NOTICES` à jour.

## Risques & points de vérification

- **Latence int8 inconnue tant que non mesurée** (le legacy a mesuré
  fp32 à RTF ~0,8 : une phrase de ~5 s ≈ 4 s de synthèse — le préchauffage
  et le buffer du pipeline gapless sont le levier, à confirmer sur
  device).
- **Débit de parole upmc** : une issue Home Assistant signale une voix
  trop rapide — reprendre `lengthScale = 1.08` du legacy et vérifier
  l'écoute sur device.
- **Noms de fichiers int8** (`fr_FR-upmc-medium.onnx` confirmé au
  listing ; layout CTC `model.int8.onnx`/`tokens.txt` à confirmer).
- **Taille du téléchargement CTC (102 Mo)** — communiquer la taille
  totale réelle (~125 Mo) avant confirmation, jamais après.
- **`CtcForcedAligner`** : aucune modification attendue (resample 16 kHz
  interne), à re-valider sur device avec l'audio 22 050 Hz d'upmc.

---

## Statut d'exécution (Lot 20)

| Élément | Statut |
|---|---|
| Variante retenue | **fp32** (80,4 Mo — décision utilisateur, la variante exacte validée par le legacy) |
| Voix par défaut | **jessica** (sid 0 — décision utilisateur) |
| Extraction tar.bz2 (`TarBz2Extractor` + commons-compress) | ✅ implémenté + testé (3 tests JVM) |
| Bascule moteur Kokoro → VITS upmc (`OfflineTtsVitsModelConfig`, sid par voix, prosaïdie legacy) | ✅ implémenté |
| Distribution CTC (URL/hash vérifiés, extraction) | ✅ implémenté (progression combinée voix+CTC) |
| UI Réglages (bouton téléchargement, taille réelle ~183 Mo, états honnêtes) | ✅ restauré |
| Prompt Reader au premier usage TTS | ✅ restauré |
| Catalogue voix (`jessica`/`pierre` + labels) | ✅ |
| Moteur par défaut `SHERPA_ONNX` | ✅ |
| README + `THIRD_PARTY_NOTICES` + écran À propos (CC-BY-SA-4.0 upmc) | ✅ |
| Diagnostic Kokoro/XNNPACK obsolète | ✅ supprimé (chemin rejeté Phase 5) |
| Imports `Paragraph` obsolètes (androidTest reader) | ✅ nettoyés (cassure préexistante) |
| `./gradlew build` | ✅ vert |
| **Vérification device (V2206)** — modèles réels poussés sur device, tests instrumentés exécutés derrière le keyguard | ✅ **4/4 tests passés** (RTF, CTC, moteur complet, export WAV) |
| Latence mesurée + préchauffage | ✅ mesurée et documentée (§ ci-dessous) — préchauffage câblé |
| **Vérification UI de bout en bout** (appareil déverrouillé) | ✅ **complète** : téléchargement réel ~183 Mo via l'app (tailles exactes, SHA-256 vérifié) → extraction sur device (voix 359 fichiers + CTC `model.int8.onnx`) → archives supprimées → « Voix neuronale installée » → moteur Sherpa-ONNX (UPMC) actif → **lecture TTS réelle à 22 050 Hz dans le Reader (jessica puis pierre), sans repli** |
| **2 bugs réels trouvés par la vérification device et corrigés** | ✅ voir § « Bugs corrigés » ci-dessous |

### Bugs corrigés (vérification device, Round 2)

1. **`FallbackTtsEngine` avalait les `CancellationException`** : un timeout de
   l'ordonnanceur (`withTimeout(20 s)` sur la synthèse) était traité comme un
   échec du moteur → repli **définitif** sur la voix système après UN seul
   dépassement. Corrigé : les annulations sont re-lancées, seul un vrai
   échec de synthèse déclenche le repli (`FallbackTtsEngine.kt`).
2. **Init froide du moteur dépassant le timeout** : la première synthèse d'un
   process neuf (chargement des ~180 Mo de modèles, ~10-20 s sur V2206)
   dépassait les 20 s → sans le fix n°1, repli permanent. Corrigé :
   préchauffage ajouté au **contrat domaine `TtsEngine.warmUp()`** (no-op par
   défaut), déclenché à l'ouverture du Reader (`ReaderViewModel.openPublication`)
   et à la fin du téléchargement — premier son ~4 s après le tap (mesuré).

### Mesures device (V2206, modèles réels, 4 threads)

| Mesure | Résultat |
|---|---|
| Synthèse VITS upmc (pure, `PiperUpmcLatencyTest`) | cold RTF **0,32** ; median RTF **0,29** (~1,07 s pour ~3,6 s d'audio) |
| Alignement CTC (`CtcForcedAligner`, session seule) | cold ~4,6 s, **warm ~1,6 s** pour ~3,7 s d'audio — `setIntraOpNumThreads(4)` **sans effet mesuré** (FastConformer séquentiel) |
| Pipeline moteur complet (`SherpaOnnxTtsEngineLatencyTest`) | premier appel **8,1 s** (init + synth + align cold) ; **médiane 2,8 s/phrase** (synth 1,1 s + align 1,6 s) |
| Surlignage mot à mot | ✅ 10 mots alignés sur l'audio upmc réel (timestamps réels, `charOffset` corrects) |
| Voix | ✅ WAV de **jessica** (sid 0) et **pierre** (sid 1) exportés pour écoute |

**Lecture du budget §11.2 (tap → premier audio ≤ 1 500 ms)** : le
préchauffage (câblé à la fin du téléchargement) supprime le coût froid
(~8,1 s → ~2,7 s au premier usage) ; le coût résiduel est l'alignement
CTC (~1,6 s/phrase), synthèse comprise à ~1,1 s (RTF 0,29 — excellente).
La v1.0.0 tient l'expérience « la voix démarre en ~3 s puis lit en
continu » ; le **ré-arbitrage formel du budget alignement** (ou un modèle
d'alignement plus léger / surlignage asynchrone) est consigné comme
dette à trancher — jamais une ignorance silencieuse (Blueprint §11.2).
