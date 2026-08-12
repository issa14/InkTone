# InkTone Software Architecture Blueprint

| | |
|---|---|
| **Version** | 1.2.2 |
| **Statut** | Review |
| **Date** | 2026-07-26 |
| **Auteur** | Issa ADAMOU |
| **Remplace** | 1.2.1 |
| **Références** | `REVUE_BLUEPRINT_ARCHITECTURE_V1_2026-07-26.md` · code legacy au commit `69d18a8` |

**Changements majeurs depuis 1.0.0 :** ajout du chapitre 13 (Rewrite & Legacy Strategy) et du chapitre 14 (Testing Strategy) ; liste canonique unique des modules ; scission ReadingState / ReadingSession ; value object Locator unifié ; modèle de capacités TTS avec timestamps mot comme exigence de première classe ; réintégration des champs série/favoris/sujets dans Publication ; spécification MVI ; budgets de performance chiffrés ; modèle de concurrence ; arbitrage data/infrastructure ; réconciliation crash reporting / confidentialité ; ADR réécrits au format complet et 10 nouveaux ADR ; gouvernance documentaire ; purge des résidus éditoriaux.

**1.2.0 (2026-07-26) :** ADR-013 superseded par ADR-021 suite à
l'infirmation empirique de l'hypothèse « timestamps natifs Sherpa-ONNX »
pendant le spike d'ouverture de la Phase 3. Nouvelle architecture à
paliers pour le timing mot (Android natif + `onRangeStart` en Palier 1,
Sherpa-ONNX + alignement forcé CTC en Palier 2). Piper écarté des
moteurs candidats (licence GPL-3.0 depuis octobre 2025). §8.4/8.5/8.9/
8.10 révisés en conséquence. Aucun changement du Domain Model.

**1.2.1 (2026-07-26) :** Corrige K9 (§13.4), devenu obsolète après
ADR-021 — Sherpa-ONNX ne fournit pas de timestamps mot natifs, aucun
moteur ne le fait.

**1.2.2 (2026-07-26) :** Ajoute un addendum à ADR-021 documentant un
comportement réel observé en Tâche 3.1 (device V2206) : certains moteurs
TTS constructeur ne respectent pas la sémantique documentée par Android
pour `onRangeStart`, ce qui justifie la double interprétation défensive
dans `AndroidNativeTtsEngine.resolveWordBoundary()`.

---

## Table des matières

