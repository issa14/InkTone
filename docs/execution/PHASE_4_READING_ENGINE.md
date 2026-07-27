# Phase 4 — Reading Engine complet

**Dépend de :** Phase 3 (close, décision Palier 1+2 actée)
**Précède :** Phase 5 — TTS Engine complet
**Référence :** Blueprint InkTone v1.1.0/1.2.2, §7 (Reading Engine), §11.2 (budgets), §14.6 (garde-fous K3/K6/K7)
**Sortie de phase :** voir Checklist finale en fin de document.

## Ce qui est déjà fait, à ne pas refaire

La Phase 3 a livré plus que son périmètre annoncé (« un chapitre suffit »). Avant d'écrire la moindre ligne, un état des lieux honnête de ce qui existe déjà :

| Acquis Phase 3 | État réel |
|---|---|
| Détection DRM (K7) | **Déjà implémentée** — `ReadiumPublicationParser.parse()` renvoie `isDrmProtected = publication.isProtected`, câblé depuis la Tâche 3.2. Il manque seulement un fixture EPUB protégé pour le tester (Tâche 4.4). |
| Extraction multi-chapitres | **Le code existe déjà** (`DocumentModelExtractor.extract()` itère `publication.readingOrder`), **mais n'a jamais été testé** que sur un fixture à un seul chapitre. Tâche 4.1 = tester, pas réécrire. |
| Résolution des ressources (hrefs) | Le bug `Href`/`Url` (Tâche 3.4) est corrigé et testé. La question du **percent-encoding** (K6) reste ouverte — Readium le gère peut-être déjà via sa propre résolution d'URL, à vérifier avant d'écrire du code redondant (Tâche 4.3). |
| Gestion d'erreurs de parsing | Le chemin `Corrupted` existe déjà (`assetRetriever.retrieve()`/`publicationOpener.open()` renvoient des `Result` gérés). Il manque des fixtures qui déclenchent réellement ces chemins (Tâche 4.8). |

Cette phase construit sur l'existant plutôt que de le dupliquer — chaque tâche qui touche à du code déjà présent le dit explicitement.

---

## Tâche 4.0 — Fixtures manquantes, préalable à presque tout le reste

**Objectif :** un seul endroit pour les fixtures EPUB de test, avant que chaque tâche n'en réclame une différemment.

`infrastructure/parser/src/androidTest/assets/` — ajouter :

- `fixture-multi-chapitre.epub` : 3 chapitres, contenu original construit pour le test (pas de contenu tiers), un TOC à 3 entrées.
- `fixture-hrefs-encodes.epub` : ressources dont certains hrefs contiennent des caractères percent-encodés (ex. `chapitre%20un.xhtml` référencé tantôt encodé, tantôt décodé selon les fichiers internes — cas réel qui a cassé le lecteur legacy, K6).
- `fixture-drm.epub` : structure minimale déclarant une protection LCP ou Adobe ADEPT à la racine du conteneur (`META-INF/rights.xml` ou équivalent) — suffisant pour déclencher `publication.isProtected`, pas besoin d'un vrai fichier chiffré fonctionnel.
- `fixture-corrompu.epub` : fichier ZIP invalide ou EPUB avec `container.xml` manquant.
- `fixture-ressource-manquante.epub` : un chapitre référence une image qui n'existe pas dans le conteneur.

**Commit :** `Ajoute les fixtures EPUB de la Phase 4 (multi-chapitre, hrefs encodes, DRM, corrompu, ressource manquante)`

---

## Tâche 4.1 — Vérifier l'extraction multi-chapitres (pas la réécrire)

**Objectif :** confirmer ou infirmer que le code existant de `DocumentModelExtractor` fonctionne réellement sur plusieurs chapitres — c'était le point explicitement laissé ouvert en Phase 3.

`infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/DocumentModelExtractorMultiChapterTest.kt` :

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ferme le point laissé explicitement ouvert en Phase 3 (Tâche 3.4) :
 * DocumentModelExtractor n'avait jamais été vérifié au-delà d'un fixture
 * à un seul chapitre. Ce test ne modifie pas l'extracteur — il le met à
 * l'épreuve.
 */
@RunWith(AndroidJUnit4::class)
class DocumentModelExtractorMultiChapterTest {

    @Test
    fun extrait_trois_chapitres_sans_contamination_croisee() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-multi-chapitre.epub")
        val parser = ReadiumPublicationParser(context)

        val result = parser.parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)
        val chapters = result.documentModel.chapters

        assertEquals("le fixture a 3 chapitres", 3, chapters.size)

        // Le test critique : le contenu de chaque chapitre doit être
        // DISTINCT et ne pas fuiter dans les chapitres voisins. Si le
        // filtrage par href (Href.resolve(), Tâche 3.4) a une régression
        // sur un cas à plusieurs ressources, ce test doit le révéler —
        // un test sur un seul chapitre ne le pouvait pas par construction.
        val allSentenceTexts = chapters.map { chapter ->
            chapter.paragraphs.flatMap { it.sentences }.joinToString(" ") { it.text }
        }
        assertTrue("chapitre 1 doit contenir son propre texte", allSentenceTexts[0].contains("premier"))
        assertTrue("chapitre 2 doit contenir son propre texte", allSentenceTexts[1].contains("deuxieme"))
        assertTrue("chapitre 3 doit contenir son propre texte", allSentenceTexts[2].contains("troisieme"))
        assertTrue(
            "le chapitre 1 ne doit PAS contenir le texte du chapitre 2",
            !allSentenceTexts[0].contains("deuxieme"),
        )

        // Chaque Sentence.startOffset doit repartir de 0 par chapitre —
        // pas de dérive d'un compteur runningOffset partagé entre
        // chapitres (bug plausible si l'extracteur réutilisait par
        // erreur une variable au mauvais niveau de portée).
        chapters.forEach { chapter ->
            val firstSentence = chapter.paragraphs.first().sentences.first()
            assertEquals("chaque chapitre commence son offset a 0", 0, firstSentence.startOffset)
        }
    }

    @Test
    fun le_tableau_des_matieres_correspond_aux_trois_chapitres() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-multi-chapitre.epub")
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)

        assertEquals(3, result.documentModel.tableOfContents.size)
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input -> outFile.outputStream().use { input.copyTo(it) } }
        return outFile
    }
}
```

**Critère de validation avant/après :**
- Avant : le filtrage multi-chapitres n'a jamais été mis à l'épreuve — Phase 3 l'a explicitement signalé comme non couvert.
- Après : soit ce test passe du premier coup (le code de Phase 3 était déjà correct au-delà du cas simple), soit il révèle une régression réelle à corriger **dans le code existant**, pas en le réécrivant à côté.

**Commit :** `Verifie l'extraction multi-chapitres (ferme le point ouvert de la Phase 3)`

---

## Tâche 4.2 — Parser TXT

**Objectif :** contrairement à l'EPUB, rien n'existe encore ici — Readium ne parse pas le texte brut comme une publication structurée. Implémentation directe, sans dépendance externe.

`infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/TxtPublicationParser.kt` :

