# Phase 1 — Fondations du domaine

**Dépend de :** Phase 0 (close)
**Précède :** Phase 2 — Fondations data & persistance
**Référence :** Blueprint InkTone v1.1.0, §3 (Domain Model), §4.6 (Domain Layer), §7.5 (Document Model), §8.4 (TtsCapabilities), §14.3 (Testing per-layer)
**Sortie de phase :** voir Checklist finale en fin de document.

**Emplacement du code :** tout le contenu de cette phase vit dans `domain/src/main/kotlin/com/inktone/domain/` et `domain/src/test/kotlin/...`, à l'exception des fakes qui vivent dans `core/testing/src/main/kotlin/com/inktone/core/testing/fake/`.

---

## Tâche 1.0 — Deux correctifs à la Phase 0, à faire avant tout le reste

En concevant le contenu réel du domaine, deux trous de la matrice de dépendances posée en Phase 0 apparaissent — ils n'étaient invisibles que parce que `domain` et `core:testing` étaient vides. À corriger en premier, sinon les tâches suivantes ne compilent pas.

### 1.0.1 — `domain` n'a pas de dépendance vers `kotlinx-coroutines-core`

Les interfaces de repository (Tâche 1.6) exposent des `Flow` et des fonctions `suspend`. `kotlinx-coroutines-core` est une bibliothèque Kotlin pure (aucune dépendance Android) : l'ajouter ne viole pas la règle « le domaine ne dépend jamais d'Android » (Blueprint §4.6). Seul un import `android.*` la violerait.

Dans `build-logic/convention/src/main/kotlin/InkToneDomainConventionPlugin.kt`, modifier le bloc `dependencies` :

```kotlin
dependencies {
    add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

### 1.0.2 — `core:testing` ne peut pas dépendre de `domain`

Les fakes de la Tâche 1.9 implémentent les interfaces de repository du domaine : `core:testing` doit pouvoir déclarer `implementation(project(":domain"))`. Or la matrice posée en Phase 0 (`InkToneArchitectureCheckPlugin.kt`) n'autorisait `:core:testing` qu'à dépendre de `:core:common`.

Corriger la matrice :

```kotlin
private val ALLOWED_DEPENDENCIES: Map<String, Set<String>> = mapOf(
    ":domain" to emptySet(),
    ":core:common" to emptySet(),
    ":core:testing" to setOf(":core:common", ":domain"),   // ← domain ajouté
    ":core:designsystem" to setOf(":core:common"),
    ":core:ui" to setOf(":core:common", ":core:designsystem"),
    ":data" to setOf(":domain", ":infrastructure"),
    ":infrastructure" to setOf(":domain"),
    ":feature" to setOf(":domain", ":core"),
    ":app" to setOf(":feature", ":data", ":infrastructure", ":core"),
)
```

**Note de conception, à ne pas « corriger » par erreur :** ce lien `core:testing → domain` ne crée pas de cycle. `domain` lui-même ne dépendra de `core:testing` qu'en `testImplementation` (Tâche 1.9), jamais en `implementation`/`api` — la tâche `checkArchitectureRules` posée en Phase 0 ne filtre que ces deux dernières configurations (voir Tâche 0.5.5), donc `testImplementation` échappe volontairement à la matrice. C'est le mécanisme correct pour ce cas, pas un trou à combler.

**Critère de validation avant/après :**
- Avant : `core/testing/build.gradle.kts` ne peut pas déclarer `project(":domain")` sans faire échouer `checkArchitectureRules`.
- Après : la même déclaration passe `./gradlew :core:testing:checkArchitectureRules`.

**Commit :** `Corrige la matrice d'architecture et les dépendances domain avant la Phase 1`

---
## Tâche 1.1 — `Locator`, le value object d'adressage unique

**Objectif :** transposer le Blueprint §3.2 exactement. C'est la pièce la plus critique du domaine — tout le reste (ReadingState, Bookmark, Annotation, recherche, TTS) s'adresse à travers elle.

`domain/src/main/kotlin/com/inktone/domain/valueobject/Locator.kt` :

```kotlin
package com.inktone.domain.valueobject

/**
 * Position unique dans une publication (Blueprint §3.2).
 *
 * Toute position — reprise de lecture, signet, annotation, résultat de
 * recherche, cible de synchronisation — s'exprime EXCLUSIVEMENT via ce
 * value object. Jamais de numéro de page (revue B5) : [progression] issue
 * de [computeProgression] est une valeur dérivée pour l'affichage,
 * jamais la source de vérité de la reprise de lecture.
 */
data class Locator(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int? = null,
    val charOffset: Int,
) : Comparable<Locator> {

    init {
        require(resourceHref.isNotBlank()) { "resourceHref ne peut pas être vide" }
        require(chapterIndex >= 0) { "chapterIndex doit être positif ou nul" }
        require(charOffset >= 0) { "charOffset doit être positif ou nul" }
        paragraphIndex?.let {
            require(it >= 0) { "paragraphIndex doit être positif ou nul" }
        }
    }

    /**
     * Ordre naturel : par chapitre, puis par offset de caractère. Ne
     * compare jamais `resourceHref` ni `paragraphIndex` seuls — deux
     * Locators du même chapitre s'ordonnent strictement par offset,
     * quel que soit leur `paragraphIndex`.
     */
    override fun compareTo(other: Locator): Int =
        compareValuesBy(this, other, Locator::chapterIndex, Locator::charOffset)

    companion object {
        /**
         * Progression 0..1 pour l'affichage (badge %, barre de
         * progression) UNIQUEMENT. Jamais utilisée pour la reprise —
         * Blueprint §3.2 : "toujours recalculable... sert à l'affichage
         * et à la réconciliation de synchronisation, pas à la reprise."
         */
        fun computeProgression(
            locator: Locator,
            totalCharsBeforeChapter: Int,
            totalCharsInPublication: Int,
        ): Float {
            if (totalCharsInPublication <= 0) return 0f
            val absoluteOffset = totalCharsBeforeChapter + locator.charOffset
            return (absoluteOffset.toFloat() / totalCharsInPublication).coerceIn(0f, 1f)
        }
    }
}
```

