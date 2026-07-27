# PROTOTYPE_SYNTHESE_KOKORO_ONNX.md — Prototype de synthèse Kokoro (français)

> **Projet InkTone** — Prototype de synthèse Kokoro via ONNX Runtime direct
> (préalable éventuel à la Tâche 5.1 TTS Engine)
> **Date :** 2026-07-28
> **Objectif :** prouver la synthèse Kokoro en français AVANT tout code
> Android — même principe que le pipeline CTC (`PROTOTYPE_ALIGNEMENT_CTC.md`) :
> pas de code de production écrit avant que chaque étape soit vérifiée en
> pratique, pas supposée.

---

## 0. Fichier déjà présent dans `~/Downloads` — vérifié, pas le même objet

Avant tout téléchargement, vérification demandée de
`/home/majeur/Downloads/` : un fichier `kokoro-int8-multi-lang-v1_0.tar.bz2`
(131 Mo) y est déjà présent. Inspection de son contenu (`tar tjf`, sans
extraction complète) :

```
model.int8.onnx, voices.bin, tokens.txt, lexicon-zh.txt, lexicon-gb-en.txt,
lexicon-us-en.txt, date-zh.fst, number-zh.fst, phone-zh.fst, dict/ (jieba,
pos_dict, ...), espeak-ng-data/ (bundle complet), README.md, LICENSE
```

**Ce n'est pas l'objet demandé par cette tâche.** C'est le paquet Kokoro
« multi-lang » packagé pour **sherpa-onnx** (nommage `model.int8.onnx`,
`voices.bin`, `tokens.txt`, lexiques chinois dédiés) — pas la distribution
brute `kokoro-v1.0.onnx` + `voices-v1.0.bin` du dépôt officiel
`thewh1teagle/kokoro-onnx` demandée ici, dont l'intérêt spécifique est de
passer par `kokoro-onnx` (pip) + son G2P Python réel pour l'identifier
précisément (§4). Les deux distributions ne sont pas interchangeables pour
répondre à la question posée — **pas réutilisé pour cette tâche**, mais son
existence est directement pertinente à la conclusion de la §6 : il contient
déjà un `espeak-ng-data/` complet, cohérent avec ce que la §4 découvre côté
Python.

---

## 1. Installation — `pip install kokoro-onnx`, dépendances réelles

```bash
pip install kokoro-onnx
```

Paquets réellement installés (pas supposés) : `kokoro-onnx==0.4.7`,
**`phonemizer-fork==3.3.1`**, **`espeakng-loader==0.2.4`**, `colorlog`,
`numpy`, `onnxruntime` (déjà présent). **Aucune dépendance `misaki`** —
fait constaté avant même d'exécuter quoi que ce soit, qui répond par
avance à une partie de la question de la §4 : ce paquet PyPI précis
(`kokoro-onnx` par `thewh1teagle`) ne passe pas par misaki.

`espeakng-loader` fournit un `libespeak-ng.so` + un `espeak-ng-data/`
**portables, embarqués dans le wheel** (10,1 Mo), chargés par `ctypes` — pas
d'installation système d'espeak-ng requise. Confirmé par l'exécution :

```python
espeakng_loader.get_data_path()    # .../espeakng_loader/espeak-ng-data
espeakng_loader.get_library_path() # .../espeakng_loader/libespeak-ng.so
```

---

## 2. Modèles téléchargés — dépôt officiel, pas devinés

