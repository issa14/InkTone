# Phase 0 — Bootstrap

**Dépend de :** rien (point de départ)
**Précède :** Phase 1 — Fondations du domaine
**Référence :** Blueprint InkTone v1.1.0, §13.7 (Bootstrap Sequence), §12 (Project Structure), §14.8 (CI Gates)
**Sortie de phase :** les 5 critères du §13.7 remplis — voir Checklist finale en fin de document.

---

## ⚠️ Répartition Issa / Claude Code pour cette phase

La Tâche 0.1 (archivage du legacy, `--force-with-lease`, renommage de `main`) touche l'historique Git de façon difficilement réversible. **Recommandation : exécute la Tâche 0.1 toi-même, ligne par ligne, en vérifiant chaque push avant le suivant.** Les Tâches 0.2 à 0.7 sont sans risque (nouveaux fichiers uniquement) et conviennent parfaitement à Claude Code, y compris en une seule session si tu préfères.

---

## Tâche 0.1 — Archivage du legacy et branche orpheline

**Objectif :** conserver le code existant en lecture seule, démarrer un historique vierge.

**Prérequis :** `git status` propre sur `main`, aucun commit local non poussé.

**Commandes exactes :**

```bash
# --- Étape 1 : vérifier l'état avant toute action irréversible ---
git checkout main
git pull origin main
git status                      # doit être "nothing to commit, working tree clean"
git log --oneline -1            # note ce hash, c'est ton point de retour

# --- Étape 2 : archiver ---
git tag legacy-final-v0
git branch legacy/monolith
git push origin legacy/monolith legacy-final-v0

# --- Étape 3 : vérifier l'archive AVANT de toucher à main ---
# Va sur GitHub, confirme que la branche legacy/monolith et le tag
# legacy-final-v0 existent bien et pointent sur le bon commit.

# --- Étape 4 : protéger legacy/monolith ---
# Sur GitHub : Settings → Branches → Add rule → legacy/monolith
# → cocher "Restrict deletions" et "Lock branch" (lecture seule).

# --- Étape 5 : créer la nouvelle main orpheline ---
git checkout --orphan main-rewrite
git rm -rf .
# (le prochain commit sur cette branche sera fait en Tâche 0.2)
```

**Critère de validation avant/après :**
- Avant : `main` contient 68 commits du legacy, aucune branche `legacy/monolith`.
- Après : `legacy/monolith` et le tag `legacy-final-v0` existent sur GitHub, branche protégée en lecture seule ; la branche locale `main-rewrite` existe, orpheline, répertoire de travail vide (`git log` sur cette branche renvoie vide, `ls` ne montre plus les fichiers legacy).

**Ne pas encore pousser `main-rewrite` ni renommer `main`** — cela se fait en fin de Tâche 0.2, une fois le premier commit prêt.

---

## Tâche 0.2 — Premier commit : Blueprint et ADR fondateurs

**Objectif :** respecter §2.1 (Architecture First) et §13.7 : le premier commit de la nouvelle `main` est la documentation, jamais du code.

**Fichiers à créer (sur la branche `main-rewrite`, répertoire de travail vide depuis 0.1) :**

```text
docs/
├── blueprint/
│   └── BLUEPRINT_ARCHITECTURE_INKTONE_v1.1.0.md
└── adr/
    ├── ADR-001-clean-architecture.md
    ├── ADR-002-domain-driven-design.md
    ├── ADR-003-offline-first.md
    ├── ADR-004-capability-aware-tts-abstraction.md
    ├── ADR-005-unified-document-model.md
    ├── ADR-006-modular-project-structure.md
    ├── ADR-007-local-data-ownership.md
    ├── ADR-008-reader-as-core-component.md
    ├── ADR-009-performance-by-design-with-budgets.md
    ├── ADR-010-privacy-by-design.md
    ├── ADR-011-readium-encapsulated.md
    ├── ADR-012-mvi-presentation-pattern.md
    ├── ADR-013-sherpa-onnx-reference-engine.md
    ├── ADR-014-crash-reporting-opt-in.md
    ├── ADR-015-storage-access-framework-only.md
    ├── ADR-016-wal-journal-mode.md
    ├── ADR-017-pdf-scope-deferred.md
    ├── ADR-018-voice-model-distribution.md
    ├── ADR-019-full-rewrite-orphan-branch.md
    └── ADR-020-database-schema-break.md
README.md
CONTRIBUTING.md
```

