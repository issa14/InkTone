# Mentions de tiers

Le code source écrit pour InkTone est distribué sous licence MIT (voir
[`LICENSE`](LICENSE)). Ce fichier recense ce qui n'est **pas** couvert par
cette licence : le code tiers présent dans le dépôt, les bibliothèques
liées à la compilation, et les modèles téléchargés à l'exécution.

## Code tiers inclus dans le dépôt

| Fichier | Origine | Licence |
|---|---|---|
| [`infrastructure/tts/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt`](infrastructure/tts/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt) | Liaisons Kotlin de [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Copyright (c) 2023 Xiaomi Corporation | Apache-2.0 |

Ce fichier conserve son en-tête de copyright d'origine et **reste sous sa
licence amont** : la licence MIT d'InkTone ne s'y applique pas.

## Binaires non versionnés

`app/libs/sherpa-onnx-1.13.4.aar` est requis pour builder mais n'est pas
versionné (voir `.gitignore`). Il provient du projet
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) et doit
être récupéré depuis les artefacts amont.

## Bibliothèques liées

Versions exactes dans [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Principales dépendances et leurs licences amont, à vérifier à la source
avant toute distribution :

- Readium Kotlin Toolkit — lecture EPUB
- ONNX Runtime (Microsoft) — inférence des modèles TTS
- PdfiumAndroid (`io.legere`) — rendu PDF
- AndroidX / Jetpack Compose / Room / Media3 / Hilt (Google)
- OkHttp (Square), AppAuth (OpenID Foundation)

## Modèles téléchargés à l'exécution

Les modèles de synthèse vocale ne sont pas embarqués dans l'APK : ils sont
téléchargés à la demande depuis l'application
(voir [ADR-018](docs/adr/ADR-018-voice-model-distribution.md)). Leurs
licences sont distinctes de celle du code et **s'imposent à toute
distribution de l'application** :

| Modèle | Rôle | Licence |
|---|---|---|
| Kokoro-82M (hexgrad) | synthèse vocale neuronale | Apache-2.0 |
| NeMo FastConformer CTC multilingue (NVIDIA) | alignement forcé pour le timing mot | **CC-BY-4.0 — attribution obligatoire** |

L'obligation d'attribution CC-BY-4.0 du modèle d'alignement doit être
honorée dans l'application distribuée (écran « À propos »), pas seulement
dans ce dépôt.

Le choix de ces modèles et le rejet documenté des alternatives à licence
disqualifiante sont détaillés dans
[ADR-022](docs/adr/ADR-022-kokoro-tts-engine-piper-alternatives-rejected.md).

## Service cloud optionnel

Le moteur Edge-TTS s'appuie sur une API Microsoft non officielle
([ADR-024](docs/adr/ADR-024-edge-tts-optional-cloud-engine.md)). Il est
désactivé par défaut et relève des conditions d'utilisation de Microsoft,
qu'aucune licence de ce dépôt ne couvre.
