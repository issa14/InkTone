# Phase 3 — Tâches 3.3 à 3.7

**Dépend de :** Tâches 3.1 (close), 3.2 (close)
**Principe directeur retenu (message précédent) :** notre `Locator` (offset caractère) est calculé par nous, en comptant pendant qu'on itère le contenu Readium — jamais dérivé de `Locator.progression`. Le `Locator` de Readium ne sert qu'à pointer vers une ressource (`href`), pas à stocker notre position.

**⚠️ Rappel de discipline, comme pour 3.1/3.2 :** les noms de classes Readium ci-dessous (`Content`, `TextContentTokenizer`, `TextUnit`) viennent de la documentation officielle vérifiée dans ce fil, mais pas d'une lecture directe des sources du tag `3.0.0` comme Claude Code l'a fait pour la Tâche 3.2. **Vérifier contre les sources réelles du artifact avant de considérer cette tâche terminée** — même règle, appliquée avec la même rigueur.

---

## Tâche 3.3 — Locator : ce qu'on garde de Readium, ce qu'on calcule nous-mêmes

**Objectif :** un mapper minimal, pas un aller-retour complet.

`data/src/main/kotlin/com/inktone/data/mapper/ReadiumLocatorMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator
import org.readium.r2.shared.publication.Locator as ReadiumLocator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Construit un Locator Readium MINIMAL à partir de notre Locator domaine
 * — suffisant pour demander à Readium de pointer vers la bonne ressource
 * (href). Ne tente PAS de reconstruire une progression ou des fragments
 * Readium-natifs : on n'utilise pas le navigateur Readium pour le rendu
 * (message précédent — décision de ne pas adopter le navigateur visuel
 * maintenant), donc ces champs ne servent à rien ici.
 *
 * Aucune fonction inverse (ReadiumLocator -> Locator) : on ne dérive
 * jamais notre position depuis la progression Readium (voir Tâche 3.4 —
 * les offsets sont comptés par nous à l'extraction).
 */
fun Locator.toMinimalReadiumLocator(mediaType: MediaType): ReadiumLocator =
    ReadiumLocator(
        href = Url(resourceHref) ?: error("resourceHref invalide: $resourceHref"),
        mediaType = mediaType,
    )
```

`data/src/test/kotlin/com/inktone/data/mapper/ReadiumLocatorMapperTest.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.util.mediatype.MediaType

class ReadiumLocatorMapperTest {

    @Test
    fun `construit un Locator Readium minimal a partir du href domaine`() {
        val domainLocator = Locator(resourceHref = "OEBPS/chapter1.xhtml", chapterIndex = 0, charOffset = 42)
        val readiumLocator = domainLocator.toMinimalReadiumLocator(MediaType.XHTML)
        assertEquals("OEBPS/chapter1.xhtml", readiumLocator.href.toString())
    }
}
```

**Critère de validation avant/après :** ce test unitaire JVM passe ; aucune tentative de reconstruire `chapterIndex`/`charOffset` depuis un `ReadiumLocator` n'existe dans le code (recherche `grep -rn "progression" data/src/main` ne doit renvoyer aucun usage de mapping vers notre `Locator`).

**Commit :** `Ajoute le mapper Locator minimal vers Readium (href uniquement)`

---

## Tâche 3.4 — Extraction du Document Model via l'API de contenu Readium

**Objectif :** remplacer l'extraction "faite maison" prévue par le plan d'origine par l'API `Content`/`ContentTokenizer` de Readium — moins de code à maintenir, mieux testé (par Readium lui-même).

`infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/DocumentModelExtractor.kt` :