`domain/src/test/kotlin/com/inktone/domain/valueobject/LocatorTest.kt` :

```kotlin
package com.inktone.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatorTest {

    @Test
    fun `deux locators du meme chapitre s'ordonnent par offset`() {
        val early = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 10)
        val late = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 200)
        assertTrue(early < late)
    }

    @Test
    fun `un locator de chapitre ulterieur est toujours superieur, meme avec un offset plus petit`() {
        val chapter0 = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 5000)
        val chapter1 = Locator(resourceHref = "ch2.xhtml", chapterIndex = 1, charOffset = 0)
        assertTrue(chapter1 > chapter0)
    }

    @Test
    fun `resourceHref vide est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "", chapterIndex = 0, charOffset = 0)
        }
    }

    @Test
    fun `chapterIndex negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "ch1.xhtml", chapterIndex = -1, charOffset = 0)
        }
    }

    @Test
    fun `charOffset negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = -1)
        }
    }

    @Test
    fun `progression est calculee et bornee entre 0 et 1`() {
        val locator = Locator(resourceHref = "ch2.xhtml", chapterIndex = 1, charOffset = 500)
        val progression = Locator.computeProgression(
            locator = locator,
            totalCharsBeforeChapter = 10_000,
            totalCharsInPublication = 20_000,
        )
        assertEquals(0.525f, progression, 0.001f)
    }

    @Test
    fun `progression ne depasse jamais 1 meme avec un offset aberrant`() {
        val locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 999_999)
        val progression = Locator.computeProgression(
            locator = locator, totalCharsBeforeChapter = 0, totalCharsInPublication = 1000,
        )
        assertEquals(1f, progression, 0.001f)
    }

    @Test
    fun `progression est nulle si la publication n'a aucun caractere connu`() {
        val locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0)
        val progression = Locator.computeProgression(
            locator = locator, totalCharsBeforeChapter = 0, totalCharsInPublication = 0,
        )
        assertEquals(0f, progression, 0.001f)
    }
}
```

**Critère de validation avant/après :**
- Avant : le type `Locator` n'existe pas.
- Après : `./gradlew :domain:test --tests "*.LocatorTest"` → 8 tests verts.

**Commit :** `Ajoute le value object Locator et sa suite de tests`

---

## Tâche 1.2 — Document Model et `Publication`

**Objectif :** la représentation interne unifiée d'un document (Blueprint §7.5), produite par tout parser (Readium pour EPUB, TXT, PDF plus tard — §7.3), et l'entité `Publication` (§3.3).

`domain/src/main/kotlin/com/inktone/domain/model/DocumentModel.kt` :

```kotlin
package com.inktone.domain.model

/**
 * Représentation interne unifiée d'un document, indépendante du format
 * source (Blueprint §7.5). Reader, TTS et recherche travaillent tous sur
 * cette structure commune — jamais directement sur un modèle Readium ou
 * un autre parseur (ADR-011 : Readium encapsulé dans
 * infrastructure/parser).
 */
data class DocumentModel(
    val chapters: List<Chapter>,
    val tableOfContents: List<TableOfContentsEntry>,
    val resources: List<Resource>,
)

data class Chapter(
    val index: Int,
    val href: String,
    val title: String?,
    val paragraphs: List<Paragraph>,
)

data class Paragraph(
    val index: Int,
    val sentences: List<Sentence>,
)

/**
 * Unité de synthèse TTS. `startOffset`/`endOffset` sont des offsets de
 * caractère dans la ressource du chapitre — c'est ce qui rend possible la
 * synchronisation mot-à-mot (Blueprint §8.6 : "le découpage en phrases
 * conserve les offsets").
 */
data class Sentence(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(endOffset >= startOffset) { "endOffset doit être >= startOffset" }
        require(startOffset >= 0) { "startOffset doit être positif ou nul" }
    }

    /** Construit le Locator de début de cette phrase. */
    fun startLocator(
        chapterIndex: Int,
        resourceHref: String,
        paragraphIndex: Int? = null,
    ): com.inktone.domain.valueobject.Locator = com.inktone.domain.valueobject.Locator(
        resourceHref = resourceHref,
        chapterIndex = chapterIndex,
        paragraphIndex = paragraphIndex,
        charOffset = startOffset,
    )
}