**Contenu :**

1. `docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.1.0.md` : copie intégrale du Blueprint validé.

2. Chaque `docs/adr/ADR-XXX-*.md` : reprend le contenu condensé du §15.3/§15.4 du Blueprint, développé au format complet du template §15.2. Exemple pour ADR-001 (à répliquer pour les 19 autres avec leur contenu respectif) :

```markdown
# ADR-001 : Adoption de la Clean Architecture

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le projet vise plusieurs années d'évolution avec des technologies (UI, base
de données, moteurs TTS) susceptibles de changer. L'implémentation legacy
mono-couche rendait les responsabilités poreuses : logique métier mêlée à
la persistance et à l'UI, ce qui a rendu chaque audit du projet plus long
et chaque correction plus risquée.

## Decision

InkTone adopte une architecture en couches (Presentation → Application →
Domain ← Data ← Infrastructure, cf. Blueprint §4.3) avec une règle de
dépendance stricte : toutes les dépendances pointent vers le domaine.

## Rationale

- Testabilité du métier indépendamment d'Android.
- Remplacement des technologies (base de données, moteur TTS, parseur)
  sans modifier le cœur métier.
- Clarté des responsabilités par couche.

## Consequences

Plus de code de liaison (interfaces, mappers) qu'une architecture plate.
En contrepartie, chaque couche évolue et se teste indépendamment.

## Alternatives Considered

- **Architecture par écrans sans couches** : rapide à démarrer, mais c'est
  exactement le schéma qui a produit la dette technique du legacy.
- **Architecture hexagonale stricte** : bénéfices équivalents, vocabulaire
  moins répandu dans l'écosystème Android — Clean Architecture retenue
  pour la familiarité de l'outillage et de la documentation disponibles.
```

Pour les 19 autres ADR, reprendre le contenu déjà rédigé au Blueprint §15.3 (ADR-002 à ADR-010) et §15.4 (ADR-011 à ADR-020), en développant chaque rubrique condensée (Context/Decision/Rationale/Consequences/Alternatives) en paragraphes complets selon le même principe que l'exemple ci-dessus. Le contenu de fond est déjà arrêté — il s'agit d'une mise en forme, pas d'une nouvelle rédaction.

3. `README.md` — minimal, pointe vers le Blueprint :

```markdown
# InkTone

Lecteur EPUB Android premium avec narration TTS neuronale synchronisée
mot à mot. Architecture, décisions et roadmap : voir
[`docs/blueprint/`](docs/blueprint/) et [`docs/adr/`](docs/adr/).

> Ce dépôt a été réécrit depuis zéro le 2026-07-26. L'implémentation
> précédente est archivée sur la branche `legacy/monolith` (lecture
> seule) — voir ADR-019.

## Statut

Bootstrap en cours (Phase 0). Voir le Blueprint, chapitre 16, pour la
roadmap produit, et le plan d'exécution Claude Code pour l'avancement
technique détaillé.
```

4. `CONTRIBUTING.md` — encode la gouvernance documentaire du §17.2 :

```markdown
# Contribuer à InkTone

## Le code fait foi

Aucun document de statut (CHANGELOG, notes d'avancement) ne doit affirmer
qu'une fonctionnalité est terminée sans citer le commit, le fichier ou le
test qui le prouve. Tout audit d'avancement se fait sur le code source,
jamais sur la documentation déclarative. Voir Blueprint §17.2.

## Toute décision d'architecture passe par un ADR

Nouveau fichier dans `docs/adr/`, format du Blueprint §15.2 — jamais de
suppression, un ADR remplacé passe en statut `Superseded`.

## Règles de dépendance entre modules

Encodées dans `build-logic/` (voir Blueprint §12.4). Une dépendance
interdite fait échouer le build, pas la revue de code.

## Conventions de code

Voir Blueprint §12.5 : nommage par concepts métier, Use Cases en verbe à
l'infinitif, aucun emoji dans le code de production, messages de commit
en français à l'impératif ("Corrige…", "Ajoute…", "Initialise…").
```

**Commande de commit :**

```bash
git add .
git commit -m "Initialise InkTone v2 : Blueprint 1.1.0 et ADR fondateurs"
```