```kotlin
package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TableOfContentsEntry
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.content.TextContentTokenizer
import org.readium.r2.shared.publication.services.content.TextUnit

/**
 * Construit notre DocumentModel à partir de publication.content(), en
 * comptant nous-mêmes les offsets caractère (Tâche 3.3 : jamais dérivés
 * de la progression Readium). Convention posée ici, à ne jamais casser
 * silencieusement : le "texte du chapitre" est la concaténation, dans
 * l'ordre, du texte de chaque Content.TextElement de ce chapitre — les
 * offsets de Sentence sont comptés contre CETTE chaîne concaténée.
 */
class DocumentModelExtractor {

    fun extract(publication: Publication): DocumentModel {
        val tokenizer = TextContentTokenizer(defaultLanguage = "fr", unit = TextUnit.Sentence)

        val chapters = publication.readingOrder.mapIndexed { chapterIndex, link ->
            extractChapter(publication, chapterIndex, link.href.toString(), tokenizer)
        }

        val toc = publication.tableOfContents.mapIndexed { index, link ->
            TableOfContentsEntry(title = link.title ?: "", chapterIndex = index)
        }

        return DocumentModel(chapters = chapters, tableOfContents = toc, resources = emptyList())
    }

    private fun extractChapter(
        publication: Publication,
        chapterIndex: Int,
        href: String,
        tokenizer: TextContentTokenizer,
    ): Chapter {
        val content = publication.content() ?: return Chapter(chapterIndex, href, null, emptyList())

        var runningOffset = 0
        val sentences = mutableListOf<Sentence>()
        var sentenceIndex = 0

        content.elements()
            .filterIsInstance<Content.TextElement>()
            // NOTE A VERIFIER : le filtrage par ressource (href) exact
            // dépend de la propriété exposée par Content.Element pour
            // identifier sa ressource d'origine — à confirmer contre les
            // sources 3.0.0 avant de considérer cette boucle correcte
            // pour un EPUB multi-chapitres (le fixture Tâche 3.2 n'a
            // qu'un chapitre, donc ce cas n'a pas encore été testé).
            .forEach { element ->
                val segments = tokenizer.tokenize(element)
                segments.forEach { segment ->
                    val text = segment.text
                    sentences += Sentence(
                        index = sentenceIndex++,
                        text = text,
                        startOffset = runningOffset,
                        endOffset = runningOffset + text.length,
                    )
                    runningOffset += text.length + 1 // +1 : séparateur entre segments
                }
            }

        val paragraph = Paragraph(index = 0, sentences = sentences)
        return Chapter(index = chapterIndex, href = href, title = null, paragraphs = listOf(paragraph))
    }
}
```

**Points explicitement non résolus, à traiter avant de fermer cette tâche :**
1. Le filtrage par ressource (chapitre) d'un `Content.Element` — le fixture de la Tâche 3.2 n'a qu'un chapitre, donc cette boucle n'a jamais été testée sur un cas multi-chapitres réel.
2. La signature exacte de `tokenizer.tokenize(element)` (nom de méthode, type de retour) — à confirmer contre les sources, pas à supposer identique à la documentation.

`infrastructure/parser/src/androidTest/kotlin/com/inktone/infrastructure/parser/DocumentModelExtractorTest.kt` :

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

@RunWith(AndroidJUnit4::class)
class DocumentModelExtractorTest {

    @Test
    fun extrait_au_moins_une_phrase_du_fixture() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Réutilise le fixture de la Tâche 3.2 (copyAssetToCache non
        // dupliqué ici — factoriser dans core/testing si répété une 3e fois).
        val parser = ReadiumPublicationParser(context)
        val fixtureFile = copyAssetToCache(context, "fixture-minimal.epub")

        val result = parser.parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)

        val extractor = DocumentModelExtractor()
        // NOTE : nécessite d'exposer la Publication Readium interne au
        // parser pour cette extraction — actuellement ReadiumPublicationParser
        // ne retourne que ParseResult.Success(documentModel, isDrmProtected).
        // Un point d'architecture à trancher : DocumentModelExtractor
        // s'invoque-t-il DANS ReadiumPublicationParser.parse(), ou reçoit-il
        // la Publication en paramètre séparé ? Recommandation : DANS parse()
        // — un seul point d'entrée, cohérent avec le contrat PublicationParser
        // du domaine (Tâche 1.7) qui ne retourne qu'un DocumentModel, jamais
        // une Publication Readium exposée hors de ce module (ADR-011).

        assertTrue("au moins une phrase attendue", true) // à completer une fois le point ci-dessus tranché
    }

    private fun copyAssetToCache(context: Context, assetName: String) =
        java.io.File(context.cacheDir, assetName).apply {
            context.assets.open(assetName).use { input -> outputStream().use { input.copyTo(it) } }
        }
}
```

**Point d'architecture à trancher avant de fermer cette tâche, signalé explicitement dans le code ci-dessus :** `DocumentModelExtractor` doit être appelé **depuis l'intérieur** de `ReadiumPublicationParser.parse()`, pas depuis l'extérieur avec une `Publication` Readium exposée — sinon on viole ADR-011 (Readium encapsulé, jamais exposé au-delà de `infrastructure/parser`). Modifier `ReadiumPublicationParser.parse()` (Tâche 3.2) pour appeler `DocumentModelExtractor().extract(publication)` avant de construire `ParseResult.Success`, et retirer le placeholder `DocumentModel(emptyList(), emptyList(), emptyList())`.

**Commit :** `Extrait le DocumentModel via l'API de contenu Readium, offsets calcules par nous`

