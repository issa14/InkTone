package com.inktone.feature.reader.pagination

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Couvre `ChapterTextMeasurer` directement (Tâche 3a, révision post-3a.1) —
 * migré vers le modèle Rich (Plan v3, Palier 5). Les `ParagraphStyle`/`Paragraph`
 * ont été remplacés par `BookBlock.ParagraphBlock`/`BookBlock.HeadingBlock`.
 *
 * Nécessite Robolectric : `TextMeasurer` mesure avec de vraies métriques
 * de police Android, indisponibles en JVM pur.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterTextMeasurerTest {

    private lateinit var measurer: ChapterTextMeasurer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textMeasurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(density = 1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        measurer = ChapterTextMeasurer(textMeasurer)
    }

    private fun sentence(index: Int, text: String, startOffset: Int): Sentence =
        Sentence(index = index, text = text, startOffset = startOffset, endOffset = startOffset + text.length)

    @Test
    fun `l annotatedString concatene les blocs dans l ordre`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = "Chapitre 1",
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.HeadingBlock(
                        level = 1,
                        richText = StyledText.plain("Titre."),
                        globalOffsetRange = 0..5,
                    ),
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Première phrase. Deuxième phrase."),
                        globalOffsetRange = 7..38,
                    ),
                ),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        // Les blocs sont concaténés séquentiellement (sans newline entre eux
        // dans le modèle Rich — la séparation visuelle est gérée par le layout).
        assertEquals("Titre.Première phrase. Deuxième phrase.", result.annotatedString.text)
    }

    @Test
    fun `les offsets locaux de phrase correspondent au texte mesure`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Alpha."),
                        globalOffsetRange = 0..5,
                    ),
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Beta."),
                        globalOffsetRange = 7..11,
                    ),
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Gamma."),
                        globalOffsetRange = 13..18,
                    ),
                ),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)
        val text = result.annotatedString.text

        // Les sentenceStartOffsets marquent le début de chaque bloc dans
        // le texte concaténé — vérification que les offsets pointent
        // bien sur le début du texte attendu.
        assertTrue(text.substring(result.sentenceStartOffsets[0]).startsWith("Alpha."))
        assertTrue(text.substring(result.sentenceStartOffsets[1]).startsWith("Beta."))
        assertTrue(text.substring(result.sentenceStartOffsets[2]).startsWith("Gamma."))

        // Les offsets locaux dans l'AnnotatedString (0, 6, 11) ne
        // coïncident PAS avec les startOffset de ressource EPUB (qui
        // seraient différents pour un vrai EPUB avec whitespace).
        val resourceOffsets = chapter.sentences.map { it.startOffset }
        assertTrue(resourceOffsets.isEmpty() || resourceOffsets != result.sentenceStartOffsets)
    }

    @Test
    fun `un titre HEADING produit une ligne plus haute qu un paragraphe normal`() {
        val text = "Un texte assez long pour occuper une ligne entiere."
        val headingChapter = Chapter(
            index = 0,
            href = "h.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.HeadingBlock(
                        level = 1,
                        richText = StyledText.plain(text),
                        globalOffsetRange = 0 until text.length,
                    ),
                ),
            ),
        )
        val normalChapter = Chapter(
            index = 0,
            href = "n.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(text),
                        globalOffsetRange = 0 until text.length,
                    ),
                ),
            ),
        )

        val style = TextStyle(fontSize = 18.sp)
        val headingResult = measurer.measure(headingChapter, style, maxWidthPx = 2000)
        val normalResult = measurer.measure(normalChapter, style, maxWidthPx = 2000)

        val headingLineHeight = headingResult.lines[0].bottom - headingResult.lines[0].top
        val normalLineHeight = normalResult.lines[0].bottom - normalResult.lines[0].top
        assertTrue(
            "un HEADING doit mesurer plus haut qu'un paragraphe normal",
            headingLineHeight > normalLineHeight,
        )
    }

    @Test
    fun `chapitre vide produit un AnnotatedString vide et aucune ligne`() {
        val chapter = Chapter(
            index = 0,
            href = "e.xhtml",
            title = null,
            content = ChapterContent.Rich(blocks = emptyList()),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals("", result.annotatedString.text)
        assertTrue(result.lines.isEmpty())
        assertTrue(result.sentenceStartOffsets.isEmpty())
    }
}