data class TableOfContentsEntry(
    val title: String,
    val chapterIndex: Int,
    val children: List<TableOfContentsEntry> = emptyList(),
)

data class Resource(
    val href: String,
    val mediaType: String,
)
```

`domain/src/main/kotlin/com/inktone/domain/model/Publication.kt` :

```kotlin
package com.inktone.domain.model

enum class PublicationFormat { EPUB, TXT, PDF }

/**
 * Œuvre importée dans la bibliothèque (Blueprint §3.3).
 *
 * `seriesName`/`seriesIndex`/`isFavorite`/`subjects` font partie du
 * modèle dès la v1 (acquis K11 — extraction via `belongsTo` Readium et
 * `subjects` peuplés à l'import, pas une évolution future).
 *
 * `pageCount` est délibérément absent (revue B5) : un EPUB reflowable
 * n'a pas de pages ; un compte de pages sera introduit avec une
 * définition précise si un format paginé (PDF) l'exige un jour — jamais
 * comme champ générique ambigu.
 */
data class Publication(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val language: String? = null,
    val description: String? = null,
    val coverUri: String? = null,
    val format: PublicationFormat,
    val fileUri: String,
    val fileHash: String,
    val fileSize: Long,
    val chapterCount: Int,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val isFavorite: Boolean = false,
    val subjects: List<String> = emptyList(),
    val isDrmProtected: Boolean = false,
    val importDate: Long,
    val lastOpened: Long? = null,
) {
    init {
        require(title.isNotBlank()) { "title ne peut pas être vide" }
        require(chapterCount >= 0) { "chapterCount doit être positif ou nul" }
        require(fileSize >= 0) { "fileSize doit être positif ou nul" }
        require(fileHash.isNotBlank()) { "fileHash ne peut pas être vide (détection de doublons K-issue)" }
    }
}
```

`domain/src/test/kotlin/com/inktone/domain/model/SentenceTest.kt` :

```kotlin
package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SentenceTest {

    @Test
    fun `startLocator utilise l'offset de debut, pas de fin`() {
        val sentence = Sentence(index = 2, text = "Bonjour le monde.", startOffset = 150, endOffset = 168)
        val locator = sentence.startLocator(chapterIndex = 3, resourceHref = "ch3.xhtml")
        assertEquals(
            Locator(resourceHref = "ch3.xhtml", chapterIndex = 3, charOffset = 150),
            locator,
        )
    }

    @Test
    fun `endOffset inferieur a startOffset est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sentence(index = 0, text = "x", startOffset = 100, endOffset = 50)
        }
    }
}
```

**Critère de validation avant/après :**
- Avant : aucun modèle de document.
- Après : `./gradlew :domain:test --tests "*.SentenceTest"` vert ; `Publication` avec `fileHash` vide lève bien `IllegalArgumentException` (test à ajouter dans `PublicationTest.kt` sur le même principe).

**Commit :** `Ajoute le Document Model et l'entite Publication`

---

## Tâche 1.3 — `ReadingState`, `ReadingSession` et la règle de précédence des réglages

**Objectif :** matérialiser la scission actée en revue (B10) — état de reprise (unique, source de vérité) vs historique (multiple, statistiques) — et la règle de précédence surcharge/global (§3.3).

`domain/src/main/kotlin/com/inktone/domain/model/ReadingState.kt` :

```kotlin
package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

enum class ReadingMode { VISUAL, AUDIO }

enum class ReadingTheme { LIGHT, DARK, SEPIA, SYSTEM }

/**
 * État de reprise d'une publication — SOURCE DE VÉRITÉ UNIQUE de la
 * position de lecture, quel que soit le mode (Blueprint §3.3, §7.7,
 * acquis K3). Au plus une instance par publication — ne pas confondre
 * avec [ReadingSession] (revue B10).
 */
data class ReadingState(
    val publicationId: String,
    val locator: Locator,
    val lastReadAt: Long,
    val voiceProfileId: String? = null,
    val overrides: ReadingOverrides? = null,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
    }
}

/**
 * Surcharges de réglages propres à une publication. Priment toujours sur
 * [UserPreferences] — voir la règle de précédence du Blueprint §3.3 et
 * [EffectiveReadingSettings.resolve].
 */
data class ReadingOverrides(
    val theme: ReadingTheme? = null,
    val fontSize: Int? = null,
)

/**
 * Enregistrement HISTORIQUE d'une période de lecture, à des fins
 * statistiques uniquement (Blueprint §3.3). Plusieurs instances par
 * publication.
 */
data class ReadingSession(
    val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: ReadingMode,
    val sentencesRead: Int = 0,
    val durationMs: Long = 0,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(sentencesRead >= 0) { "sentencesRead doit être positif ou nul" }
        require(durationMs >= 0) { "durationMs doit être positif ou nul" }
        endedAt?.let {
            require(it >= startedAt) { "endedAt doit être postérieur ou égal à startedAt" }
        }
    }
}

/**
 * Réglages effectifs après application de la règle de précédence
 * (Blueprint §3.3) : surcharge de publication > préférences globales.
 * Résultat calculé, jamais persisté tel quel.
 */
data class EffectiveReadingSettings(
    val theme: ReadingTheme,
    val fontSize: Int,
) {
    companion object {
        fun resolve(overrides: ReadingOverrides?, global: UserPreferences): EffectiveReadingSettings =
            EffectiveReadingSettings(
                theme = overrides?.theme ?: global.theme,
                fontSize = overrides?.fontSize ?: global.fontSize,
            )
    }
}
```

`domain/src/test/kotlin/com/inktone/domain/model/EffectiveReadingSettingsTest.kt` :

```kotlin
package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveReadingSettingsTest {

    private val globalPrefs = UserPreferences(theme = ReadingTheme.LIGHT, fontSize = 16)

    @Test
    fun `sans surcharge, les reglages globaux s'appliquent integralement`() {
        val effective = EffectiveReadingSettings.resolve(overrides = null, global = globalPrefs)
        assertEquals(ReadingTheme.LIGHT, effective.theme)
        assertEquals(16, effective.fontSize)
    }

    @Test
    fun `une surcharge partielle (theme seul) prime sur ce seul champ`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.SEPIA, fontSize = null)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.SEPIA, effective.theme)
        assertEquals(16, effective.fontSize) // reste global : pas de surcharge sur ce champ
    }

    @Test
    fun `une surcharge complete prime entierement sur le global`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.DARK, fontSize = 22)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.DARK, effective.theme)
        assertEquals(22, effective.fontSize)
    }
}
```

`domain/src/test/kotlin/com/inktone/domain/model/ReadingSessionTest.kt` :

```kotlin
package com.inktone.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingSessionTest {

    @Test
    fun `endedAt anterieur a startedAt est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingSession(
                id = "s1", publicationId = "pub-1",
                startedAt = 1_000L, endedAt = 500L,
                mode = ReadingMode.AUDIO,
            )
        }
    }

    @Test
    fun `une session sans endedAt (en cours) est valide`() {
        // Ne doit pas lever d'exception.
        ReadingSession(
            id = "s1", publicationId = "pub-1",
            startedAt = 1_000L, endedAt = null,
            mode = ReadingMode.VISUAL,
        )
    }
}
```

**Critère de validation avant/après :**
- Avant : un seul concept `ReadingSession` conflaté (position + historique) — le défaut de modèle explicitement rejeté en revue (B10).
- Après : deux entités distinctes, la règle de précédence est un comportement testé (3 tests verts) et non une convention non écrite.

**Commit :** `Ajoute ReadingState, ReadingSession et la regle de precedence des reglages`

---

## Tâche 1.4 — `Bookmark` et `Annotation`

`domain/src/main/kotlin/com/inktone/domain/model/Bookmark.kt` :

```kotlin
package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

data class Bookmark(
    val id: String,
    val publicationId: String,
    val locator: Locator,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/model/Annotation.kt` :

```kotlin
package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

enum class AnnotationColor { YELLOW, GREEN, BLUE, PINK, ORANGE }

/**
 * Surlignage, note ou citation liée à une plage de [Locator]s. Un seul
 * modèle d'adressage pour toute la plage — jamais chapter+startOffset
 * d'un côté et Locator de l'autre (revue B2/D7).
 */
data class Annotation(
    val id: String,
    val publicationId: String,
    val startLocator: Locator,
    val endLocator: Locator,
    val color: AnnotationColor,
    val content: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(endLocator >= startLocator) { "endLocator doit être postérieur ou égal à startLocator" }
    }
}
```

`domain/src/test/kotlin/com/inktone/domain/model/AnnotationTest.kt` :

```kotlin
package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertThrows
import org.junit.Test

class AnnotationTest {

    private fun locator(offset: Int) = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = offset)

    @Test
    fun `endLocator anterieur a startLocator est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Annotation(
                id = "a1", publicationId = "pub-1",
                startLocator = locator(200), endLocator = locator(50),
                color = AnnotationColor.YELLOW,
                createdAt = 0L, updatedAt = 0L,
            )
        }
    }

    @Test
    fun `une plage de longueur nulle (start egal end) est valide`() {
        // Ne doit pas lever d'exception — surlignage ponctuel valide.
        Annotation(
            id = "a1", publicationId = "pub-1",
            startLocator = locator(100), endLocator = locator(100),
            color = AnnotationColor.YELLOW,
            createdAt = 0L, updatedAt = 0L,
        )
    }
}
```

**Commit :** `Ajoute Bookmark et Annotation avec validation de plage sur Locator`

---

## Tâche 1.5 — `VoiceProfile`, `UserPreferences`, `TtsCapabilities`

`domain/src/main/kotlin/com/inktone/domain/model/VoiceProfile.kt` :

```kotlin
package com.inktone.domain.model