```kotlin
package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un fichier TXT est traité comme UN SEUL chapitre. Découpage en phrases
 * par une regex simple sur la ponctuation forte (. ! ? suivi d'espace ou
 * fin de ligne) — volontairement naïf : un vrai découpeur linguistique
 * (gestion des abréviations "M.", "etc.") est le travail du pipeline TTS
 * (Blueprint §8.6), pas de ce parser. Ne pas complexifier ici tant qu'un
 * cas réel ne le justifie pas.
 */
@Singleton
class TxtPublicationParser @Inject constructor() : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.TXT)

    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")

    override suspend fun parse(fileUri: String): ParseResult {
        val file = File(fileUri)
        if (!file.exists()) return ParseResult.Corrupted("Fichier introuvable: $fileUri")

        val text = runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { return ParseResult.Corrupted("Lecture impossible (encodage ?): ${it.message}") }

        if (text.isBlank()) return ParseResult.Corrupted("Fichier TXT vide")

        var offset = 0
        val sentences = sentenceBoundary.split(text.trim()).mapIndexed { index, raw ->
            val trimmed = raw.trim()
            val sentence = Sentence(index = index, text = trimmed, startOffset = offset, endOffset = offset + trimmed.length)
            offset += trimmed.length + 1
            sentence
        }.filter { it.text.isNotBlank() }

        val chapter = Chapter(index = 0, href = file.name, title = null, paragraphs = listOf(Paragraph(0, sentences)))
        return ParseResult.Success(
            documentModel = DocumentModel(chapters = listOf(chapter), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false, // TXT n'a jamais de DRM par définition
        )
    }
}
```

**Enregistrer les deux parsers auprès du domaine** — le contrat `PublicationParser` (Tâche 1.7) est une interface unique ; il faut un point de sélection par format :

`infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/CompositePublicationParser.kt` :

```kotlin
package com.inktone.infrastructure.parser

import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée unique injecté dans le domaine (lié à l'interface
 * PublicationParser via Hilt) — sélectionne le bon parser par extension
 * de fichier. Étendre cette liste pour PDF (Phase 1.x, ADR-017) plutôt
 * que de faire porter la décision de format à chaque appelant.
 */
@Singleton
class CompositePublicationParser @Inject constructor(
    private val readiumParser: ReadiumPublicationParser,
    private val txtParser: TxtPublicationParser,
) : PublicationParser {

    override val supportedFormats = readiumParser.supportedFormats + txtParser.supportedFormats

    override suspend fun parse(fileUri: String): ParseResult {
        val delegate = if (fileUri.endsWith(".txt", ignoreCase = true)) txtParser else readiumParser
        return delegate.parse(fileUri)
    }
}
```

**Mettre à jour la liaison Hilt** (`infrastructure/parser/src/main/kotlin/.../di/ParserModule.kt`) pour binder `PublicationParser` à `CompositePublicationParser`, pas directement à `ReadiumPublicationParser` comme le faisait implicitement la Phase 3.

`infrastructure/parser/src/test/kotlin/com/inktone/infrastructure/parser/TxtPublicationParserTest.kt` (JVM pur — aucune API Android utilisée) :

```kotlin
package com.inktone.infrastructure.parser

import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TxtPublicationParserTest {

    @Test
    fun decoupe_un_texte_simple_en_phrases() = runTest {
        val file = File.createTempFile("test", ".txt").apply {
            writeText("Bonjour le monde. Ceci est un test. Il fonctionne !")
            deleteOnExit()
        }
        val result = TxtPublicationParser().parse(file.absolutePath)
        check(result is ParseResult.Success)

        val sentences = result.documentModel.chapters.single().paragraphs.single().sentences
        assertEquals(3, sentences.size)
        assertEquals("Bonjour le monde.", sentences[0].text)
        assertEquals("Il fonctionne !", sentences[2].text)
    }

    @Test
    fun fichier_vide_renvoie_corrompu_pas_un_crash() = runTest {
        val file = File.createTempFile("empty", ".txt").apply { deleteOnExit() }
        val result = TxtPublicationParser().parse(file.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun fichier_inexistant_renvoie_corrompu_pas_une_exception() = runTest {
        val result = TxtPublicationParser().parse("/chemin/qui/n/existe/pas.txt")
        assertTrue(result is ParseResult.Corrupted)
    }
}
```

**Commit :** `Ajoute le parser TXT et le selecteur de parser par format`

---

## Tâche 4.3 — Percent-encoding (K6) : vérifier avant d'écrire

**Objectif :** l'acquis K6 du legacy dit « normaliser les hrefs percent-encodés ». Avant d'écrire du code de normalisation, vérifier si Readium le fait déjà via `Href.resolve()` (déjà utilisé depuis la Tâche 3.4) — écrire du code redondant serait contraire à l'esprit du projet.

`infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/HrefEncodingTest.kt` :

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Test-first, K6 : vérifie si le pipeline existant (Href.resolve(),
 * Tâche 3.4) gère déjà correctement le percent-encoding mixte, avant de
 * supposer qu'un correctif custom est nécessaire.
 */
@RunWith(AndroidJUnit4::class)
class HrefEncodingTest {

    @Test
    fun extrait_le_contenu_malgre_des_hrefs_encodes_differemment() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-hrefs-encodes.epub").apply {
            context.assets.open("fixture-hrefs-encodes.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)

        check(result is ParseResult.Success)
        val allSentences = result.documentModel.chapters.flatMap { it.paragraphs }.flatMap { it.sentences }
        assertTrue(
            "le contenu doit etre extrait malgre le href encode differemment entre le spine et le fichier interne",
            allSentences.isNotEmpty(),
        )
    }
}
```

**Deux issues possibles, à documenter dans le commit selon le résultat réel :**
- **Si le test passe du premier coup** : K6 est déjà satisfait par `Href.resolve()` — aucun code supplémentaire à écrire. Documenter cette conclusion dans le commentaire de `DocumentModelExtractor.extractChapter` (là où `link.href.resolve()` est déjà appelé), pour qu'une future session ne réintroduise pas une normalisation redondante en pensant corriger un bug inexistant.
- **Si le test échoue** : ajouter une étape de normalisation explicite avant la comparaison (`java.net.URLDecoder.decode(href, "UTF-8")` sur les deux côtés de la comparaison avant `==`), avec un test qui isole précisément le cas qui échouait.

**Commit (à adapter selon le résultat) :** `Confirme que Href.resolve() gère le percent-encoding (K6)` ou `Corrige la comparaison de hrefs pour le percent-encoding mixte (K6)`

---

## Tâche 4.4 — DRM : tester la détection déjà câblée

**Objectif :** `publication.isProtected` est déjà branché depuis la Tâche 3.2 — cette tâche ajoute le fixture et le test qui manquaient, elle n'écrit pas de nouvelle logique de détection.

`infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/DrmDetectionTest.kt` :

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DrmDetectionTest {

    @Test
    fun detecte_un_epub_protege_sans_crash_et_sans_dechiffrement() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-drm.epub").apply {
            context.assets.open("fixture-drm.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)

        // Le parsing doit reussir (ouverture des metadonnees), meme si le
        // contenu est protege — la detection n'est pas un dechiffrement
        // (hors perimetre v1, Blueprint §7.11).
        check(result is ParseResult.Success)
        assertTrue("le fixture DRM doit etre detecte comme protege", result.isDrmProtected)
    }
}
```

**Si ce test échoue** (le fixture ne déclenche pas `isProtected`), le problème est probablement dans la structure du fixture (mécanisme de protection non reconnu par `FallbackContentProtection`), pas dans le code de détection — vérifier le fixture avant de toucher à `ReadiumPublicationParser`.

**Commit :** `Ajoute le test de detection DRM (K7) avec fixture dedie`

---

## Tâche 4.5 — Navigation complète

**Objectif :** chapitre suivant/précédent, TOC virtualisée avec scroll vers le chapitre courant, retour à la dernière position.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderUiState.kt`, étendre (remplace la version squelette de la Phase 3) :

```kotlin
package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.TableOfContentsEntry

data class ReaderUiState(
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val tableOfContents: List<TableOfContentsEntry> = emptyList(),
    val currentSentenceIndex: Int = 0,
    val highlightedWordRange: IntRange? = null,
    val isPlaying: Boolean = false,
    val isTocVisible: Boolean = false,
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
    val hasNextChapter: Boolean get() = currentChapterIndex < chapters.lastIndex
    val hasPreviousChapter: Boolean get() = currentChapterIndex > 0
}

