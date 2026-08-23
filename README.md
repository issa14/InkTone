# InkTone

**Lecteur d'ebooks Android à narration vocale neuronale, synchronisée mot à mot — et entièrement hors ligne.**

[![CI](https://github.com/issa14/InkTone/actions/workflows/ci.yml/badge.svg)](https://github.com/issa14/InkTone/actions/workflows/ci.yml)
[![Licence MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)](#prérequis)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF.svg)](https://kotlinlang.org)

<p align="center">
  <img src="docs/assets/narration.gif" alt="Le texte défile, chaque mot se surligne à mesure qu'il est prononcé" width="560">
  <br>
  <em>Le surlignage suit la voix, mot par mot.</em>
</p>

| Bibliothèque | Lecture | Narration | Statistiques |
|:---:|:---:|:---:|:---:|
| ![Bibliothèque](docs/assets/library.png) | ![Lecture](docs/assets/reader.png) | ![Narration avec surlignage mot à mot](docs/assets/narration.png) | ![Statistiques](docs/assets/stats.png) |

---

## Ce que c'est

InkTone lit vos livres numériques — EPUB, PDF, texte brut — et les lit **à voix haute**, avec une voix neuronale qui tourne sur l'appareil et un surlignage synchronisé mot par mot.

Tout fonctionne sans connexion : l'import, la lecture, la synthèse vocale. Les services en ligne existent, mais ils sont optionnels et désactivés par défaut.

Le projet est pensé francophone d'abord — voix, interface, découpage des phrases.

## Fonctionnalités

**Lire**
- EPUB, PDF et texte brut, importés depuis n'importe quel dossier de l'appareil
- Défilement continu ou mode paginé
- Taille de texte, interligne, marges, police et thème de lecture réglables
- Luminosité propre au lecteur, indépendante du réglage système
- Signets, annotations et surlignages, recherche plein texte, table des matières

**Écouter**
- Synthèse neuronale locale (Sherpa-ONNX), avec **timestamps par mot** pour un surlignage réellement synchronisé — jamais interpolé
- Voix système Android en repli, moteur cloud optionnel pour qui l'active
- Vitesse, intonation et gain réglables ; règles de prononciation personnalisées
- La narration continue écran éteint, pilotable depuis la notification et l'écran verrouillé
- Minuteur de sommeil

**Organiser**
- Séries, étiquettes, favoris, épinglage
- Filtres, tris et trois dispositions d'affichage
- Catalogues OPDS pour découvrir et importer de nouveaux ouvrages

**Suivre**
- Temps de lecture visuelle et d'écoute comptés séparément
- Série de jours consécutifs, objectif quotidien, carte d'activité horaire
- Vitesse de lecture, statistiques par ouvrage, export CSV et JSON

**Confort**
- Rappel de repos oculaire
- Préréglage d'accessibilité qui pilote taille, disposition et contrastes d'un seul geste
- Sauvegarde locale et restauration ; synchronisation Google Drive optionnelle

## Installation

**Aucune version binaire n'est encore publiée.** La 1.0.0 est en préparation ; ni page Releases ni fiche Play Store pour l'instant.

En attendant, voir [Compiler depuis les sources](#compiler-depuis-les-sources).

### Prérequis

| | |
|---|---|
| Android | 8.0 (API 26) minimum |
| Processeur | **`arm64-v8a` uniquement** — le code natif de synthèse n'est compilé que pour cette architecture. Les appareils 32 bits et les émulateurs x86 ne sont pas pris en charge. |
| Espace disque | ~65 Mo pour l'application, plus les voix neuronales téléchargées séparément |
| Connexion | requise uniquement pour télécharger une voix, parcourir un catalogue OPDS ou synchroniser |

## Voix et modèles

Les modèles de synthèse **ne sont pas embarqués dans l'application** : ils se téléchargent à la demande depuis les réglages ([ADR-018](docs/adr/ADR-018-voice-model-distribution.md)). L'application reste utilisable sans eux, avec la voix système Android.

Leurs licences sont distinctes de celle du code et **s'imposent à toute redistribution** :

| Modèle | Rôle | Licence |
|---|---|---|
| `fr_FR-upmc-medium` (Piper VITS) | synthèse vocale française | **CC-BY-SA-4.0** |
| NeMo FastConformer CTC (NVIDIA) | alignement forcé pour le timing par mot | **CC-BY-4.0**, attribution obligatoire |

Si vous distribuez une version d'InkTone, ces obligations vous incombent. Le choix de ces modèles et le rejet documenté des alternatives à licence disqualifiante sont détaillés dans [ADR-022](docs/adr/ADR-022-kokoro-tts-engine-piper-alternatives-rejected.md).

## Vie privée

- **Rien ne sort de l'appareil par défaut.** Synthèse vocale, lecture et import sont locaux.
- **Aucun accès de masse au stockage.** Les fichiers passent exclusivement par le Storage Access Framework ; la permission `MANAGE_EXTERNAL_STORAGE` est absente, et la CI le vérifie à chaque commit.
- **Rapport de plantage sur consentement seulement**, et sans effet si aucune configuration n'est fournie.
- **Services en ligne optionnels et désactivés** : synchronisation, catalogues OPDS, voix cloud.

À savoir : le fichier de **sauvegarde locale n'est pas chiffré**. Il reste sur le stockage que vous désignez et n'est jamais transmis, mais si vous le recopiez vers un cloud personnel, son contenu — quels livres, quel passage, quand — sera lisible par qui y accède. Voir [CONTRIBUTING.md](CONTRIBUTING.md#sauvegarde-locale-backupmanager-tâche-85--fichier-en-clair).

## État du projet

Version `1.0.0` en préparation, jamais publiée. Toutes les fonctionnalités listées plus haut sont implémentées et vérifiées sur appareil ; aucune n'est un stub.

Points ouverts, connus et assumés :

- **Les tests instrumentés ne tournent pas en CI.** Migrations Room, DAO et accessibilité Compose exigent un émulateur ou un appareil ; ils sont exécutés et vérifiés manuellement avant chaque fusion sensible. La CI couvre le build, les tests JVM, les règles d'architecture et les garde-fous de régression.
- **Le moteur cloud Edge-TTS s'appuie sur une API Microsoft non officielle** ([ADR-024](docs/adr/ADR-024-edge-tts-optional-cloud-engine.md)). Désactivé par défaut, il peut cesser de fonctionner sans préavis.

Ce dépôt applique une règle stricte : aucun document ne déclare une fonctionnalité terminée sans le commit, le fichier ou le test qui le prouve. Pour connaître l'avancement réel, lire `docs/execution/` et `git log`, jamais un résumé.

## Compiler depuis les sources

> **À faire en premier.** `app/libs/sherpa-onnx-1.13.4.aar` est requis pour compiler mais **n'est pas versionné**. Récupérez-le depuis les artefacts du projet [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) et déposez-le à ce chemin, sinon le build échoue immédiatement.

```bash
git clone https://github.com/issa14/InkTone.git
cd InkTone
# déposer app/libs/sherpa-onnx-1.13.4.aar (voir ci-dessus)
./gradlew build          # compile, tests unitaires, règles d'architecture
./gradlew :app:installDebug
```

JDK 17 requis.

`google-services.json` (rapport de plantage) et `local.properties` (OAuth de synchronisation) sont facultatifs : sans eux, le build reste vert et les fonctionnalités concernées se désactivent proprement.

Commandes utiles :

```bash
./gradlew :domain:test                            # tests du domaine seuls
./gradlew :<module>:checkArchitectureRules        # règles d'un module
bash scripts/check-no-emoji.sh                    # garde-fou iconographie
bash scripts/check-no-manage-external-storage.sh  # garde-fou permissions
```

## Architecture

Clean Architecture multi-module, présentation en MVI, un état immuable par écran.

```
Presentation → Application → Domain ← Data ← Infrastructure
```

Le module `domain` ne dépend ni d'Android, ni de Room, ni de Compose, ni d'aucun autre module du projet. **Ce n'est pas une convention de revue : chaque module applique un plugin qui câble `checkArchitectureRules`, et une dépendance interdite fait échouer `./gradlew build`.**

Pile technique : Kotlin, Jetpack Compose (Material 3), Readium, Sherpa-ONNX / ONNX Runtime via JNI, Room, Hilt, Media3.

Le détail — découpage des modules, modèle de domaine, chaîne TTS — est dans le Blueprint, qui fait autorité. Il n'est volontairement pas résumé ici : deux sources finiraient par diverger.

## Documentation

| Pour | Aller voir |
|---|---|
| L'architecture cible, chapitre par chapitre | [`docs/blueprint/`](docs/blueprint/) |
| Les décisions d'architecture et les alternatives écartées | [`docs/adr/`](docs/adr/) |
| L'avancement réel, plans et critères de validation | [`docs/execution/`](docs/execution/) |
| Les conventions de contribution | [CONTRIBUTING.md](CONTRIBUTING.md) |

## Contribuer

Lire [CONTRIBUTING.md](CONTRIBUTING.md) avant d'ouvrir une pull request. L'essentiel :

- Messages de commit **en français, à l'impératif** (`Corrige…`, `Ajoute…`).
- Toute décision d'architecture passe par un **ADR** dans `docs/adr/` — jamais de suppression, un ADR remplacé passe en `Superseded`.
- Toute migration Room est accompagnée de son test **dans le même commit**.
- `./gradlew build` doit rester vert : il inclut les règles d'architecture.

## Licences

Le code d'InkTone est distribué sous **licence MIT** (voir [LICENSE](LICENSE) et [ADR-026](docs/adr/ADR-026-licence-mit-ouverture-du-code.md)).

Ne sont **pas** couverts par cette licence : les liaisons Kotlin de sherpa-onnx (Apache-2.0), les jeux d'icônes (Material Symbols en Apache-2.0, Lucide en ISC), et les modèles de synthèse téléchargés à l'exécution, dont les obligations d'attribution et de partage à l'identique s'imposent à toute distribution.

Le détail complet est dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — **à lire avant toute redistribution**.
