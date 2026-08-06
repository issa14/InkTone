package com.inktone.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.ParagraphStyle
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.pagination.rememberChapterPaginationState
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests Compose du mode pagé (Tâche 3a.4, points 8-10 de la checklist).
 * Exécutés sur appareil réel — la mesure de texte y a de vraies métriques
 * de police, à la différence du sandbox de développement où
 * `ChapterTextMeasurerTest` a un écart déclaré.
 *
 * Le surlignage mot-à-mot et la sélection sont dessinés en phase de
 * dessin (`drawWithContent`, voir `PagedChapterContent`) — invisibles à
 * l'arbre de sémantique par construction (c'est précisément ce qui
 * évite une recomposition par mot). Ces tests vérifient donc le
 * comportement observable qui en dépend (positionnement du pager,
 * `SpanStyle` réellement posés sur le texte rendu) plutôt que le pixel
 * du surlignage lui-même, qu'une capture d'écran seule pourrait
 * atteindre mais de façon plus fragile.
 *
 * Vérifié sur appareil réel (V2206, Android 14) : 3/3 tests verts. A
 * nécessité d'ajouter `packaging.jniLibs.pickFirsts` pour
 * `libonnxruntime.so` dans `feature/reader/build.gradle.kts` — la même
 * règle que `infrastructure/tts` avait déjà pour son propre packaging,
 * mais qui ne se propage pas aux modules consommateurs
 * (`androidTestImplementation(project(":infrastructure:tts"))` tire le
 * doublon dans l'APK de test de ce module) ; bloquait tout test
 * instrumenté du module, pas seulement ceux-ci (confirmé via
 * `ReadingResumeTest`, préexistant et inchangé).
 */
class PagedChapterContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sentence(index: Int, text: String, startOffset: Int): Sentence =
        Sentence(index = index, text = text, startOffset = startOffset, endOffset = startOffset + text.length)

    /**
     * Reproduit le hoisting de 3b.1 : mesure la zone réelle disponible
     * (comme le `Box` partagé sous `ReaderScreen`) et hisse la pagination
     * dessus, avant de rendre `PagedChapterContent` en pur consommateur —
     * sinon la pagination calculée (viewport arbitraire) et le rendu
     * effectif (taille réelle du `Box` de test) divergeraient.
     */
    @Composable
    private fun PagedChapterContentHarness(
        chapter: Chapter,
        fontSizeSp: Int,
        currentSentenceIndex: Int,
        highlightedWordRange: IntRange?,
        onSentenceLongClick: (Int) -> Unit,
        onSentenceClick: (Int) -> Unit,
    ) {
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier.size(320.dp, 160.dp).onGloballyPositioned { coordinates -> areaSize = coordinates.size },
        ) {
            val pagination = rememberChapterPaginationState(
                chapter = chapter,
                nextChapter = null,
                currentSentenceIndex = currentSentenceIndex,
                fontSizeSp = fontSizeSp,
                lineHeightSp = fontSizeSp,
                viewportWidthPx = areaSize.width,
                viewportHeightPx = areaSize.height,
                paddingPx = 0,
            )
            PagedChapterContent(
                chapter = chapter,
                pagination = pagination,
                currentSentenceIndex = currentSentenceIndex,
                highlightedWordRange = highlightedWordRange,
                selectedRange = null,
                annotations = emptyList<Annotation>(),
                currentChapterIndex = 0,
                textColor = Color.Black,
                isReadingRulerEnabled = false,
                onSentenceLongClick = onSentenceLongClick,
                onSentenceClick = onSentenceClick,
                onNextChapter = {},
                onCurrentLineY = {},
            )
        }
    }

    /** N'importe quel nœud portant du texte Compose (`SemanticsProperties.Text`), sans exiger un contenu précis. */
    private val hasAnyText = SemanticsMatcher("hasAnyText") { it.config.contains(SemanticsProperties.Text) }

    private fun SemanticsNode.renderedText(): String =
        config[SemanticsProperties.Text].joinToString(separator = "") { it.text }

    /** Nœuds portant du texte actuellement visibles à l'écran — exclut les pages voisines préchargées hors-viewport (`beyondViewportPageCount`). */
    private fun displayedTextNodes(): List<SemanticsNode> =
        composeTestRule.onAllNodes(hasAnyText).fetchSemanticsNodes().filter { node ->
            runCatching {
                composeTestRule.onNode(SemanticsMatcher("byId-${node.id}") { it.id == node.id }).isDisplayed()
            }.getOrDefault(false)
        }

    @Test
    fun titre_epub_s_affiche_avec_le_style_titre_en_mode_page() {
        val headingText = "Titre du chapitre"
        val chapter = Chapter(
            index = 0,
            href = "c.xhtml",
            title = null,
            paragraphs = listOf(
                Paragraph(0, listOf(sentence(0, "$headingText.", 0)), ParagraphStyle.HEADING),
                Paragraph(1, listOf(sentence(1, "Texte normal qui suit le titre.", 40)), ParagraphStyle.NORMAL),
            ),
        )

        composeTestRule.setContent {
            PagedChapterContentHarness(
                chapter = chapter,
                fontSizeSp = 18,
                currentSentenceIndex = 0,
                highlightedWordRange = null,
                onSentenceLongClick = {},
                onSentenceClick = {},
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(headingText, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        val node = composeTestRule.onAllNodesWithText(headingText, substring = true)
            .fetchSemanticsNodes().first()

        val hasBoldSpanOverHeading = node.config[SemanticsProperties.Text].any { annotated: AnnotatedString ->
            annotated.spanStyles.any { range: AnnotatedString.Range<SpanStyle> ->
                range.item.fontWeight == FontWeight.Bold && range.start < headingText.length
            }
        }
        assertTrue(
            "le texte du titre EPUB doit porter un SpanStyle gras (ParagraphStyle.HEADING) en mode pagé",
            hasBoldSpanOverHeading,
        )
    }

    @Test
    fun appui_long_sur_une_phrase_de_la_deuxieme_page_donne_le_bon_index_global() {
        val (chapter, sentenceTexts) = manyShortSentencesChapter()
        var clickedIndex: Int? = null

        composeTestRule.setContent {
            PagedChapterContentHarness(
                chapter = chapter,
                fontSizeSp = 32,
                currentSentenceIndex = 0,
                highlightedWordRange = null,
                onSentenceLongClick = { index -> clickedIndex = index },
                onSentenceClick = {},
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { displayedTextNodes().isNotEmpty() }
        val page1Text = displayedTextNodes().first().renderedText()

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            displayedTextNodes().any { it.renderedText() != page1Text }
        }
        val page2Node = displayedTextNodes().first { it.renderedText() != page1Text }
        val page2Text = page2Node.renderedText()

        val firstSentenceIndexOnPage2 = sentenceTexts.indexOfFirst { text -> page2Text.contains(text) }
        assertTrue(
            "aucune phrase connue trouvée sur la page 2 (contenu: \"$page2Text\") — fixture ou hypothèse de pagination à revoir",
            firstSentenceIndexOnPage2 >= 0,
        )
        assertNotEquals("la page 2 ne devrait pas commencer par la phrase 0", 0, firstSentenceIndexOnPage2)

        composeTestRule.onNode(SemanticsMatcher("page2") { it.id == page2Node.id })
            .performTouchInput { longClick(topLeft + Offset(4f, 4f)) }

        composeTestRule.waitUntil(timeoutMillis = 3_000) { clickedIndex != null }
        assertNotEquals(
            "l'appui long en haut de la page 2 doit rapporter l'index GLOBAL de la phrase, pas 0 " +
                "(position locale dans une liste propre à la page) — c'est le bug indexOf O(n²) corrigé en 3a.2",
            0,
            clickedIndex,
        )
    }

    @Test
    fun le_pager_suit_automatiquement_le_mot_surligne_quand_il_change_de_page() {
        val (chapter, sentenceTexts) = manyShortSentencesChapter()
        var currentSentenceIndex by mutableStateOf(0)
        var highlightedWordRange by mutableStateOf<IntRange?>(0..0)

        composeTestRule.setContent {
            PagedChapterContentHarness(
                chapter = chapter,
                fontSizeSp = 32,
                currentSentenceIndex = currentSentenceIndex,
                highlightedWordRange = highlightedWordRange,
                onSentenceLongClick = {},
                onSentenceClick = {},
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { displayedTextNodes().isNotEmpty() }
        val page1Text = displayedTextNodes().first().renderedText()

        val offPage1SentenceIndex = sentenceTexts.indexOfFirst { text -> !page1Text.contains(text) }
        assertTrue(
            "toutes les phrases tiennent sur la page 1 — la fixture doit forcer plusieurs pages",
            offPage1SentenceIndex > 0,
        )

        // Simule la progression du TTS vers une phrase hors de la page
        // actuellement affichée : le pager doit basculer automatiquement
        // (LaunchedEffect sur l'offset absolu du mot, voir
        // PagedChapterContent) sans action de l'utilisateur.
        composeTestRule.runOnIdle {
            currentSentenceIndex = offPage1SentenceIndex
            highlightedWordRange = 0..0
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            displayedTextNodes().any { it.renderedText().contains(sentenceTexts[offPage1SentenceIndex]) }
        }
    }

    /**
     * 8 phrases courtes, distinctes et numérotées — police volontairement
     * très grande et viewport volontairement petit (voir le `Box` de
     * chaque test) pour forcer plusieurs pages de façon fiable, sans
     * dépendre d'une estimation précise du retour à la ligne.
     */
    private fun manyShortSentencesChapter(): Pair<Chapter, List<String>> {
        val texts = (0 until 8).map { i -> "Phrase numero $i." }
        var offset = 0
        val paragraphs = texts.mapIndexed { i, text ->
            val p = Paragraph(i, listOf(sentence(i, text, offset)), ParagraphStyle.NORMAL)
            offset += text.length + 10
            p
        }
        return Chapter(index = 0, href = "c.xhtml", title = null, paragraphs = paragraphs) to texts
    }
}