sealed interface ReaderIntent {
    data class OpenPublication(val publicationId: String) : ReaderIntent
    object NextChapter : ReaderIntent
    object PreviousChapter : ReaderIntent
    data class JumpToChapter(val chapterIndex: Int) : ReaderIntent
    object ToggleToc : ReaderIntent
    object PlayCurrentSentence : ReaderIntent
    object Pause : ReaderIntent
}
```

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderViewModel.kt`, ajouter (en plus de `playCurrentSentence` existant depuis la Phase 3) :

```kotlin
fun onIntent(intent: ReaderIntent) {
    when (intent) {
        is ReaderIntent.OpenPublication -> openPublication(intent.publicationId)
        is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
        is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
        is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
        is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
        is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
        is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
    }
}

private fun navigateToChapter(targetIndex: Int) {
    val chapters = _state.value.chapters
    if (targetIndex !in chapters.indices) return // pas de navigation hors bornes silencieuse
    _state.value = _state.value.copy(
        currentChapterIndex = targetIndex, currentSentenceIndex = 0,
        highlightedWordRange = null, isTocVisible = false,
    )
    persistPosition(chapterIndex = targetIndex, sentenceIndex = 0)
    triggerPreload(targetIndex) // Tâche 4.6
}

/**
 * Chemin manuel K3 (Blueprint §7.7) — distinct du chemin TTS
 * (playCurrentSentence). Les deux ne s'exécutent jamais simultanément :
 * la navigation manuelle interrompt implicitement toute lecture en
 * cours (isPlaying repasse a false via l'etat recompose).
 */
private fun persistPosition(chapterIndex: Int, sentenceIndex: Int) {
    viewModelScope.launch {
        val chapter = _state.value.chapters.getOrNull(chapterIndex) ?: return@launch
        val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(sentenceIndex) ?: return@launch
        updateReadingState(
            com.inktone.domain.model.ReadingState(
                publicationId = currentPublicationId ?: return@launch,
                locator = sentence.startLocator(chapterIndex = chapterIndex, resourceHref = chapter.href),
                lastReadAt = System.currentTimeMillis(),
            ),
        )
    }
}
```

**Table des matières virtualisée** — `feature/reader/src/main/kotlin/com/inktone/feature/reader/TableOfContentsSheet.kt` :

```kotlin
package com.inktone.feature.reader

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.inktone.domain.model.TableOfContentsEntry

@Composable
fun TableOfContentsSheet(
    entries: List<TableOfContentsEntry>,
    currentChapterIndex: Int,
    onEntryClick: (chapterIndex: Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll vers le chapitre courant a l'ouverture — pas juste afficher
    // la liste depuis le debut a chaque fois (Blueprint §7.6).
    LaunchedEffect(currentChapterIndex) {
        val targetIndex = entries.indexOfFirst { it.chapterIndex == currentChapterIndex }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    LazyColumn(state = listState, modifier = Modifier) {
        itemsIndexed(entries, key = { _, entry -> entry.chapterIndex }) { _, entry ->
            Text(
                text = entry.title,
                modifier = Modifier.let { m ->
                    if (entry.chapterIndex == currentChapterIndex) {
                        m // TODO Claude Code : appliquer un style de mise en evidence ici (couleur/poids), pas seulement la position scrollee
                    } else m
                },
            )
        }
    }
}
```

**Commit :** `Ajoute la navigation complete (chapitre suivant/precedent, TOC virtualisee, retour position)`

---

## Tâche 4.6 — Préchargement du chapitre suivant