enum class TtsEngineId { SHERPA_ONNX, PIPER, EDGE_TTS }

/**
 * Configuration vocale réutilisable (Blueprint §3.3). Le champ `style`
 * fait partie du modèle ici ET dans le Data Model (§6.2) — l'alignement
 * entre les deux était une contradiction identifiée en revue (B6),
 * désormais résolue par un seul chapitre de référence pour les deux.
 */
data class VoiceProfile(
    val id: String,
    val engine: TtsEngineId,
    val voice: String,
    val language: String,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val style: String? = null,
) {
    init {
        require(voice.isNotBlank()) { "voice ne peut pas être vide" }
        require(speed > 0f) { "speed doit être strictement positif" }
        require(volume in 0f..1f) { "volume doit être compris entre 0 et 1" }
        require(pitch > 0f) { "pitch doit être strictement positif" }
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/model/UserPreferences.kt` :

```kotlin
package com.inktone.domain.model

/**
 * Préférences globales — une seule instance par application. Toute
 * surcharge par publication vit dans [ReadingOverrides] et prime sur ces
 * valeurs (Blueprint §3.3).
 */
data class UserPreferences(
    val theme: ReadingTheme = ReadingTheme.SYSTEM,
    val fontSize: Int = 18,
    val defaultTtsEngine: TtsEngineId = TtsEngineId.SHERPA_ONNX,
    val crashReportingEnabled: Boolean = false,
    val language: String = "fr",
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/service/TtsCapabilities.kt` :

```kotlin
package com.inktone.domain.service

/**
 * Déclaration des capacités d'un moteur TTS (Blueprint §8.4, ADR-004).
 * `wordTimestamps` est la capacité de premier rang : elle seule autorise
 * le surlignage mot-à-mot réel (§8.9, ADR-013) — jamais simulé par
 * interpolation de caractères si elle est fausse.
 */
data class TtsCapabilities(
    val offline: Boolean,
    val wordTimestamps: Boolean,
    val sentenceTimestamps: Boolean,
    val languages: List<String>,
    val streamingSynthesis: Boolean,
    val speedControl: Boolean,
    val pitchControl: Boolean,
    val modelSizeMb: Int,
    val license: String,
)
```

**Critère de validation avant/après :** un `VoiceProfile` avec `volume = 1.5f` lève `IllegalArgumentException` (test à ajouter sur le même principe que les tâches précédentes).

**Commit :** `Ajoute VoiceProfile, UserPreferences et le contrat TtsCapabilities`

---

## Tâche 1.6 — Interfaces de repository

**Objectif :** le contrat que `data/` implémentera en Phase 2 (Blueprint §4.7). Aucune implémentation ici — uniquement des interfaces, en Kotlin pur.

`domain/src/main/kotlin/com/inktone/domain/repository/PublicationRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.Publication
import kotlinx.coroutines.flow.Flow

interface PublicationRepository {
    fun observeAll(): Flow<List<Publication>>
    suspend fun getById(id: String): Publication?
    suspend fun getByFileHash(hash: String): Publication?
    suspend fun insert(publication: Publication)
    suspend fun update(publication: Publication)
    suspend fun delete(id: String)
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/ReadingStateRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow

interface ReadingStateRepository {
    suspend fun get(publicationId: String): ReadingState?
    fun observe(publicationId: String): Flow<ReadingState?>

    /** Persiste l'état de reprise. Appelée par les deux chemins K3 (TTS / manuel) — jamais simultanément. */
    suspend fun save(state: ReadingState)
    suspend fun delete(publicationId: String)
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/ReadingSessionRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.ReadingSession

interface ReadingSessionRepository {
    suspend fun insert(session: ReadingSession)
    suspend fun getAllForPublication(publicationId: String): List<ReadingSession>
    suspend fun getAll(): List<ReadingSession>
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/BookmarkRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeForPublication(publicationId: String): Flow<List<Bookmark>>
    suspend fun insert(bookmark: Bookmark)
    suspend fun delete(id: String)
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/AnnotationRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun observeForPublication(publicationId: String): Flow<List<Annotation>>
    suspend fun insert(annotation: Annotation)
    suspend fun update(annotation: Annotation)
    suspend fun delete(id: String)
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/VoiceProfileRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.VoiceProfile

interface VoiceProfileRepository {
    suspend fun getById(id: String): VoiceProfile?
    suspend fun getAll(): List<VoiceProfile>
    suspend fun save(profile: VoiceProfile)
    suspend fun delete(id: String)
}
```

`domain/src/main/kotlin/com/inktone/domain/repository/PreferencesRepository.kt` :

```kotlin
package com.inktone.domain.repository

import com.inktone.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observe(): Flow<UserPreferences>
    suspend fun get(): UserPreferences
    suspend fun update(preferences: UserPreferences)
}
```

**Critère de validation avant/après :**
- Avant : aucun contrat entre `domain` et `data`.
- Après : `./gradlew :domain:compileKotlin` réussit ; les 7 interfaces existent, zéro import Android, zéro import Room.

**Commit :** `Ajoute les interfaces de repository du domaine`

---

## Tâche 1.7 — Interfaces de service (`TtsEngine`, `PublicationParser`, `SearchService`)

**Objectif :** les contrats que `infrastructure/tts`, `infrastructure/parser` et la recherche implémenteront (Phases 4, 5, 7). C'est ici que l'abstraction capability-aware du §8 prend forme concrète.

`domain/src/main/kotlin/com/inktone/domain/service/TtsEngine.kt` :

```kotlin
package com.inktone.domain.service

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.flow.Flow

/**
 * Contrat commun à tout moteur TTS (Blueprint §8.3, ADR-004). Chaque
 * adaptateur (Sherpa-ONNX, Piper, Edge TTS) implémente cette interface
 * dans infrastructure/tts et déclare ses capacités RÉELLES via
 * [capabilities] — jamais de plus petit dénominateur commun (§2.6).
 */
interface TtsEngine {
    val id: TtsEngineId
    val capabilities: TtsCapabilities

    suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment

    /** Événements de progression pendant la lecture d'un segment (§8.9). */
    fun observePlaybackEvents(): Flow<PlaybackEvent>
}

/**
 * Segment audio synthétisé. `wordTimestamps` est vide si
 * [TtsCapabilities.wordTimestamps] est faux pour ce moteur — jamais
 * simulé par interpolation de caractères (§8.9, ADR-013).
 *
 * Classe ordinaire (pas `data class`) : `audioData` est un ByteArray, et
 * l'égalité par défaut d'une data class sur un ByteArray compare des
 * références, pas du contenu — piège classique. `equals`/`hashCode` sont
 * donc écrits à la main avec `contentEquals`/`contentHashCode`.
 */
class AudioSegment(
    val audioData: ByteArray,
    val durationMs: Long,
    val wordTimestamps: List<WordTimestamp>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSegment) return false
        return audioData.contentEquals(other.audioData) &&
            durationMs == other.durationMs &&
            wordTimestamps == other.wordTimestamps
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + wordTimestamps.hashCode()
        return result
    }
}

data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val charOffset: Int,
)

sealed interface PlaybackEvent {
    data class SentenceStarted(val sentenceIndex: Int) : PlaybackEvent
    data class WordReached(val wordTimestamp: WordTimestamp) : PlaybackEvent
    data class SentenceCompleted(val sentenceIndex: Int) : PlaybackEvent
    data class Error(val message: String) : PlaybackEvent
}
```

`domain/src/main/kotlin/com/inktone/domain/service/PublicationParser.kt` :

```kotlin
package com.inktone.domain.service

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat

/**
 * Contrat implémenté par infrastructure/parser (Readium pour EPUB — voir
 * ADR-011 : encapsulé ici, jamais exposé au-delà de ce module).
 */
interface PublicationParser {
    val supportedFormats: List<PublicationFormat>
    suspend fun parse(fileUri: String): ParseResult
}

/**
 * Jamais un échec silencieux (Blueprint §7.11) : chaque cas d'erreur
 * attendu est un type, pas une exception qui remonte au hasard.
 */
sealed interface ParseResult {
    data class Success(val documentModel: DocumentModel, val isDrmProtected: Boolean) : ParseResult
    data class DrmProtected(val message: String) : ParseResult
    data class Corrupted(val message: String) : ParseResult
    data class UnsupportedFormat(val format: String) : ParseResult
}
```

`domain/src/main/kotlin/com/inktone/domain/service/SearchService.kt` :

```kotlin
package com.inktone.domain.service

import com.inktone.domain.valueobject.Locator

/** Contrat implémenté par la recherche FTS (Blueprint §6.9, Phase 7). */
interface SearchService {
    suspend fun search(query: String, publicationId: String? = null): List<SearchResult>
}

data class SearchResult(
    val publicationId: String,
    val locator: Locator,
    val snippet: String,
)
```

`domain/src/test/kotlin/com/inktone/domain/service/AudioSegmentTest.kt` :

```kotlin
package com.inktone.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioSegmentTest {

    @Test
    fun `deux segments avec le meme contenu binaire sont egaux`() {
        val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList())
        val b = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList())
        assertEquals(a, b) // échouerait avec l'égalité par défaut d'une data class sur ByteArray
    }

    @Test
    fun `deux segments avec un contenu binaire different ne sont pas egaux`() {
        val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList())
        val b = AudioSegment(audioData = byteArrayOf(9, 9, 9), durationMs = 500L, wordTimestamps = emptyList())
        assertNotEquals(a, b)
    }
}
```

**Critère de validation avant/après :**
- Avant : aucun contrat pour les moteurs externes.
- Après : `./gradlew :domain:test --tests "*.AudioSegmentTest"` vert — vérifie explicitement que le piège `ByteArray` est évité, pas supposé.

**Commit :** `Ajoute les interfaces TtsEngine, PublicationParser et SearchService`

---

## Tâche 1.8 — Use Cases

**Objectif :** le Blueprint (roadmap macro) prévoit des « Use Cases en signature » pour cette phase. En pratique, deux catégories bien distinctes émergent — la nuance vaut la peine d'être explicite plutôt que d'écrire 12 signatures vides par principe :

- **Triviaux, implémentables dès maintenant** : ils ne font qu'orchestrer un appel de repository déjà défini (Tâche 1.6). Les laisser en signature vide serait du travail jeté pour rien — ils sont donc **complets et testés** dans cette phase.
- **Dépendants d'infrastructure pas encore construite** (parser, TTS, SAF, FTS) : **signature + contrat documenté + `TODO` explicite** renvoyant à la phase qui les complètera. Les appeler avant cette phase est une erreur de développement, pas un cas silencieux.

### 1.8.1 — Use Cases complets

`domain/src/main/kotlin/com/inktone/domain/usecase/ToggleFavoriteUseCase.kt` :
```kotlin
package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository

class ToggleFavoriteUseCase(
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(publicationId: String, isFavorite: Boolean) {
        publicationRepository.setFavorite(publicationId, isFavorite)
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/usecase/CreateBookmarkUseCase.kt` :
```kotlin
package com.inktone.domain.usecase

import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository

class CreateBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(bookmark: Bookmark) {
        bookmarkRepository.insert(bookmark)
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/usecase/UpdateReadingStateUseCase.kt` :
```kotlin
package com.inktone.domain.usecase

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository

/**
 * Persiste la position de lecture (acquis K3). Rappel Blueprint §7.7 :
 * cette fonction est appelée par DEUX chemins distincts (transition de
 * phrase TTS, scroll manuel débouncé) qui ne s'exécutent JAMAIS
 * simultanément. Cette classe ne connaît pas l'appelant — la garantie
 * d'exclusivité est de la responsabilité du ViewModel Reader (Phase 3/4),
 * pas de ce Use Case.
 */
class UpdateReadingStateUseCase(
    private val readingStateRepository: ReadingStateRepository,
) {
    suspend operator fun invoke(state: ReadingState) {
        readingStateRepository.save(state)
    }
}
```

`domain/src/main/kotlin/com/inktone/domain/usecase/AddAnnotationUseCase.kt` :
```kotlin
package com.inktone.domain.usecase

import com.inktone.domain.model.Annotation
import com.inktone.domain.repository.AnnotationRepository

class AddAnnotationUseCase(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(annotation: Annotation) {
        annotationRepository.insert(annotation)
    }
}
```

**Répéter ce schéma** (une classe, un constructeur avec le repository nécessaire, un `invoke` qui délègue) pour : `DeleteBookmarkUseCase`, `UpdateAnnotationUseCase`, `DeleteAnnotationUseCase`, `UpdatePreferencesUseCase`, `GetReadingStateUseCase`, `SaveVoiceProfileUseCase`.

### 1.8.2 — Use Cases en signature (dépendances futures)

`domain/src/main/kotlin/com/inktone/domain/usecase/ImportPublicationUseCase.kt` :
```kotlin
package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PublicationParser

/**
 * Importe une publication depuis une URI SAF.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel (extraction de
 * métadonnées, détection de doublons par [Publication.fileHash],
 * détection DRM K7) exige [PublicationParser] (infrastructure/parser,
 * complété en Phase 4) et l'accès fichier SAF (infrastructure/storage,
 * Phase 2). Ne pas invoquer avant l'injection d'implémentations réelles.
 *
 * Contrat :
 * - Entrée : URI SAF d'un fichier sélectionné par l'utilisateur.
 * - Sortie : un [ImportResult] typé — jamais d'exception pour un cas
 *   métier attendu (Blueprint §7.11).
 */
class ImportPublicationUseCase(
    private val publicationParser: PublicationParser,
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(fileUri: String): ImportResult {
        TODO("Complété en Phase 4/6 — nécessite PublicationParser et la détection de doublons par hash (K2, K7)")
    }
}

sealed interface ImportResult {
    data class Success(val publication: Publication) : ImportResult
    data class Duplicate(val existingPublicationId: String) : ImportResult
    data class DrmProtected(val message: String) : ImportResult
    data class Corrupted(val message: String) : ImportResult
    data class UnsupportedFormat(val format: String) : ImportResult
}
```

**Répéter ce schéma** (signature + KDoc de contrat + `TODO` référençant la phase) pour :

| Use Case | Dépendance manquante | Complété en |
|---|---|---|
| `OpenPublicationUseCase` | `PublicationParser` | Phase 4 |
| `ResumeReadingUseCase` | état Reader + orchestration TTS | Phase 3/4 |
| `StartAudioReadingUseCase` | `TtsEngine` | Phase 5 |
| `PauseAudioReadingUseCase` | `TtsEngine` | Phase 5 |
| `SearchPublicationUseCase` | `SearchService` (FTS) | Phase 7 |
| `ExportLibraryUseCase` | accès SAF en écriture | Phase 6 |

**Critère de validation avant/après :**
- Avant : aucun Use Case.
- Après : les Use Cases complets ont chacun un test avec fake (Tâche 1.9) ; les Use Cases en signature compilent (le `TODO()` de Kotlin lève `NotImplementedError` s'il est appelé — c'est le comportement voulu, pas une lacune) mais n'ont pas de test d'appel réel avant leur phase de complétion.

**Commit :** `Ajoute les Use Cases du domaine (complets et en signature)`

---

## Tâche 1.9 — Fakes dans `core:testing`

**Objectif :** implémentations en mémoire des interfaces de repository, réutilisables par tous les tests futurs (ViewModels en Phase 3+, autres Use Cases). Elles vivent dans `core:testing`, pas dans `domain`, pour rester disponibles à tous les modules qui en auront besoin (Blueprint §5.2 : « fakes, fixtures, règles de test partagées »).

`core/testing/build.gradle.kts` :
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inktone.jvm")
}

dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

`core/testing/src/main/kotlin/com/inktone/core/testing/fake/FakePublicationRepository.kt` :
```kotlin
package com.inktone.core.testing.fake

import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePublicationRepository : PublicationRepository {
    private val state = MutableStateFlow<List<Publication>>(emptyList())

    override fun observeAll(): Flow<List<Publication>> = state

    override suspend fun getById(id: String): Publication? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getByFileHash(hash: String): Publication? =
        state.value.firstOrNull { it.fileHash == hash }

    override suspend fun insert(publication: Publication) {
        state.value = state.value + publication
    }

    override suspend fun update(publication: Publication) {
        state.value = state.value.map { if (it.id == publication.id) publication else it }
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }
}
```

`core/testing/src/main/kotlin/com/inktone/core/testing/fake/FakeReadingStateRepository.kt` :
```kotlin
package com.inktone.core.testing.fake

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReadingStateRepository : ReadingStateRepository {
    private val state = MutableStateFlow<Map<String, ReadingState>>(emptyMap())

    override suspend fun get(publicationId: String): ReadingState? = state.value[publicationId]

    override fun observe(publicationId: String): Flow<ReadingState?> =
        kotlinx.coroutines.flow.map(state) { it[publicationId] }

    override suspend fun save(state: ReadingState) {
        this.state.value = this.state.value + (state.publicationId to state)
    }

    override suspend fun delete(publicationId: String) {
        state.value = state.value - publicationId
    }
}
```

`core/testing/src/main/kotlin/com/inktone/core/testing/fake/FakeBookmarkRepository.kt` :
```kotlin
package com.inktone.core.testing.fake

import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookmarkRepository : BookmarkRepository {
    private val state = MutableStateFlow<List<Bookmark>>(emptyList())

    override fun observeForPublication(publicationId: String): Flow<List<Bookmark>> =
        state.map { list -> list.filter { it.publicationId == publicationId } }

    override suspend fun insert(bookmark: Bookmark) {
        state.value = state.value + bookmark
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
```

**Répéter ce schéma** (StateFlow en mémoire + implémentation directe de l'interface) pour `FakeReadingSessionRepository`, `FakeAnnotationRepository`, `FakeVoiceProfileRepository`, `FakePreferencesRepository`.

`domain/src/test/kotlin/com/inktone/domain/usecase/ToggleFavoriteUseCaseTest.kt` — exemple de test de Use Case s'appuyant sur les fakes :
```kotlin
package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    @Test
    fun `bascule le favori d'une publication existante`() = runTest {
        val repository = FakePublicationRepository()
        val publication = Publication(
            id = "pub-1", title = "Les Misérables", format = PublicationFormat.EPUB,
            fileUri = "content://fake/1", fileHash = "hash1", fileSize = 1000L,
            chapterCount = 10, importDate = 0L,
        )
        repository.insert(publication)

        ToggleFavoriteUseCase(repository)(publication.id, isFavorite = true)

        assertTrue(repository.getById(publication.id)!!.isFavorite)
    }
}
```

Note : `domain/build.gradle.kts` déclare `testImplementation(project(":core:testing"))` — autorisé par la configuration `testImplementation`, exempte de la vérification `checkArchitectureRules` (voir Tâche 1.0.2).

**Critère de validation avant/après :**
- Avant : chaque test de Use Case nécessiterait un mock ad hoc, dupliqué à chaque phase future.
- Après : `./gradlew :domain:test --tests "*.ToggleFavoriteUseCaseTest"` vert ; les fakes sont prêts pour les ViewModels MVI de la Phase 3.

**Commit :** `Ajoute les fakes de repository dans core-testing et le premier test de Use Case`

---

## Checklist finale de sortie de Phase 1

| # | Critère | Vérification |
|---|---|---|
| 1 | `domain` compile à 100 %, zéro dépendance Android | `./gradlew :domain:compileKotlin` vert ; `grep -r "android\." domain/src/main` ne renvoie rien |
| 2 | `Locator` testé exhaustivement | `LocatorTest` (8 tests) vert |
| 3 | Toutes les entités du §3.3 existent avec leurs invariants | Publication, ReadingState, ReadingSession, Bookmark, Annotation, VoiceProfile, UserPreferences — chacune avec au moins un test d'invariant |
| 4 | Les 7 interfaces de repository existent | `domain/repository/*.kt`, zéro implémentation (normal — Phase 2) |
| 5 | Les interfaces de service existent avec le modèle de capacités | `TtsEngine`, `TtsCapabilities`, `PublicationParser`, `SearchService` |
| 6 | Use Cases triviaux complets et testés ; Use Cases dépendants en signature documentée | Tableau §1.8.2 à jour, aucun `TODO()` appelé en dehors des tests qui vérifient justement qu'il lève `NotImplementedError` |
| 7 | Fakes disponibles dans `core:testing` | Au moins `FakePublicationRepository`, `FakeReadingStateRepository`, `FakeBookmarkRepository` — les autres suivant le même schéma |
| 8 | CI verte sur PR | Build + `checkArchitectureRules` + lint emoji + check manifest (sans objet ici, module JVM pur) |

Une fois les 8 critères vérifiés sur un clone frais (comme pour la Phase 0), Phase 1 est close. Étape suivante : **Phase 2 — Fondations data & persistance** (schéma Room v1, harnais de migration, SAF, mappers — dont le mapper `Locator` ↔ colonnes à plat, Blueprint §6.2).
