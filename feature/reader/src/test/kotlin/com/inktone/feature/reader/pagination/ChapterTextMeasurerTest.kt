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
 * de police Android, indisponibles en JVM pur.
 *
 * Écart déclaré : dans le sandbox où cette suite a été écrite, le
 * chargement de la lib native Robolectric semble restreint et les
 * métriques de police y sont dégénérées (constantes, indépendantes de la
 * taille demandée — vérifié directement sur `android.graphics.Paint`,
 * hors Compose). Les tests qui ne dépendent que de la construction du
 * texte (concaténation, offsets) restent valides. Celui qui dépend d'une
 * vraie mesure (hauteur de ligne HEADING vs NORMAL) est correct en
 * principe mais doit être vérifié vert sur une machine sans cette
 * restriction avant de considérer la tâche close — voir son KDoc.
 */
@RunWith(RobolectricTestRunner::class)
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
            paragraphs = listOf(
                Paragraph(
                    index = 0,
                    sentences = listOf(
                        // Offsets ressource choisis pour ne PAS coïncider avec
                        // les offsets locaux qu'ils produiront (0, 7, 13) — sinon
                        // le test ne distinguerait pas les deux espaces de
                        // coordonnées par coïncidence numérique.
                        sentence(0, "Alpha.", 4),
                        sentence(1, "Beta.", 19),
                        sentence(2, "Gamma.", 41),
                    ),
                    style = ParagraphStyle.NORMAL,
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

    /**
     * Écart déclaré (revue du lot, post-3a.1) : dans le sandbox où cette
     * tâche a été développée, `android.graphics.Paint().measureText()`
     * renvoie une valeur **constante quelle que soit `textSize`**, et
     * `Paint().typeface` est `null` — vérifié en descendant sous Compose,
     * directement sur l'API Android brute. La lib native Robolectric
     * (`librobolectric-nativeruntime.so`, présente et de la bonne
     * architecture dans le jar `nativeruntime-dist-compat`) ne s'active
     * donc pas dans cet environnement précis, probablement une
     * restriction de chargement de bibliothèque native propre au
     * bac à sable d'exécution de l'agent — pas une machine de
     * développement ni une CI standard.
     *
     * Ce test est donc correct en principe et **doit être vérifié vert
     * sur une machine où le chargement natif n'est pas restreint** avant
     * de considérer la tâche 3a.1/3a.2 close — décision actée : le garder
     * actif plutôt que l'ignorer silencieusement, pour qu'un environnement
     * fonctionnel le fasse échouer bruyamment s'il régresse un jour, au
     * lieu de rester invisible.
     */
    @Test
    fun `un titre HEADING produit une ligne plus haute qu un paragraphe NORMAL a texte egal`() {
        val text = "Un texte assez long pour occuper une ligne entiere."
        val headingChapter = Chapter(
            index = 0,
            href = "h.xhtml",
            title = null,
            paragraphs = listOf(Paragraph(0, listOf(sentence(0, text, 0)), ParagraphStyle.HEADING)),
        )
        val normalChapter = Chapter(
            index = 0,
            href = "n.xhtml",
            title = null,
            paragraphs = listOf(Paragraph(0, listOf(sentence(0, text, 0)), ParagraphStyle.NORMAL)),
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
        val chapter = Chapter(index = 0, href = "e.xhtml", title = null, paragraphs = emptyList())

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals("", result.annotatedString.text)
        assertTrue(result.lines.isEmpty())
        assertTrue(result.sentenceStartOffsets.isEmpty())
    }
}