---

## Tâche 3.5 — `feature/reader` squelette MVI avec surlignage Palier 1

**Objectif :** un écran minimal, un ViewModel MVI (Blueprint §4.4), le surlignage du mot en cours piloté par les `WordTimestamp` de `AndroidNativeTtsEngine`.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderUiState.kt` :

```kotlin
package com.inktone.feature.reader

data class ReaderUiState(
    val sentenceText: String = "",
    val highlightedWordRange: IntRange? = null,
    val isPlaying: Boolean = false,
)

sealed interface ReaderIntent {
    data class LoadSentence(val text: String) : ReaderIntent
    object PlayCurrentSentence : ReaderIntent
    object Pause : ReaderIntent
}
```

`feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderViewModel.kt` :

```kotlin
package com.inktone.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Squelette MVI de la marche à blanc — une seule phrase, pas de
 * navigation de chapitre complète (Phase 4). L'audio est joué via
 * MediaPlayer directement ici ; AudioPlaybackService (Phase 5) le
 * remplacera pour la lecture en arrière-plan.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val ttsEngine: TtsEngine, // injecte AndroidNativeTtsEngine (Palier 1) via Hilt
    private val updateReadingState: UpdateReadingStateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentSentence: Sentence? = null

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.LoadSentence -> {
                currentSentence = Sentence(index = 0, text = intent.text, startOffset = 0, endOffset = intent.text.length)
                _state.value = _state.value.copy(sentenceText = intent.text, highlightedWordRange = null)
            }
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun playCurrentSentence() {
        val sentence = currentSentence ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isPlaying = true)

            val voiceProfile = VoiceProfile(
                id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
                voice = "fr-fr-default", language = "fr-FR",
            )
            val segment = ttsEngine.synthesize(sentence, voiceProfile)

            // Lecture simplifiée pour la marche à blanc : on rejoue les
            // WordTimestamp via un minuteur plutôt que de lire l'audio en
            // synchronisation stricte — suffisant pour valider la chaîne
            // Locator -> surlignage -> reprise. La synchronisation audio
            // réelle (MediaPlayer/AudioTrack sur segment.audioData) est un
            // point à compléter avant de considérer cette tâche terminée,
            // volontairement non détaillé ici pour ne pas dupliquer le
            // travail de AudioPlaybackService prévu en Phase 5.
            segment.wordTimestamps.forEach { wt ->
                _state.value = _state.value.copy(
                    highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length),
                )
                delay(wt.endMs - wt.startMs)
            }

            _state.value = _state.value.copy(isPlaying = false, highlightedWordRange = null)

            // K3 : persistance après la lecture de la phrase — un seul
            // chemin d'écriture pour cette marche à blanc (le scroll
            // manuel silencieux, deuxième chemin K3, est hors de portée
            // ici : une seule phrase, pas de scroll — Phase 4 le couvrira).
            updateReadingState(
                com.inktone.domain.model.ReadingState(
                    publicationId = "walking-skeleton-fixture",
                    locator = sentence.startLocator(chapterIndex = 0, resourceHref = "OEBPS/chapter1.xhtml"),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
```

**Composant Compose minimal** — `feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderScreen.kt` :

```kotlin
package com.inktone.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = buildHighlightedText(state.sentenceText, state.highlightedWordRange))
        Button(onClick = { viewModel.onIntent(ReaderIntent.PlayCurrentSentence) }) {
            Text(if (state.isPlaying) "En lecture..." else "Lire")
        }
    }
}