**Objectif :** navigation perçue instantanée — le chapitre n+1 est prêt avant que l'utilisateur ne l'atteigne.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ChapterPreloader.kt` :

```kotlin
package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cache minimal a une entree : le chapitre courant est deja en memoire
 * (fait partie de state.chapters, tout le DocumentModel est charge a
 * l'ouverture pour la Phase 4 — pas de chargement paresseux par chapitre
 * pour l'instant). "Precharger" ici signifie donc : preparer le rendu
 * (mesures de mise en page Compose) du chapitre suivant en arriere-plan,
 * pas charger des donnees qui sont deja toutes en memoire.
 *
 * Cette simplification est assumee pour la Phase 4 — un chargement
 * veritablement paresseux (chapitre par chapitre depuis le fichier EPUB,
 * plutot que tout le DocumentModel d'un coup) est un sujet de
 * performance a mesurer en Tache 4.9 avant de decider s'il est
 * necessaire pour de gros EPUB (budget memoire, §11.2).
 */
class ChapterPreloader(private val scope: CoroutineScope) {

    private var preloadJob: Job? = null

    fun preload(chapter: Chapter?, onReady: (Chapter) -> Unit) {
        preloadJob?.cancel()
        if (chapter == null) return
        preloadJob = scope.launch {
            // Placeholder pour un travail de preparation reel (ex.
            // pre-tokenisation d'affichage) — vide pour l'instant car le
            // DocumentModel est deja entierement en memoire (voir note
            // ci-dessus). A completer si les benchmarks de la Tache 4.9
            // montrent un cout de recomposition non negligeable au
            // changement de chapitre.
            onReady(chapter)
        }
    }
}
```

**Appeler depuis `ReaderViewModel.navigateToChapter`** (Tâche 4.5) :

```kotlin
private fun triggerPreload(currentIndex: Int) {
    val nextChapter = _state.value.chapters.getOrNull(currentIndex + 1)
    chapterPreloader.preload(nextChapter) { /* chapitre pret, no-op pour l'instant */ }
}
```

**Honnêteté sur cette tâche :** elle livre une structure extensible, pas une optimisation mesurable pour l'instant — le `DocumentModel` entier est déjà en mémoire après l'ouverture (Phase 3/4.1), donc il n'y a rien de coûteux à précharger tant qu'un gros EPUB ne révèle pas un coût de recomposition réel en Tâche 4.9. Documenté comme tel plutôt que présenté comme une optimisation qui n'en est pas encore une.

**Commit :** `Ajoute la structure de prechargement de chapitre (optimisation differee a la mesure, Tache 4.9)`

---

## Tâche 4.7 — Rendu, thèmes, typographie, `EffectiveReadingSettings`

**Objectif :** appliquer réellement la cascade de précédence posée en Phase 1 (Tâche 1.3) — pas juste l'avoir en théorie dans le domaine.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderScreen.kt`, remplacer le Composable minimal de la Phase 3 :

```kotlin
package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.ReadingTheme

@Composable
fun ReaderScreen(effectiveSettings: com.inktone.domain.model.EffectiveReadingSettings, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBackgroundColor(effectiveSettings.theme))
            .padding(16.dp),
    ) {
        state.currentChapter?.paragraphs?.flatMap { it.sentences }?.forEach { sentence ->
            Text(
                text = sentence.text,
                fontSize = effectiveSettings.fontSize.sp,
                color = themeTextColor(effectiveSettings.theme),
            )
        }
    }
}

private fun themeBackgroundColor(theme: ReadingTheme) = when (theme) {
    ReadingTheme.LIGHT, ReadingTheme.SYSTEM -> androidx.compose.ui.graphics.Color.White
    ReadingTheme.DARK -> androidx.compose.ui.graphics.Color.Black
    ReadingTheme.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF4ECD8)
}

private fun themeTextColor(theme: ReadingTheme) = when (theme) {
    ReadingTheme.LIGHT, ReadingTheme.SYSTEM, ReadingTheme.SEPIA -> androidx.compose.ui.graphics.Color.Black
    ReadingTheme.DARK -> androidx.compose.ui.graphics.Color.White
}
```

**Le point important, pas juste décoratif :** `effectiveSettings` doit être calculé par l'appelant (l'écran parent, pas ce Composable) via `EffectiveReadingSettings.resolve(readingState.overrides, userPreferences)` — Tâche 1.3, déjà testé en Phase 1. **Ne jamais recalculer une logique de précédence dans l'UI** : ce Composable ne fait qu'afficher un résultat déjà résolu, il ne connaît ni `ReadingOverrides` ni `UserPreferences` séparément. Vérifier que c'est bien le cas avant de fermer cette tâche — c'est exactement le genre de règle qui peut se déliter silencieusement si un futur écran recalcule sa propre version « simplifiée ».

`feature/reader/src/test/kotlin/com/inktone/feature/reader/ThemeColorTest.kt` (JVM pur, pas de rendu Compose réel nécessaire pour tester le mapping) :

```kotlin
package com.inktone.feature.reader

// Note : les fonctions themeBackgroundColor/themeTextColor sont privees.
// Si ce test est ecrit, extraire ces fonctions dans un objet public
// testable (ThemeColors.kt) plutot que de les laisser prive et
// intestables — a faire au moment d'ecrire ce test, pas apres coup.
```

**Commit :** `Applique EffectiveReadingSettings au rendu reel (theme, typographie)`

---

## Tâche 4.8 — Gestion d'erreurs avec fixtures réelles

**Objectif :** les chemins d'erreur existent déjà (Tâche 3.2/4.2) — cette tâche prouve qu'ils se déclenchent vraiment, jamais par un crash.

`infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/ErrorHandlingTest.kt` :

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ErrorHandlingTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun copyFixture(name: String): File =
        File(context.cacheDir, name).apply {
            context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
        }

    @Test
    fun fichier_corrompu_renvoie_une_erreur_typee_jamais_une_exception() = runTest {
        val fixtureFile = copyFixture("fixture-corrompu.epub")
        // Le test lui-meme echoue si parse() leve une exception non geree
        // — c'est le point : aucun crash, meme sur un fichier invalide.
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun ressource_manquante_n_empeche_pas_l_ouverture_des_chapitres_valides() = runTest {
        val fixtureFile = copyFixture("fixture-ressource-manquante.epub")
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)
        // Une image manquante ne doit pas empecher l'extraction du TEXTE
        // des chapitres valides — degradation partielle, pas un echec total.
        check(result is ParseResult.Success)
        assertTrue(result.documentModel.chapters.isNotEmpty())
    }

    @Test
    fun uri_totalement_invalide_ne_crash_jamais() = runTest {
        val result = ReadiumPublicationParser(context).parse("ceci-n-est-pas-une-uri-valide")
        assertTrue(result is ParseResult.Corrupted)
    }
}
```

**Commit :** `Verifie la gestion d'erreurs avec des fixtures reelles (corrompu, ressource manquante, URI invalide)`

