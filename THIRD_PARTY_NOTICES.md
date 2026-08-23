# Mentions de tiers

Le code source écrit pour InkTone est distribué sous licence MIT (voir
[`LICENSE`](LICENSE) et [ADR-026](docs/adr/ADR-026-licence-mit-ouverture-du-code.md)). Ce fichier recense ce qui n'est **pas** couvert par
cette licence : le code tiers présent dans le dépôt, les bibliothèques
liées à la compilation, et les modèles téléchargés à l'exécution.

## Code tiers inclus dans le dépôt

| Fichier | Origine | Licence |
|---|---|---|
| [`infrastructure/tts/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt`](infrastructure/tts/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt) | Liaisons Kotlin de [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Copyright (c) 2023 Xiaomi Corporation | Apache-2.0 |

Ce fichier conserve son en-tête de copyright d'origine et **reste sous sa
licence amont** : la licence MIT d'InkTone ne s'y applique pas.

## Icônes

Les glyphes de `core/designsystem/src/main/res/drawable/ic_symbol_*.xml` ne
sont pas dessinés pour InkTone : ce sont des tracés amont repris tels quels,
chacun portant sa provenance en en-tête de fichier.

| Jeu | Fichiers | Origine | Licence |
|---|---|---|---|
| Material Symbols Rounded | `ic_symbol_*.xml`, sauf ceux listés ci-dessous | [google/material-design-icons](https://github.com/google/material-design-icons) — Copyright Google | Apache-2.0 |
| Lucide | [`ic_symbol_library.xml`](core/designsystem/src/main/res/drawable/ic_symbol_library.xml), [`ic_symbol_library_big.xml`](core/designsystem/src/main/res/drawable/ic_symbol_library_big.xml) | [lucide-icons/lucide](https://github.com/lucide-icons/lucide) — portions Copyright (c) 2013-2022 Cole Bemis (Feather, MIT) | ISC |

L'illustration de marque (`ic_brand_inktone`) et les tracés dessinés
directement en Compose sont, eux, du travail original couvert par la
licence MIT du dépôt.

## Binaires non versionnés

`app/libs/sherpa-onnx-1.13.4.aar` est requis pour builder mais n'est pas
versionné (voir `.gitignore`). Il provient du projet
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) et doit
être récupéré depuis les artefacts amont.

## Bibliothèques liées

Versions exactes dans [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Liste des dépendances **réellement embarquées dans l'application distribuée**
(les dépendances de test — JUnit, Espresso, Robolectric, MockWebServer,
`androidx.benchmark`, `room-testing`, `work-testing` — ne sont pas
distribuées et ne figurent donc pas ici). Licences telles qu'annoncées en
amont, **à revérifier à la source avant toute distribution** :

| Composant | Rôle | Licence annoncée |
|---|---|---|
| Readium Kotlin Toolkit (`org.readium.kotlin-toolkit`) | lecture EPUB | BSD-3-Clause |
| ONNX Runtime (Microsoft) | inférence des modèles TTS et d'alignement | MIT |
| PdfiumAndroid (`io.legere`) | rendu PDF (enveloppe de Pdfium, BSD-3-Clause) | Apache-2.0 |
| AndroidX, Jetpack Compose, Room, Media3, WorkManager, Navigation, Lifecycle, `core-splashscreen`, `security-crypto` (Google) | socle applicatif | Apache-2.0 |
| Hilt / Dagger (Google) | injection de dépendances | Apache-2.0 |
| kotlinx-coroutines, kotlinx-serialization (JetBrains) | concurrence, sérialisation JSON | Apache-2.0 |
| OkHttp (Square) | client HTTP et WebSocket | Apache-2.0 |
| AppAuth (OpenID Foundation) | flux OAuth de la synchronisation Drive | Apache-2.0 |
| Coil (`io.coil-kt`) | chargement des couvertures | Apache-2.0 |
| jsoup (`org.jsoup`) | analyse du HTML des chapitres EPUB | MIT |
| Apache Commons Compress | extraction des archives `.tar.bz2` des modèles de voix | Apache-2.0 |
| `desugar_jdk_libs` (Google) | rétroportage des API Java 8+ sur minSdk 26 | **GPL-2.0 avec exception Classpath** (dérivé d'OpenJDK) — l'exception couvre la liaison, aucune contamination du code applicatif |
| Firebase Crashlytics (Google) | rapports de plantage, **opt-in** | SDK Apache-2.0 ; le service relève des conditions Firebase |

Deux points à surveiller avant publication :

- `androidx.security:security-crypto` est en **1.1.0-alpha06** — une
  version alpha embarquée dans une release, sur le chemin du chiffrement
  des jetons de synchronisation et des identifiants OPDS/WebDAV. Choix
  assumé (c'est la seule branche compatible avec les API récentes), à
  réévaluer dès qu'une stable existe.
- La licence de `desugar_jdk_libs` est la seule non permissive de la
  liste ; c'est l'exception Classpath qui la rend utilisable ici, pas sa
  nature.

## Modèles téléchargés à l'exécution

Les modèles de synthèse vocale ne sont pas embarqués dans l'APK : ils sont
téléchargés à la demande depuis l'application
(voir [ADR-018](docs/adr/ADR-018-voice-model-distribution.md)). Leurs
licences sont distinctes de celle du code et **s'imposent à toute
distribution de l'application** :

| Modèle | Rôle | Licence |
|---|---|---|
| Voix `fr_FR-upmc-medium` (Piper VITS, entraînée sur le corpus UPMC) | synthèse vocale neuronale française — locuteurs Jessica & Pierre | **CC-BY-SA-4.0** (jeu de données UPMC) — voir [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/fr/fr_FR/upmc/medium/MODEL_CARD) |
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