**Critère de validation avant/après :**
- Avant : répertoire de travail vide (issu de la Tâche 0.1).
- Après : `git log --oneline` sur `main-rewrite` affiche un unique commit ; `git show --stat HEAD` liste uniquement des fichiers sous `docs/`, `README.md`, `CONTRIBUTING.md` — **aucun fichier `.kt`, `.gradle.kts` ou autre code**.

---

## Tâche 0.3 — Bascule des noms de branche

**Objectif :** `main-rewrite` devient la `main` officielle.

```bash
# Sur GitHub d'abord : lever temporairement la protection de branche
# sur main (Settings → Branches), sinon le push --force sera rejeté.

git branch -m main legacy-main-temp
git branch -m main-rewrite main
git push origin main --force-with-lease

# Vérification AVANT suppression :
# Sur GitHub, confirme que main affiche bien le commit unique de la
# Tâche 0.2, et que legacy/monolith (poussée en 0.1) est intacte et
# distincte.

git push origin --delete legacy-main-temp

# Remettre la protection de branche sur main (Settings → Branches),
# cette fois avec les règles définitives : PR obligatoire, CI verte
# obligatoire (une fois le workflow de la Tâche 0.6 en place).
```

**Critère de validation avant/après :**
- Avant : `main` = 68 commits legacy, `legacy/monolith` inexistante.
- Après : `main` = 1 commit (docs uniquement), `legacy/monolith` = 68 commits legacy intacts et protégée en lecture seule, `legacy-main-temp` supprimée.

---
## Tâche 0.4 — Squelette Gradle multi-modules

**Objectif :** matérialiser la liste canonique des modules (Blueprint §5.2/§12.2), sans une seule ligne de logique métier.

**Modules créés en Phase 0** (les modules post-v1 comme `infrastructure/sync` ne sont pas créés maintenant — ils le seront à la Phase où ils deviennent nécessaires, cf. Blueprint §16.5) :

```text
app
core/designsystem, core/ui, core/common, core/testing
domain
data
infrastructure/database, infrastructure/storage, infrastructure/parser,
infrastructure/tts, infrastructure/media, infrastructure/worker
feature/library, feature/reader, feature/player, feature/search,
feature/import, feature/settings, feature/statistics, feature/onboarding
```

### 0.4.1 — `settings.gradle.kts` (racine)

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "InkTone"

include(":app")

include(":core:designsystem")
include(":core:ui")
include(":core:common")
include(":core:testing")

include(":domain")
include(":data")

include(":infrastructure:database")
include(":infrastructure:storage")
include(":infrastructure:parser")
include(":infrastructure:tts")
include(":infrastructure:media")
include(":infrastructure:worker")

include(":feature:library")
include(":feature:reader")
include(":feature:player")
include(":feature:search")
include(":feature:import")
include(":feature:settings")
include(":feature:statistics")
include(":feature:onboarding")
```

### 0.4.2 — `gradle/libs.versions.toml` (catalogue de versions, socle minimal Phase 0)

```toml
[versions]
agp = "8.6.0"
kotlin = "2.0.20"
coreKtx = "1.13.1"
composeBom = "2024.09.02"
hilt = "2.52"
room = "2.6.1"
coroutines = "1.9.0"
junit = "4.13.2"
androidxTestExtJunit = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.20-1.0.25" }
room = { id = "androidx.room", version.ref = "room" }
```

### 0.4.3 — Squelette de chaque module

Chaque module reçoit, pour l'instant, **le strict minimum pour compiler** : un `build.gradle.kts` appliquant le convention plugin approprié (créé en Tâche 0.5) et un fichier Kotlin vide marqueur.

Exemple pour `domain` (module JVM pur, aucune dépendance Android) :

`domain/build.gradle.kts` :
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inktone.domain")
}
```

`domain/src/main/kotlin/com/inktone/domain/Placeholder.kt` :
```kotlin
package com.inktone.domain

// Marqueur de squelette Phase 0 — supprimé dès le premier commit de la
// Phase 1 (entités, value objects, interfaces de repository).
internal object DomainModulePlaceholder
```

Exemple pour un module feature (Android + Compose), `feature/reader` :

`feature/reader/build.gradle.kts` :
```kotlin
plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.reader"
}
```