---

## Tâche 4.9 — Benchmarks (§11.2)

**Objectif :** mesurer, pas supposer — première mise en place de Macrobenchmark pour le projet.

`benchmark/build.gradle.kts` (nouveau module, hors de la liste canonique §5.2 — module de test, pas de production, à noter dans le Blueprint si conservé) :

```kotlin
plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.inktone.benchmark"
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.0")
}
```

`benchmark/src/main/kotlin/com/inktone/benchmark/EpubOpenBenchmark.kt` :

```kotlin
package com.inktone.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Budget cible (Blueprint §11.2) : ouverture d'un EPUB de 5 Mo -> premier
 * rendu <= 800ms sur baseline Snapdragon 680. Ce benchmark mesure le
 * temps reel sur device de test ; le comparer au budget est un geste
 * manuel pour l'instant (pas d'assertion automatique de seuil — a
 * envisager une fois plusieurs mesures de reference accumulees, pas des
 * la premiere execution).
 */
@RunWith(AndroidJUnit4::class)
class EpubOpenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun ouvertureEpub5Mo() = benchmarkRule.measureRepeated(
        packageName = "com.inktone.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        // Necessite un point d'entree UI pour ouvrir un EPUB de test
        // fixe (5 Mo) depuis l'ecran de demarrage — a cabler une fois
        // feature/library existant (Phase 6). Placeholder de mesure de
        // demarrage pur pour l'instant, pas encore le scenario complet
        // "ouverture EPUB" annonce par le budget §11.2.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000)
    }
}
```

