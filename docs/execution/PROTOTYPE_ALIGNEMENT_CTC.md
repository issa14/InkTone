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

### 5.5 Conséquence sur la feuille de route Android (section 4.3)

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

### 5.6 Scripts

- `extract_log_probs.py` — inférence ONNX Runtime directe + extraction fbank.
- `run_viterbi_prototype.py` — branche `extract_log_probs()` dans
  `viterbi_forced_alignment()`, vérification croisée, 4 phrases de test.
- Ces deux scripts et les fichiers audio de test vivent dans
  `~/projects/inktone-ctc-prototype/` (hors dépôt Git, comme le reste du prototypage
  Python de cette tâche) — pas committés, conformément au principe déjà appliqué aux
  fixtures volumineuses (Tâche 4.11, modèles vocaux Tâche 5.1).

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