**Répéter ce schéma pour les 21 modules restants**, en appliquant :
- `id("inktone.jvm")` pour les modules JVM purs sans Android (`core:common`, `core:testing`) — aucun module métier n'a de dépendance Android en Phase 0 hormis `domain` qui utilise `inktone.domain` spécifiquement (règle plus stricte, voir 0.5).
- `id("inktone.android.library")` pour les modules Android sans Compose (`infrastructure/*`, `data`).
- `id("inktone.feature")` pour les modules `feature/*` (Android + Compose + Hilt + dépendances domain/core par défaut).
- `id("inktone.application")` uniquement pour `app`.

Chaque `namespace` suit le schéma `com.inktone.<segment>.<module>` (ex. `com.inktone.infrastructure.database`, `com.inktone.core.designsystem`).

**Note d'exécution — `core:designsystem` et `core:ui` :** ce schéma à quatre plugins ne couvre pas ces deux modules : ils sont Android + Compose (comme `feature/*`) mais ne doivent dépendre ni de `domain` ni de Hilt (comme `core/*`), ce qu'aucun des convention plugins existants n'exprime. Exception assumée pour la Phase 0 : ils appliquent directement `com.android.library` + `org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.plugin.compose` dans leur propre `build.gradle.kts`, plus `id("inktone.architecture.check")` appliqué explicitement (et non hérité d'un convention plugin) pour rester couverts par la matrice §12.4. Ce n'est pas un oubli : si un troisième module Android+Compose sans dépendance domaine apparaît, il justifiera l'extraction d'un `inktone.android.compose.library` dédié plutôt que la duplication de ce bloc une troisième fois.

**Critère de validation avant/après :**
- Avant : aucun module n'existe.
- Après : `./gradlew projects` liste les 22 modules ; `./gradlew build` réussit (compilation à vide, tests vides autorisés à ce stade — cf. §13.7 point 4).
---

## Tâche 0.5 — `build-logic/` : règles de dépendance appliquées par construction

**Objectif :** transposer le §12.4 du Blueprint (règles de dépendance) en plugins Gradle, de sorte qu'une dépendance interdite **fasse échouer le build**, pas la revue de code — exactement l'exigence du §14.8.

**Approche à deux niveaux, volontairement redondante :**
1. **Par construction** : chaque convention plugin n'expose que les dépendances autorisées pour son type de module (un module `feature` ne peut physiquement pas déclarer une dépendance vers un autre `feature` sans le faire à la main, hors convention).
2. **Par vérification** : une tâche Gradle `checkArchitectureRules` parcourt les dépendances déclarées de chaque module et échoue si l'une d'elles viole la matrice autorisée — filet de sécurité si quelqu'un contourne le point 1.

### 0.5.1 — `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "build-logic"
include(":convention")
```

### 0.5.2 — `build-logic/convention/build.gradle.kts`

```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.inktone.buildlogic"