1. [Vision & Philosophy](#1-vision--philosophy)
2. [Architecture Principles](#2-architecture-principles)
3. [Domain Model](#3-domain-model)
4. [System Architecture](#4-system-architecture)
5. [Module Specifications](#5-module-specifications)
6. [Data Model](#6-data-model)
7. [Reading Engine](#7-reading-engine)
8. [TTS Engine](#8-tts-engine)
9. [Synchronization Architecture](#9-synchronization-architecture)
10. [Security & Privacy](#10-security--privacy)
11. [Performance & Optimization](#11-performance--optimization)
12. [Project Structure](#12-project-structure)
13. [Rewrite & Legacy Strategy](#13-rewrite--legacy-strategy)
14. [Testing Strategy](#14-testing-strategy)
15. [Architecture Decision Records](#15-architecture-decision-records-adr)
16. [Future Roadmap](#16-future-roadmap)
17. [Governance & Appendices](#17-governance--appendices)

---

# 1. Vision & Philosophy

## 1.1 Vision

InkTone est une plateforme de lecture numérique moderne qui combine une expérience de lecture immersive avec une synthèse vocale neuronale de haute qualité.

Son objectif est de rendre la lecture plus accessible, plus confortable et plus naturelle, en permettant à chaque utilisateur de passer librement entre la lecture visuelle et l'écoute sans interruption — avec, comme signature du produit, une synchronisation texte-audio à la granularité du mot (§8.9).

InkTone ne cherche pas uniquement à être un lecteur d'ebooks. Il ambitionne de devenir une plateforme où le texte et l'audio coexistent comme deux façons complémentaires de consommer un même contenu.

## 1.2 Mission

Créer le lecteur de livres le plus élégant, le plus performant et le plus intelligent sur Android.

InkTone doit permettre de :

* lire confortablement pendant plusieurs heures ;
* écouter des livres avec une voix naturelle, le texte lu étant surligné mot à mot ;
* reprendre une lecture exactement là où elle a été interrompue, quel que soit le mode (visuel ou audio) ;
* offrir une expérience fluide, rapide et fiable, même hors connexion.

## 1.3 Primary Language & Internationalization

InkTone est un produit **francophone-first** : l'interface, les voix de référence et le traitement du texte (découpage en phrases, abréviations, ponctuation) sont conçus et validés pour le français en priorité.

L'architecture reste internationalisable : toutes les chaînes passent par les ressources Android, le pipeline TTS est paramétré par locale, et l'ajout d'une langue est une extension, jamais une refonte. Aucune langue supplémentaire n'est promise pour la v1.

## 1.4 Philosophy

### Reading First
Chaque décision est prise pour améliorer l'expérience de lecture. L'interface, les animations, les performances et les fonctionnalités servent avant tout la lecture.

### Audio is a First-Class Citizen
La lecture audio n'est pas une fonctionnalité secondaire. Le moteur TTS fait partie intégrante du cœur de l'application. Toutes les fonctionnalités doivent fonctionner aussi bien en lecture visuelle qu'en lecture audio, et la position de lecture est unique quel que soit le mode (§7.7).

### Accessible from Day One
« Rendre la lecture plus accessible » n'est pas un slogan de roadmap : le socle d'accessibilité (labels sémantiques Compose pour TalkBack, tailles de texte dynamiques, contrastes conformes, cibles tactiles suffisantes) est une exigence de la v1 (§16.3). Les capacités avancées (commandes vocales, thèmes à contraste renforcé supplémentaires) viennent ensuite.

### Offline by Default
Toutes les fonctions essentielles sont disponibles sans connexion Internet. L'utilisateur reste propriétaire de sa bibliothèque. Un téléchargement ponctuel (modèle de voix, §8.11) est compatible avec ce principe dès lors que l'usage quotidien n'exige aucune connexion.

### Modularity over Complexity
Chaque composant doit pouvoir évoluer indépendamment. Changer un moteur TTS ou ajouter un format de livre ne doit pas nécessiter de modifier toute l'application.

### Performance is a Feature
Une interface fluide est une fonctionnalité. Chaque écran est tenu par des budgets chiffrés (§11.2), mesurés sur la baseline matérielle du projet (Snapdragon 680).

### Privacy by Design
Les données personnelles et les livres de l'utilisateur lui appartiennent. InkTone limite au maximum les échanges réseau, protège les informations stockées, et toute télémétrie — y compris le rapport de crash — est soumise à un consentement explicite (§10.7).

### Long-Term Architecture
L'architecture est pensée pour évoluer sur plusieurs années. Les décisions privilégient la maintenabilité, les tests, l'évolutivité et la qualité du code plutôt que les solutions rapides. Les leçons acquises par l'implémentation précédente sont capitalisées en règles et en ADR (§13.4) : le code peut être réécrit, le savoir jamais perdu.

---

# 2. Architecture Principles

Les principes d'architecture définissent les règles que tout le projet doit respecter. Ils servent de référence lors de chaque décision technique.

## 2.1 Architecture First
L'architecture précède l'implémentation. Aucune fonctionnalité n'est développée sans avoir été intégrée au modèle architectural. Ce principe s'applique à la réécriture elle-même : le premier commit de la nouvelle branche est ce Blueprint, pas du code (§13.7).

## 2.2 Domain-Driven Design
L'architecture est organisée autour du domaine métier plutôt que des technologies. Les concepts métier (Publication, ReadingState, ReadingSession, Library, Bookmark, Annotation, VoiceProfile, Locator) constituent le cœur du système. Les technologies (SQLite, Room, Kotlin, Compose, moteurs TTS) restent des moyens de les implémenter.

## 2.3 Modular Architecture
Le système est composé de modules indépendants, chacun responsable d'un domaine fonctionnel précis. **La liste canonique des modules est définie une seule fois, au §5.2** ; toute autre mention de modules dans ce document y renvoie.

## 2.4 Separation of Concerns
Chaque composant possède une responsabilité unique. L'interface utilisateur ne contient aucune logique métier. La logique métier ne dépend pas des composants Android. L'accès aux données est isolé dans une couche dédiée.

## 2.5 Dependency Inversion
Les couches métier ne dépendent jamais des détails techniques. Les dépendances sont inversées à l'aide d'interfaces, permettant de remplacer une implémentation (moteur TTS, base de données) sans toucher au domaine.

## 2.6 Capability-Aware Engine Abstraction
Les moteurs externes (TTS, parseurs) sont des fournisseurs interchangeables intégrés via une couche d'abstraction commune. **Mais l'abstraction ne doit jamais produire un plus petit dénominateur commun** : chaque moteur déclare ses capacités (§8.4), et les fonctionnalités différenciantes — comme les timestamps par mot — sont des exigences de première classe pour les moteurs qui les supportent. Le moteur de référence est Sherpa-ONNX (ADR-013).

## 2.7 Offline First
Toutes les fonctionnalités essentielles fonctionnent sans connexion Internet. Les composants réseau restent optionnels et ne bloquent jamais l'expérience de lecture.

## 2.8 Scalability
L'architecture permet l'ajout de nouvelles capacités sans remettre en cause l'existant : nouveaux formats, nouveaux moteurs TTS, synchronisation cloud, assistance IA, traduction, OCR.

## 2.9 Testability
Chaque composant est conçu pour être testable. La stratégie de test complète est définie au chapitre 14 ; elle inclut des obligations non négociables (tests de migration de schéma, garde-fous de régression portés du legacy).

## 2.10 Performance by Design
Les performances sont prises en compte dès la conception et tenues par les budgets chiffrés du §11.2. « Mesurer avant d'optimiser » implique d'avoir des cibles mesurables.

## 2.11 Security & Privacy
La confidentialité est un principe fondamental. Les données de lecture appartiennent à l'utilisateur. Les traitements sont réalisés localement autant que possible. Les permissions Android sont limitées au strict nécessaire, et l'accès aux fichiers passe exclusivement par le Storage Access Framework (ADR-015).

## 2.12 Maintainability
Le code doit rester compréhensible et maintenable sur le long terme. Les conventions de nommage, la documentation, les ADR et la gouvernance documentaire (§17.2) font partie intégrante du projet.

---

# 3. Domain Model

## 3.1 Purpose

Le Domain Model décrit les concepts métier fondamentaux d'InkTone et leurs relations. Ces entités représentent le langage du domaine et restent indépendantes des technologies (Android, Room, SQLite, Compose, moteurs TTS).

## 3.2 Locator — le value object d'adressage unique

Toute position dans une publication — position de lecture, signet, annotation, résultat de recherche, cible de synchronisation — est exprimée par un unique value object : **Locator**.

```text
Locator
├── resourceHref        ancre stable vers la ressource (fichier de chapitre)
├── chapterIndex        index du chapitre dans l'ordre de lecture
├── paragraphIndex      index du paragraphe dans la ressource (nullable)
├── charOffset          offset caractère dans la ressource
└── progression         fraction 0..1 dérivée — jamais source de vérité
```

Règles :

* La position ne dépend **jamais** d'un numéro de page, qui varie selon la police, la taille du texte et l'écran.
* `progression` est toujours recalculable depuis les autres champs ; elle sert à l'affichage (badge %) et à la réconciliation de synchronisation, pas à la reprise.
* Le Locator du domaine encapsule le Locator de Readium sans l'exposer (ADR-011) : le domaine ne dépend pas de Readium, mais le mapping est sans perte.
* Un seul modèle d'adressage pour Bookmark, Annotation, ReadingState et la recherche — trois formats concurrents dans une même base sont interdits.

## 3.3 Core Entities

### Publication

Représente une œuvre écrite importée dans la bibliothèque. Formats : EPUB et TXT en v1 ; PDF et autres formats selon la roadmap (§7.4, ADR-017).

```text
Publication
├── id
├── title, subtitle
├── authors
├── publisher
├── language
├── description
├── cover
├── format
├── fileUri              (URI SAF persistée)
├── fileHash             (détection de doublons à l'import)
├── fileSize
├── chapterCount
├── seriesName           (extrait via belongsTo Readium, fallback calibre)
├── seriesIndex
├── isFavorite
├── subjects             (tags, peuplés à l'import depuis les métadonnées)
├── isDrmProtected       (détecté à l'import)
├── importDate
└── lastOpened
```

Notes :

* Les champs `seriesName`, `seriesIndex`, `isFavorite` et `subjects` sont des acquis du legacy (K11) : ils font partie du modèle **v1**, pas d'une évolution future. Les filtres de bibliothèque (favoris, séries, tags, auteur, statut de lecture) reposent sur eux.
* `pageCount` est volontairement absent : un EPUB reflowable n'a pas de pages. Si un compte de pages « éditeur » devient nécessaire (formats paginés type PDF), il sera introduit avec une définition précise, jamais comme champ générique ambigu.

### Library

L'ensemble des publications de l'utilisateur. Responsable de l'importation, de la suppression, du classement, de la recherche et du filtrage. Une Library contient plusieurs Publications.

### ReadingState

**L'état de reprise** d'une publication. Il existe au plus un ReadingState par publication : c'est la source de vérité unique de la position, quel que soit le mode de lecture (K3).

```text
ReadingState
├── publicationId
├── locator              (Locator — position exacte)
├── progression          (dérivée, pour affichage)
├── lastReadAt
├── voiceProfileId       (profil vocal actif pour cette publication)
└── overrides            (surcharges par publication : thème, taille — nullable)
```

Règle de précédence des réglages : **surcharge de publication (ReadingState.overrides) > préférences globales (UserPreferences)**. Aucun réglage n'existe à un troisième niveau.

### ReadingSession

**L'enregistrement historique** d'une période de lecture, à des fins de statistiques. Plusieurs ReadingSessions par publication.

```text
ReadingSession
├── id
├── publicationId
├── startedAt, endedAt
├── mode                 (VISUAL | AUDIO)
└── sentencesRead / durationMs
```

ReadingState et ReadingSession sont deux concepts distincts et deux entités distinctes : l'un répond à « où reprendre ? », l'autre à « combien ai-je lu ? ». Leur conflation est une erreur de modèle explicitement rejetée (revue B10).

### Bookmark

Un signet mémorise une position importante.

```text
Bookmark
├── id
├── publicationId
├── locator              (Locator)
├── title
├── note
└── createdAt
```

### Annotation

Une interaction de l'utilisateur avec le texte : surlignage, note, citation, commentaire.

```text
Annotation
├── id
├── publicationId
├── startLocator         (Locator — début de la sélection)
├── endLocator           (Locator — fin de la sélection)
├── color
├── content
├── createdAt
└── updatedAt
```

### VoiceProfile

Configuration vocale réutilisable.

```text
VoiceProfile
├── id
├── engine
├── voice
├── language
├── speed
├── pitch
├── volume
└── style
```

Le champ `style` fait partie du modèle partout où VoiceProfile apparaît (Domain Model et Data Model alignés — revue B6). Plusieurs ReadingStates peuvent référencer le même VoiceProfile.

### AudioSession

Session de lecture audio **éphémère** : état de lecture, texte courant, segments générés, file de préchargement, statistiques de synthèse. Elle n'existe que pendant une lecture audio active et n'est jamais persistée.

### UserPreferences

Préférences globales : thème, police, taille, marges, mode nuit, animation de page, langue, comportement TTS par défaut, moteur par défaut. Une seule instance pour l'application. Les surcharges par publication vivent dans ReadingState (voir règle de précédence ci-dessus).

### SearchIndex

Structure interne de recherche rapide : titres, auteurs, chapitres, contenu plein texte, annotations. Implémentée en SQLite FTS (§6.9).

## 3.4 Domain Relationships

```text
Library
 ├── Publication
 │      ├── ReadingState  (0..1)  ──► VoiceProfile
 │      ├── ReadingSession (0..n)
 │      ├── Bookmark      (0..n)
 │      ├── Annotation    (0..n)
 │      └── SearchIndex
 │
 └── (UserPreferences — global, hors hiérarchie Library)
```

## 3.5 Domain Rules

* Une Library contient plusieurs Publications.
* Une Publication possède **au plus un** ReadingState (source de vérité de la position) et zéro à plusieurs ReadingSessions (historique).
* Bookmark, Annotation et ReadingState adressent le texte exclusivement via Locator.
* Un VoiceProfile peut être partagé par plusieurs ReadingStates.
* Une AudioSession n'existe que pendant une lecture audio active.
* La suppression d'une Publication supprime son ReadingState, ses ReadingSessions, ses Bookmarks et ses Annotations.
* UserPreferences est global ; toute surcharge par publication vit dans ReadingState.overrides et prime sur le global.

---

# 4. System Architecture

## 4.1 Purpose

L'architecture système décrit l'organisation globale d'InkTone : les couches, leurs responsabilités et les règles de communication. Chaque couche ne communique qu'avec les couches autorisées.

## 4.2 Architectural Style

InkTone adopte une **Clean Architecture** guidée par le Domain-Driven Design. Les dépendances pointent toujours vers le cœur métier. Le domaine ne dépend jamais d'Android, de Room, de Jetpack Compose ni d'un moteur TTS.

## 4.3 High-Level Layers

```text
+--------------------------------------------------+
|                Presentation Layer                |
|          Compose UI • ViewModels (MVI)           |
+--------------------------------------------------+
|                Application Layer                 |
|        Use Cases • Orchestrators • Services      |
+--------------------------------------------------+
|                  Domain Layer                    |
|   Entities • Value Objects • Interfaces • Rules  |
+--------------------------------------------------+
|      Data Layer          |  Infrastructure Layer |
| Repositories • Mappers   | Room • FS • TTS • ... |
+--------------------------------------------------+
```

## 4.4 Presentation Layer — pattern MVI

La couche Presentation contient les écrans, les composants Compose, les ViewModels, la navigation et la gestion des états UI. Elle ne contient aucune logique métier et délègue toutes les opérations aux Use Cases.

**Le pattern de gestion d'état est MVI (Model–View–Intent)**, formalisé ainsi (ADR-012) :

* chaque écran expose un **état unique et immuable** (`data class` UiState) via un `StateFlow` ;
* les interactions de l'utilisateur sont des **intents** explicites traités par le ViewModel ;
* les effets ponctuels (navigation, snackbar) passent par un canal d'événements dédié, jamais par l'état ;
* aucun état dupliqué entre ViewModel et composables : la source de vérité UI est le UiState.

Ce choix reprend le pattern éprouvé dans l'implémentation legacy et le rend normatif : sans cette spécification, chaque contributeur — humain ou agent — trancherait différemment.

## 4.5 Application Layer

Orchestre les fonctionnalités : Use Cases, orchestrateurs (ex. l'orchestrateur de lecture audio), services applicatifs.

Exemples de Use Cases : `ImportPublication`, `OpenPublication`, `ResumeReading`, `StartAudioReading`, `SearchPublication`, `CreateBookmark`, `AddAnnotation`, `ExportLibrary`.

Les Use Cases manipulent les entités du domaine sans connaître les détails techniques.

## 4.6 Domain Layer

Le cœur d'InkTone : entités, value objects (dont Locator), interfaces (Repository, Engine, Service), règles métier, événements du domaine. Aucune dépendance Android. Testable en JVM pure.

## 4.7 Data Layer & Infrastructure Layer — frontière arbitrée

La version 1.0.0 laissait `data/` et `infrastructure/` se chevaucher (revue B4). L'arbitrage est le suivant :

| Couche | Contenu | Exemples |
|---|---|---|
| **data/** | Implémentations des interfaces Repository du domaine, mappers entité↔persistance, politique de cache, orchestration des sources | `PublicationRepositoryImpl`, `LocatorMapper`, stratégie mémoire/disque |
| **infrastructure/** | Adaptateurs de plateforme et de technologies externes | Room (base, DAO, entités de persistance), système de fichiers/SAF, parseurs (Readium, TXT), moteurs TTS, MediaSession, notifications, WorkManager, réseau |

Règles :

* **Room vit dans `infrastructure/database`**, y compris les entités annotées et les DAO. `data/` les consomme via ses mappers.
* `data/` dépend de `infrastructure/` ; jamais l'inverse.
* Aucun composant de `feature/` n'importe quoi que ce soit de `infrastructure/` directement.

## 4.8 Dependency Rule

```text
Presentation ──► Application ──► Domain ◄── Data ◄── Infrastructure
```

Le domaine ne connaît personne. Data implémente les interfaces du domaine en s'appuyant sur Infrastructure.

## 4.9 Module Communication

Les modules fonctionnels ne dépendent jamais des **implémentations** les uns des autres. Ils communiquent exclusivement par :

* les interfaces du domaine ;
* les Use Cases de la couche Application ;
* des événements applicatifs si nécessaire.

Quand ce document indique qu'un module « dépend » d'un autre (§5), cela signifie toujours une dépendance vers les **interfaces du domaine** correspondantes, jamais vers le module concret. Cette formulation résout la contradiction relevée en revue (B2).

## 4.10 External Systems

Moteurs TTS, système de fichiers Android (via SAF exclusivement), MediaSession, fournisseurs cloud optionnels : tous sont isolés derrière des adaptateurs d'infrastructure.

---

# 5. Module Specifications

## 5.1 Purpose

L'application est divisée en modules à responsabilité unique, interface publique claire et dépendances minimales.

## 5.2 Canonical Module List

**Cette table est l'unique liste de référence des modules du projet.** Toute autre section (principes, structure de projet, roadmap) y renvoie sans la redéfinir.

| Module | Type | Responsabilité | Dépend de (interfaces du domaine) |
|---|---|---|---|
| `feature/library` | Feature | Bibliothèque : liste, tri, filtres (favoris, séries, tags, auteur, statut), collections | PublicationRepository, SearchService |
| `feature/reader` | Feature | Affichage et navigation dans une publication ; surlignage, signets, notes | ReadingService, AnnotationRepository, TtsService |
| `feature/player` | Feature | UI de contrôle de la lecture audio (barre, écran plein, vitesse, voix) | TtsService, AudioPlaybackService |
| `feature/search` | Feature | Recherche (titres, auteurs, plein texte, annotations) | SearchService |
| `feature/import` | Feature | Sélection SAF, validation, extraction de métadonnées, détection DRM et doublons, import par lot | ImportService |
| `feature/settings` | Feature | Configuration : apparence, langue, TTS, sauvegarde | PreferencesRepository |
| `feature/statistics` | Feature | Statistiques locales : temps de lecture/écoute, livres terminés, streaks | StatisticsRepository |
| `feature/onboarding` | Feature | Premier lancement : présentation, consentement crash reporting, téléchargement de la voix initiale | PreferencesRepository, VoiceModelService |
| `domain` | Cœur | Entités, value objects, interfaces, règles | — |
| `data` | Data | Repositories, mappers, cache (frontière §4.7) | domain, infrastructure |
| `infrastructure/database` | Infra | Room : base, DAO, entités, migrations | domain (interfaces) |
| `infrastructure/storage` | Infra | SAF, fichiers, cache disque, couvertures | — |
| `infrastructure/parser` | Infra | Parseurs de formats → Document Model (Readium pour EPUB) | domain (Document Model) |
| `infrastructure/tts` | Infra | Adaptateurs des moteurs TTS + gestion des modèles de voix | domain (TtsEngine) |
| `infrastructure/media` | Infra | MediaSession, service de lecture en arrière-plan, notifications média | — |
| `infrastructure/worker` | Infra | WorkManager : import par lot, tâches de fond | — |
| `infrastructure/crashreporting` | Infra | Rapport de crash opt-in (Firebase Crashlytics), no-op gracieux sans identifiants Firebase (K10, ADR-014) | domain (CrashReporter) |
| `infrastructure/sync` | Infra (futur) | Fournisseurs cloud derrière adaptateurs | domain (SyncService) |
| `core/designsystem` | Core | Thème Material 3, typographie, `AppIcons` (Material Symbols — aucun emoji dans le code, K12) | — |
| `core/ui` | Core | Composants Compose partagés | designsystem |
| `core/common` | Core | Utilitaires, extensions, Result/erreurs, logging | — |
| `core/testing` | Core | Fakes, fixtures, règles de test partagées | — |

Notes de renommage par rapport à la 1.0.0 : le module « Analytics » devient **`feature/statistics`** (revue B3 — le terme Analytics évoque une télémétrie sortante contraire au §10) ; « Audio » devient `feature/player` (UI) + `infrastructure/media` (plateforme) ; « Preferences » est absorbé par `data` (DataStore) exposé via le domaine ; « Notification » est absorbé par `infrastructure/media`.

## 5.3 Module Rules

* Chaque module feature contient son UI, ses ViewModels et ses Use Cases spécifiques.
* Un module feature n'importe jamais un autre module feature ; le partage passe par le domaine (§4.9).
* `core/*` ne dépend d'aucune fonctionnalité métier.
* L'ajout d'un module passe par la mise à jour de la table §5.2 — dans le même commit.

---

# 6. Data Model

## 6.1 Purpose

Le Data Model définit la structure des données persistées. Il implémente le Domain Model (§3) et garantit cohérence, performance, évolutivité du schéma et compatibilité avec les fonctionnalités futures.

## 6.2 Entities

Les entités persistées correspondent au Domain Model : Publication, ReadingState, ReadingSession, Bookmark, Annotation, VoiceProfile, UserPreferences — plus Collection et Tag comme extensions prévues (§6.8). Les attributs sont ceux du §3.3 ; ils ne sont pas redéfinis ici pour éviter toute divergence entre les deux chapitres (leçon de la 1.0.0 : VoiceProfile différait entre §3 et §6).

Représentation persistée du Locator : les champs du value object (`resourceHref`, `chapterIndex`, `paragraphIndex`, `charOffset`, `progression`) sont stockés à plat dans chaque table concernée (ReadingState, Bookmark, Annotation ×2), avec un mapper unique et testé dans `data/`.

## 6.3 Entity Relationships

```text
Publication 1 ──── 0..1 ReadingState ──► VoiceProfile
     │  1 ──── 0..n ReadingSession
     │  1 ──── 0..n Bookmark
     │  1 ──── 0..n Annotation
UserPreferences (singleton, hors relations)
```

Suppressions en cascade : Publication → ReadingState, ReadingSessions, Bookmarks, Annotations (clés étrangères `ON DELETE CASCADE`).

## 6.4 Schema Versioning & Migrations — règles non négociables

Héritées du legacy au prix de bibliothèques de testeurs effacées (K4) :

1. Le nouveau schéma démarre en **version 1** (ADR-020 : rupture assumée avec le schéma legacy v17, aucune migration fournie — projet pré-release).
2. À partir de la version 1, **chaque** incrément de version s'accompagne d'une migration explicite (`ALTER TABLE`/copie) **et de son test** (§14.5).
3. `fallbackToDestructiveMigration` global est **interdit**. Tout fallback destructif doit être circonscrit à une liste explicite de versions, justifié par écrit dans le code, et fait l'objet d'un ADR. Par défaut : une migration manquante fait planter l'application au lieu d'effacer silencieusement les données.
4. Aucune version de schéma n'est « consommée » sans migration pendant le développement : les migrations s'écrivent au moment du changement, pas rétroactivement.

## 6.5 Journal Mode

La base utilise le journal mode **WAL** (K1, ADR-016). TRUNCATE est interdit : son coût de commit croît avec la taille du fichier, dégradation mesurée sur l'import par lot dans le legacy. Un seul processus accède à la base ; aucune contrainte multi-process ne justifie un autre mode.

## 6.6 Indexes

Index obligatoires dès la v1 :

```text
Publication.title
Publication.authors
Publication.lastOpened
Publication.seriesName
Bookmark.publicationId
Annotation.publicationId
ReadingState.publicationId (unique)
ReadingSession.publicationId
```

## 6.7 Query Rules

* La progression de la bibliothèque se charge en **une requête groupée** (jamais de N+1 par livre — K8).
* Les listes potentiellement longues (bibliothèque, annotations) sont paginées (Paging ou requêtes fenêtrées).
* Les écritures d'import par lot sont regroupées en transactions.

## 6.8 Extension Entities

`Collection` (regroupements de publications) et `Tag` normalisé (au-delà des `subjects` importés) sont prévus sans rupture : tables d'association dédiées, migrations explicites. Rappel : les `subjects` importés sont disponibles **dès la v1** comme tags de fait (K11) ; l'entité Tag normalisée est l'évolution, pas le prérequis.

## 6.9 Full-Text Search

La recherche plein texte repose sur **SQLite FTS4 via Room** (table FTS liée au contenu des chapitres et aux annotations). Les recherches par `LIKE '%...%'` sur le contenu sont interdites au-delà des champs courts (titre, auteur).

## 6.10 Design Principles

Normalisation raisonnée, relations explicites avec clés étrangères, évolutivité par migrations testées, performance par index et requêtes groupées, séparation données métier / données techniques (caches).

---

# 7. Reading Engine

## 7.1 Purpose

Le Reading Engine est responsable de l'affichage, de la navigation et de la progression dans une publication. Il fournit une expérience de lecture fluide et cohérente, quel que soit le format du document. Il constitue le cœur fonctionnel d'InkTone (ADR-008).

## 7.2 Responsibilities

Charger une publication ; parser son contenu ; construire le Document Model ; afficher le texte ; gérer la navigation ; maintenir la position via Locator ; communiquer avec le moteur TTS ; exposer l'état à l'interface.

## 7.3 Reading Pipeline

```text
Publication → File Loader → Format Parser → Document Model → Layout Engine → Reading Engine → UI
```

L'interface ne dépend jamais du format du fichier. Pour l'EPUB, le Format Parser est **Readium** (ADR-011) : le Document Model du domaine encapsule le modèle de publication Readium, et le Locator du domaine encapsule le Locator Readium, sans exposer la bibliothèque au-delà de `infrastructure/parser`.

Règle héritée du legacy (K6) : la résolution des ressources normalise les hrefs percent-encodés — les EPUB réels contiennent des références encodées et non encodées vers les mêmes ressources.

## 7.4 Supported Formats

| Phase produit | Formats | Périmètre |
|---|---|---|
| **v1** | EPUB, TXT | Expérience complète : lecture, TTS, annotations, recherche |
| v1.x | PDF | ✅ **Affichage seul livré** (Lot 12, 2026-08-12) : rendu paginé PDFium, import, couverture, navigation, signets, reprise, thèmes sombre/sépia. TTS sur PDF conditionné à une extraction fiable de l'ordre de lecture (ADR-017, second volet). |
| v2.x | HTML, Markdown, FB2 | Selon demande |
| Étude | DOCX, MOBI, AZW3 | Sous réserve de faisabilité technique et légale |

Le phasage des formats suit la numérotation produit du chapitre 16 — il n'existe plus de système de « phases » parallèle (revue B9). PDF n'est **pas** un format v1 : un rendu PDF au niveau d'exigence du projet (pas de reflow, extraction d'ordre de lecture pour le TTS) est un chantier à part entière qui aurait dilué la qualité EPUB (ADR-017).

## 7.5 Document Model

Représentation interne unifiée d'un livre, masquant les différences de formats :

```text
Publication
 ├── Metadata (dont belongsTo → série)
 ├── Chapters
 │      ├── Paragraphs
 │      ├── Sentences (unités TTS, avec offsets — voir §8.6)
 │      ├── Images
 │      └── Links
 ├── Table of Contents
 └── Resources
```

Reader, TTS et recherche travaillent tous sur cette structure commune.

## 7.6 Navigation

Chapitre précédent/suivant ; défilement continu ; pagination optionnelle ; table des matières (liste virtualisée avec défilement vers le chapitre courant) ; retour à la dernière position ; navigation via signets et annotations.

## 7.7 Reading Position — source de vérité unique

La position est un **Locator** (§3.2), et il n'existe qu'**une seule source de vérité** : `ReadingState.locator`.

Règles héritées du legacy (K3), où leur violation a produit des bugs réels :

* La position est persistée **quel que soit le mode** : lecture TTS active (à chaque transition de phrase) **et** lecture visuelle silencieuse (scroll/tap manuel, débouncé).
* Les deux chemins d'écriture (TTS, manuel) sont distincts et ne s'exécutent jamais simultanément : les scrolls programmatiques déclenchés par le TTS ne repassent pas par le chemin manuel.
* Démarrer une lecture audio part **toujours** de la position restaurée — jamais d'index de départ implicite à zéro.
* Un test de régression protège chacune de ces règles (§14.6).

## 7.8 Rendering

Mise en page, polices, interlignage, marges, thèmes, images, liens. Les modifications de style ne changent jamais le contenu ni le Locator. Rendu virtualisé : seules les portions visibles sont composées.

## 7.9 Reader State

État interne unique (pattern MVI, §4.4) : publication ouverte, chapitre, Locator, progression, mode (visuel/audio), thème actif, paramètres typographiques effectifs (après application de la précédence §3.3). Source de vérité pour l'UI.

## 7.10 Integration with TTS

Le Reading Engine ne produit jamais de voix. Il fournit au TTS : les phrases avec leurs offsets (Locators), la position actuelle, et reçoit en retour les événements de progression (phrase et **mot**, §8.9) pour le surlignage synchronisé.

## 7.11 Error Handling

Fichiers corrompus, formats non pris en charge, ressources manquantes, erreurs de parsing : message clair, jamais de crash. **EPUB protégés par DRM : détectés à l'import** (K7), signalés à l'utilisateur avec un badge et un message explicite — jamais un échec silencieux à l'ouverture.

## 7.12 Extensibility

Mode double page (tablettes), sens de lecture, traduction, dictionnaire, OCR, assistance IA : extensions prévues sans refonte (chapitre 16).

---

# 8. TTS Engine

## 8.1 Purpose

Le TTS Engine convertit le texte en parole à travers une interface unifiée. Il doit offrir une lecture fluide, naturelle et **synchronisée au mot près** avec le contenu affiché — la fonctionnalité signature d'InkTone.

## 8.2 Design Philosophy

* **Capability-aware, pas plus petit dénominateur commun** (§2.6) : l'abstraction expose les capacités différenciantes au lieu de les niveler.
* **Offline first** : les moteurs locaux sont privilégiés ; les moteurs en ligne restent optionnels.
* **Replaceable components** : chaque moteur peut être ajouté ou remplacé sans modifier le Reader.

## 8.3 Architecture

```text
Reader ──► TTS Service ──► TTS Manager ──► Engine Adapters
                                            ├── Sherpa-ONNX  (référence)
                                            ├── Piper
                                            ├── Edge TTS
                                            └── futurs moteurs
```

Le TTS Manager est le point d'entrée unique : sélection du moteur, files d'attente, surveillance d'état.

## 8.4 Capability Model

Chaque adaptateur déclare ses capacités via un contrat explicite :

```text
TtsCapabilities
├── offline               : Boolean
├── wordTimestamps        : Boolean   ← capacité de première classe
├── sentenceTimestamps    : Boolean
├── languages             : List<Locale>
├── streamingSynthesis    : Boolean
├── speedControl          : Boolean
├── pitchControl          : Boolean
├── modelSizeMb           : Int
└── license               : String
```

L'application adapte son comportement aux capacités déclarées : le surlignage mot-à-mot est actif avec un moteur qui fournit `wordTimestamps` ; avec un moteur qui ne les fournit pas, le surlignage est **honnêtement** limité à la phrase — jamais simulé par interpolation proportionnelle aux caractères, une approche trompeuse explicitement rejetée par le projet (audit legacy : « product honesty around the highlighting feature »).

**Précision post-ADR-021 :** `wordTimestamps` décrit une garantie de
résultat (« ce moteur, tel que configuré, produit des timestamps mot
réels »), jamais un mécanisme particulier. Un adaptateur peut satisfaire
cette capacité par un callback natif (Palier 1) ou par un second passage
d'alignement forcé qu'il orchestre lui-même en interne (Palier 2) — le
Reader et le Player ne connaissent jamais cette distinction.

## 8.5 Engine Capability Matrix

| Moteur | Offline | Timestamps mot | Français | Statut | Notes |
|---|---|---|---|---|---|
| **Android TextToSpeech natif** | Oui (voix Google embarquées) | **Oui (natif, via `onRangeStart`)** | Oui | **Palier 1 — référence de repli (ADR-021)** | Dépend du moteur OS actif ; à détecter au runtime (Google confirmé, moteurs constructeur non garantis) ; qualité vocale inférieure au TTS neuronal |
| **Sherpa-ONNX (moteur JNI)** | Oui | **Non, nativement, quel que soit le modèle chargé** | Oui | **Palier 2 — moteur de synthèse de référence pour la qualité vocale (ADR-021)** | `GeneratedAudio` = `samples`+`sample_rate` uniquement (vérifié empiriquement, Kotlin et Python) ; timestamps obtenus par un second passage d'alignement forcé (CTC), pas par le moteur lui-même |
| ↳ *modèle Kokoro (via Sherpa-ONNX)* | Oui | Non (voir ci-dessus) | Oui (54 voix, 8 langues dont le français) | **Moteur retenu pour la v1 (ADR-022)**, licence Apache-2.0 | RTF ~4,7× (CPU+4 threads, meilleure configuration mesurée — threads, NNAPI et XNNPACK recompilés et testés, tous les deux plus lents que CPU) ; budget §11.2 non tenu, atténuation produit en place (voir ADR-022) ; un extracteur de durée natif existe côté Python (misaki) mais est perdu sur le chemin ONNX/Kotlin sans portage — piste d'optimisation future, pas la v1 |
| ↳ *modèle VITS/Piper `upmc-medium` (via Sherpa-ONNX)* | Oui | Non (voir ci-dessus) | Oui (2 locuteurs) | **Écarté (ADR-022)** | RTF 0,331× mesuré (largement sous budget), qualité 8/10 confirmée par écoute — mais licence disqualifiante : voix de base `lessac` restreinte à un usage non-commercial (confirmé à la source primaire, CSTR Edinburgh), incompatible avec le don volontaire d'InkTone |
| ↳ *modèle VITS/Piper `mls-medium` (via Sherpa-ONNX)* | Oui | Non (voir ci-dessus) | Oui (125 locuteurs) | **Écarté (ADR-022)** | RTF 0,375× mesuré, licence propre (CC-BY 4.0, entraîné from scratch) — mais qualité vocale insuffisante confirmée par écoute humaine sur 7 échantillons |
| **Piper** | Oui | Non | Oui | **Écarté (ADR-021, confirmé ADR-022)** | Dépôt archivé le 6 octobre 2025, relicencié GPL-3.0 (`piper1-gpl`) — incompatible avec une app commerciale fermée, indépendamment des timestamps ; les deux voix candidates évaluées via Sherpa-ONNX (ci-dessus) écartées chacune pour une raison distincte (licence, qualité) |
| **Edge TTS** | Non (en ligne) | Oui (frontières de mot SSML) | Oui | Optionnel, jamais requis pour l'usage quotidien | Cité pour mémoire — le timing y est réel mais la dépendance réseau exclut ce moteur du Palier 1 comme du Palier 2 |

Cette matrice est normative : un moteur n'est proposé à l'utilisateur que si sa ligne est complète et validée.

## 8.6 Text Processing

Avant synthèse : découpage en phrases **avec conservation des offsets** (chaque phrase connaît son Locator de début — c'est ce qui rend la synchronisation possible), normalisation des espaces, ponctuation, abréviations (règles françaises en priorité), caractères spéciaux. Les règles de prononciation personnalisées de l'utilisateur s'appliquent à cette étape.

## 8.7 Audio Pipeline

```text
Sentences (+offsets) → Text Normalizer → TTS Manager → Engine → Audio Buffer (+timestamps) → Audio Player
```

Préchargement : pendant la lecture de la phrase n, la phrase n+1 est synthétisée et mise en buffer. Taille de buffer adaptative selon mémoire disponible et moteur. Objectif chiffré : silence inter-phrases ≤ 150 ms perçues (§11.2).

## 8.8 Playback Control

Lecture, pause, reprise, arrêt, phrase suivante/précédente, avance/recul, changement de vitesse en temps réel, changement de voix (selon capacités du moteur). La lecture continue en arrière-plan via `infrastructure/media` (MediaSession, notification, écran verrouillé).

## 8.9 Word-Level Synchronization — exigence de première classe

La correspondance texte-audio est maintenue à deux granularités :

* **Phrase** : toujours disponible, sert à la reprise, au repositionnement et au fallback d'affichage.
* **Mot** : disponible avec tout moteur déclarant `wordTimestamps` (Sherpa-ONNX en référence). Les timestamps réels produits par le moteur pilotent le surlignage — le mot en cours de prononciation est le mot surligné.

Règles :

1. Les timestamps proviennent **du moteur**, jamais d'une estimation par proportion de caractères.
2. Le changement de vitesse de lecture recalcule ou remet à l'échelle les timestamps de façon exacte (les moteurs locaux resynthétisent ou fournissent les durées réelles).
3. La reprise après pause repositionne sur le mot exact.
4. Cette exigence figure dans les critères d'acceptation du module TTS (§14.7) — elle n'est pas une évolution future.

**Mécanismes acceptés post-ADR-021 :** un timestamp mot est considéré
« réel » (donc autorisant l'exigence du §8.9) s'il provient soit d'un
callback natif du moteur de synthèse (Palier 1), soit d'un passage
d'alignement forcé opéré sur l'audio effectivement généré, contraint par
le texte connu (Palier 2). Les deux sont des mesures, pas des
estimations — la distinction avec l'interpolation de caractères
(interdite) est que l'un et l'autre s'appuient sur un signal réel
(callback OS ou posterior acoustique), jamais sur une simple
proportionnalité de durée.

## 8.10 Engine Selection

L'utilisateur choisit son moteur ; InkTone mémorise le choix et signale clairement les capacités perdues ou gagnées lors d'un changement (ex. passage à Piper : « le surlignage mot à mot n'est pas disponible avec ce moteur »).

**Note post-ADR-021 :** le choix affiché à l'utilisateur porte sur la
**qualité vocale** (voix Android natives vs. voix neuronale Sherpa-ONNX/
Kokoro) — le mécanisme de timing (Palier 1 ou 2) est une conséquence
interne de ce choix, pas une option distincte présentée séparément.

## 8.11 Voice Model Distribution

Les modèles de voix neuronaux pèsent des dizaines de Mo : ils ne sont **pas embarqués dans l'APK** (budget §11.2). Distribution à la demande (ADR-018) :

* l'onboarding propose le téléchargement de la voix française de référence (taille affichée, Wi-Fi recommandé) ;
* une fois téléchargé, un modèle est stocké localement et l'usage est intégralement hors ligne — conforme à Offline by Default (§1.4) ;
* les modèles sont vérifiés par empreinte à la réception ;
* la gestion (télécharger, supprimer, espace occupé) est exposée dans les réglages.

## 8.12 Error Handling

Voix indisponible, modèle absent ou corrompu, erreur réseau (moteurs en ligne), interruption de synthèse, changement de moteur à chaud : signalés clairement, sans interrompre brutalement la lecture, avec repli proposé (autre voix, autre moteur).

## 8.13 Future Extensions

Nouvelles voix, lecture multilingue, changement de voix par personnage, synthèse expressive, cache audio intelligent. L'architecture par adaptateurs et capacités les accueille sans modifier le Reader.

---

# 9. Synchronization Architecture

## 9.1 Purpose

Le module Synchronization garde les données cohérentes entre appareils, tout en garantissant qu'InkTone reste entièrement utilisable hors connexion. La synchronisation n'est **jamais obligatoire**.

## 9.2 Design Principles

* **Offline First** : toutes les opérations sont locales d'abord ; la synchronisation est différée et optionnelle.
* **User Control** : activation, choix des données, déclenchement manuel — toujours sous contrôle de l'utilisateur.
* **Conflict Safety** : aucun conflit ne supprime silencieusement des données.
* **Provider Independence** : aucun fournisseur cloud spécifique n'est requis ; tous passent par un adaptateur commun.

## 9.3 Architecture

```text
Application → Sync Service → Sync Manager → { Local DB, Conflict Resolver, Sync Queue, Provider Adapter }
```

## 9.4 Synchronizable Data

ReadingState (position — exprimée en Locator, ce qui rend la réconciliation entre appareils déterministe), Bookmarks, Annotations, UserPreferences, VoiceProfiles, ReadingSessions (statistiques). Les fichiers de livres eux-mêmes ne sont synchronisés qu'à la demande explicite.

## 9.5 Flow & Conflict Resolution

File d'attente locale → envoi dès qu'une connexion existe → fusion → mise à jour locale. Chaque donnée porte identifiant, date de modification et version. Stratégies : dernière modification prioritaire pour les données scalaires ; fusion pour les ensembles (annotations) ; arbitrage utilisateur pour les cas ambigus. Jamais de suppression silencieuse.

## 9.6 Providers

Interface commune ; candidats : WebDAV, Google Drive, Dropbox, serveur personnel. Le choix du fournisseur est invisible pour le reste de l'application.

## 9.7 Security

Connexions chiffrées (TLS), vérification des certificats, chiffrement de bout en bout possible pour les données personnelles, identifiants dans le stockage sécurisé Android (§10.5).

## 9.8 Background Sync

Si l'utilisateur l'autorise : WorkManager avec contraintes (réseau, batterie), synchronisations regroupées, jamais de réveil superflu.

---

# 10. Security & Privacy

## 10.1 Purpose

La sécurité et la confidentialité sont des piliers d'InkTone : protéger les données de l'utilisateur, minimiser la collecte, appliquer Security by Design et Privacy by Design à chaque fonctionnalité.

## 10.2 Security Principles

* **Local First** : données stockées localement par défaut ; aucun envoi réseau sans accord explicite de l'utilisateur — le rapport de crash inclus (§10.7).
* **Least Privilege** : permissions strictement nécessaires, demandées en contexte.
* **Secure by Default** : les fonctionnalités réseau sont désactivées tant qu'elles ne sont pas configurées.
* **Transparency** : l'utilisateur sait quelles données sont utilisées, où, et pourquoi.

## 10.3 File Access — Storage Access Framework uniquement

Règle absolue (K5, ADR-015) : l'accès aux fichiers passe **exclusivement** par le Storage Access Framework (sélecteur de documents, URI persistées). La permission `MANAGE_EXTERNAL_STORAGE` est **interdite** dans le manifeste comme dans le code — elle a constitué un blocker de publication Play Store dans l'implémentation précédente, marqué résolu dans la documentation alors qu'il était actif dans le code. Un contrôle CI vérifie son absence (§14.8).

## 10.4 Local Data Protection

Bibliothèque, progression, annotations, signets, préférences, statistiques : intégrité garantie par les règles de migration (§6.4) et les transactions. Aucune perte silencieuse tolérée.

## 10.5 Sensitive Data

InkTone évite de stocker des informations sensibles. Les identifiants éventuels (fournisseur cloud) vont dans le stockage sécurisé Android (Keystore/EncryptedSharedPreferences).

## 10.6 Permissions Management

Permissions demandées **au moment où elles deviennent nécessaires**, précédées d'une explication : import d'un livre → sélecteur SAF (aucune permission de stockage global) ; contrôle de lecture audio → notifications.

## 10.7 Crash Reporting & Consent — tension résolue

Le projet utilise un rapport de crash (Firebase Crashlytics) : indispensable pour la qualité, mais c'est un envoi de données réseau. Réconciliation avec Privacy by Design (ADR-014) :

1. Le rapport de crash est **désactivé par défaut** (opt-in).
2. L'onboarding propose son activation avec une explication honnête : contenu d'un rapport (trace, version, modèle d'appareil), ce qui n'y figure jamais (contenu des livres, annotations, identité).
3. Le choix est modifiable à tout moment dans les réglages.
4. L'implémentation est un **no-op gracieux** sans identifiants Firebase commis (K10) : le dépôt reste clonable et buildable sans secrets, sans remontée de crash.
5. Les clés custom attachées se limitent à version et commit — jamais de données de lecture.

## 10.8 Logging

Journaux techniques limités au diagnostic. Interdits dans les logs : contenu des livres, annotations, informations personnelles. Les logs de debug verbeux (pipeline TTS) sont conditionnés au build de développement.

## 10.9 Privacy Commitments

Aucune collecte de données de lecture sans consentement ; aucune publicité ; aucune revente de données ; statistiques locales par défaut. Le module de statistiques s'appelle Statistics et non Analytics précisément pour refléter cette réalité (§5.2).

## 10.10 Future Security Enhancements

Chiffrement de la base locale, chiffrement de bout en bout pour la sync, sauvegardes chiffrées, verrouillage par biométrie, vérification d'intégrité des fichiers importés.

---

# 11. Performance & Optimization

## 11.1 Purpose

La performance est une fonctionnalité. Elle est tenue par des **budgets chiffrés**, mesurés sur la baseline matérielle du projet, intégrés aux critères d'acceptation de chaque module et vérifiés par des benchmarks (§14.7).

## 11.2 Performance Budgets

**Baseline matérielle : classe Snapdragon 680, 4–6 Go de RAM** — le milieu de gamme visé par le produit. Les budgets s'entendent sur cette baseline ; les appareils plus puissants font mieux, aucun appareil supporté ne fait significativement moins bien.

| Domaine | Métrique | Budget v1 |
|---|---|---|
| Démarrage | Cold start → bibliothèque affichée | ≤ 1 500 ms |
| Démarrage | Reprise de la dernière lecture (warm) | ≤ 800 ms |
| Reader | Ouverture d'un EPUB de 5 Mo → premier rendu | ≤ 800 ms |
| Reader | Défilement | 60 fps ; aucune frame > 32 ms |
| Reader | Navigation vers un chapitre (préchargé) | ≤ 150 ms |
| TTS | Latence tap → premier audio (Sherpa-ONNX local) | ≤ 1 500 ms — **non tenu par Kokoro tel que mesuré (~4,7×, atténuation produit retenue plutôt que révision du budget, voir ADR-022)** |
| TTS | Silence inter-phrases perçu | ≤ 150 ms |
| TTS | Précision du surlignage mot | ≤ ±120 ms vs audio |
| Import | 500 EPUB (taille moyenne 2 Mo) | ≤ 5 min, UI non bloquée |
| Bibliothèque | Affichage et scroll à 1 000+ livres | 60 fps, requêtes paginées |
| Mémoire | Pic en lecture (EPUB 5 Mo, TTS actif) | ≤ 250 Mo |
| APK | Taille de base **hors modèles de voix** | ≤ 60 Mo |
| Batterie | Lecture TTS continue, écran éteint | ≤ 8 %/heure |

Ces budgets sont des cibles initiales : ils peuvent être révisés **par ADR** après mesure contradictoire, jamais ignorés silencieusement.

## 11.3 Caching Strategy

* **Mémoire** : livre ouvert, chapitre actif, chapitres voisins préchargés, couvertures visibles.
* **Disque** : miniatures, métadonnées extraites, ressources EPUB décompressées à la demande, segments audio temporaires, phrases découpées (cache de segmentation).

## 11.4 Reader Optimization

Lazy loading des chapitres (jamais un livre entier en mémoire) ; rendu virtualisé ; préchargement du chapitre suivant en arrière-plan ; listes UI virtualisées (bibliothèque, table des matières).

## 11.5 TTS Optimization

File de phrases avec synthèse en avance ; buffer adaptatif (mémoire, puissance, moteur) ; réutilisation des segments déjà synthétisés lors des retours en arrière ; synthèse sur executor dédié (§11.8).

## 11.6 Database Optimization

Règles héritées et normatives : WAL (§6.5, K1) ; requêtes groupées, jamais de N+1 (K8) ; pagination des grandes listes ; transactions regroupées à l'import ; index du §6.6 ; FTS pour le plein texte (§6.9).

## 11.7 Import Optimization

Règles héritées du legacy, dans l'ordre où elles ont été durement apprises :

1. **WAL d'abord** : aucune parallélisation n'a de sens tant que chaque transaction paie un fsync proportionnel à la taille de la base (K1).
2. **Une seule ouverture ZIP par EPUB** pendant tout l'import d'un fichier (K2).
3. Parallélisation ensuite : fichiers traités en parallèle (borné par les cœurs), chapitres d'un même fichier traités en parallèle.
4. Import par lot en tâche WorkManager : survit à la mise en arrière-plan, UI non bloquante (bannière de progression, badges), reprise après interruption.

## 11.8 Concurrency Model

Modèle normatif pour tout le projet :

* **Structured concurrency** exclusivement : tout travail asynchrone vit dans un scope à cycle de vie défini (`viewModelScope`, scope de service, scope de worker). `GlobalScope` est interdit.
* **Politique de dispatchers** : `Main` pour l'UI uniquement ; `Default` pour le CPU (parsing, segmentation) ; `IO` pour disque et réseau ; **executor mono-thread dédié pour les appels JNI de synthèse** (Sherpa-ONNX) — un moteur natif n'est pas supposé thread-safe tant que sa documentation ne le garantit pas.
* **Annulation** : toute coroutine de longue durée est annulable et nettoie ses ressources natives (sessions ONNX, fichiers ouverts) dans un bloc `finally`.
* **Pont JNI** : les handles natifs sont possédés par un unique wrapper Kotlin à cycle de vie explicite ; aucun handle natif ne traverse les couches.
* Le thread principal n'exécute **jamais** de parsing, d'E/S ou de synthèse.

## 11.9 Memory & Battery

Libération des ressources inutilisées, fermeture des fichiers inactifs, profils adaptatifs (préchargement et caches réduits sur les appareils contraints, plus agressifs sur le haut de gamme). Réveils processeur minimaux ; lecture audio continue à coût de calcul minimal ; activité réduite en batterie faible.

## 11.10 Monitoring

Mesures en build de développement : temps de démarrage, ouverture des livres, latences TTS, mémoire, frames. Les benchmarks automatisés (§14.7) comparent chaque évolution aux budgets du §11.2.

---

# 12. Project Structure

## 12.1 Purpose

Organisation des répertoires, modules Gradle et packages : maintenance facilitée, lisibilité, développement parallèle, évolutivité. La structure matérialise les couches du §4 et la liste canonique du §5.2.

## 12.2 High-Level Structure

```text
InkTone/
├── app/                     point d'entrée : Application, navigation, DI, splash
├── core/
│   ├── designsystem/        thème M3, typographie, AppIcons
│   ├── ui/                  composants Compose partagés
│   ├── common/              utilitaires, Result, logging
│   └── testing/             fakes, fixtures, règles de test
├── domain/
│   ├── entity/  valueobject/  repository/  usecase/  service/  event/
├── data/
│   ├── repository/  mapper/  cache/
├── infrastructure/
│   ├── database/            Room : base, DAO, entités, migrations
│   ├── storage/             SAF, fichiers, couvertures
│   ├── parser/              Readium (EPUB), TXT, futurs formats
│   ├── tts/                 adaptateurs moteurs + modèles de voix
│   ├── media/               MediaSession, service audio, notifications
│   ├── worker/              WorkManager
│   └── sync/                (futur) adaptateurs cloud
├── feature/
│   ├── library/  reader/  player/  search/  import/
│   ├── settings/  statistics/  onboarding/
├── build-logic/             convention plugins Gradle
├── docs/
│   ├── blueprint/           ce document
│   ├── adr/                 un fichier par ADR
│   └── diagrams/
├── scripts/                 automatisation, vérifications
└── gradle/
```

La liste `feature/` reproduit exactement la table canonique §5.2 — toute divergence entre les deux est un bug de documentation à corriger dans le même commit.

## 12.3 Module Rules

* `app/` ne contient aucune logique métier : navigation, DI, initialisation.
* `domain/` ne dépend de rien (ni Android, ni Room, ni Compose) — vérifié par les règles de dépendance Gradle.
* `data/` dépend de `domain` et `infrastructure` ; `infrastructure/*` dépend de `domain` (interfaces) uniquement.
* `feature/*` dépend de `domain`, `core/*` — jamais d'un autre `feature/*`, jamais d'`infrastructure/*`.
* Room vit intégralement dans `infrastructure/database` (§4.7).

## 12.4 Dependency Rules (Gradle)

```text
app ──► feature/* ──► domain ◄── data ◄── infrastructure/*
              │
              └────► core/*
```

Ces règles sont encodées dans `build-logic/` (convention plugins) : une dépendance interdite fait échouer le build, pas une revue.

## 12.5 Coding Conventions

* Classes et interfaces nommées par concepts métier : `Publication`, `ReadingState`, `PublicationRepository`, `TtsEngine`, `PublicationParser`.
* Use Cases : verbe à l'infinitif + concept — `ImportPublication`, `ResumeReading`, `StartAudioReading`.
* Packages en minuscules, par domaine fonctionnel.
* **Aucun emoji dans le code de production** ; toute icône passe par `core/designsystem` (`AppIcons`, Material Symbols Outlined) — contrôle lint en CI (K12, §14.8).
* Jamais de compensation aval d'un problème amont : la leçon du legacy (nettoyage de caractères parasites côté affichage au lieu de corriger la source) est érigée en règle de revue.
* Messages de commit en français, à l'impératif : « Corrige… », « Ajoute… », « Initialise… ».

---

# 13. Rewrite & Legacy Strategy

## 13.1 Purpose

Ce chapitre définit la stratégie officielle de transition entre l'implémentation existante d'InkTone (mono-module, branche historique du dépôt `issa14/InkTone`) et l'architecture cible de ce Blueprint. Il répond à trois questions : que devient le code existant ? que conserve-t-on de lui ? comment la réécriture démarre-t-elle sans perdre les acquis ?

## 13.2 Decision

InkTone est **réécrit intégralement** selon ce Blueprint (ADR-019) :

* le dépôt `issa14/InkTone` est conservé ;
* la branche `main` actuelle est archivée sous `legacy/monolith`, protégée et taguée `legacy-final-v0` ;
* une **branche orpheline** devient la nouvelle `main` : aucun héritage de code, historique vierge ;
* le legacy reste consultable en lecture ; il n'est **jamais fusionné** dans la nouvelle branche.

## 13.3 Rationale & Assumed Risk

L'architecture cible diffère structurellement du mono-module au point qu'une migration incrémentale coûterait plus cher qu'une réécriture guidée. Le projet est pré-release : aucune installation publique, aucune base utilisateur à migrer — cette fenêtre se referme à la première publication Play Store. Le développement assisté par agent rend le coût de réécriture de code déjà spécifié faible, **à condition que les spécifications capturent les leçons du legacy**.

La réécriture complète reste un risque classique du génie logiciel : perte de corrections invisibles, régressions sur des cas limites déjà résolus, effet tunnel. Ce risque est accepté **uniquement** parce que les garde-fous des §13.4 et §13.6 sont obligatoires et bloquants. Si l'inventaire du §13.4 n'est pas transposé avant l'écriture du premier module concerné, la décision de réécriture doit être réexaminée.

## 13.4 Knowledge Capitalization — le code est jeté, le savoir jamais

Inventaire des acquis du legacy, chacun payé par un bug réel, un crash ou un audit (établi par audit du code au commit `69d18a8`) :

| # | Acquis | Origine legacy | Destination dans ce Blueprint |
|---|---|---|---|
| K1 | Journal mode WAL obligatoire ; TRUNCATE interdit | Fix Phase 2.0, commenté dans le code | §6.5, §11.7, ADR-016 |
| K2 | Une seule ouverture ZIP par import EPUB ; parallélisation seulement après K1 | Phases 2.1–2.3 | §11.7 |
| K3 | Source de vérité unique de la position ; chemins TTS/manuel distincts ; jamais de départ implicite à zéro | `onManualPositionChanged`, garde-fou de régression | §7.7, §14.6 |
| K4 | Fallback destructif global interdit ; migrations explicites testées ; plantage plutôt qu'effacement silencieux | Leçon des versions 6→13 perdues | §6.4, §14.5 |
| K5 | SAF exclusivement ; `MANAGE_EXTERNAL_STORAGE` interdit | Blocker Play Store, Phase 0 | §10.3, ADR-015, contrôle CI |
| K6 | Normalisation des hrefs EPUB percent-encodés | Phase 2.2bis | §7.3 |
| K7 | Détection DRM à l'import, message clair | Phase 6.1 | §7.11 |
| K8 | Requête groupée de progression ; jamais de N+1 | Phase 4.1 | §6.7, §11.6 |
| K9 | Aucun moteur TTS offline neuronal n'expose de timestamps mot natifs (Sherpa-ONNX y compris, quel que soit le modèle chargé) ; capability exposée par l'abstraction, satisfaite soit par un moteur natif (Android TextToSpeech), soit par un second passage d'alignement forcé | Vérification empirique Phase 3 (spike), recherche comparative | §8.4, §8.5, §8.9, ADR-021 |
| K10 | Crash reporting no-op gracieux sans secrets commis | `CrashReporter.kt` | §10.7 |
| K11 | `subjects` peuplé à l'import = tags immédiats ; série via `belongsTo` + fallback calibre | Modèle Book livré | §3.3, §6.8 |
| K12 | Aucun emoji en production ; icônes centralisées ; jamais de compensation aval | `AppIcons.kt`, leçon du nettoyage aval | §12.5, contrôle lint CI |

**Règle de gouvernance bloquante :** aucun module de la nouvelle branche ne passe en revue si un acquis K# le concernant n'est ni transposé ni explicitement invalidé par un ADR motivé.

## 13.5 Legacy Consultation Policy

* Le legacy est une **référence de comportement et de leçons**, pas une bibliothèque de code.
* Copier-coller depuis `legacy/monolith` vers `main` : interdit par défaut. Exception : extrait cité dans un ADR ou une revue, réécrit selon les conventions de la nouvelle architecture, couvert par un test.
* Les tests du legacy — en particulier les garde-fous de régression (K3) et les tests de migration — sont **portés en priorité** : ils encodent les bugs déjà payés.
* À la clôture du bootstrap, une revue finale du legacy vérifie qu'aucun correctif tardif n'a échappé à l'inventaire §13.4.

## 13.6 Database Policy

Nouveau schéma **version 1**, conçu depuis le chapitre 6. Aucune migration depuis le schéma legacy (v17) n'est fournie (ADR-020) — rupture acceptable uniquement parce qu'aucune installation publique n'existe ; toute installation de test personnelle est ré-importée, pas migrée. Dès la version 1 : les règles du §6.4 s'appliquent intégralement.

## 13.7 Bootstrap Sequence & Exit Criteria

```bash
# 1. Archiver l'existant (état vérifié : branche unique main)
git checkout main
git tag legacy-final-v0
git branch legacy/monolith
git push origin legacy/monolith legacy-final-v0

# 2. Créer la nouvelle main orpheline
git checkout --orphan main-rewrite
git rm -rf .
# premier commit : Blueprint 1.1.0 + ADR fondateurs — docs d'abord, code ensuite
git commit -m "Initialise InkTone v2 : Blueprint 1.1.0 et ADR fondateurs"

# 3. Basculer les noms
git branch -m main legacy-main-temp
git branch -m main-rewrite main
git push origin main --force-with-lease
git push origin --delete legacy-main-temp   # après vérification de legacy/monolith en ligne

# 4. Protéger legacy/monolith (lecture seule) dans les réglages GitHub
```

**Ordre impératif : le premier commit de la nouvelle `main` contient ce Blueprint et les ADR fondateurs — pas de code** (§2.1 appliqué à la réécriture elle-même).

Critères de sortie du bootstrap :

1. `legacy/monolith` et `legacy-final-v0` poussés, branche protégée ;
2. Blueprint 1.1.0 en premier commit de la nouvelle `main` ;
3. ADR de l'inventaire §13.4 rédigés (statut Accepted) ;
4. structure multi-modules du §12 en place, CI verte ;
5. budgets du §11.2 actés — ils servent de critères d'acceptation à chaque module.

## 13.8 Success Criteria

* Aucun bug de l'inventaire K# ne réapparaît dans la nouvelle implémentation.
* Chaque module livré respecte les budgets du §11.2 dès sa première version.
* Le legacy n'est plus consulté que pour vérification historique une fois la parité fonctionnelle atteinte.
* L'historique de la nouvelle `main` commence par la documentation.

---

# 14. Testing Strategy

## 14.1 Purpose

La testabilité (§2.9) n'est un principe que si elle est outillée et exigée. Ce chapitre définit la pyramide de tests, les obligations par couche, les tests **non négociables**, et les portes de qualité en CI.

## 14.2 Test Pyramid

```text
        UI / intégration (peu, ciblés sur les parcours critiques)
      ───────────────────────────────────────────
        Tests de composants (ViewModels MVI, Use Cases avec fakes)
      ───────────────────────────────────────────
        Tests unitaires JVM (domaine, mappers, segmentation, Locator)
```

La logique métier vit dans `domain/` et `data/`, testables en JVM pure et rapides : c'est là que se concentre le volume. Les tests instrumentés restent rares et ciblés.

## 14.3 Per-Layer Expectations

| Couche | Type de test | Exigence |
|---|---|---|
| `domain` | Unitaires JVM | Toute règle métier et tout value object (Locator : mapping, égalité, progression dérivée) |
| `data` | Unitaires JVM + Room in-memory | Mappers exhaustifs ; repositories avec base in-memory |
| `infrastructure/database` | Instrumentés | **Migrations (§14.5)** ; contraintes de cascade |
| `infrastructure/parser` | Unitaires avec EPUB de fixtures | Hrefs percent-encodés (K6), DRM (K7), EPUB malformés |
| `infrastructure/tts` | Unitaires + contrat | Chaque adaptateur vérifié contre le contrat TtsCapabilities ; timestamps mot vérifiés sur corpus de référence |
| ViewModels | Composants JVM | Cycle MVI complet : intent → état ; cas d'erreur |
| `feature/*` UI | Instrumentés ciblés | Parcours critiques uniquement (§14.6) |

## 14.4 Fixtures

`core/testing` fournit : EPUB de référence (sain, percent-encodé, DRM, corrompu, volumineux), corpus de phrases françaises pour la segmentation et la vérification des timestamps, fakes des interfaces du domaine.

## 14.5 Mandatory: Migration Tests

Chaque migration de schéma est accompagnée, **dans le même commit**, d'un test `MigrationTestHelper` qui : crée la base en version N, insère des données représentatives, migre vers N+1, vérifie données et contraintes. Une migration sans test ne passe pas la CI. Cette obligation est la transposition directe de la perte de bibliothèques du legacy (K4).

## 14.6 Mandatory: Regression Guards (portés du legacy)

Tests de non-régression obligatoires dès l'implémentation des modules concernés, encodant les bugs déjà payés :

* la lecture audio démarre à la **position restaurée**, jamais au début du chapitre (K3) ;
* la lecture visuelle silencieuse **persiste la position** (scroll manuel débouncé) (K3) ;
* le chemin d'écriture TTS et le chemin manuel ne s'exécutent jamais simultanément (K3) ;
* un EPUB avec hrefs percent-encodés s'ouvre et résout toutes ses ressources (K6) ;
* un EPUB DRM est détecté à l'import et signalé (K7) ;
* la progression de la bibliothèque se charge en une requête (assertion sur le nombre de requêtes) (K8).

## 14.7 Benchmarks

Macrobenchmarks (Jetpack Macrobenchmark) sur les budgets du §11.2 : cold start, ouverture d'EPUB, latence premier audio, import par lot. Exécutés sur un appareil de la baseline avant chaque release ; un dépassement de budget bloque la release ou déclenche un ADR de révision du budget. Le critère de précision du surlignage mot (±120 ms) est vérifié par un test dédié comparant timestamps moteur et positions d'événements de surlignage sur le corpus de référence.

## 14.8 CI Gates

Portes bloquantes sur chaque pull request / push :

1. build complet + tests JVM ;
2. tests instrumentés sur les modules à migrations et parseurs ;
3. lint : **aucun emoji dans les sources de production** (K12) ; icônes hors `AppIcons` interdites ;
4. vérification du manifeste : `MANAGE_EXTERNAL_STORAGE` absent (K5) ;
5. règles de dépendance inter-modules (encodées dans `build-logic/`, §12.4) ;
6. toute migration accompagnée de son test (§14.5).

## 14.9 Definition of Done (par module)

Un module est terminé quand : ses Use Cases sont couverts par des tests JVM ; ses garde-fous K# applicables sont en place ; ses budgets §11.2 applicables sont mesurés et tenus ; sa documentation de module (rôle, dépendances, interfaces, limites) est écrite ; la CI est verte. Les critères d'acceptation sont des comportements vérifiables avant/après, jamais des cases à cocher déclaratives.

---

# 15. Architecture Decision Records (ADR)

## 15.1 Purpose

Les ADR constituent l'historique officiel des décisions d'architecture. Chaque ADR répond à quatre questions : quel problème ? quelle décision ? pourquoi ? quelles conséquences ? Ils sont stockés dans `docs/adr/`, un fichier par ADR, numérotés séquentiellement, jamais supprimés (statut `Superseded` avec référence au remplaçant).

## 15.2 Template

```text
ADR-XXX : Titre
Status : Proposed | Accepted | Implemented | Superseded
Date :
Context      — le problème et son contexte
Decision     — ce qui est décidé
Rationale    — pourquoi ce choix
Consequences — impacts positifs et négatifs
Alternatives Considered — options écartées et raisons
```

Tout ADR doit remplir **chaque** rubrique — la version 1.0.0 de ce Blueprint violait son propre template dès ses ADR fondateurs (revue B8) ; les ADR ci-dessous sont au format complet, en version condensée ; les fichiers de `docs/adr/` peuvent les détailler davantage.

## 15.3 Founding ADRs

### ADR-001 : Clean Architecture
**Status : Accepted** · **Context :** le projet vise plusieurs années d'évolution avec des technologies (UI, DB, TTS) susceptibles de changer ; le legacy mono-couche rendait les responsabilités poreuses. · **Decision :** architecture en couches (§4.3) avec règle de dépendance stricte vers le domaine. · **Rationale :** testabilité du métier hors Android, remplacement des technologies sans refonte. · **Consequences :** plus de code de liaison (mappers, interfaces) ; en échange, chaque couche évolue seule. · **Alternatives :** architecture par écrans sans couches (rapide au début, ingérable à l'échelle — c'est l'histoire du legacy) ; architecture hexagonale stricte (bénéfice équivalent, vocabulaire moins répandu dans l'écosystème Android).

### ADR-002 : Domain-Driven Design
**Status : Accepted** · **Context :** les concepts du produit (Publication, ReadingState, VoiceProfile, Locator) doivent survivre aux choix techniques. · **Decision :** le domaine métier est le cœur ; les technologies l'implémentent. · **Rationale :** langage commun stable, indépendance technologique. · **Consequences :** discipline de modélisation avant l'implémentation. · **Alternatives :** modèle guidé par le schéma de base (couple le métier à Room — rejeté).

### ADR-003 : Offline First
**Status : Accepted** · **Context :** produit de lecture destiné à un usage quotidien y compris sans réseau ; marché cible où la connectivité n'est pas garantie. · **Decision :** toutes les fonctions essentielles fonctionnent hors ligne ; le réseau est optionnel (sync, voix cloud, téléchargement ponctuel de modèles). · **Rationale :** disponibilité, vie privée, expérience. · **Consequences :** la sync est une réconciliation différée (§9), pas une dépendance ; les modèles de voix se téléchargent une fois puis vivent localement. · **Alternatives :** cloud-first (rejeté : contraire à la propriété des données et au contexte d'usage).

### ADR-004 : Capability-Aware TTS Abstraction
**Status : Accepted** · **Context :** plusieurs moteurs TTS aux capacités inégales ; le risque d'une abstraction naïve est de niveler par le bas et de perdre la fonctionnalité signature (timestamps mot). · **Decision :** interface commune `TtsEngine` + contrat `TtsCapabilities` déclaratif (§8.4). · **Rationale :** interchangeabilité sans plus petit dénominateur commun. · **Consequences :** l'UI adapte honnêtement ses fonctionnalités au moteur actif. · **Alternatives :** interface minimale commune (rejetée : efface l'avantage compétitif) ; pas d'abstraction, Sherpa codé en dur (rejetée : ferme Edge TTS et les moteurs futurs).

### ADR-005 : Unified Document Model
**Status : Accepted** · **Context :** plusieurs formats de livres ; Reader, TTS et recherche ne doivent pas connaître le format d'origine. · **Decision :** tout parser produit le même Document Model (§7.5). · **Rationale :** un seul moteur de lecture pour tous les formats. · **Consequences :** chaque nouveau format = un parser, rien d'autre. · **Alternatives :** chemins de rendu par format (duplication de tout le Reader — rejeté).

### ADR-006 : Modular Project Structure
**Status : Accepted** · **Context :** mono-module legacy : temps de build croissants, frontières implicites, violations de couches invisibles. · **Decision :** structure multi-modules Gradle (§12) avec règles de dépendance encodées dans `build-logic/`. · **Rationale :** frontières vérifiées par le build, compilation incrémentale, développement parallèle. · **Consequences :** coût initial de mise en place des convention plugins. · **Alternatives :** mono-module à packages disciplinés (rejeté : la discipline non outillée s'érode — démontré par le legacy).

### ADR-007 : Local Data Ownership
**Status : Accepted** · **Context :** les livres et données de lecture appartiennent à l'utilisateur. · **Decision :** stockage local par défaut, sync optionnelle et contrôlée par l'utilisateur. · **Rationale :** confiance, conformité à Offline First. · **Consequences :** la valeur du produit ne dépend d'aucun compte ni serveur. · **Alternatives :** compte obligatoire (rejeté).

### ADR-008 : Reader as Core Component
**Status : Accepted** · **Context :** besoin d'un critère d'arbitrage produit. · **Decision :** le Reader est le cœur ; TTS, annotations, recherche, statistiques gravitent autour. · **Rationale :** Reading First (§1.4). · **Consequences :** aucun développement périphérique ne dégrade l'expérience de lecture. · **Alternatives :** produit audio-first (rejeté : InkTone est un lecteur qui parle, pas un lecteur audio qui affiche).

### ADR-009 : Performance by Design, with Budgets
**Status : Accepted** · **Context :** « performance is a feature » sans chiffres est un slogan ; le legacy a montré des dégradations découvertes tard (import 15+ minutes). · **Decision :** budgets chiffrés (§11.2) sur baseline Snapdragon 680, vérifiés par benchmarks, intégrés à la Definition of Done. · **Rationale :** mesurer avant d'optimiser exige des cibles. · **Consequences :** un dépassement bloque la release ou déclenche un ADR de révision. · **Alternatives :** objectifs qualitatifs (rejetés : invérifiables).

### ADR-010 : Privacy by Design
**Status : Accepted** · **Context :** produit manipulant des habitudes de lecture, données sensibles par nature. · **Decision :** minimisation de la collecte, traitement local, transparence, consentement pour tout envoi (§10). · **Rationale :** confiance des utilisateurs, éthique du produit. · **Consequences :** contraintes sur la télémétrie — résolues par l'ADR-014. · **Alternatives :** télémétrie par défaut avec opt-out (rejetée : contraire au principe).

## 15.4 Structural ADRs (nouveaux en 1.1.0)

### ADR-011 : Readium comme fondation EPUB, encapsulée
**Status : Accepted** · **Context :** le Reading Engine exige parsing EPUB, navigation, locators. Construire un Document Model custom from scratch signifie réimplémenter tout cela ; dépendre nûment de Readium couple le domaine à une bibliothèque externe. Le legacy utilisait Readium avec succès (parsing, `belongsTo`, subjects). · **Decision :** Readium est le parser EPUB officiel, confiné à `infrastructure/parser` ; le Document Model et le Locator du domaine encapsulent les modèles Readium avec mapping sans perte. · **Rationale :** hériter d'années de travail sur la conformité EPUB tout en gardant le domaine indépendant. · **Consequences :** une couche de mapping à maintenir ; les autres formats (TXT, PDF) produisent le même Document Model par leurs propres parsers. · **Alternatives :** Document Model 100 % custom (rejeté : réimplémentation massive sans valeur ajoutée) ; exposition directe des types Readium dans le domaine (rejetée : violation de la règle de dépendance).

### ADR-012 : MVI comme pattern de présentation
**Status : Accepted** · **Context :** la couche Presentation doit avoir un pattern d'état normatif, sinon chaque contributeur — humain ou agent — tranche différemment. Le legacy a éprouvé MVI avec succès. · **Decision :** MVI formalisé au §4.4 : état unique immuable par écran, intents explicites, effets par canal dédié. · **Rationale :** prévisibilité, testabilité des ViewModels en JVM, cohérence inter-écrans. · **Consequences :** un peu de cérémonie par écran ; en échange, chaque écran se teste par « intent entrant → état attendu ». · **Alternatives :** MVVM libre (rejeté : états multiples divergents, le défaut que MVI corrige) ; Molecule/autres frameworks (rejetés : dépendance supplémentaire sans besoin démontré).

### ADR-013 : Sherpa-ONNX moteur de référence, timestamps mot exigence de première classe
**Status : Superseded by ADR-021 (2026-07-26)**

> **Note de révision :** la prémisse de cet ADR — « Sherpa-ONNX fournit
> des timestamps natifs » — s'est révélée fausse à la vérification
> empirique en Phase 3 (inspection directe des bindings Kotlin et
> Python). `GeneratedAudio` n'expose que `samples`/`sample_rate`, pour
> tous les modèles supportés. Sherpa-ONNX reste le moteur de synthèse de
> référence pour la **qualité vocale** ; la question des timestamps mot
> est traitée séparément par ADR-021. Contenu original conservé
> ci-dessous pour l'historique — ne pas le lire comme la décision en
> vigueur.

**Context (original) :** le surlignage mot-à-mot avec vrais timestamps est l'écart compétitif n°1 identifié face aux lecteurs top-tier. Sherpa-ONNX fournit des timestamps natifs ; Piper non — un pivot non documenté vers Piper a déjà été flagué et corrigé dans l'historique du projet. Le legacy simulait le surlignage par interpolation proportionnelle aux caractères, approche rejetée comme malhonnête. · **Decision :** Sherpa-ONNX est le moteur de référence ; `wordTimestamps` est une capability de premier rang du contrat `TtsCapabilities` ; le surlignage mot n'est jamais simulé (§8.9). · **Rationale :** la fonctionnalité signature repose sur des données réelles du moteur, pas sur une illusion. · **Consequences :** les moteurs sans timestamps offrent un surlignage phrase, honnêtement annoncé ; le critère de précision ±120 ms entre dans les benchmarks. · · **Alternatives :** interpolation par caractères (rejetée : trompeuse) ; exiger les timestamps de tous les moteurs (rejeté : exclurait Piper et Edge TTS inutilement).

### ADR-014 : Crash reporting en opt-in explicite
**Status : Accepted** · **Context :** Crashlytics est nécessaire à la qualité mais constitue un envoi réseau de données — en tension frontale avec « aucune donnée sans accord explicite » (§10.2). Le legacy embarquait Crashlytics sans flux de consentement. · **Decision :** rapport de crash désactivé par défaut ; opt-in à l'onboarding avec explication honnête du contenu ; réversible dans les réglages ; no-op gracieux sans identifiants Firebase commis (K10). · **Rationale :** réconcilier qualité et Privacy by Design sans hypocrisie documentaire. · **Consequences :** taux de remontée partiel — accepté ; les testeurs volontaires activent. · **Alternatives :** opt-out (rejeté : contraire au §10.2) ; aucun crash reporting (rejeté : voler à l'aveugle).

### ADR-015 : Storage Access Framework exclusivement
**Status : Accepted** · **Context :** le legacy a utilisé `MANAGE_EXTERNAL_STORAGE` — blocker de publication Play Store, marqué résolu dans la documentation alors qu'il restait actif dans le code. · **Decision :** SAF (sélecteur de documents, URI persistées) est l'unique voie d'accès aux fichiers ; `MANAGE_EXTERNAL_STORAGE` interdit et vérifié en CI (§14.8). · **Rationale :** conformité Play Store, moindre privilège. · **Consequences :** l'import « scanner un dossier » passe par `OpenDocumentTree` ; ergonomie légèrement différente d'un accès brut, assumée. · **Alternatives :** `MANAGE_EXTERNAL_STORAGE` avec justification Play Store (rejetée : refusée en pratique pour un lecteur d'ebooks).

### ADR-016 : WAL comme journal mode
**Status : Accepted** · **Context :** le legacy en TRUNCATE payait un coût de commit croissant avec la taille de la base — dégradation mesurée sur l'import par lot (15+ minutes pour 500 EPUB avant correction). · **Decision :** WAL obligatoire (§6.5). · **Rationale :** coût de commit constant, lectures concurrentes aux écritures. · **Consequences :** fichiers -wal/-shm à côté de la base ; sans objet, un seul processus y accède. · **Alternatives :** TRUNCATE (rejeté par les mesures du legacy).

### ADR-017 : Périmètre PDF différé et borné
**Status : Accepted** · **Context :** la version 1.0.0 plaçait PDF en v1 aux côtés d'EPUB. Un PDF top-tier (rendu paginé sans reflow, extraction fiable de l'ordre de lecture pour le TTS) est un chantier à part entière ; le mener en v1 aurait dilué la qualité EPUB — contraire au standard « production-grade, no half-measures ». · **Decision :** v1 = EPUB + TXT complets ; PDF en v1.x, **affichage seul** d'abord ; TTS sur PDF conditionné à une extraction d'ordre de lecture validée. · **Rationale :** exceller sur un périmètre plutôt que moyenner sur deux. · **Consequences :** communication produit claire sur le périmètre v1. · **Alternatives :** PDF complet en v1 (rejeté : risque qualité sur les deux formats) ; abandon du PDF (rejeté : demande réelle, l'architecture le prévoit).

### ADR-018 : Distribution des modèles de voix à la demande
**Status : Accepted** · **Context :** un modèle Sherpa-ONNX pèse des dizaines de Mo ; les embarquer explose le budget APK (≤ 60 Mo, §11.2) ; ne pas les fournir casse le TTS. · **Decision :** APK sans modèles ; téléchargement de la voix française de référence proposé à l'onboarding ; stockage local ensuite, usage intégralement hors ligne ; vérification d'empreinte ; gestion dans les réglages (§8.11). · **Rationale :** APK léger, Offline by Default respecté après un téléchargement unique. · **Consequences :** premier lancement avec une étape réseau optionnelle ; le mode sans TTS reste fonctionnel pour la lecture visuelle. · **Alternatives :** modèles dans l'APK (rejeté : budget) ; Play Feature Delivery (à réévaluer si la friction du téléchargement direct s'avère excessive — noté comme piste, pas retenu en v1).

### ADR-019 : Réécriture complète sur branche orpheline
**Status : Accepted** · **Context :** voir §13.1–13.3. · **Decision :** nouvelle `main` orpheline ; legacy archivé en `legacy/monolith`, tagué, protégé, jamais fusionné. · **Rationale :** architecture cible structurellement différente ; fenêtre pré-release ; coût de réécriture assistée faible si les leçons sont spécifiées. · **Consequences :** risque classique de réécriture, encadré par les garde-fous bloquants du §13.4 ; historique Git vierge commençant par la documentation. · **Alternatives :** migration incrémentale strangler fig (rejetée : coût supérieur pour cette ampleur de restructuration) ; nouveau dépôt (rejeté : perd la co-localisation de l'historique et des leçons).

### ADR-020 : Rupture du schéma de base de données
**Status : Accepted** · **Context :** le schéma legacy (v17) porte l'histoire de ses migrations, dont un trou 6→13 documenté ; le nouveau Data Model (§6) en diffère structurellement (Locator à plat, scission ReadingState/ReadingSession). · **Decision :** nouveau schéma version 1, aucune migration depuis le legacy ; installations de test ré-importées. · **Rationale :** pré-release, aucune installation publique — la seule fenêtre où cette rupture est gratuite. · **Consequences :** dès la première publication, plus aucune rupture ne sera possible : les règles du §6.4 s'appliquent sans exception à partir de la v1. · **Alternatives :** migration v17→v1 (rejetée : coût élevé pour zéro utilisateur) ; reprendre le schéma legacy (rejeté : reconduirait la conflation ReadingSession et l'adressage triple).

### ADR-021 : Architecture à paliers pour le timing mot du TTS
**Status : Accepted** · **Date : 2026-07-26**

**Context :** ADR-013 supposait que Sherpa-ONNX fournissait des
timestamps natifs pour le TTS. La vérification empirique en Phase 3 l'a
infirmé : `GeneratedAudio` (Kotlin et Python, même cœur C++) n'expose
que `samples`/`sample_rate`, pour tous les backends de modèle
(VITS, Matcha, Kokoro, Pocket, Supertonic, ZipVoice). Une recherche
comparative a confirmé qu'il s'agit d'un manque connu et documenté côté
Sherpa-ONNX (issue #3536, alignement forcé demandé mais non implémenté),
et a établi qu'aucun moteur TTS offline neuronal évalué n'expose de
timing mot natif de bout en bout sur Android sans travail
supplémentaire.

**Decision :** InkTone adopte une architecture à paliers pour le
surlignage mot-à-mot, au lieu de parier sur un unique moteur :

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

**Rationale :** le Palier 1 est un filet de sécurité quasi gratuit
(Kotlin pur, zéro JNI, zéro modèle supplémentaire) qui valide toute la
chaîne Locator → surlignage → reprise (K3) dès la marche à blanc, avant
d'avoir résolu le Palier 2. Le Palier 2 préserve la promesse de qualité
vocale et d'usage hors ligne simultanément, via un mécanisme (alignement
forcé) déjà éprouvé en production dans l'écosystème Sherpa-ONNX
(`react-native-sherpa-onnx`, mode `timingMode: 'aligned'`).

**Consequences :** deux chemins TTS à construire au lieu d'un ; la
fiabilité du Palier 1 dépend du moteur OS installé (Google TTS confirmé,
moteurs constructeur incertains — détection au runtime nécessaire) ; le
Palier 2 ajoute un modèle CTC et une latence de traitement à mesurer
(§11.2, nouveau budget à fixer en Phase 5) ; le contrat de domaine
(`TtsEngine`, `WordTimestamp`, Tâche 1.7) reste inchangé — seule
l'infrastructure change, preuve que la conception du domaine a bien
isolé cette incertitude.

**Alternatives Considered :** simuler le timing par interpolation de
caractères (rejeté — malhonnête, §8.9) ; Edge TTS comme mécanisme
principal de timing (rejeté — en ligne, contraire à Offline First) ;
portage natif de l'extracteur de durée interne de Kokoro vers
Kotlin/C++, qui éliminerait le second passage (retenu comme piste
d'optimisation future si le Palier 2 s'avère trop coûteux, pas comme
décision v1 — effort de portage élevé, non justifié sans mesure
préalable).

**Addendum (Tâche 3.1, device V2206) :** certains moteurs TTS
constructeur ne respectent pas la sémantique documentée par Android pour
`onRangeStart(utteranceId, start, end, frame)`. Sur le device de test
(voix embarquée `fr-fr-x-frb-seanet-embedded`), les paramètres portent en
réalité `(audioPosition, charStart, charEnd)` au lieu de
`(charStart, charEnd, audioFrame)` — décodage vérifié sur 7 mots
consécutifs. `AndroidNativeTtsEngine.resolveWordBoundary()` teste les
deux interprétations et ignore (avec avertissement) tout évènement qui
ne correspond à aucune des deux, plutôt que de supposer laquelle est
active. **Ne jamais retirer cette double interprétation en pensant
simplifier** : elle encode un comportement réel observé, pas une
précaution excessive.

---

# 16. Future Roadmap

## 16.1 Purpose

La roadmap présente la vision d'évolution d'InkTone : les grandes versions, les objectifs et les capacités que l'architecture doit accueillir sans refonte. Elle est indicative — elle guide les priorités sans constituer un engagement de calendrier. **Elle est le seul système de phasage du projet** (revue B9) : le phasage des formats (§7.4) s'y réfère.

## 16.2 Long-Term Vision

Une plateforme de lecture complète : lecture visuelle et audio unifiées, synchronisation mot-à-mot signature, personnalisation avancée, fonctionnement hors ligne, synchronisation optionnelle, extensibilité par modules.

## 16.3 Version 1.x — Core Reading Experience

Objectif : une base irréprochable sur un périmètre maîtrisé.

* Bibliothèque locale : import par lot (SAF), filtres réels (favoris, séries, tags via `subjects`, auteur, statut), détection DRM et doublons, performances tenues à 1 000+ livres ;
* Import EPUB et TXT complets (§7.4) ;
* Reader : position unique par Locator, reprise fiable dans les deux modes, annotations, signets, recherche plein texte (FTS) ;
* **TTS avec surlignage mot-à-mot sur timestamps réels (Sherpa-ONNX)** — la fonctionnalité signature est un livrable v1, pas une évolution future ;
* **Socle d'accessibilité** : labels TalkBack, tailles dynamiques, contrastes, cibles tactiles — engagement de la vision (§1.4), tenu dès la v1 ;
* Statistiques locales ;
* Onboarding : consentement crash reporting (ADR-014), téléchargement de la voix de référence (ADR-018) ;
* Budgets de performance §11.2 tenus et mesurés.

## 16.4 Version 1.x+ — Advanced Reading

Collections personnalisées ; entité Tag normalisée au-delà des `subjects` ; **PDF en affichage ✅ (Lot 12, 2026-08-12)** — rendu paginé sans reflow (PDFium), import, couverture, navigation page à page, signets, reprise de lecture, thèmes sombre/sépia sur pages vectorielles. TTS sur PDF conditionné à l'extraction d'ordre de lecture (ADR-017, volet 2) ; thèmes avancés ; dictionnaire intégré ; améliorations typographiques (césure, justification).

## 16.5 Version 2.x — Sync & Beyond

Synchronisation optionnelle multi-fournisseurs (§9) ; sauvegardes ; migration d'appareil ; TTS sur PDF si l'extraction d'ordre de lecture est validée ; HTML/Markdown/FB2 selon demande ; traduction de passages.

## 16.6 Version 3.x — Intelligent Reading

Résumés, explication de passages, recherche sémantique, recommandations locales, assistance contextuelle — dans le respect strict des principes de confidentialité (§10) : le traitement local est privilégié, tout traitement distant est opt-in.

## 16.7 Version 4.x — Multi-Platform

Desktop, tablette (mode double page), autres cibles selon besoin. Le domaine étant indépendant d'Android, une part importante du cœur est réutilisable.

## 16.8 Evolution Principles

Toute évolution préserve : la modularité, la règle de dépendance, la compatibilité avec le domaine, les budgets de performance, la confidentialité — et passe par la mise à jour de ce Blueprint et des ADR (§17.3).

---

# 17. Governance & Appendices

## 17.1 Purpose

Ce chapitre fixe la gouvernance documentaire du projet et regroupe les références partagées : hiérarchie des documents, glossaire, maintenance du Blueprint.

## 17.2 Documentation Governance — le code fait foi

L'histoire du projet a démontré le danger des documents de statut déclaratifs : PROJECT_STATUS.md et des fichiers d'audit ont, de façon répétée, marqué comme résolus des problèmes actifs dans le code. La règle est donc formalisée :

| Document | Rôle | Autorité |
|---|---|---|
| **Blueprint** (ce document) | Prescriptif : ce que le système **doit** être | Référence architecturale ; modifiable par version + ADR |
| **ADR** (`docs/adr/`) | Décisions : pourquoi le système est ainsi | Historique immuable ; jamais supprimé, seulement `Superseded` |
| **Code source** | Descriptif : ce que le système **est** | **Fait foi pour tout état d'avancement** |
| Documents de statut (CHANGELOG, statut projet) | Déclaratif | Vérifiable : toute affirmation d'avancement cite un commit, un fichier ou un test ; une affirmation invérifiable est réputée fausse |

Règles :

1. Tout audit d'avancement se fait sur le code source, jamais sur les documents de statut.
2. Un écart entre le Blueprint et le code est soit un bug du code (à corriger), soit une évolution légitime (à acter par ADR + mise à jour du Blueprint) — jamais un écart silencieux.
3. Les critères d'achèvement sont des comportements vérifiables (§14.9), pas des cases cochées.

## 17.3 Blueprint Maintenance & Versioning

Document vivant, versionné sémantiquement :

* **MAJOR** : changement architectural incompatible ;
* **MINOR** : capacités nouvelles compatibles ;
* **PATCH** : corrections et clarifications.

À chaque évolution majeure : version incrémentée, sections mises à jour, nouveaux ADR ajoutés, décisions obsolètes conservées avec leur historique. Le Blueprint vit dans `docs/blueprint/` de la branche `main`.

## 17.4 Glossary

| Terme | Définition |
|---|---|
| **Publication** | Œuvre numérique importée dans InkTone |
| **Library** | Collection des publications de l'utilisateur |
| **Locator** | Value object d'adressage unique d'une position dans une publication (§3.2) |
| **ReadingState** | État de reprise d'une publication : position, profil vocal, surcharges — au plus un par publication |
| **ReadingSession** | Enregistrement historique d'une période de lecture, à des fins statistiques |
| **Bookmark** | Repère enregistré à un Locator |
| **Annotation** | Surlignage, note ou citation liée à une plage de Locators |
| **VoiceProfile** | Configuration vocale réutilisable (moteur, voix, paramètres) |
| **TTS Engine** | Composant transformant le texte en parole, derrière le contrat TtsCapabilities |
| **TtsCapabilities** | Déclaration des capacités d'un moteur (offline, timestamps mot, langues…) |
| **Document Model** | Représentation interne unifiée d'un document, indépendante du format |
| **Parser** | Composant convertissant un fichier vers le Document Model |
| **Repository** | Interface d'accès aux données sans exposer leur stockage |
| **Use Case** | Opération métier représentant une action utilisateur ou un processus |
| **Adapter** | Composant reliant InkTone à une technologie externe sans exposer ses détails |
| **ADR** | Architecture Decision Record : décision d'architecture documentée et historisée |
| **K1–K12** | Acquis capitalisés du legacy (§13.4) |

## 17.5 Documentation Standards

Chaque module documente : son rôle, ses dépendances, ses interfaces publiques, ses responsabilités, ses limites. Toute décision importante passe par un ADR. Les diagrammes suivent une représentation unique : flèches = sens des dépendances, blocs = modules, couches de haut en bas.

## 17.6 Final Statement

Le **InkTone Software Architecture Blueprint** est la référence architecturale officielle du projet. Toute évolution du code reste cohérente avec ses principes, ses modèles et ses décisions — et tout écart constaté est traité selon la gouvernance du §17.2.

L'objectif : qu'InkTone demeure modulaire, maintenable, performant, extensible, centré sur l'expérience de lecture, fidèle à Offline First — et honnête, dans ses fonctionnalités comme dans sa documentation.

---

# End of Document

**InkTone Software Architecture Blueprint**
**Version : 1.2.2** · **Statut : Review** · **Auteur : Issa ADAMOU**