Assets réels du tag `model-files-v1.0` de `thewh1teagle/kokoro-onnx`
(vérifiés via l'API GitHub avant téléchargement, pas une URL supposée) :

| Fichier | Taille |
|---|---|
| `kokoro-v1.0.onnx` | 325 532 387 octets (~310 Mo, fp32) |
| `voices-v1.0.bin` | 28 214 398 octets (~27 Mo) |

**Voix disponibles : 54 au total, une seule française : `ff_siwis`**
(féminine — corpus SIWIS). Aucune voix masculine française dans ce
voicepack v1.0. À noter pour la suite du produit (ADR-018, voix française
de référence à l'onboarding) : le choix n'existe pas côté Kokoro v1.0, une
seule option.

---

## 3. Synthèse des 4 phrases de référence

Mêmes 4 phrases que `PROTOTYPE_ALIGNEMENT_CTC.md` (liaisons/élisions déjà
testées côté alignement), voix `ff_siwis`, `lang="fr-fr"` :

| Fichier | Texte | Durée | RTF (desktop, fp32) |
|---|---|---|---|
| `test_fr` | Bonjour le monde. Ceci est un test pour vérifier l'alignement. | 3.65 s | 0.75 |
| `liaison_elision_1` | L'homme et la femme sont arrivés. | 1.60 s | 0.82 |
| `liaison_2` | Les amis arrivent bientôt. | 1.47 s | 0.85 |
| `elision_2` | Peut-être qu'il viendra demain. | 1.66 s | 0.82 |

RTF < 1 sur CPU x86_64 desktop (fp32, pas int8) : synthèse plus rapide que
le temps réel. **Proxy desktop, pas une mesure Snapdragon 680** — même
réserve méthodologique que §5.5/§7.4 de `PROTOTYPE_ALIGNEMENT_CTC.md`, pas
répétée en détail ici.

---

## 4. Évaluation honnête de la qualité du G2P français

**Limite méthodologique déclarée d'emblée** : cet agent n'a pas de capacité
d'écoute audio littérale. L'évaluation ci-dessous combine trois signaux
objectifs, mais **ne remplace pas une écoute humaine** — une page
d'écoute avec les 4 échantillons a été publiée pour cette raison précise :

**https://claude.ai/code/artifact/65f8d68a-fd10-463b-9baa-09dd24b1ffed**

### 4.1 Transcription phonétique IPA produite (espeak-ng `fr-fr`)

```
test_fr             : bɔ̃ʒˈuʁ lə mˈɔ̃d. səsˌi ɛt œ̃ tˈɛst puʁ veʁifjˈe laliɲəmˈɑ̃.
liaison_elision_1   : lˈɔm e la fˈam sˈɔ̃t aʁivˈe.
liaison_2           : lez amˈi aʁˈiv bjɛ̃tˈoː.
elision_2           : pˈøtˈɛtʁ kil vjɛ̃dʁˈa dəmˈɛ̃.
```

Points vérifiés précisément (pas juste « ça a produit un fichier ») :

- **Liaisons faites correctement** : « les amis » → `lez amˈi` (le /z/ de
  liaison présent, pas « lə ami ») ; « sont arrivés » → `sˈɔ̃t aʁivˈe` (le
  /t/ de liaison présent) ; « est un » → `ɛt œ̃` (liaison /t/ devant
  voyelle).
- **Élisions faites correctement** : « l'homme » → `lˈɔm` (une syllabe, pas
  « lə ɔm ») ; « qu'il » → `kil` (pas « kə il »).
- **Nasales françaises correctes** : ɔ̃ (bonjour, monde, sont), œ̃ (un), ɑ̃
  (alignement), ɛ̃ (bientôt, viendra, demain) — toutes présentes aux bons
  endroits.
- **Voyelles/consonnes spécifiques au français correctement rendues** : ʁ
  (r français, pas le r anglais), ø (« peut »), pas de rhotacisme anglais
  visible.
- **Point d'attention réel, pas caché** : les marques d'accent tonique
  (`ˈ`) d'espeak sont posées **par mot**, comme en anglais — le français
  n'a pas d'accent lexical de ce type (l'accent y est plutôt de groupe
  rythmique, en fin de syntagme). C'est une convention générique
  d'espeak-ng, pas une erreur de configuration ; son effet réel sur la
  prosodie perçue ne peut être jugé qu'à l'écoute (cf. limite ci-dessus).

### 4.2 Retour ASR croisé (NeMo CTC français, déjà validé Tâche 5.2)

Les 4 échantillons ont été redécodés avec le modèle NeMo FastConformer CTC
français déjà validé (`PROTOTYPE_ALIGNEMENT_CTC.md`) — signal objectif et
automatisé, complémentaire à l'inspection IPA :

| Fichier | Référence | ASR | Concordance |
|---|---|---|---|
| `test_fr` | bonjour le monde ceci est un test pour vérifier l'alignement | bonjour le monde, ceci est un test pour vérifier l'alignement | **Exacte** |
| `liaison_elision_1` | l'homme et la femme sont arrivés | l'homme et la femme sont arrivés | **Exacte** |
| `liaison_2` | les amis arrivent bientôt | les amis arrivent bientôt | **Exacte** |
| `elision_2` | peut-être qu'il viendra demain | **peuut** - être qu'il viendra demain | Écart mineur |

**3/4 exactes.** L'écart sur `elision_2` (« peuut » au lieu de « peut »)
est **le même artefact déjà documenté** dans
`PROTOTYPE_ALIGNEMENT_CTC.md` §5.2, sur un échantillon TTS **différent**
(gTTS, pas Kokoro). Deux moteurs de synthèse indépendants produisent le
même écart de reconnaissance sur ce mot précis avec le même modèle ASR —
signe plausible d'une sensibilité du modèle NeMo CTC à ce mot (peut-être
une ambiguïté acoustique intrinsèque), pas forcément un défaut de synthèse
Kokoro. Documenté tel quel, pas masqué, comme le veut la discipline déjà
appliquée à ce prototype.

