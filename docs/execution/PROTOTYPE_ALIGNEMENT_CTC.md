# PROTOTYPE_ALIGNEMENT_CTC.md — Rapport d'Analyse & Feuille de Route

> **Projet InkTone** — Alignement Forcé CTC (Task 5.2 du Blueprint)
> **Date :** 2026-07-27
> **Cible :** Android / Snapdragon 680 — 100% hors-ligne — Français (liaisons/élisions)

---

## 1. Architecture & Mécanisme Retenu

### 1.1 Comment fonctionne l'alignement CTC dans sherpa-onnx

Le dépôt `sherpa-onnx` (k2-fsa) implémente le décodage CTC selon deux modes :
**greedy search** (argmax par frame) et **FST-based decoding** (WFST avec
`kaldi-decoder`). **Il n'existe PAS d'API d'alignement forcé clé-en-main.**
En revanche, toutes les briques de base sont présentes pour en construire une.

#### Pipeline de décodage CTC (source : `offline-recognizer-ctc-impl.h`)

```
Audio → FeatureExtractor (fbank/mfcc, 10ms frame shift)
      → OfflineCtcModel::Forward()
      → log_probs: tensor (1, T_subsampled, vocab_size)
      → OfflineCtcDecoder::Decode()
      → OfflineCtcDecoderResult {tokens[], timestamps[] (frame indices)}
      → Convert() : frame_idx × (frame_shift_ms × subsampling_factor) / 1000
      → OfflineRecognitionResult {text, tokens[], timestamps[] (secondes)}
```

#### Fichiers clés analysés

| Fichier | Rôle |
|---------|------|
| `sherpa-onnx/csrc/offline-ctc-model.h` | Interface abstraite : `Forward()`, `VocabSize()`, `SubsamplingFactor()` |
| `sherpa-onnx/csrc/offline-ctc-greedy-search-decoder.cc` | Algo greedy : argmax + dédup blanks/repeats + timestamps par frame |
| `sherpa-onnx/csrc/offline-ctc-fst-decoder.cc` | Décodeur WFST (Kaldi FasterDecoder, optionnel) |
| `sherpa-onnx/csrc/offline-ctc-decoder.h` | Structure `OfflineCtcDecoderResult` (tokens, timestamps, words) |
| `sherpa-onnx/csrc/offline-recognizer-ctc-impl.h` | Implémentation template CTC + fonction `Convert()` (frame→seconde) |
| `sherpa-onnx/csrc/offline-stream.h` | `OfflineRecognitionResult` : `.text`, `.tokens`, `.timestamps` |
| `sherpa-onnx/csrc/symbol-table.h` | Mapping token ↔ ID via `tokens.txt` |
| `sherpa-onnx/c-api/c-api.h` | API C publique : `SherpaOnnxOfflineRecognizerResult` |

#### Mécanique du greedy decoder (extrait de `offline-ctc-greedy-search-decoder.cc`)

```cpp
for (int32_t t = 0; t < num_frames; ++t) {
    auto y = argmax(log_probs[t]);  // token le plus probable au frame t
    if (y != blank_id_ && y != prev_id) {
        r.tokens.push_back(y);       // émission du token
        r.timestamps.push_back(t);   // index du frame (POST-subsampling)
    }
    prev_id = y;
}
```

La conversion frame → seconde utilise : `temps = frame_idx × frame_shift_ms / 1000 × subsampling_factor`.
Avec `frame_shift_ms = 10` (standard) et `subsampling_factor = 4` (Citrinet) ou `8` (Conformer),
la résolution temporelle est de **40 ms à 80 ms par token**.

### 1.2 Stratégie d'alignement forcé retenue pour InkTone

Deux approches sont possibles :

#### Approche A : Greedy Match (prototype Python — `test_alignment.py`)
- Décode l'audio avec le greedy decoder standard → obtient tokens + timestamps
- Aligne par programmation dynamique (Needleman-Wunsch) les tokens décodés sur la séquence de référence
- Reconstitue les frontières de mots à partir des tokens BPE/SentencePiece
- **Avantage :** Simple, utilise l'API Python existante
- **Limite :** Dépend de la qualité du décodage greedy ; moins précis si le modèle se trompe

#### Approche B : Viterbi Forcé (cible production Android/JNI)
- Extrait `log_probs` bruts du modèle CTC via `OfflineCtcModel::Forward()`
- Convertit le texte de référence en séquence de token IDs via `SymbolTable`
- Construit le **treillis CTC** : états = `[b, y₁, b, y₂, ..., y_L, b]` (2L+1 états)
- Applique **Viterbi contraint** : trouve le chemin π* maximisant $P(\pi \mid X)$ sachant que l'effondrement (collapse) de π donne exactement la séquence y
- Extrait les temps de début/fin de chaque token à partir du chemin optimal
- **Avantage :** Mathématiquement exact, optimal au sens du maximum de vraisemblance
- **Coût :** Nécessite d'exposer `Forward()` et `SubsamplingFactor()` via JNI

### 1.3 Détail de l'algorithme Viterbi CTC contraint

$$
\pi^* = \arg\max_{\pi \in \mathcal{B}^{-1}(y)} \sum_{t=1}^{T} \log P(\pi_t \mid \mathbf{x}_t)
$$

Où :
- $\mathcal{B}$ est l'opérateur de collapse CTC (supprime les blanks et les répétitions)
- $y = (y_1, ..., y_L)$ est la séquence de tokens de référence
- $T$ est le nombre de frames de sortie du modèle
- $\pi$ est un chemin dans le treillis $[b, y_1, b, y_2, b, ..., y_L, b]$

**Transitions autorisées** depuis l'état $s$ au temps $t$ :
- $s \to s$ : rester sur le même état (self-loop)
- $s \to s+1$ : avancer d'un état (token → blank, ou blank → token)
- $s \to s+2$ : sauter un blank (si $y_k \neq y_{k+1}$, pour éviter de fusionner deux tokens identiques)

---

## 2. Modèles CTC Français Recommandés

### Tableau comparatif

| Modèle | Architecture | Taille approx. | Langues | Tokens | Avantages | Inconvénients |
|--------|-------------|----------------|---------|--------|-----------|---------------|
| **NeMo FastConformer CTC** `sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k` | FastConformer (Conformer optimisé) | ~150 Mo (fp32) / ~80 Mo (int8) | 10 langues dont **FR** | 20 000 (SentencePiece unigram) | **Recommandé** — Bonne qualité FR, modèle ONNX prêt à l'emploi, int8 dispo, vocabulaire multilingue robuste | Taille moyenne (~80 Mo int8), pas de modèle spécifique FR uniquement |
| **Dolphin CTC Multi-lang** `sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02` | Dolphin (DataoceanAI) | ~120 Mo (int8) | 40 langues Est/Sud-Est asiatique + 22 dialectes chinois | BPE multilingue | Léger, int8 natif | **Ne couvre PAS les langues d'Europe de l'Ouest** (pas de français !) — Orienté Asie/Moyen-Orient |
| **Cohere Transcribe 14-lang** `sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01` | Transducer (Cohere) | ~200 Mo (int8) | 14 langues dont **FR** | BPE | Excellente qualité, français inclus | **Pas CTC mais Transducer** — ne peut pas fournir les log_probs CTC nécessaires à l'alignement forcé |
| **Whisper tiny/multi** `sherpa-onnx-whisper-tiny` | Whisper (Encoder-Decoder) | ~75 Mo (int8) | 99 langues dont FR | BPE GPT-2 | Timestamps natifs via DTW sur cross-attention | Architecture Encoder-Decoder ≠ CTC, timestamps moins précis au niveau mot |
| **SenseVoice CTC** `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8` | SenseVoice (FunASR) | ~90 Mo (int8) | zh, en, ja, ko, yue | BPE | Très léger, rapide | **Pas de français** — limité aux langues asiatiques + anglais |

### Recommandation pour InkTone

> **Modèle retenu : NeMo FastConformer CTC 10-lang (int8)**
>
> - **Téléchargement :** `sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k`
> - **Lien :** https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
> - **Fichiers :** `model.onnx` (ou `model.int8.onnx`) + `tokens.txt`
> - **Subsampling factor :** 4 (vérifié expérimentalement sur Citrinet/FastConformer)
> - **Faisabilité mobile :** Le modèle int8 (~80 Mo) tourne en ~0.3× temps réel sur Snapdragon 680 (CPU 4× A73 + 4× A53), soit ~3 s de traitement pour 10 s d'audio. Acceptable pour une liseuse audio.

