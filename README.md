# InkTone

Lecteur EPUB Android premium avec narration TTS neuronale synchronisée
mot à mot. Francophone-first, offline-first.

## Aperçu

InkTone combine une lecture visuelle immersive et une narration audio de
haute qualité, avec le surlignage mot à mot comme fonctionnalité
signature : la synthèse vocale n'est jamais un citoyen de seconde zone.
La position de lecture (`Locator`) est unique et exacte, qu'elle vienne
de la lecture visuelle ou de l'écoute. L'accessibilité est une exigence
de la v1, pas une évolution future, et l'application fonctionne
intégralement hors ligne.

## Stack technique

- **Langage / UI** : Kotlin, Jetpack Compose (Material 3)
- **Lecture EPUB** : Readium
- **TTS** : Sherpa-ONNX / onnxruntime via JNI
- **Persistance** : Room (journal mode WAL)
- **Injection de dépendances** : Hilt
- **Architecture** : Clean Architecture, pattern de présentation MVI

Versions clés (voir `gradle/libs.versions.toml` et
`build-logic/convention/`) : compileSdk 35, minSdk 26, targetSdk 34,
AGP 8.6.0, Kotlin 2.0.20. JDK 17 (Temurin) requis pour builder, comme en CI.

## Architecture

Sens de dépendance strict et vérifié automatiquement :

```
Presentation → Application → Domain ← Data ← Infrastructure
```

`domain/` ne dépend jamais d'Android, de Room, de Compose ni d'aucun
autre module du projet. Chaque module applique un convention plugin
`inktone.*` qui câble `checkArchitectureRules` : une dépendance interdite
fait échouer `./gradlew build`, pas seulement la revue de code.

Le dépôt est découpé en modules Gradle par responsabilité :

- `app` — coquille applicative, câblage DI, `MainActivity`
- `core:*` — design system, UI Compose partagée, utilitaires, fakes de test
- `domain` / `data` — entités et règles métier / repositories et mappers
- `infrastructure:*` — Room, SAF, parsers de format, moteurs TTS, media
  session, WorkManager, crash reporting opt-in, synchronisation cloud, OPDS
- `feature:*` — bibliothèque, lecteur, lecteur audio, recherche, import,
  réglages, statistiques, onboarding, synchronisation, catalogues OPDS
- `benchmark` — scénarios macrobenchmark, aucun code métier

Détail complet des modules et de leurs responsabilités :
[`docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md`](docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md)
(chapitre 5).

## Construire et lancer

```bash
./gradlew build                                    # build + tests + règles d'architecture
./gradlew :domain:test                             # tests du domaine uniquement
./gradlew :<module>:checkArchitectureRules          # vérifier un module isolément
bash scripts/check-no-emoji.sh                      # aucun emoji en production
bash scripts/check-no-manage-external-storage.sh    # accès fichiers via SAF exclusivement
```

## Tests

- Tests unitaires JVM par module (domaine, data).
- Toute migration Room est accompagnée, dans le même commit, d'un test
  `MigrationTestHelper`.
- Les tests instrumentés (androidTest — DAO/migrations Room, accessibilité
  Compose) ne tournent pas en CI ; ils sont exécutés manuellement sur
  appareil physique avant tout merge sensible.

## Documentation

| Besoin | Emplacement |
|---|---|
| Architecture cible, tous les chapitres | [`docs/blueprint/`](docs/blueprint/) |
| Décisions d'architecture et alternatives écartées | [`docs/adr/`](docs/adr/) |
| Plan détaillé de la phase/du lot en cours, avancement réel | [`docs/execution/`](docs/execution/) |
| Conventions de contribution | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| Contexte permanent pour Claude Code | [`CLAUDE.md`](CLAUDE.md) |

L'avancement réel du projet ne se lit pas dans ce README : il se
vérifie dans `docs/execution/` et dans `git log --oneline main`.

## Historique

Ce dépôt a été réécrit intégralement le 2026-07-26 (voir
[ADR-019](docs/adr/ADR-019-full-rewrite-orphan-branch.md)).
L'implémentation précédente est archivée en lecture seule sur la branche
`legacy/monolith` : elle ne sert que de référence de comportement, jamais
de source à fusionner dans `main`.

## Contribuer

Voir [`CONTRIBUTING.md`](CONTRIBUTING.md) : toute décision d'architecture
nécessite un ADR, les messages de commit sont en français à l'impératif,
et aucun document de statut n'affirme qu'une fonctionnalité est terminée
sans citer le commit, le fichier ou le test qui le prouve.

## Secrets

`keystore.properties`, `*.jks`, `google-services.json` sont gitignorés et
ne doivent jamais être committés, affichés ni supprimés — en particulier
le keystore de signature, dont la perte serait irréversible pour toute
mise à jour Play Store future.