### 4.3 Vérifications acoustiques basiques

Aucun écrêtage (`clipped_samples=0` sur les 4 fichiers), niveaux RMS
cohérents (0.108–0.125), silence de tête raisonnable (34–38 ms). Rien
d'anormal au niveau signal.

### 4.4 Conclusion de l'évaluation

**Qualité du G2P français jugée bonne sur les critères vérifiables sans
écoute** : liaisons et élisions systématiquement correctes sur les 4 cas
testés (les mêmes cas que la Tâche 5.2, choisis pour être exigeants sur ce
point précis), nasales correctes, ASR de contrôle à 3/4 exact avec un écart
déjà connu et documenté ailleurs. **Ce n'est pas une validation complète** :
seulement 4 phrases courtes, une seule voix, pas de jugement humain sur la
prosodie/le naturel — la page d'écoute ci-dessus doit être utilisée avant
de considérer ce point définitivement acquis.

---

## 5. Bibliothèque de G2P réellement utilisée — pas misaki

**Réponse précise, vérifiée en lisant le code du paquet installé** (pas
supposée) : `kokoro_onnx/tokenizer.py` (`kokoro-onnx==0.4.7`) n'utilise
**pas misaki du tout**. Il appelle directement :

```python
import phonemizer
from phonemizer.backend.espeak.wrapper import EspeakWrapper
import espeakng_loader
...
EspeakWrapper.set_data_path(espeak_config.data_path)  # espeakng_loader
EspeakWrapper.set_library(espeak_config.lib_path)      # libespeak-ng.so
...
phonemes = phonemizer.phonemize(text, lang, preserve_punctuation=True, with_stress=True)
```

**La bibliothèque de G2P réelle à porter est donc `espeak-ng` lui-même**
(via `phonemizer-fork`, un simple wrapper Python autour de la bibliothèque
C `espeak-ng` — pas une logique à réimplémenter, juste un appel de
fonction C avec des données linguistiques). `phonemizer-fork` et
`espeakng-loader` ne sont que des emballages Python ; aucune logique de
G2P n'existe dans ces couches Python elles-mêmes — tout le travail
phonétique est fait par `libespeak-ng.so` et son jeu de données
(`espeak-ng-data/`).

Note de précision sur « misaki » (nommé dans la consigne) : misaki est la
bibliothèque de G2P de l'auteur original de Kokoro (hexgrad), utilisée par
la démo HuggingFace officielle — un G2P anglais fait à la main, avec (par
conception, information générale sur ce projet, non vérifiée ici puisque
non installée) un **repli sur espeak-ng pour les langues non anglaises**,
dont probablement le français. Ce paquet PyPI (`kokoro-onnx`) contourne
misaki entièrement et appelle espeak-ng directement — mais dans les deux
cas, **la dépendance de fond pour le français est la même : espeak-ng**.
Ce n'est pas une coïncidence à ignorer : c'est un signal convergent fort
que c'est bien espeak-ng, et rien d'autre, qui doit être porté.

---