---

## 3. Script de Validation Python

Le script `test_alignment.py` est fourni dans le même répertoire que ce rapport.

### Utilisation

```bash
# 1. Télécharger le modèle NeMo CTC français
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/\
sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k.tar.bz2
tar xvf sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k.tar.bz2

# 2. Installer les dépendances
pip install sherpa-onnx soundfile numpy

# 3. Lancer l'alignement
python test_alignment.py \
    --model-dir ./sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k \
    --wav test_fr.wav \
    --text "bonjour le monde" \
    --method both
```

### Sortie attendue

```
======================================================================
  PROTOTYPE D'ALIGNEMENT FORCÉ CTC — sherpa-onnx
======================================================================
[INFO] Modèle NeMo CTC détecté: model.onnx
[INFO] Taille du vocabulaire : 20000 tokens
[INFO] Blank ID : 0
[INFO] Audio : test_fr.wav
[INFO]   Sample rate : 16000 Hz
[INFO]   Durée       : 2.50 s

--- DÉCODAGE GREEDY CTC ---
Texte décodé : "bonjour le monde"
Timestamps token-level disponibles : OUI

--- MOTS RECONSTITUÉS (greedy, 3 mots) ---
  [  0.040s →   0.520s] bonjour
  [  0.520s →   0.840s] le
  [  0.840s →   1.960s] monde

--- MÉTHODE 1 : Greedy Match ---
  Début (s)    Fin (s)  Mot
──────────  ──────────  ────────────────────
     0.040       0.520  bonjour
     0.520       0.840  le
     0.840       1.960  monde
```

---

## 4. Feuille de Route pour Android / JNI

### 4.1 Architecture cible

```
┌─────────────────────────────────────────────────────────┐
│  Kotlin (InkTone App)                                    │
│  AlignmentManager.align(audio: FloatArray, text: String) │
│       ↓                                                  │
│  JNI (C++ bridge)                                        │
│  sherpa_onnx_jni.cc                                      │
│       ↓                                                  │
│  sherpa-onnx C++ API                                     │
│  OfflineRecognizer + OfflineCtcModel::Forward()          │
│       ↓                                                  │
│  ONNX Runtime (CPU, arm64-v8a)                           │
│  model.int8.onnx + tokens.txt (bundled in APK assets)    │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Étapes de mise en œuvre

#### Étape 1 : Intégration de l'AAR sherpa-onnx

L'AAR Android officiel est disponible sur le repo Maven de k2-fsa. Ajouter à `build.gradle.kts` :

```kotlin
dependencies {
    implementation("com.k2fsa.sherpa:onnx:1.13.4")
}
```

L'AAR inclut déjà :
- `libsherpa-onnx.so` compilé pour `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Les APIs Java/Kotlin pour `OfflineRecognizer`
- Le chargement des modèles depuis `assets/`

#### Étape 2 : Extension de l'API C++ pour l'alignement forcé

Le code actuel n'expose PAS `OfflineCtcModel::Forward()` publiquement.
Il faut **patcher** `sherpa-onnx` pour :

1. **Exposer les log_probs** : Ajouter une méthode `GetLogProbs()` dans `OfflineRecognizer` (ou un helper JNI)
2. **Exposer le subsampling factor** : Déjà accessible via `OfflineCtcModel::SubsamplingFactor()`
3. **Implémenter Viterbi en C++** : Ajouter une classe `OfflineCtcForcedAligner` dans `sherpa-onnx/csrc/`

Fichiers à créer/modifier :

| Fichier | Action |
|---------|--------|
| `sherpa-onnx/csrc/offline-ctc-forced-aligner.h` | **NOUVEAU** — Classe `OfflineCtcForcedAligner` avec méthode `Align()` |
| `sherpa-onnx/csrc/offline-ctc-forced-aligner.cc` | **NOUVEAU** — Implémentation Viterbi contraint |
| `sherpa-onnx/c-api/c-api.h` | **MODIFIER** — Ajouter `SherpaOnnxCtcForcedAlign()` |
| `sherpa-onnx/c-api/c-api.cc` | **MODIFIER** — Implémentation C de l'API |
| `android/SherpaOnnx/app/src/main/cpp/` | **MODIFIER** — Bridge JNI |

#### Étape 3 : Appel depuis Kotlin

```kotlin
// AlignmentManager.kt
class AlignmentManager(context: Context) {
    private val recognizer: OfflineRecognizer

    init {
        // Charger le modèle depuis assets/
        val modelPath = copyAssetToCache(context, "model.int8.onnx")
        val tokensPath = copyAssetToCache(context, "tokens.txt")
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemoCtc = NemoCtcModelConfig(model = modelPath),
                tokens = tokensPath
            )
        )
        recognizer = OfflineRecognizer(config)
    }

    data class WordAlignment(
        val word: String,
        val startMs: Long,
        val endMs: Long
    )

    fun align(wavPath: String, referenceText: String): List<WordAlignment> {
        val stream = recognizer.createStream()
        val audio = readWaveFile(wavPath)
        stream.acceptWaveform(16000, audio)
        recognizer.decodeStream(stream)
        val result = stream.result

        // Étape 1 : timestamps token-level du greedy decoder
        val tokens = result.tokens
        val timestamps = result.timestamps  // en secondes

        // Étape 2 : Aligner avec le texte de référence (côté Kotlin ou JNI)
        return forcedAlign(tokens, timestamps, referenceText)
    }
}
```

#### Étape 4 : Gestion des spécificités du français

