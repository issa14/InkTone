# 📖 ReadFlow

> Lecteur d'ebooks Android avec synthèse vocale neuronale locale — 100% offline, 100% français.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI-00C853)](./architecture.md)
[![License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
[![Status](https://img.shields.io/badge/Status-Phase%200%20%E2%80%94%20Pr%C3%A9paration-orange)](./PROJECT_STATUS.md)

---

## 🎯 Vision

**ReadFlow** est un lecteur d'ebooks Android inspiré de l'UX de **Moon+ Reader**, couplé à un moteur de **synthèse vocale neuronale** (Text-to-Speech) qui tourne **100% en local** sur l'appareil — aucune connexion internet requise.

Le surlignage dynamique **mot-à-mot** synchronisé avec la voix permet une expérience de lecture immersive unique.

### ✨ Fonctionnalités clés (cibles)

- 📚 **Lecture EPUB2 & EPUB3** — Import local via fichier, parsing robuste
- 🗣️ **TTS neuronal local** — Voix française naturelle (Sherpa-ONNX / VITS)
- 🎯 **Surlignage mot-à-mot** — Synchronisation audio ↔ texte en temps réel
- 🎛️ **Contrôles de lecture avancés** — Vitesse, voix, navigation phrase par phrase
- 🌓 **Thèmes multiples** — Clair, sépia, sombre + police OpenDyslexic
- 🔖 **Signets & progression** — Sauvegarde automatique, reprise exacte
- 🔍 **Recherche full-text** — Dans le livre courant
- 🎧 **Background audio** — Notification lockscreen, contrôles Bluetooth
- 📱 **Android 14+** — Material 3, edge-to-edge, per-app language

---

## 🏗️ Architecture

```
UI (Compose)  →  ViewModel (MVI)  →  Domain (UseCases)  →  Data (Room + Files)
                                                              ↓
                                                    Native (ONNX + AudioTrack)
```

Voir le document complet : **[📄 ARCHITECTURE.md](./architecture.md)**

| Composant | Technologie |
|---|---|
| **Langage** | Kotlin 2.x |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | Clean Architecture 4 couches + MVI |
| **DI** | Hilt |
| **Base de données** | Room + FTS5 |
| **Parser EPUB** | Readium Kotlin Toolkit |
| **Moteur TTS** | Sherpa-ONNX (VITS) via ONNX Runtime |
| **Audio** | AudioTrack (PCM gapless) |
| **Background** | MediaSessionService (Media3) |

---

## 🚀 Quickstart

### Prérequis

- **Android Studio** Hedgehog (2024.1+) ou plus récent
- **JDK 17+**
- **Android SDK 34+** (Android 14)
- **NDK 26+** (pour ONNX Runtime et piper-phonemize)

### Build & Run

```bash
# Cloner le repo
git clone https://github.com/issa14/ReadFlow.git
cd ReadFlow

# Ouvrir dans Android Studio
# → Sync Gradle
# → Run sur device Android 14+ (ARM64)

# Ou en ligne de commande
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Premier lancement

1. L'application télécharge automatiquement le modèle vocal français (~60 Mo) au premier démarrage (Wi-Fi recommandé)
2. Importer un fichier `.epub` via le bouton "+" ou le partage Android
3. Ouvrir le livre → appuyer sur ▶️ Play
4. La synthèse démarre — le texte se surligne en temps réel

---

## 📊 Statut du Projet

→ **[📊 PROJECT_STATUS.md](./PROJECT_STATUS.md)** — Suivi détaillé des tâches, bloqueurs, risques et métriques.

**Phase actuelle :** Phase 0 — Préparation & Prototype Sherpa-ONNX  
**Progression :** 0%  
**Livraison estimée :** ~20 semaines

---

## 📂 Structure

```
ReadFlow/
├── 📄 README.md
├── 📄 ARCHITECTURE.md          # Spécifications techniques
├── 📄 PROJECT_STATUS.md        # Suivi d'avancement
├── 📄 CHANGELOG.md
├── 📄 CONTRIBUTING.md
├── 📄 .gitignore
├── 📁 docs/
│   └── 📄 prototype-report.md  # Rapport de prototype
├── 📁 app/                     # Code source Android
│   └── 📁 src/
│       ├── 📁 main/java/com/readflow/
│       │   ├── 📁 ui/          # Compose UI
│       │   ├── 📁 domain/      # UseCases + Models (Kotlin pur)
│       │   ├── 📁 data/        # Room, Repositories
│       │   └── 📁 service/     # ONNX, AudioTrack, Readium
│       └── 📁 test/            # Tests unitaires
└── 📁 gradle/
    └── 📄 libs.versions.toml   # Version Catalog
```

---

## 🤝 Contribuer

Voir **[CONTRIBUTING.md](./CONTRIBUTING.md)**.

Les contributions sont les bienvenues une fois la Phase 2 terminée (pipeline audio stable).

---

## 📜 Licence

Ce projet est sous licence **MIT**. Voir **[LICENSE](./LICENSE)**.

Les modèles ONNX utilisés (Sherpa-ONNX/VITS) sont sous leurs licences respectives (Apache 2.0 / MIT). Readium Kotlin Toolkit est sous licence BSD-3.

---

## 🙏 Remerciements

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — Inference TTS with timestamps
- [Readium Kotlin](https://github.com/readium/kotlin-toolkit) — EPUB parser
- [ONNX Runtime](https://onnxruntime.ai/) — Cross-platform ML inference
- [Moon+ Reader](https://www.moondownload.com/) — UX inspiration