**Honnêteté sur cette tâche, comme pour 4.6 :** le scénario complet « ouverture d'un EPUB de 5 Mo » ne peut pas être mesuré avant que `feature/library` (Phase 6) offre un point d'entrée UI réel. Ce que la Tâche 4.9 livre pour l'instant : l'outillage Macrobenchmark en place et fonctionnel, mesurant le démarrage à froid — le scénario précis du budget sera complété en Phase 6, pas simulé artificiellement ici.

**Commit :** `Ajoute le module benchmark et la premiere mesure de demarrage (scenario complet differe a la Phase 6)`

---

## Tâche 4.10 — Consolidation des garde-fous de régression (K3/K6/K7)

**Objectif :** le Blueprint §14.6 exige des garde-fous explicites. Cette tâche ne crée rien de nouveau — elle vérifie que les tests déjà écrits (Tâches 3.6, 4.3, 4.4) couvrent bien chacun un K précis, et les regroupe pour qu'un futur audit les trouve d'un coup.

**Action :** créer `infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/RegressionGuardsSuite.kt` :

```kotlin
package com.inktone.infrastructure.parser

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Regroupe les garde-fous de regression K3/K6/K7 (Blueprint §14.6) en un
 * seul point d'execution, pour un audit rapide — ne duplique aucun test,
 * reference seulement ceux qui existent deja depuis les Phases 2/3/4.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    HrefEncodingTest::class,     // K6 (Tache 4.3)
    DrmDetectionTest::class,     // K7 (Tache 4.4)
    // K3 (reprise de lecture) vit dans feature/reader :
    // ReadingResumeTest (Tache 3.6) — module different, pas inclus dans
    // cette Suite JUnit (limitation technique : une Suite ne traverse
    // pas les modules Gradle). Documenté ici comme rappel, pas un oubli.
)
class RegressionGuardsSuite
```

**Commit :** `Regroupe les garde-fous de regression K6/K7 en une suite auditable`

---

## Checklist finale de sortie de Phase 4

| # | Critère | Vérification |
|---|---|---|
| 1 | Extraction multi-chapitres vérifiée sans contamination croisée | `DocumentModelExtractorMultiChapterTest` vert |
| 2 | Parser TXT fonctionnel, sélection automatique par format | `TxtPublicationParserTest` vert ; `CompositePublicationParser` lié à Hilt |
| 3 | K6 (percent-encoding) tranché empiriquement, pas supposé | `HrefEncodingTest` vert, conclusion documentée dans le code |
| 4 | K7 (DRM) testé avec un fixture dédié | `DrmDetectionTest` vert |
| 5 | Navigation complète (chapitre suiv./préc., TOC, retour position) | Parcours manuel + `navigateToChapter` avec bornes vérifiées |
| 6 | Préchargement en place (structure, optimisation différée honnêtement) | `ChapterPreloader` câblé, limitation documentée |
| 7 | `EffectiveReadingSettings` réellement appliqué au rendu | Aucune logique de précédence recalculée dans l'UI |
| 8 | Gestion d'erreurs prouvée sur fixtures réelles | `ErrorHandlingTest` (3 cas) vert |
| 9 | Outillage de benchmark en place | `EpubOpenBenchmark` s'exécute ; scénario complet noté comme différé à la Phase 6 |
| 10 | Garde-fous K3/K6/K7 regroupés et traçables | `RegressionGuardsSuite` + note sur la limite inter-modules |

Une fois les 10 critères vérifiés sur un clone frais, Phase 4 est close. Étape suivante : **Phase 5 — TTS Engine complet**, dont le tableau de tâches a déjà été révisé post-ADR-021 (Palier 1 + Palier 2, décision actée en Phase 3) — adaptateur Sherpa-ONNX (Tâche 5.1) et alignement forcé CTC (Tâche 5.2) en tête de liste.