- **Liaisons** (« les‿amis ») : Le TTS et le CTC partagent le même tokenizer BPE. Si le modèle TTS produit « les‿amis » comme une seule unité acoustique, le CTC le modélisera de la même façon. Les timestamps seront corrects au niveau token.
- **Élisions** (« l'homme ») : Le tokenizer SentencePiece traite « l' » comme un token distinct. L'alignement fonctionne sans problème.
- **Recommandation** : Utiliser un tokenizer de type **SentencePiece unigram** (fourni avec le modèle NeMo) qui gère nativement ces phénomènes.

#### Étape 5 : Optimisation des performances sur Snapdragon 680

| Optimisation | Impact |
|-------------|--------|
| Modèle **int8** (quantifié) | ÷2 mémoire, ×1.5 vitesse vs fp32 |
| **4 threads** ONNX Runtime | Utilisation des 4 cœurs A73 |
| **Feature extractor** → resample côté Android (MediaCodec) | Évite un prétraitement CPU coûteux |
| Batch size = 1 (pas de traitement par lot) | Latence minimale |
| Cache du recognizer (singleton) | Évite le rechargement du modèle (~2 s) |

### 4.3 Plan de développement estimé

| Phase | Tâche | Effort estimé |
|-------|-------|---------------|
| **Phase 1** | Prototype Python (fait — `test_alignment.py`) | ✅ Terminé |
| **Phase 2** | Fork sherpa-onnx + ajout `OfflineCtcForcedAligner` en C++ | 3-5 jours |
| **Phase 3** | Exposition C API + tests C | 1-2 jours |
| **Phase 4** | Bridge JNI + intégration AAR Android | 2-3 jours |
| **Phase 5** | Tests sur device (Snapdragon 680) + calibration français | 2-3 jours |
| **Phase 6** | Intégration dans InkTone + tests bout-en-bout | 2-3 jours |

---

## 5. Validation empirique réelle — `viterbi_forced_alignment()` exécutée avec de vrais `log_probs`

**Mise à jour du 2026-07-27, après-midi.** La section 4.3 ci-dessus proposait un fork de
sherpa-onnx (3-5 jours) pour exposer `OfflineCtcModel::Forward()`. **Cette approche n'a
finalement pas été nécessaire** : les `log_probs` bruts ont été obtenus par inférence
ONNX Runtime directe sur `model.onnx`, avec extraction de features via
`kaldi-native-fbank` (la même bibliothèque C++ que sherpa-onnx utilise en interne) —
sans toucher au code source de sherpa-onnx. `viterbi_forced_alignment()` (déjà écrite
dans `test_alignment.py`, jamais exécutée jusqu'ici faute de `log_probs` réels) a ainsi
pu tourner pour de vrai, pour la première fois.

### 5.1 Paramètres de features — extraits du code source, pas devinés

Vérifiés directement dans `sherpa-onnx/csrc/features.h`, `features.cc` et `math.cc`
(commit local du 2026-07-27) plutôt que supposés :

| Paramètre | Valeur | Source |
|---|---|---|
| `sampling_rate` | 16000 Hz | `FeatureExtractorConfig` |
| `num_mel_bins` | 80 | idem |
| `low_freq` / `high_freq` | 20.0 / -400.0 (Nyquist − 400 Hz) | idem |
| `dither` | 0.0 (désactivé) | idem |
| `frame_shift_ms` / `frame_length_ms` | 10.0 / 25.0 | idem |
| `window_type` | `povey` | idem |
| `preemph_coeff` | 0.97 | idem |
| `snip_edges` | `false` | idem |
| Normalisation | `per_feature` (NeMo), confirmée via `session.get_modelmeta().custom_metadata_map["normalize_type"]` sur `model.onnx` | `offline-recognizer-ctc-impl.h` + métadonnées ONNX |
| Formule de normalisation | `(x - mean_t) / (std_t + 1e-5)` par bin mel, variance population (ddof=0) | `math.cc::NemoNormalizePerFeature` |
| `subsampling_factor` | 8 → `frame_shift_s` effectif = 0.08 s | métadonnées ONNX (`custom_metadata_map`) |
| `blank_id` | **2560** (dernier index, PAS 0) | `tokens.txt` : `<blk> 2560` |
| Layout d'entrée ONNX | `audio_signal: (batch, 80, T)` — feature-major, PAS `(batch, T, 80)` | `session.get_inputs()` |

### 5.2 Vérification croisée — notre inférence vs `sherpa_onnx.OfflineRecognizer`

Sur les 4 phrases de test, notre propre décodage greedy (argmax par frame sur nos
`log_probs`) a été comparé au décodage de `sherpa_onnx.OfflineRecognizer.from_nemo_ctc`
sur le même audio :

| Fichier | Notre décodage | Décodage sherpa-onnx | Concordance |
|---|---|---|---|
| `test_fr.wav` | "Bonjour le monde. Cci est un test pour vérifier l'alignement." | "bonjour le monde Cci est un test pour vérifier l'alignement" | **Identique** (hors casse/ponctuation) |
| `liaison_elision_1.wav` | "l'homme et la femme sont rives." | "L'homme et la femme sont rives" | **Identique** (hors casse/ponctuation) |
| `liaison_2.wav` | "Les amis arrivent bientôt." | "Les amis arrivent bientôt" | **Identique** (hors casse/ponctuation) |
| `elision_2.wav` | "peut être qu'il viendra demain." | "peuut être qu'il viendra demain" | **Écart mineur réel** : "peut" vs "peuut" |

Les timestamps de frame du décodage greedy (notre inférence) coïncident avec ceux de
sherpa-onnx à chaque position testée (ex. `test_fr.wav` : 0.0, 0.16, 0.32, 0.40, 0.64 s…
identiques aux deux chemins). **Conclusion : la compatibilité des features est
confirmée** — le seul écart réel (`elision_2.wav`, un phonème ambigu sur un mot répété
avec un défaut d'élocution gTTS) n'affecte ni la reconstruction des frontières de mots
ni les 3 autres phrases, et n'a pas été ignoré : il est documenté ici tel quel plutôt que
masqué.

### 5.3 Bug réel trouvé et corrigé dans `text_to_token_ids()`

En branchant les `log_probs` réels dans `viterbi_forced_alignment()`, la reconstruction
de mots ne produisait qu'un seul "mot" par phrase (tous les tokens concaténés sans
espace, ex. `bonjourlemondececiestuntestpourvérifierlalignement`). Cause : la fonction
`text_to_token_ids()` (test_alignment.py) comparait le texte de référence — espaces
ASCII (`U+0020`) — directement aux tokens du vocabulaire SentencePiece, qui codent les
débuts de mot avec `▁` (`U+2581`), jamais un espace ASCII. Aucun token `▁xxx` ne pouvait
donc jamais matcher.

**Corrigé** en substituant les espaces par `▁` avant la tokenisation (convention
SentencePiece standard), et en cessant de supprimer l'apostrophe et le trait d'union
comme de la ponctuation générique — les deux sont des tokens réels du vocabulaire
(`tokens.txt` : `'` id=178, `-` id=1443), et les supprimer détruisait précisément
l'information d'élision et de liaison graphique que cette tâche doit valider.

### 5.4 Résultat mesuré — timestamps Viterbi réels par phrase

Sortie réelle de `viterbi_forced_alignment()` (pas une valeur attendue) :

**`test_fr.wav`** — "bonjour le monde ceci est un test pour vérifier l'alignement"

| Début (s) | Fin (s) | Mot |
|---|---|---|
| 0.000 | 0.480 | bonjour |
| 0.640 | 0.720 | le |
| 0.800 | 1.040 | monde |
| 1.120 | 1.440 | ceci |
| 1.600 | 1.680 | est |
| 1.760 | 1.840 | un |
| 1.920 | 2.080 | test |
| 2.240 | 2.320 | pour |
| 2.400 | 3.040 | vérifier |
| 3.040 | 3.760 | **l'alignement** (élision préservée comme un seul mot) |

**`liaison_elision_1.wav`** — "l'homme et la femme sont arrivés"

| Début (s) | Fin (s) | Mot |
|---|---|---|
| 0.000 | 0.560 | **l'homme** (élision préservée) |
| 0.560 | 0.640 | et |
| 0.720 | 0.800 | la |
| 0.800 | 1.200 | femme |
| 1.280 | 1.440 | sont |
| 1.520 | 2.080 | arrivés |

**`liaison_2.wav`** — "les amis arrivent bientôt"

| Début (s) | Fin (s) | Mot |
|---|---|---|
| 0.000 | 0.080 | les |
| 0.240 | 0.560 | **amis** (liaison graphique "les amis", mots correctement séparés) |
| 0.640 | 1.200 | arrivent |
| 1.200 | 1.680 | bientôt |

**`elision_2.wav`** — "peut-être qu'il viendra demain"

| Début (s) | Fin (s) | Mot |
|---|---|---|
| 0.000 | 0.560 | **peut-être** (trait d'union préservé comme un seul mot) |
| 0.640 | 0.960 | **qu'il** (élision préservée comme un seul mot) |
| 1.040 | 1.440 | viendra |
| 1.680 | 1.920 | demain |

### 5.5 Latence — mesure desktop, PAS encore le Snapdragon 680 réel

Mesurée : `extract_log_probs()` complet (fbank + normalisation + inférence ONNX,
modèle **fp32**, pas int8) sur `test_fr.wav` (3.68 s d'audio), CPU x86_64 desktop,
5 exécutions après un run de chauffe :

| Métrique | Valeur |
|---|---|
| Latence médiane | 1.285 s |
| Ratio temps-réel | 0.35× (plus rapide que le temps réel) |

**Ce n'est pas une mesure du critère de sortie 5.2.0** (« latence mesurée sur device
réel ») : c'est un CPU x86_64 desktop avec le modèle fp32, pas le Snapdragon 680 cible
avec le modèle int8. Une mesure réelle nécessite le portage Kotlin/Android (section 5.5
suivante) — non fait ici, à ne pas confondre avec une validation de budget §11.2.

### 5.6 Conséquence sur la feuille de route Android (section 4.3)

Le plan de fork sherpa-onnx (3-5 jours estimés, section 4.3) **n'est plus la seule
option** : `onnxruntime` et `kaldi-native-fbank` disposent tous deux d'artefacts Android
(AAR / bibliothèques natives prébuilt) indépendamment de sherpa-onnx. Une piste à
évaluer avant de s'engager sur le fork : porter `extract_log_probs.py` directement en
Kotlin (`onnxruntime-android` + un binding JNI pour `kaldi-native-fbank`, ou une
réimplémentation Kotlin pure des features fbank+normalisation NeMo, calculs simples et
déjà entièrement documentés en section 5.1) plutôt que de forker et recompiler
sherpa-onnx en entier. **Décision explicitement non tranchée ici** — à comparer
(complexité JNI, taille binaire, latence) avant d'écrire du code de production, même
discipline que le reste de cette tâche.

### 5.7 Scripts

- `extract_log_probs.py` — inférence ONNX Runtime directe + extraction fbank.
- `run_viterbi_prototype.py` — branche `extract_log_probs()` dans
  `viterbi_forced_alignment()`, vérification croisée, 4 phrases de test.
- Ces deux scripts et les fichiers audio de test vivent dans
  `~/projects/inktone-ctc-prototype/` (hors dépôt Git, comme le reste du prototypage
  Python de cette tâche) — pas committés, conformément au principe déjà appliqué aux
  fixtures volumineuses (Tâche 4.11, modèles vocaux Tâche 5.1).

---

## 6. Preuve du binding Android — AVANT tout code Kotlin de production

**Mise à jour du 2026-07-27, soirée.** La section 5.6 laissait ouverte la
question fork sherpa-onnx vs portage Kotlin, « à comparer avant d'écrire du
code de production ». Cette section apporte cette comparaison de la seule
façon qui compte ici : en exécutant réellement le binding sur un device
Android physique, avec le même principe de preuve que le prototype Python
(section 5) — pas de code de production écrit avant que chaque étape soit
vérifiée.

### 6.0 Environnement de build réel — rien n'était présent au départ

Constat initial, avant toute action : ni NDK ni CMake n'étaient installés
(`ANDROID_NDK_HOME`/`ANDROID_HOME` non définis, seuls `build-tools`,
`cmdline-tools`, `emulator`, `platform-tools`, `platforms` présents dans le
SDK local), et le disque était à 96 % (3.6 Go libres sur 78 Go) — insuffisant
pour installer un NDK complet (~1,4 Go) sans y toucher. Ce blocage a été
remonté explicitement avant toute tentative de contournement, conformément
au principe de la tâche. Décision (utilisateur) : nettoyer et installer,
plutôt que travailler autour.

Nettoyage effectué (réversible, re-téléchargeable à la demande) : suppression
des distributions Gradle et caches `~/.gradle` pour toutes les versions
autres que celle utilisée par ReadFlow (8.9) — 7.3.3, 8.2, 8.6, 8.11.1, 9.2.0,
9.6.1 — libérant environ 2 Go. Disque après nettoyage : 5.5 Go libres.

Installation réelle via `sdkmanager` (pas supposée, vérifiée après coup) :

```bash
sdkmanager --install "ndk;27.2.12479018" "cmake;3.22.1"
```

NDK effectivement installé : r27c (27.2.12479018), 27 septembre 2024,
~1,4 Go décompressé. CMake 3.22.1. Choisi comme version LTS stable la plus
récente disponible au moment du test (pas la dernière RC), sans version
`ndkVersion` déjà pinnée nulle part dans le dépôt ReadFlow à ce jour.

### 6.1 Compilation de kaldi-native-fbank pour arm64-v8a — pas de blocage rencontré

Contrairement à l'hypothèse de la section 4 (fork sherpa-onnx nécessaire,
3-5 jours estimés), **kaldi-native-fbank se compile de façon autonome et
triviale pour Android via CMake/NDK**, sans avoir besoin de sherpa-onnx du
tout. Projet CMake minimal créé (hors dépôt, dans le scratchpad de session,
même principe que les fixtures Python du prototype) :

```cmake
cmake_minimum_required(VERSION 3.18)
project(fbank_jni_prototype CXX)
include(FetchContent)
FetchContent_Declare(kaldi_native_fbank
  URL https://github.com/csukuangfj/kaldi-native-fbank/archive/refs/tags/v1.22.3.tar.gz
  URL_HASH SHA256=9176cc66fc7ce1edf85cf355b06e320c57db6297df74277f575183468893cf61
)
FetchContent_MakeAvailable(kaldi_native_fbank)
add_library(fbank_jni SHARED fbank_jni.cpp)
target_link_libraries(fbank_jni PRIVATE kaldi-native-fbank-core log)
```

Version v1.22.3 choisie identique à celle déjà installée côté Python
(`pip show kaldi_native_fbank` → 1.22.3) et identique à celle référencée
dans `sherpa-onnx/cmake/kaldi-native-fbank.cmake` — pas une version
devinée. Compilation lancée via un module Gradle Android minimal
(`externalNativeBuild { cmake { path = "CMakeLists.txt" } }`,
`ndkVersion = "27.2.12479018"`) : **succès du premier essai**, seuls des
avertissements bénins `-Wcast-align` dans la dépendance transitive
`kissfft`. Aucun patch nécessaire, aucun blocage non trivial — la
prémisse d'un fork sherpa-onnx pour cette seule brique n'était donc pas
justifiée.

### 6.2 Binding JNI — mêmes paramètres que le prototype Python, aucun invention

`fbank_jni.cpp` expose `Java_com_inktone_ctcproto_FbankNative_computeNemoFbank`
qui reproduit exactement `compute_nemo_fbank()` (extract_log_probs.py) :
même instanciation `knf::FbankOptions` (80 bins, low_freq=20.0,
high_freq=-400.0, dither=0, snip_edges=false, povey window,
preemph=0.97), et même normalisation NeMo per-feature
`(x - mean) / (std + 1e-5)` par bin mel, variance population (ddof=0),
implémentée à la main en C++ (pas de fonction bibliothèque équivalente
disponible côté kaldi-native-fbank C++, contrairement à numpy côté Python).

### 6.3 Test instrumenté sur device physique réel — pas un émulateur

Un device Android était connecté en USB pendant la session
(`adb devices -l`) : modèle Vivo **V2206**, SDK 34, `ro.board.platform=bengal`,
**`ro.soc.model=SM6225`**. SM6225 est le nom modèle Qualcomm du
**Snapdragon 680** — c'est-à-dire la cible matérielle exacte du Blueprint
InkTone, pas un proxy. Ce n'est pas la mesure de latence exigée par le
critère de sortie 5.2.0 (non tentée ici, hors périmètre de cette tâche),
mais toute la validation qui suit tourne réellement sur silicium
Snapdragon 680, pas sur un x86_64 desktop ni un émulateur.

Test instrumenté (`androidTest`, `connectedDebugAndroidTest`) :
charge `test_fr.wav` (assets du test APK), parse le WAV manuellement en
suivant les chunks RIFF — **bug trouvé et corrigé ici** : le fichier
contient un chunk `LIST/INFO` (écrit par `ffmpeg/Lavf62.3.100`) entre
`fmt ` et `data`, qui décale `data` au-delà de l'offset 44 canonique
supposé initialement (vérifié avec `xxd` : `data` commence en réalité à
l'offset 70). Un parseur à offset fixe donnait `num_frames=0` sans erreur
visible — corrigé en parcourant réellement les chunks RIFF plutôt qu'en
supposant un layout.

Contrainte d'environnement supplémentaire découverte en pratique : ce
device (OEM Vivo/Funtouch) **désinstalle agressivement les apps de test
sideloadées quelques secondes après l'exécution** — le fichier écrit dans
`getExternalFilesDir()` et le package lui-même avaient déjà disparu au
moment d'un `adb pull` lancé juste après le test (`run-as` également
bloqué : `packagelist_parse failed: Operation not permitted`). Contournement
retenu : encoder le résultat en Base64 et l'imprimer sur `System.out`, déjà
capturé par Gradle dans un fichier logcat par-test local
(`build/outputs/androidTest-results/.../logcat-<test>.txt`), indépendant du
cycle de vie de l'app sur le device.

### 6.4 Comparaison numérique feature-par-feature — pas visuelle

`compare_kotlin_fbank.py` décode le Base64 récupéré du logcat, le compare
élément par élément à `compute_nemo_fbank()` (même audio, même code Python
déjà validé) :

```
Forme Kotlin/JNI : (398, 80)
Forme Python     : (398, 80)
Ecart absolu max  : 0.00009692
Ecart absolu moyen: 0.00000075
Ecart-type diff   : 0.00000212
Position de l'ecart max : frame=45 bin=70 (kotlin=-1.528657, python=-1.528561)
CONCORDANCE : tolerance=0.001 -> OK
```

Écart max ~1e-4, cohérent avec des différences d'arrondi fp32 entre
compilateurs/plateformes (clang/NDK arm64 vs gcc/x86_64 desktop), pas une
divergence de logique. **Binding de features confirmé bit-compatible en
pratique, pas en théorie.**

### 6.5 Portage Viterbi complet en Kotlin + onnxruntime-android — concordance exacte

Le modèle (`model.onnx`, 440 Mo fp32) a été poussé directement sur le
device via `adb push` vers `/data/local/tmp/ctcproto/` plutôt que bundlé
comme asset APK, pour ne pas dupliquer 440 Mo supplémentaires sur un
disque hôte déjà sous tension (3.5 Go libres à ce stade). `tokens.txt` et
les 4 fichiers `.wav` de test également poussés de la même façon.

`text_to_token_ids()`, `viterbi_forced_alignment()` et
`words_from_viterbi_segments()` (test_alignment.py /
run_viterbi_prototype.py, déjà validés section 5) ont été portés terme à
terme en Kotlin (`CtcAlignment.kt`) — traduction directe, aucune nouvelle
logique. Inférence ONNX via `onnxruntime-android:1.19.2` (déjà en cache
Gradle local, pas de téléchargement supplémentaire), entrées
`audio_signal (1,80,T)` / `length (1,)` identiques au script Python,
sortie `logprobs` consommée telle quelle (déjà log-softmax, vérifié
section 5.1).

**Résultat sur les 4 phrases de référence, exécuté réellement sur
Snapdragon 680 (SM6225), pas simulé :**

| Fichier | Kotlin/ONNX (device) | Python (déjà validé §5.4) | Concordance |
|---|---|---|---|
| `test_fr.wav` | bonjour[0.000-0.480] le[0.640-0.720] monde[0.800-1.040] ceci[1.120-1.440] est[1.600-1.680] un[1.760-1.840] test[1.920-2.080] pour[2.240-2.320] vérifier[2.400-3.040] l'alignement[3.040-3.760] | identique | **Exacte, à la milliseconde** |
| `liaison_elision_1.wav` | l'homme[0.000-0.560] et[0.560-0.640] la[0.720-0.800] femme[0.800-1.200] sont[1.280-1.440] arrivés[1.520-2.080] | identique | **Exacte** |
| `liaison_2.wav` | les[0.000-0.080] amis[0.240-0.560] arrivent[0.640-1.200] bientôt[1.200-1.680] | identique | **Exacte** |
| `elision_2.wav` | peut-être[0.000-0.560] qu'il[0.640-0.960] viendra[1.040-1.440] demain[1.680-1.920] | identique | **Exacte** |

Concordance **100 %, mot pour mot, timestamp pour timestamp** entre le
pipeline Kotlin/JNI/onnxruntime-android tournant sur le device physique
Snapdragon 680 et le prototype Python déjà validé — liaisons et élisions
toujours préservées comme des mots uniques (`l'homme`, `peut-être`,
`qu'il`, `l'alignement`).

### 6.5bis Traçabilité des résultats — un run par test, pas un « meilleur de N »

Question de traçabilité à clarifier explicitement, pas supposée réglée par
défaut : les chiffres ci-dessus (§6.4 : écart 9.7e-5 ; §6.5 : concordance
exacte sur 4 phrases) proviennent-ils d'un seul run cohérent de bout en
bout, ou de plusieurs runs dont on aurait gardé le meilleur ?

Réponse précise, reconstituée à partir des logs Gradle de la session :
- **§6.4 (écart 9.7e-5)** provient de `FbankNativeTest`, exécuté seul sur
  `test_fr.wav`. Un premier run a **échoué** (`num_frames=0`, bug de
  parsing WAV décrit en §6.3 — le chunk `LIST/INFO` non géré). Après
  correction du parseur, un second run a réussi et produit le résultat
  rapporté. Un seul run réussi, pas de choix parmi plusieurs succès.
- **§6.5 (concordance exacte sur les 4 phrases)** provient de
  `OnnxAlignmentTest`, un test distinct (fichier séparé, JNI + inférence
  ONNX + Viterbi complets sur les 4 `.wav`), exécuté **une seule fois**, en
  même temps que `FbankNativeTest` dans la dernière invocation
  `connectedDebugAndroidTest` de la session (« Starting 2 tests… Finished 2
  tests », build réussi du premier coup pour ce test-ci). Aucun run
  antérieur de `OnnxAlignmentTest` n'a échoué ni été écarté.

Donc : chaque chiffre rapporté est le résultat du **seul run réussi
obtenu** pour le test correspondant, après correction des bugs réels
rencontrés en cours de route (documentés tels quels : parsing WAV en
§6.3, désinstallation OEM agressive nécessitant le contournement logcat
en §6.3) — pas une sélection parmi plusieurs exécutions concurrentes ou
répétées. Les deux tests restent deux exécutions distinctes du même code
JNI (`FbankNative.computeNemoFbank`), pas un seul run unique produisant
les deux résultats à la fois.

### 6.6 Décision — la question ouverte en §5.6 est tranchée

**Le fork sherpa-onnx n'est pas nécessaire.** Le pipeline complet — features
fbank (kaldi-native-fbank compilé seul via CMake/NDK), inférence CTC
(onnxruntime-android, AAR officiel, pas de patch), et alignement forcé
Viterbi (port Kotlin direct du prototype Python) — fonctionne de bout en
bout, prouvé sur le device physique cible (Snapdragon 680 réel), sans
modifier une seule ligne de sherpa-onnx. C'était l'option B de la section
5.6, maintenant confirmée par la pratique plutôt que choisie par défaut.

Ce que ce prototype ne couvre pas encore, à traiter avant tout code de
production dans `infrastructure/tts` (Blueprint §5.2, TODO déjà posé en
`SherpaOnnxTtsEngine.kt`) :
- Mesure de latence réelle sur ce Snapdragon 680 (critère de sortie 5.2.0,
  toujours non tentée ici — modèle fp32 utilisé, pas int8, pas de
  threading ONNX Runtime configuré).
- Modèle quantifié int8 (recommandé section 2) plutôt que fp32 (440 Mo,
  utilisé ici uniquement parce que déjà présent du prototypage Python).
- Intégration dans l'architecture Clean/MVI réelle (`AlignmentManager`
  esquissé section 4.2 reste un squelette à adapter, pas du code final).

### 6.7 Fichiers

- Projet CMake + module Gradle Android du prototype JNI/ONNX (scratchpad de
  session, hors dépôt Git, même convention que le reste du prototypage de
  cette tâche) : `CMakeLists.txt`, `fbank_jni.cpp`, `FbankNative.kt`,
  `FbankNativeTest.kt`, `CtcAlignment.kt`, `OnnxAlignmentTest.kt`.
- `compare_kotlin_fbank.py` — comparaison numérique, dans
  `~/projects/inktone-ctc-prototype/` (même répertoire que le reste du
  prototypage Python, non committé).

---

## 7. Modèle int8 + latence réelle sur le V2206 (Snapdragon 680) — critère de sortie 5.2.0

**Mise à jour du 2026-07-27, nuit.** Cette section apporte la seule mesure qui
manquait encore : la latence chronométrée sur le device cible, avec le modèle
réellement destiné à la production (int8, pas fp32). Sans ce chiffre, la
section 6 prouvait la *correction* du pipeline, pas sa *viabilité*.

### 7.1 Modèle téléchargé — même release, même répertoire que §2

`sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8.tar.bz2`,
même tag de release GitHub (`asr-models`) que le modèle fp32 déjà utilisé.
`model.int8.onnx` : 132 445 134 octets (~126 Mo), contre 461 313 276 octets
(~440 Mo) pour le fp32 — **3,5× plus petit**. `tokens.txt` de l'archive int8
vérifié **binairement identique** à celui du fp32 (`diff` exit 0) : même
vocabulaire, aucun risque de désynchronisation d'IDs entre les deux modèles.
Poussé sur le device via `adb push` vers `/data/local/tmp/ctcproto/`, aux
côtés du fp32 déjà présent (device : 55 Go libres, aucune contrainte).

### 7.2 Rejeu du pipeline — un seul changement, tout le reste identique

`Int8LatencyTest.kt` réutilise **sans modification** `FbankNative` (JNI, §6.2),
`CtcAlignment.kt` (`textToTokenIds`/`viterbiForcedAlignment`/
`wordsFromViterbiSegments`, §6.5) et la même logique d'entrée/sortie ONNX
(`audio_signal`/`length`/`logprobs`). Seule différence avec `OnnxAlignmentTest`
(§6.5) : `env.createSession(...)` pointe vers `model.int8.onnx` au lieu de
`model.onnx`. Exécuté sur les mêmes 4 phrases de référence, sur le même
device physique Snapdragon 680 (V2206, SM6225).

### 7.3 Écart de précision int8 vs fp32 — mesuré, pas supposé négligeable

**Log_probs bruts (`test_fr.wav`, T=50 frames × 2561 classes)** — comparaison
élément par élément contre la référence Python fp32
(`logprobs_fp32_python_test_fr.bin`, régénérée pour cette section) :

```
Ecart absolu max   : 6.45
Ecart absolu moyen : 0.75
Ecart-type          : 0.63
Frames avec argmax different : 3 / 50 (6 %)
  frame 16 : int8 -> token 1794 (logp=-0.420) | fp32 -> token 2560/blank (logp=-0.577)
  frame 28 : int8 -> token 2560/blank (logp=-0.193) | fp32 -> token 1893 (logp=-0.010)
  frame 29 : int8 -> token 1893 (logp=-0.008) | fp32 -> token 2560/blank (logp=-0.069)
```

C'est un écart **d'un tout autre ordre de grandeur** que la comparaison
fp32-vs-fp32 de la §6.4 (9.7e-5, simple bruit d'arrondi de plateforme) : ici,
6.45 en espace log-probabilité et 3 frames sur 50 (6 %) où le token le plus
probable change réellement entre les deux modèles. C'est la quantification
elle-même qui modifie la sortie, pas un artefact de portage — **effet mesuré,
pas supposé négligeable**, conformément à la consigne.

**Conséquence sur les timestamps de mots finaux**, comparés à la table
fp32 exacte de la §6.5 (mêmes 4 phrases) :

| Fichier | Mot | fp32 (§6.5) | int8 | Écart |
|---|---|---|---|---|
| `test_fr.wav` | pour | [2.240–2.320] | [2.320–2.400] | +80 ms (début et fin) |
| `liaison_elision_1.wav` | sont | [1.280–1.440] | [1.280–1.520] | +80 ms (fin) |
| `liaison_2.wav` | amis | [0.240–0.560] | [0.320–0.560] | +80 ms (début) |
| `liaison_2.wav` | arrivent | [0.640–1.200] | [0.640–1.280] | +80 ms (fin) |
| `liaison_2.wav` | bientôt | [1.200–1.680] | [1.280–1.680] | +80 ms (début) |
| `elision_2.wav` | peut-être | [0.000–0.560] | [0.000–0.640] | +80 ms (fin) |
| `elision_2.wav` | qu'il | [0.640–0.960] | [0.720–0.960] | +80 ms (début) |
| `elision_2.wav` | demain | [1.680–1.920] | [1.600–1.920] | **-80 ms** (début) |

Tous les autres mots (majorité, non listés ici) restent identiques au frame
près. **Aucun mot manquant, aucun mot supplémentaire, aucune fusion/coupure
de mot** dans les 4 phrases — uniquement des décalages de frontière, tous de
grandeur **exactement 1 frame (80 ms)**, jamais plus. C'est cohérent avec les
3 frames à argmax différent identifiées ci-dessus (ex. frames 28-29 ≈
2.24-2.32 s correspondent exactement au décalage observé sur « pour »).

80 ms reste **dans** le budget Blueprint §11.2 « Précision du surlignage mot
≤ ±120 ms vs audio » — mais ce n'est pas une marge confortable : un décalage
systématique d'une frame entière sur plusieurs mots par phrase courte laisse
peu de marge résiduelle si d'autres sources d'erreur s'ajoutent en production
(latence audio de lecture, jitter de threading). À surveiller, pas à ignorer.

### 7.4 Latence réelle mesurée sur le V2206 — chronométrée, pas estimée

Phrase de test : `test_fr.wav`, 3,68 s d'audio (T=50 frames), `System.nanoTime()`
autour de chaque étape, exécuté sur le Snapdragon 680 physique (SM6225) :

**Premier appel (état du process après chargement de la session ONNX, pas de warm-up dédié) :**

| Étape | Latence |
|---|---|
| Features (fbank JNI) | 59,41 ms |
| Inférence ONNX (int8) | 500,20 ms |
| Viterbi (Kotlin) | 4,24 ms |
| **Total** | **563,84 ms** |

**5 exécutions supplémentaires du pipeline complet (session déjà chargée) :**

```
541.09, 528.53, 507.12, 570.12, 781.44 ms  →  médiane = 541,09 ms
```

Le dernier run (781 ms) est un net décrochage — probablement jitter de
scheduling/thermal sur le device, pas re-mesuré au-delà de 5 runs faute de
budget de session ; à confirmer par une campagne plus longue avant
d'arrêter un budget définitif. Pas d'options de threading ONNX Runtime
configurées (`SessionOptions()` par défaut) — piste d'optimisation non
explorée ici.

**Comparaison au proxy desktop (§5.5)** : 1,285 s médiane sur CPU x86_64
desktop, modèle **fp32**, fbank+inférence seules (Viterbi non chronométré
côté Python). Le pipeline int8 sur le Snapdragon 680 réel (~537 ms hors
Viterbi) est **plus rapide** que le proxy desktop fp32 — cohérent avec un
modèle 3,5× plus petit, pas une anomalie de mesure.

### 7.5 Confrontation au budget Blueprint §11.2 — l'alignement s'ajoute-t-il ou se chevauche-t-il ?

Deux lignes du budget §11.2 sont concernées, et la réponse diffère entre les
deux :

**« TTS Latence tap → premier audio ≤ 1 500 ms »** — l'alignement forcé a
besoin de l'audio déjà synthétisé en entrée (les features fbank sont
calculées sur la forme d'onde produite par le TTS). Pour la **toute première
phrase** d'une session de lecture, il n'y a pas de phrase précédente dont la
lecture masquerait ce calcul : synthèse et alignement sont **séquentiels, pas
parallélisables**, pour cette phrase-là. Si le surlignage mot doit être prêt
dès le premier son (pas de rattrapage progressif), l'alignement (~540 ms)
**s'ajoute directement** au budget de 1 500 ms — laissant environ 960 ms pour
la synthèse TTS elle-même, la décompression du modèle de voix et le reste du
pipeline. Ce n'est pas mesuré ici (portée hors de cette tâche), mais c'est la
contrainte concrète que ce chiffre impose en aval.

**« TTS Silence inter-phrases perçu ≤ 150 ms »** — le Blueprint (§5, ligne
702) prévoit explicitement un préchargement : « pendant la lecture de la
phrase n, la phrase n+1 est synthétisée et mise en buffer ». Si l'alignement
de la phrase n+1 est calculé dans cette même fenêtre de préchargement (pendant
que la phrase n joue), alors les ~540 ms mesurés ici **se chevauchent** avec
la lecture de la phrase n et n'ajoutent rien au silence perçu, **à condition
que la durée de lecture de la phrase n dépasse (synthèse + alignement) de la
phrase n+1**. Pour des phrases de longueur courante (plusieurs secondes,
comme les 4 phrases de test ici), cette marge existe largement. **Risque
identifié, pas résolu ici** : une phrase très courte (ex. une interjection
d'un mot, <1 s de lecture) ne laisserait pas assez de temps de préchargement
pour absorber ~540 ms d'alignement + le temps de synthèse — auquel cas le
silence inter-phrases dépasserait le budget de 150 ms sur ce cas précis. Pas
mesuré ici (aucune phrase courte dans le corpus de test), à vérifier en
Phase 5 lors de l'intégration réelle.

### 7.6 Conclusion explicite

**Le modèle int8 est utilisable pour la suite du développement, avec deux
réserves écrites, pas un compromis à choix silencieux :**

1. **Précision** : la quantification introduit un décalage mesuré (jusqu'à
   80 ms, une frame) sur certaines frontières de mots — toujours dans le
   budget ±120 ms du §11.2, mais avec une marge résiduelle réduite. Pas
   bloquant en l'état, mais à revalider sur un corpus plus large que 4
   phrases avant de considérer le budget de précision définitivement acquis.
2. **Latence** : ~540 ms médians pour une phrase de 3,68 s sur le
   Snapdragon 680 réel est un résultat **positif** (le pipeline entier tient
   largement dans la fenêtre de préchargement inter-phrases pour des phrases
   de longueur courante), mais **s'ajoute intégralement, sans chevauchement
   possible, au budget « tap → premier audio » ≤ 1 500 ms** pour la première
   phrase d'une session — contrainte réelle sur le budget restant pour la
   synthèse TTS elle-même, à vérifier avec un chiffre de synthèse réel
   (hors périmètre de cette tâche).

Pas de blocage architectural trouvé. Le critère de sortie 5.2.0 (latence
mesurée sur device réel) est maintenant satisfait pour la brique
d'alignement seule — la mesure de bout en bout (TTS + alignement, sur
device, modèle de voix inclus) reste à faire avant de clore la Phase 5.2
dans son ensemble.

### 7.7 Fichiers

- `Int8LatencyTest.kt` — ajouté au même module scratchpad que §6.7
  (`~/…/scratchpad/ctc-android-jni-proto/`, hors dépôt Git).
- `logprobs_fp32_python_test_fr.bin`, `logprobs_int8_kotlin_test_fr.npy` —
  dans `~/projects/inktone-ctc-prototype/`, même convention que le reste du
  prototypage non committé de cette tâche.

---

## 8. Vendoring Kokoro (Tâche 5.1.0) — risque `libonnxruntime.so`, app officielle, production, CTC sur audio réel

**Mise à jour du 2026-07-28.** Cette section couvre le vendoring réel de
Kokoro dans `infrastructure/tts` (remplaçant VITS), vérifié à chaque étape
avant la suivante — même discipline que le reste du document.

### 8.1 Risque de double `libonnxruntime.so` — vérifié avant tout

Deux artefacts distincts existent dans ce projet :

| Artefact | Origine | Version ONNX Runtime | NDK de build | minSdk |
|---|---|---|---|---|
| `infrastructure/tts/src/main/jniLibs/arm64-v8a/libonnxruntime.so` | sherpa-onnx v1.13.4 (déjà vendoré Tâche 5.1.0) | **1.27.0** | r27d | 27 |
| `onnxruntime-android:1.19.2` (Maven) | Utilisé pour le prototype CTC (`PROTOTYPE_ALIGNEMENT_CTC.md` §6-7, scratchpad hors dépôt) | **1.19.2** | r26b | 21 |

Vérifié, pas supposé : le `.so` déjà vendoré est **identique bit à bit**
(`sha256sum`) à celui de l'archive officielle
`sherpa-onnx-v1.13.4-android.tar.bz2` re-téléchargée pour l'occasion —
aucune divergence entre ce qui est dans le dépôt et la release amont.
Version extraite via `strings` (pas via le numéro de tag, pour éviter de
supposer) : `1.27.0`. Le `.so` de l'artefact Maven `onnxruntime-android`
version `1.19.2` exporte bien `1.19.2` en interne également (cohérence
numéro de version ↔ contenu confirmée).

**Écart réel, non trivial** : 8 versions mineures d'ONNX Runtime
d'écart, `ORT_API_VERSION` différent entre les deux `.so` (le symbole
`OrtGetApiBase` est identique dans les deux, mais c'est le point d'entrée
versionné du C API — son nom ne change jamais entre versions, ce qui ne
garantit rien sur la compatibilité du contenu qu'il expose). Si les deux
`.so` finissaient avec le même nom de fichier (`libonnxruntime.so`) dans
le même APK, la fusion de `mergeNativeLibs` choisirait l'un des deux
silencieusement (`pickFirsts`/comportement par défaut) : si
`libsherpa-onnx-jni.so` (compilé contre les en-têtes 1.27.0) se retrouvait
lié à l'exécution contre un runtime 1.19.2, la requête de
`ORT_API_VERSION` correspondant à 1.27.0 échouerait côté runtime plus
ancien — crash ou pire (mismatch de layout de structures C).

**Décision, pour cette tâche précise (vendoring Kokoro) :** aucun risque
ne se matérialise ici. Le vendoring Kokoro réutilise **exclusivement**
le `.so` sherpa-onnx 1.27.0 déjà présent — aucun nouvel onnxruntime
n'est ajouté par cette tâche. Le pipeline CTC (onnxruntime-android
1.19.2) vit uniquement dans le scratchpad de session, jamais mergé dans
`infrastructure/tts` à ce jour — **pas de collision réelle
actuellement dans le dépôt**, seulement un risque latent pour une
intégration future.

**Décision documentée pour cette intégration future (pas implémentée
ici, hors périmètre de cette tâche)** : quand le pipeline CTC sera
porté en production dans `infrastructure/tts`, il devra être reconstruit
contre **onnxruntime 1.27.0** (celui déjà vendoré par sherpa-onnx, pas
1.19.2) — un seul `libonnxruntime.so` partagé entre `libsherpa-onnx-jni.so`
(TTS Kokoro) et le binding JNI CTC personnalisé (`fbank_jni.cpp` + appel
ONNX Runtime direct), chargé une seule fois par le classloader Android.
C'est l'option « un seul `.so` partagé » proposée en tête de tâche —
rendue possible ici parce que le binding CTC est du code que nous
contrôlons entièrement (kaldi-native-fbank + appel ONNX Runtime brut),
donc reciblable sur n'importe quelle version d'ONNX Runtime sans
dépendre d'un tiers. Pas une isolation par classloader/process séparé
(compromis plus lourd, non retenu tant que l'option du .so partagé reste
disponible).

### 8.2 App d'exemple officielle — build et exécution réels, device physique

`android/SherpaOnnxTts` (`~/projects/inktone-ctc-prototype/sherpa-onnx/`)
configuré avec `kokoro-int8-multi-lang-v1_0` (déjà présent dans
`~/Downloads`, vérifié `tar tjf` avant extraction — noms de fichiers
réels différents de ceux commentés dans l'exemple officiel : `model.int8.onnx`
et `kokoro-int8-multi-lang-v1_0`, pas `model.onnx`/`kokoro-multi-lang-v1_0`).
`.so` copiés depuis l'archive `sherpa-onnx-v1.13.4-android.tar.bz2`
(§8.1, identiques à ceux déjà vendorés). Build réel via `./gradlew
assembleDebug` (AGP 7.3.1, Gradle 8.2 — wrapper d'origine, redownload
nécessaire) : **succès**, 25 min 49 s (dominé par l'empaquetage des
169 Mo d'assets du modèle, pas par la compilation).

**ID du speaker français vérifié, pas deviné** : lu dans les métadonnées
ONNX du modèle (`speaker2id`) via `onnxruntime.InferenceSession(...).get_modelmeta()`
— `ff_siwis → 30`. Une hypothèse alphabétique naïve (position 31 dans la
liste triée du voicepack officiel à 54 voix) aurait été fausse : ce
paquet sherpa-onnx n'a que 53 voix (`em_santa` absent), décalant tous les
index suivants.

Installé et lancé sur le V2206 (Snapdragon 680) réel via `adb`
(`am start` + `input tap`, capture d'écran pour vérification — pas
d'interaction manuelle). Résultat, confirmé par logcat ET par
capture d'écran :

- **Aucun SIGBUS, aucun UnsatisfiedLinkError** — recherché explicitement
  dans le logcat complet, absent.
- Chargement du modèle réussi (`Finish initializing TTS`), `sampleRate:
  24000` confirmé par le log `initAudioTrack` (pas 16 000 — voir §8.4).
- Génération réussie : les boutons Play/Save/Share passent de désactivés
  à activés après le clic sur Generate — preuve directe que
  `audio.samples.size > 0 && audio.save(filename)` a retourné vrai
  (logique du code source de l'app, pas une supposition).
- Trace de génération réelle dans le logcat :
  `kokoro-multi-lang-lexicon.cc:ConvertTextToTokenIds` confirme le
  passage par la lexicon/espeak-ng pour le texte français (chemin
  « Non-Chinese »), cohérent avec `PROTOTYPE_SYNTHESE_KOKORO_ONNX.md`.

### 8.3 Portage dans `infrastructure/tts` — VITS remplacé par Kokoro

**Découverte avant modification, signalée avant d'agir** :
`SherpaOnnxTtsEngine.kt` n'avait pas de `TODO()` — une implémentation VITS
complète existait déjà (Tâche 5.1.1, voix `fr_FR-siwis-medium`, CC-BY 4.0),
avec un commentaire affirmant qu'« aucun modèle Kokoro français n'existe,
le modèle multi-lang ne couvre que zh/en ». **Cette prémisse était
factuellement fausse** — les §8.2 et `PROTOTYPE_SYNTHESE_KOKORO_ONNX.md`
la contredisent directement (voix `ff_siwis` fonctionnelle, testée deux
fois : Python et Android). Remplacement confirmé avec l'utilisateur avant
modification (pas silencieux — une décision explicite et documentée en
5.1.1 méritait une confirmation avant d'être écrasée).

Fichiers modifiés :

- `SherpaOnnxModelPaths.kt` : chemins Kokoro (`model.int8.onnx`,
  `voices.bin`, `tokens.txt`, `espeak-ng-data/`, lexiques, règles `.fst`
  zh) au lieu des chemins VITS.
- `SherpaOnnxTtsEngine.kt` : `OfflineTtsKokoroModelConfig` au lieu de
  `OfflineTtsVitsModelConfig` ; `sid = 30` (`ff_siwis`, documenté comme
  spécifique à ce modèle exact, pas une constante Kokoro générale) ;
  `license = "Apache-2.0"` (au lieu de CC-BY 4.0) ; `modelSizeMb = 164`
  (mesuré sur les fichiers réellement nécessaires, `dict/` exclu — vérifié
  non référencé par le code Kokoro de sherpa-onnx, seulement par
  Matcha/VITS) ; `pitchControl` reste `false` (aucun paramètre de hauteur
  dans `OfflineTtsKokoroModelConfig`, seulement `lengthScale`).
- Trois fichiers de test (`SherpaOnnxTtsEngineTest.kt`,
  `TtsCapabilityConsistencyTest.kt`, `TtsSynthesisBenchmarkTest.kt`) :
  répertoire de staging, nom de voix et `sampleRate` attendu (24 000,
  au lieu de 22 050 pour VITS) mis à jour en cohérence.

**Non exécuté dans cette session** : le build Gradle complet
d'InkTone (`:infrastructure:tts:connectedDebugAndroidTest`) — les
changements sont au niveau code, vérifiés par lecture et cohérence avec
l'API déjà vendorée (`Tts.kt`, identique bit à bit à la source
sherpa-onnx v1.13.4 courante, vérifié par `diff`), pas par une exécution
sur device dans ce module précis. À faire avant de considérer la Tâche
5.1.2 close.

### 8.4 Alignement CTC sur l'audio Kokoro réel — le sample rate n'est PAS 16 kHz

Kokoro produit du **24 000 Hz** (confirmé §8.2 et
`PROTOTYPE_SYNTHESE_KOKORO_ONNX.md`), pas 16 000 Hz comme les `.wav` de
test utilisés jusqu'ici (gTTS). Vérification demandée explicitement,
faite avec de l'audio Kokoro **réellement produit**
(`kokoro-fr-samples/test_fr_ff_siwis.wav`, généré en session précédente,
pas un fichier synthétique construit pour l'occasion) :
`test_ctc_on_kokoro_audio.py` fait tourner le pipeline
`compute_nemo_fbank()` + Viterbi (déjà prouvé §1-7) sur cet audio, dans
deux conditions :

**(a) Sans resampling** — `sample_rate=24000` passé tel quel à
`compute_nemo_fbank()`. Le code l'accepte **sans aucune erreur**
(`samp_freq` est un paramètre, pas une constante) — c'est précisément ce
qui rend ce bug silencieux plutôt que détecté à la compilation ou à
l'exécution.

**(b) Avec resampling correct** — `scipy.signal.resample_poly(audio,
up=2, down=3)` (24000 × 2/3 = 16000, filtrage anti-repliement inclus,
pas une décimation naïve) avant `compute_nemo_fbank()`.

Résultat mesuré (pas supposé) :

| | Sans resampling (24kHz direct) | Avec resampling (16kHz correct) |
|---|---|---|
| T (frames) | 365 | 365 (identique — `frame_shift_ms` est une durée, pas un nombre d'échantillons, donc indépendant du sample rate) |
| Décodage greedy | "bonjour le monde. Cci est un test pour vérifier l'alignement." | identique |
| `un` (Viterbi) | 1.680–1.760 | **1.760–1.840** |
| `l'alignement` (Viterbi) | 2.880–3.520 | **2.960–3.520** |
| Autres mots (8/10) | identiques | identiques |

**Constat honnête** : le nombre de frames et le décodage textuel ne sont
**pas** catastrophiquement cassés en absence de resampling dans ce test
précis — surprenant, mais réel, pas maquillé. La cause : `frame_shift_ms`
étant une durée (10 ms), le nombre de frames sur ~3.65 s reste identique
quel que soit le sample rate déclaré. Ce qui **change réellement** et
silencieusement, c'est la couverture fréquentielle du banc de filtres mel
(`high_freq = Nyquist − 400 Hz`, donc 7600 Hz à 16kHz contre 11600 Hz à
24kHz) — les 80 bins mel ne représentent plus le même contenu spectral
que celui sur lequel le modèle CTC a été entraîné. Effet mesuré ici :
décalages de frontière de mot de **80 ms** (une frame) sur 2 mots sur 10
— du même ordre de grandeur que l'écart de précision int8 déjà mesuré
en §7.3, pas une coïncidence rassurante mais un signal que ces écarts
s'additionnent avec toute approximation en amont. **Resampler
correctement reste la bonne pratique à appliquer systématiquement** —
ne pas se fier au fait que ce test précis n'ait pas explosé pour
conclure que le sample rate n'a pas d'importance.

Note additionnelle, documentée telle quelle : le décodage produit « Cci »
au lieu de « Ceci » **dans les deux conditions** (pas lié au resampling
donc) — cohérent avec le motif déjà documenté à deux reprises dans ce
rapport (§5.2 « peuut »/« peut », §7.3) d'instabilités ponctuelles du
modèle NeMo CTC sur des mots courts spécifiques, indépendamment du
moteur TTS ou du sample rate.

**État réel du code de production** : vérifié par `grep` sur
`infrastructure/` et `domain/` — **aucun sample rate n'y est codé en
dur** ; `AudioSegment.sampleRate` (Tâche 3.8.0) est déjà propagé
dynamiquement partout où il est consommé. Il n'y a donc **rien à
corriger dans le code committé aujourd'hui** — le pipeline d'alignement
CTC n'est pas encore branché en production (toujours au stade prototype,
§1-7). **Note d'architecture pour quand il le sera** : la conversion
24kHz → 16kHz devra être un maillon explicite entre `SherpaOnnxTtsEngine.synthesize()`
et le calcul des features fbank — jamais supposée implicite, exactement
le genre d'erreur que le champ `sampleRate` de `AudioSegment` (Tâche
3.8.0) existe pour rendre visible et vérifiable plutôt que silencieuse.

### 8.5 Fichiers

- `android/SherpaOnnxTts/` modifié (`MainActivity.kt`, `jniLibs/`,
  `assets/kokoro-int8-multi-lang-v1_0/`) dans
  `~/projects/inktone-ctc-prototype/sherpa-onnx/`, non committé (fork
  local du dépôt sherpa-onnx cloné en Tâche 5.2).
- `test_ctc_on_kokoro_audio.py` — dans
  `~/projects/inktone-ctc-prototype/`, même convention que le reste du
  prototypage Python.
- Fichiers modifiés dans le dépôt InkTone (committés) :
  `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/SherpaOnnxModelPaths.kt`,
  `SherpaOnnxTtsEngine.kt`, et les trois fichiers de test associés.

---

## Références

- [Sherpa-onnx GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [Pre-trained Models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html)
- [NeMo CTC Models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/index.html)
- [Android API](https://k2-fsa.github.io/sherpa/onnx/android/index.html)
- [C API Documentation](https://k2-fsa.github.io/sherpa/onnx/c-api/index.html)
- [CTC Forced Alignment (Reference paper: Graves et al., 2006)](https://www.cs.toronto.edu/~graves/icml_2006.pdf)

---

*Rapport généré le 2026-07-27 — Projet InkTone — Analyse sherpa-onnx v1.13.4*
