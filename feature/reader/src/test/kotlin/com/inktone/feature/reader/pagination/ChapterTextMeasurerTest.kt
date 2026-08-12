package com.inktone.feature.reader.pagination

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.ParagraphStyle
import com.inktone.domain.model.Sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Couvre `ChapterTextMeasurer` directement (Tâche 3a, révision post-3a.1) :
 * c'est ici, pas dans `VirtualPaginationEngine`, qu'aurait pu se glisser
 * le bug d'espace de coordonnées entre les offsets locaux au texte
 * mesuré et `Sentence.startOffset` (offsets dans la ressource EPUB
 * d'origine). Un décalage ici se manifesterait en production comme « le
 * surlignage est sur le mauvais mot » — symptôme loin de sa cause,
 * d'où l'intérêt de le tester à la source plutôt que via les tests
 * Compose de 3a.4.
 *
 * Nécessite Robolectric : `TextMeasurer` mesure avec de vraies métriques
 * de police Android, indisponibles en JVM pur — d'où
 * `@GraphicsMode(NATIVE)` explicite : le mode par défaut de ce projet
 * (`LEGACY`, ni déclaré en dur ni documenté ailleurs) stub
 * `Paint.measureText()` sur une valeur constante, indépendante de la
 * taille de police demandée. Un ancien diagnostic (session précédente)
 * avait conclu à tort à une restriction de chargement de bibliothèque
 * native propre au bac à sable d'exécution — infirmé : la lib native et
 * les données ICU se chargent normalement, `@GraphicsMode(NATIVE)` seul
 * suffit à obtenir des métriques réelles (vérifié directement sur
 * `android.graphics.Paint.measureText()`, hors Compose : constant sans
 * l'annotation, proportionnel à la taille avec).
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
    fun `l annotatedString concatene les phrases dans l ordre en preservant les paragraphes`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = "Chapitre 1",
            content = ChapterContent.Legacy(
                paragraphs = listOf(
                    Paragraph(index = 0, sentences = listOf(sentence(0, "Titre.", 0)), style = ParagraphStyle.HEADING),
                    Paragraph(
                        index = 1,
                        sentences = listOf(
                            sentence(1, "Première phrase.", 10),
                            sentence(2, "Deuxième phrase.", 30),
                        ),
                        style = ParagraphStyle.NORMAL,
                    ),
                ),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals("Titre.\nPremière phrase. Deuxième phrase.", result.annotatedString.text)
    }

    @Test
    fun `les offsets locaux de phrase correspondent au texte mesure, dans les deux sens`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Legacy(
                paragraphs = listOf(
                    Paragraph(
                        index = 0,
                        sentences = listOf(
                            sentence(0, "Alpha.", 4),
                            sentence(1, "Beta.", 19),
                            sentence(2, "Gamma.", 41),
                        ),
                        style = ParagraphStyle.NORMAL,
                    ),
                ),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)
        val text = result.annotatedString.text

        // Sens 1 : offset local -> texte de la phrase qu'il désigne.
        assertEquals("Alpha.", text.substring(result.sentenceStartOffsets[0], result.sentenceStartOffsets[1] - 1))
        assertEquals("Beta.", text.substring(result.sentenceStartOffsets[1], result.sentenceStartOffsets[2] - 1))
        assertTrue(text.substring(result.sentenceStartOffsets[2]).startsWith("Gamma."))

        // Sens 2 : ces offsets locaux (0, 7, 13) ne doivent PAS coïncider
        // avec les startOffset de la ressource EPUB d'origine (4, 19, 41
        // dans ce fixture) - les réutiliser directement serait exactement
        // le bug d'espace de coordonnées que ChapterTextMeasurer évite.
        val resourceOffsets = chapter.paragraphs.flatMap { it.sentences }.map { it.startOffset }
        assertNotEquals(resourceOffsets, result.sentenceStartOffsets)
    }

    @Test
    fun `un titre HEADING produit une ligne plus haute qu un paragraphe NORMAL a texte egal`() {
        val text = "Un texte assez long pour occuper une ligne entiere."
        val headingChapter = Chapter(
            index = 0,
            href = "h.xhtml",
            title = null,
            content = ChapterContent.Legacy(paragraphs = listOf(Paragraph(0, listOf(sentence(0, text, 0)), ParagraphStyle.HEADING))),
        )
        val normalChapter = Chapter(
            index = 0,
            href = "n.xhtml",
            title = null,
            content = ChapterContent.Legacy(paragraphs = listOf(Paragraph(0, listOf(sentence(0, text, 0)), ParagraphStyle.NORMAL))),
        )

        val style = TextStyle(fontSize = 18.sp)
        val headingResult = measurer.measure(headingChapter, style, maxWidthPx = 2000)
        val normalResult = measurer.measure(normalChapter, style, maxWidthPx = 2000)

        val headingLineHeight = headingResult.lines[0].bottom - headingResult.lines[0].top
        val normalLineHeight = normalResult.lines[0].bottom - normalResult.lines[0].top
        assertTrue(
            "un HEADING (1.25em, gras) doit mesurer plus haut qu'un NORMAL - sinon 3a.1 " +
                "retomberait sur une hauteur de ligne constante malgré la mesure réelle",
            headingLineHeight > normalLineHeight,
        )
    }

    @Test
    fun `chapitre vide produit un AnnotatedString vide et aucune ligne`() {
        val chapter = Chapter(index = 0, href = "e.xhtml", title = null, content = ChapterContent.Legacy(paragraphs = emptyList()))

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals("", result.annotatedString.text)
        assertTrue(result.lines.isEmpty())
        assertTrue(result.sentenceStartOffsets.isEmpty())
    }
}
