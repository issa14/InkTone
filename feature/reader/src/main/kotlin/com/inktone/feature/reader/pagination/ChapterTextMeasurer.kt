package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.inktone.domain.model.Chapter

/**
 * Résultat de la mesure d'un chapitre — entier ou d'un préfixe borné
 * (voir `measureFirstPage`). `annotatedString` est réutilisé tel quel
 * par le rendu du mode pagé (3a.2) — tranché par offset selon la page,
 * jamais reconstruit par page ni par mot prononcé (voir l'exigence de
 * coût de recomposition de 3a.2). `sentenceStartOffsets[i]` est
 * l'offset, dans ce même `annotatedString`, où commence la phrase
 * d'index global `i` (même indexation que
 * `chapter.paragraphs.flatMap { it.sentences }`) — sa taille indique
 * combien de phrases, depuis le début du chapitre, sont couvertes par
 * cette mesure : moins que le total pour un préfixe, tout le chapitre
 * sinon.
 */
data class ChapterMeasurement(
    val annotatedString: AnnotatedString,
    val lines: List<LineGeometry>,
    val sentenceStartOffsets: List<Int>,
)

/**
 * Adaptateur Compose du moteur de pagination (Tâche 3a.1, étapes 1-4) :
 * construit l'AnnotatedString du chapitre (ou d'un préfixe, 3a.3) en
 * préservant les `ParagraphStyle` réels de l'EPUB (voir `spanStyleFor` —
 * 3a.2 restaure leur rendu, les conserver ici évite de les reperdre au
 * passage), mesure via `TextMeasurer` avec le `TextStyle` réellement
 * appliqué au rendu, puis extrait la géométrie ligne par ligne du
 * `TextLayoutResult` pour le moteur pur (`VirtualPaginationEngine`).
 *
 * Écart déclaré (3a.1, toujours vrai en 3a.3) : cet adaptateur dépend de
 * `TextMeasurer` (Compose UI / mesure de police Android réelle), donc
 * non exécutable en test JVM pur. Couvert par `ChapterTextMeasurerTest`
 * (Robolectric) — voir son KDoc pour un écart d'environnement distinct
 * (métriques de police dégénérées dans le sandbox de développement).
 */
class ChapterTextMeasurer(private val textMeasurer: TextMeasurer) {

    /** Mesure le chapitre entier. Coût proportionnel à sa longueur — voir `measureFirstPage` pour l'ouverture (3a.3). */
    fun measure(chapter: Chapter, baseStyle: TextStyle, maxWidthPx: Int): ChapterMeasurement {
        val (annotatedString, sentenceStartOffsets) = buildAnnotatedText(chapter)
        return measureBuilt(annotatedString, sentenceStartOffsets, baseStyle, maxWidthPx)
    }

    /**
     * Mesure seulement un préfixe borné du chapitre (Tâche 3a.3) — peu
     * coûteux car indépendant de la longueur totale du chapitre, ce qui
     * permet de l'appeler de façon synchrone/quasi immédiate en
     * composition pour afficher la première page sans reflux ni écran
     * vide, pendant que le reste se mesure en arrière-plan
     * (`measure` complet, sur `Dispatchers.Default`).
     *
     * [prefixCharBudget] est une borne heuristique, pas un calcul exact
     * de « ce qui remplit le viewport » : l'API `TextMeasurer` ne permet
     * pas d'arrêter la mesure en cours de layout, seule la **taille du
     * texte fourni en entrée** borne son coût. Une valeur généreuse
     * (6000 caractères par défaut) couvre confortablement une page dans
     * l'immense majorité des tailles de police et de viewport réalistes
     * — assez pour rester bon marché sans dépendre de la longueur du
     * chapitre. L'appelant élargit ce budget par doublements successifs
     * tant que la phrase visée (reprise de lecture en milieu de
     * chapitre) n'est pas encore couverte, voir `PagedChapterContent`.
     */
    fun measureFirstPage(
        chapter: Chapter,
        baseStyle: TextStyle,
        maxWidthPx: Int,
        prefixCharBudget: Int = DEFAULT_PREFIX_CHAR_BUDGET,
    ): ChapterMeasurement {
        val (annotatedString, sentenceStartOffsets) = buildAnnotatedText(chapter, maxChars = prefixCharBudget)
        return measureBuilt(annotatedString, sentenceStartOffsets, baseStyle, maxWidthPx)
    }

    private fun buildAnnotatedText(chapter: Chapter, maxChars: Int = Int.MAX_VALUE): Pair<AnnotatedString, List<Int>> {
        val sentenceStartOffsets = mutableListOf<Int>()
        val annotatedString = buildAnnotatedString paragraphs@{
            for ((paragraphIndex, paragraph) in chapter.paragraphs.withIndex()) {
                if (length >= maxChars) return@paragraphs
                if (paragraphIndex > 0) append("\n")
                for ((indexInParagraph, sentence) in paragraph.sentences.withIndex()) {
                    if (indexInParagraph > 0) append(" ")
                    sentenceStartOffsets.add(length)
                    withStyle(spanStyleFor(paragraph.style)) {
                        append(sentence.text)
                    }
                    // On s'arrête après une phrase entière, jamais au
                    // milieu : sentenceStartOffsets doit toujours décrire
                    // des phrases complètement présentes dans le texte.
                    if (length >= maxChars) return@paragraphs
                }
            }
        }
        return annotatedString to sentenceStartOffsets
    }

    private fun measureBuilt(
        annotatedString: AnnotatedString,
        sentenceStartOffsets: List<Int>,
        baseStyle: TextStyle,
        maxWidthPx: Int,
    ): ChapterMeasurement {
        if (annotatedString.text.isEmpty()) {
            return ChapterMeasurement(annotatedString, emptyList(), sentenceStartOffsets)
        }

        val layoutResult = textMeasurer.measure(
            text = annotatedString,
            style = baseStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
        )

        val lines = (0 until layoutResult.lineCount).map { lineIndex ->
            LineGeometry(
                top = layoutResult.getLineTop(lineIndex),
                bottom = layoutResult.getLineBottom(lineIndex),
                startOffset = layoutResult.getLineStart(lineIndex),
                endOffset = layoutResult.getLineEnd(lineIndex, visibleEnd = true),
            )
        }

        return ChapterMeasurement(annotatedString, lines, sentenceStartOffsets)
    }

    private companion object {
        const val DEFAULT_PREFIX_CHAR_BUDGET = 6000
    }
}