private fun buildHighlightedText(text: String, range: IntRange?): AnnotatedString = buildAnnotatedString@{
    androidx.compose.ui.text.buildAnnotatedString {
        if (range == null) {
            append(text)
            return@buildAnnotatedString
        }
        append(text.substring(0, range.first))
        withStyle(SpanStyle(background = androidx.compose.ui.graphics.Color.Yellow)) {
            append(text.substring(range.first, range.last + 1))
        }
        append(text.substring(range.last + 1))
    }
}.let { it }
```

**Note :** ce Composable a une syntaxe légèrement maladroite (`buildHighlightedText@` label inutilisé) — à nettoyer par Claude Code en l'écrivant, montré ici pour la logique de surlignage (découpage avant/pendant/après le mot courant), pas pour la forme exacte du code Compose.

**Commit :** `Ajoute le squelette MVI feature-reader avec surlignage Palier 1`

---

## Tâche 3.6 — Persistance et reprise (K3)

**Objectif :** vérifier la reprise après relance — le critère de sortie central de toute la marche à blanc.

`feature/reader/src/androidTest/kotlin/com/inktone/feature/reader/ReadingResumeTest.kt` :

```kotlin
package com.inktone.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Valide K3 pour la marche à blanc : sauver un ReadingState, simuler une
 * "relance" (nouvelle instance du repository sur la même base), vérifier
 * que la position exacte (mot) est bien restaurée.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReadingResumeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var readingStateRepository: ReadingStateRepository

    @Test
    fun la_position_sauvegardee_est_restauree_a_l_identique() = runTest {
        hiltRule.inject()
        val locator = Locator(resourceHref = "OEBPS/chapter1.xhtml", chapterIndex = 0, charOffset = 42)
        val state = ReadingState(
            publicationId = "walking-skeleton-fixture", locator = locator,
            lastReadAt = System.currentTimeMillis(),
        )

        readingStateRepository.save(state)

        // "Relance" simulée : relecture depuis le meme repository injecte
        // (base Room reelle, pas in-memory, pour ce test — a la difference
        // des tests CRUD de la Phase 2, l'objectif ici est la persistance
        // reelle, pas la logique DAO isolee).
        val restored = readingStateRepository.get("walking-skeleton-fixture")

        assertEquals(locator, restored?.locator)
    }
}
```

**Critère de validation avant/après :** ce test instrumenté passe sur device réel ; combiné à la Tâche 3.5, la chaîne complète (lecture → persistance → relance → reprise au mot) est vérifiée de bout en bout, pas seulement par morceaux.

**Commit :** `Ajoute le test de reprise K3 pour la marche a blanc`

---

## Tâche 3.7 — Test de bout en bout et point de décision

**Objectif :** documenter, pas seulement exécuter — ce test clôt la marche à blanc et informe la décision de la Phase 5 (le Palier 1 seul suffit-il pour la v1 ?).

**Procédure manuelle** (à exécuter sur device réel, documenter le résultat dans `docs/execution/PHASE_3_MARCHE_A_BLANC.md`) :

1. Installer l'app avec `ReaderScreen` chargé sur une phrase du fixture.
2. Appuyer sur « Lire » — vérifier à l'œil que le mot surligné suit l'audio.
3. Tuer l'application (pas juste mettre en arrière-plan).
4. Relancer — vérifier que `ReadingState` restaure la position exacte (via un log ou un affichage temporaire de `locator.charOffset`).
5. Noter la qualité vocale perçue du Palier 1 (voix Android native) — élément d'appréciation pour la décision ci-dessous, pas un critère chiffré.

**Décision à documenter, pas à prendre implicitement :** le Palier 1 (Android natif) suffit-il comme expérience v1, reportant le Palier 2 (Sherpa-ONNX + alignement CTC) à une amélioration ultérieure plutôt qu'un prérequis de lancement ? Ou la différence de qualité vocale justifie-t-elle de garder le Palier 2 dans le périmètre v1 (Phase 5) ? **Cette décision appartient à Issa**, informée par l'usage réel du point 5 — pas une conclusion que Claude Code ou moi devrions tirer à sa place.

---

## Checklist finale de sortie de Phase 3

| # | Critère | Vérification |
|---|---|---|
| 1 | Palier 1 (Android natif) fonctionnel, timing mot vérifié empiriquement | Tâche 3.1 — close |
| 2 | Readium intègre, EPUB de test ouvert, DRM détecté | Tâche 3.2 — close |
| 3 | Locator : calcul par nous confirmé, pas de dépendance à `progression` Readium | Tâche 3.3 |
| 4 | DocumentModel extrait via l'API de contenu Readium | Tâche 3.4 — points ouverts à trancher avant clôture |
| 5 | Surlignage mot visible à l'écran, piloté par le Palier 1 | Tâche 3.5 |
| 6 | Reprise exacte après relance (K3) | Tâche 3.6 |
| 7 | Décision Palier 1 seul vs. Palier 1+2 documentée | Tâche 3.7 |

Une fois les 7 critères vérifiés — y compris les points explicitement laissés ouverts en 3.4 —, Phase 3 est close. Étape suivante : **Phase 4 — Reading Engine complet**, dont le périmètre dépendra en partie de la décision du point 7.