dependencies {
    compileOnly("com.android.tools.build:gradle:8.6.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
}

gradlePlugin {
    plugins {
        register("inktoneDomain") {
            id = "inktone.domain"
            implementationClass = "InkToneDomainConventionPlugin"
        }
        register("inktoneJvm") {
            id = "inktone.jvm"
            implementationClass = "InkToneJvmConventionPlugin"
        }
        register("inktoneAndroidLibrary") {
            id = "inktone.android.library"
            implementationClass = "InkToneAndroidLibraryConventionPlugin"
        }
        register("inktoneFeature") {
            id = "inktone.feature"
            implementationClass = "InkToneFeatureConventionPlugin"
        }
        register("inktoneApplication") {
            id = "inktone.application"
            implementationClass = "InkToneApplicationConventionPlugin"
        }
        register("inktoneArchitectureCheck") {
            id = "inktone.architecture.check"
            implementationClass = "InkToneArchitectureCheckPlugin"
        }
    }
}
```

### 0.5.3 — `InkToneDomainConventionPlugin.kt`

Le module `domain` : Kotlin JVM pur, **aucune dépendance autorisée vers quoi que ce soit d'autre dans le projet**. C'est la règle la plus stricte du Blueprint (§4.6 : « ne dépend d'aucune bibliothèque Android »).

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class InkToneDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("inktone.architecture.check")

            dependencies {
                add("testImplementation", "junit:junit:4.13.2")
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }

            // Garde-fou immédiat : si un jour quelqu'un ajoute
            // com.android.tools.build ou un plugin Android à ce module,
            // le build échoue avant même la résolution des dépendances.
            afterEvaluate {
                check(!pluginManager.hasPlugin("com.android.library")) {
                    "Le module domain ne peut pas appliquer de plugin Android " +
                        "(Blueprint §4.6 — le domaine ne dépend jamais d'Android)."
                }
            }
        }
    }
}
```

### 0.5.4 — `InkToneFeatureConventionPlugin.kt`

Un module `feature` : Android + Compose + Hilt, dépendances par défaut vers `domain` et `core/*` uniquement.

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.configure

class InkToneFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("inktone.architecture.check")

            extensions.configure<LibraryExtension> {
                compileSdk = 34
                defaultConfig { minSdk = 26 }
                buildFeatures { compose = true }
            }

            dependencies {
                add("implementation", project(":domain"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:common"))
                add("implementation", "com.google.dagger:hilt-android:2.52")
                add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
                add("testImplementation", project(":core:testing"))
                add("testImplementation", "junit:junit:4.13.2")
            }
        }
    }
}
```

*(`InkToneJvmConventionPlugin`, `InkToneAndroidLibraryConventionPlugin` et `InkToneApplicationConventionPlugin` suivent le même schéma — JVM pur sans dépendance projet pour `core:common`/`core:testing` ; Android library dépendant uniquement de `domain` pour `infrastructure/*` et `data` ; application dépendant de tous les `feature/*` et de `data`/`infrastructure/*` pour le graphe de DI, pour `app`.)*

### 0.5.5 — `InkToneArchitectureCheckPlugin.kt` — le filet de sécurité

```kotlin
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * Matrice de dépendances autorisées, transposition directe du Blueprint
 * §12.4. Clé = préfixe de chemin du module qui déclare la dépendance ;
 * valeur = préfixes de chemin qu'il a le droit de dépendre.
 */
private val ALLOWED_DEPENDENCIES: Map<String, Set<String>> = mapOf(
    ":domain" to emptySet(),
    ":core:common" to emptySet(),
    ":core:testing" to setOf(":core:common"),
    ":core:designsystem" to setOf(":core:common"),
    ":core:ui" to setOf(":core:common", ":core:designsystem"),
    ":data" to setOf(":domain", ":infrastructure"),
    ":infrastructure" to setOf(":domain"),
    ":feature" to setOf(":domain", ":core"),
    ":app" to setOf(":feature", ":data", ":infrastructure", ":core"),
)

class InkToneArchitectureCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("checkArchitectureRules", ArchitectureCheckTask::class.java)
        target.tasks.named("check").configure {
            it.dependsOn("checkArchitectureRules")
        }
    }
}

abstract class ArchitectureCheckTask : DefaultTask() {
    @TaskAction
    fun verify() {
        val modulePath = project.path
        val ownRule = ALLOWED_DEPENDENCIES.entries
            .firstOrNull { (prefix, _) -> modulePath.startsWith(prefix) }
            ?: return // module hors matrice (ex. build-logic lui-même) : ignoré

        val violations = mutableListOf<String>()

        project.configurations
            .filter { it.name in setOf("implementation", "api") }
            .forEach { config ->
                config.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .forEach { dep ->
                        val targetPath = dep.dependencyProject.path
                        val allowed = ownRule.value.any { targetPath.startsWith(it) }
                        if (!allowed) {
                            violations += "$modulePath -> $targetPath (règle : ${ownRule.key} " +
                                "n'autorise que ${ownRule.value})"
                        }
                    }
            }

        check(violations.isEmpty()) {
            "Violation(s) de la règle de dépendance (Blueprint §12.4) :\n" +
                violations.joinToString("\n") { "  - $it" }
        }
    }
}
```

**Critère de validation avant/après :**
- Avant : aucune règle n'est appliquée, une dépendance entre deux `feature/*` compilerait silencieusement.
- Après : ajouter manuellement `implementation(project(":feature:library"))` dans `feature/reader/build.gradle.kts` puis lancer `./gradlew :feature:reader:checkArchitectureRules` **doit échouer** avec un message citant la règle violée. Retirer la ligne, la tâche doit repasser au vert. Ce test négatif fait partie de la validation de la Tâche 0.5 elle-même — à exécuter une fois puis annuler, ne pas laisser la violation dans le code.
---

## Tâche 0.6 — CI minimale (portes du §14.8 applicables dès Phase 0)

**Objectif :** les portes qui ont un sens sans code métier tournent dès maintenant ; les autres (couverture de tests, benchmarks) s'ajouteront aux phases où elles deviennent pertinentes.

### 0.6.1 — `scripts/check-no-emoji.sh`

```bash
#!/usr/bin/env bash
# Blueprint §12.5 / §14.8 (K12) : aucun emoji dans le code de production.
set -euo pipefail

MATCHES=$(grep -rlP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]' \
    --include='*.kt' \
    -- */src/main 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "Emoji(s) détecté(s) dans le code de production :"
    echo "$MATCHES"
    exit 1
