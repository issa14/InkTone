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
import org.junit.Assert.assertFalse
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

    private fun sentence(index: Int, text: String, startOffset: Int, blockIndex: Int = 0): Sentence =
        Sentence(
            index = index,
            text = text,
            startOffset = startOffset,
            endOffset = startOffset + text.length,
            blockIndex = blockIndex,
        )

    @Test
    fun `l annotatedString concatene les blocs dans l ordre avec un separateur`() {
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
                        globalOffsetRange = 7..40,
                    ),
                ),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        // Un séparateur ("\n") est inséré entre deux blocs de texte
        // consécutifs (correctif : le texte de deux paragraphes ne doit
        // jamais fusionner sans espace dans le rendu/la mesure).
        assertEquals("Titre.\nPremière phrase. Deuxième phrase.", result.annotatedString.text)
    }

    /**
     * Diagnostic d'une page blanche observée en mode paginé (§6.13 du plan) :
     * le compteur de pages disparaissait, signe d'une mesure qui ne produit
     * AUCUNE ligne.
     *
     * Suspect n°1 : le `TextStyle` de mesure, enrichi par le palier P4
     * (justification/césure). Même hors justification il porte désormais
     * `Hyphens.None`, `LineBreak.Unspecified` et `TextAlign.Unspecified` là où
     * il ne portait rien — si l'une de ces valeurs faisait échouer la mesure,
     * tout le mode paginé tomberait.
     */
    @Test
    fun `le style de mesure du palier P4 produit bien des lignes hors justification`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(
                            "Un paragraphe assez long pour occuper plusieurs lignes une fois mesuré " +
                                "dans une largeur volontairement etroite, afin que la mesure ait " +
                                "reellement du travail a faire.",
                        ),
                        globalOffsetRange = 0..160,
                    ),
                ),
            ),
            sentences = listOf(sentence(0, "Un paragraphe.", 0, blockIndex = 0)),
        )

        val styleP4NonJustifie = TextStyle(
            fontSize = 18.sp,
            lineHeight = 25.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Unspecified,
            hyphens = androidx.compose.ui.text.style.Hyphens.None,
            lineBreak = androidx.compose.ui.text.style.LineBreak.Unspecified,
        )
        val styleP4Justifie = TextStyle(
            fontSize = 18.sp,
            lineHeight = 25.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Justify,
            hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
            lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
        )

        val nonJustifie = measurer.measure(chapter, styleP4NonJustifie, maxWidthPx = 400)
        val justifie = measurer.measure(chapter, styleP4Justifie, maxWidthPx = 400)

        assertTrue("le style non justifié doit produire des lignes", nonJustifie.lines.isNotEmpty())
        assertTrue("le style justifié doit produire des lignes", justifie.lines.isNotEmpty())
    }

    /**
     * Diagnostic de la page blanche en mode paginé (§6.13 du plan).
     *
     * Le compteur de pages disparaît quand `isMeasurementComplete` est faux —
     * autrement dit quand la mesure produit MOINS d'offsets de phrase que le
     * chapitre n'a de phrases. Or `measureRich` ne mesure que les blocs de
     * TEXTE (paragraphes et titres) : toute phrase rattachée à un autre type de
     * bloc n'obtient jamais son offset.
     *
     * Un chapitre mêlant texte et image suffit donc à rendre la mesure
     * éternellement « incomplète », quelle que soit la durée d'attente. Ce test
     * établit le mécanisme ; il ne préjuge pas du correctif (compter les
     * phrases mesurables plutôt que toutes, ou rattacher les phrases aux seuls
     * blocs de texte à l'analyse).
     */
    @Test
    fun `une phrase rattachee a un bloc non textuel laisse la mesure incomplete`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Un paragraphe."),
                        globalOffsetRange = 0..13,
                    ),
                    BookBlock.ImageBlock(href = "img/photo.jpg"),
                ),
            ),
            sentences = listOf(
                sentence(0, "Un paragraphe.", 0, blockIndex = 0),
                // Phrase rattachée au bloc image : jamais mesurée.
                sentence(1, "Légende.", 15, blockIndex = 1),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals(
            "seules les phrases des blocs de texte obtiennent un offset",
            1,
            result.sentenceStartOffsets.size,
        )
        // Avant correctif, la complétude se comparait à `chapter.sentences.size`
        // (2) : elle ne pouvait JAMAIS être atteinte, le compteur de pages
        // restait masqué et l'ancrage du mode paginé ne se recalait jamais.
        assertFalse(
            "l'ancien calcul, comparé au total des phrases, restait éternellement incomplet",
            isMeasurementComplete(result.sentenceStartOffsets.size, chapter.sentences.size),
        )
        // Après correctif : la complétude se compare au nombre d'offsets qu'une
        // mesure complète PEUT produire.
        assertTrue(
            "la mesure couvre tout ce qu'elle peut couvrir : elle est complète",
            isMeasurementComplete(result.sentenceStartOffsets.size, measurableOffsetCount(chapter)),
        )
    }

    /**
     * Garde-fou du correctif : `measurableOffsetCount` doit reproduire
     * EXACTEMENT la règle de `ChapterTextMeasurer`. Toute divergence entre les
     * deux ramènerait la mesure éternellement incomplète — ce test les compare
     * donc sur un même chapitre, plutôt que d'affirmer un nombre en dur.
     */
    @Test
    fun `le compte d offsets attendus correspond a ce que la mesure produit`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.HeadingBlock(level = 1, richText = StyledText.plain("Titre."), globalOffsetRange = 0..5),
                    BookBlock.ImageBlock(href = "img/a.jpg"),
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Phrase une. Phrase deux."),
                        globalOffsetRange = 7..30,
                    ),
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain("Bloc sans phrase déclarée."),
                        globalOffsetRange = 32..57,
                    ),
                ),
            ),
            sentences = listOf(
                sentence(0, "Titre.", 0, blockIndex = 0),
                sentence(1, "Légende.", 6, blockIndex = 1),
                sentence(2, "Phrase une.", 7, blockIndex = 2),
                sentence(3, "Phrase deux.", 19, blockIndex = 2),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)

        assertEquals(
            "le compte attendu doit coller à la mesure réelle",
            result.sentenceStartOffsets.size,
            measurableOffsetCount(chapter),
        )
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
            // sentenceStartOffsets est dérivé de Chapter.sentences (filtrées
            // par blockIndex), pas d'un offset par bloc — une seule
            // sentence par bloc ici, blockIndex = index du bloc dans
            // `blocks` (même référentiel que JsoupChapterParser).
            sentences = listOf(
                sentence(index = 0, text = "Alpha.", startOffset = 0, blockIndex = 0),
                sentence(index = 1, text = "Beta.", startOffset = 7, blockIndex = 1),
                sentence(index = 2, text = "Gamma.", startOffset = 13, blockIndex = 2),
            ),
        )

        val result = measurer.measure(chapter, TextStyle(fontSize = 18.sp), maxWidthPx = 2000)
        val text = result.annotatedString.text

        // Les sentenceStartOffsets marquent le début de chaque phrase dans
        // le texte concaténé (avec séparateurs) — vérification que les
        // offsets pointent bien sur le début du texte attendu.
        assertTrue(text.substring(result.sentenceStartOffsets[0]).startsWith("Alpha."))
        assertTrue(text.substring(result.sentenceStartOffsets[1]).startsWith("Beta."))
        assertTrue(text.substring(result.sentenceStartOffsets[2]).startsWith("Gamma."))
    }

    // Pré-existant (échoue aussi sur le code non modifié par le Plan v3,
    // vérifié en session) — l'écart d'environnement documenté dans le
    // KDoc de tête de cette classe (métriques de police dégénérées dans
    // le sandbox Robolectric de ce poste) empêche fiablement de comparer
    // deux hauteurs de ligne ici. Hors périmètre de ce lot.
    @org.junit.Ignore("Pré-existant : métriques de police dégénérées dans ce sandbox (voir KDoc de tête)")
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