## 6. Le vendoring Sherpa-ONNX (Tâche 5.1.0) reste-t-il nécessaire ?

**Oui — et ce prototype renforce cette conclusion plutôt que de l'affaiblir.**

Espeak-ng n'a pas besoin d'être porté séparément : **sherpa-onnx l'a déjà
fait**, en C++, prêt pour Android. Vérifié dans le clone source déjà
présent (`~/projects/inktone-ctc-prototype/sherpa-onnx/`, utilisé pour la
Tâche 5.2) :

- `sherpa-onnx/csrc/kokoro-multi-lang-lexicon.cc` inclut directement
  `#include "espeak-ng/speak_lib.h"` et l'utilise en repli pour les mots
  hors-lexique (« fallback to espeak »).
- `sherpa-onnx/csrc/offline-tts-kokoro-model.cc` gère un flag de
  métadonnées `has_espeak` — espeak-ng est un composant natif de
  l'implémentation Kokoro de sherpa-onnx, pas un ajout externe.
- Une **API Kotlin complète existe déjà** :
  `sherpa-onnx/sherpa-onnx/kotlin-api/Tts.kt` avec
  `OfflineTtsKokoroModelConfig`, et deux applications Android d'exemple
  fonctionnelles (`android/SherpaOnnxTts/`, `android/SherpaOnnxTtsEngine/`)
  qui utilisent Kokoro dès aujourd'hui.
- Le fichier déjà présent dans `~/Downloads`
  (`kokoro-int8-multi-lang-v1_0.tar.bz2`, §0) est précisément ce paquet
  Kokoro pré-packagé pour sherpa-onnx, **avec son `espeak-ng-data/` déjà
  embarqué** — l'artefact Android-ready existe déjà sur disque, aucun
  travail de portage manuel de G2P n'est nécessaire.

**Conclusion explicite** : le chemin « unifié » exploré dans cette tâche
(`kokoro-onnx` pip + ONNX Runtime direct + phonemizer-fork) a rempli son
rôle — prouver que le français fonctionne avec Kokoro et identifier
précisément la dépendance réelle (espeak-ng). Mais ce n'est **pas** le
chemin à suivre pour le code de production Android : reconstruire à la
main un binding JNI ONNX Runtime + espeak-ng reviendrait à réimplémenter
ce que `offline-tts-kokoro-model.cc`/`kokoro-multi-lang-lexicon.cc` font
déjà, testés et maintenus en amont. **Le vendoring Sherpa-ONNX (Tâche
5.1.0) n'est pas remis en cause — il est le chemin recommandé pour Kokoro
aussi**, y compris pour le français.

Point qui reste vrai quel que soit le chemin choisi : Kokoro (comme tout
modèle Kokoro-esque) ne produit **pas** de timestamps mot-à-mot en sortie
— c'est un modèle de synthèse, pas de reconnaissance. Le besoin de
l'alignement forcé CTC (déjà prouvé de bout en bout,
`PROTOTYPE_ALIGNEMENT_CTC.md` §1–7) **ne disparaît pas** avec Kokoro : il
reste la seule brique qui produit les timestamps nécessaires au
surlignage mot-à-mot, indépendamment du moteur TTS retenu.

---

## 7. Fichiers

- `synthesize_kokoro_fr.py`, `kokoro-onnx-model/` (modèles téléchargés),
  `kokoro-fr-samples/` (4 `.wav` générés) — dans
  `~/projects/inktone-ctc-prototype/`, non committés, même convention que
  le reste du prototypage de cette phase.
- Page d'écoute (artefact HTML, 4 échantillons + transcriptions IPA + ASR) :
  https://claude.ai/code/artifact/65f8d68a-fd10-463b-9baa-09dd24b1ffed

---

## Références

- [kokoro-onnx (thewh1teagle)](https://github.com/thewh1teagle/kokoro-onnx)
- [phonemizer-fork sur PyPI](https://pypi.org/project/phonemizer-fork/)
- [espeak-ng](https://github.com/espeak-ng/espeak-ng)
- [Sherpa-ONNX — support Kokoro](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html)

---

*Rapport généré le 2026-07-28 — Projet InkTone — Prototype kokoro-onnx v0.4.7*