fi
echo "OK : aucun emoji dans les sources de production."
```

### 0.6.2 — `scripts/check-no-manage-external-storage.sh`

```bash
#!/usr/bin/env bash
# Blueprint §10.3 / §14.8 (K5) : MANAGE_EXTERNAL_STORAGE est interdit.
set -euo pipefail

MATCHES=$(grep -rl "MANAGE_EXTERNAL_STORAGE" \
    --include='AndroidManifest.xml' --include='*.kt' \
    -- */src 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "MANAGE_EXTERNAL_STORAGE détecté (interdit — ADR-015) :"
    echo "$MATCHES"
    exit 1
fi
echo "OK : MANAGE_EXTERNAL_STORAGE absent."
```

```bash
chmod +x scripts/check-no-emoji.sh scripts/check-no-manage-external-storage.sh
```

### 0.6.3 — `.github/workflows/ci.yml`

```yaml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  build-and-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configurer JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Cache Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build + tests unitaires + règles d'architecture
        run: ./gradlew build
        # `build` dépend de `check`, qui dépend de `checkArchitectureRules`
        # (câblé en Tâche 0.5.5) sur chaque module qui applique un
        # convention plugin inktone.*.

      - name: Vérifier l'absence d'emoji en production
        run: bash scripts/check-no-emoji.sh

      - name: Vérifier l'absence de MANAGE_EXTERNAL_STORAGE
        run: bash scripts/check-no-manage-external-storage.sh
```

**Critère de validation avant/après :**
- Avant : aucun workflow, aucune porte automatisée.
- Après : une pull request ouverte sur GitHub déclenche le workflow ; il passe au vert sur le squelette de la Tâche 0.4. Test négatif (à faire une fois, puis annuler) : ajouter un emoji dans un fichier `.kt` de `feature/reader/src/main` → le job `build-and-check` doit échouer sur l'étape emoji, pas ailleurs.

---

## Tâche 0.7 — Ouverture de la Pull Request de bootstrap

```bash
git checkout -b chore/phase-0-bootstrap
git add .
git commit -m "Ajoute le squelette multi-modules, les convention plugins et la CI"
git push origin chore/phase-0-bootstrap
```

Ouvrir la PR vers `main`, la CI de la Tâche 0.6 doit passer avant fusion. Une fois fusionnée, remettre la protection définitive de branche `main` (PR + CI obligatoires) évoquée en fin de Tâche 0.3 si ce n'est pas déjà fait.

---

## Checklist finale de sortie de Phase 0

Reprend exactement les 5 critères du Blueprint §13.7 :

- [ ] `legacy/monolith` et le tag `legacy-final-v0` sont poussés et la branche est protégée en lecture seule (Tâche 0.1).
- [ ] Le Blueprint v1.1.0 est le premier commit de la nouvelle `main` (Tâche 0.2 — vérifié : ce commit ne contient aucun fichier de code).
- [ ] Les 20 ADR de l'inventaire sont rédigés au format complet, statut Accepted (Tâche 0.2).
- [ ] La structure des 22 modules existe avec CI verte (Tâches 0.4, 0.5, 0.6).
- [ ] Les budgets chiffrés du §11.2 sont actés — ils sont déjà dans le Blueprint committé ; rien à faire de plus ici, ils servent de critères d'acceptation à partir de la Phase 4 (benchmarks).

Une fois les cinq cases cochées, la Phase 0 est close. Étape suivante : **Phase 1 — Fondations du domaine** (entités, value objects dont `Locator`, interfaces de repository, Use Cases en signature — cf. Blueprint §3 et §4.6).
