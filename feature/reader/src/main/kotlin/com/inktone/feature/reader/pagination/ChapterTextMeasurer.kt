package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.inktone.domain.model.Chapter

/**
 * Résultat de la mesure d'un chapitre entier. `annotatedString` est
 * construit une seule fois ici et réutilisé tel quel par le rendu du
 * mode pagé (3a.2) — tranché par offset selon la page, jamais reconstruit
 * par page ni par mot prononcé (voir l'exigence de coût de recomposition
 * de 3a.2). `sentenceStartOffsets[i]` est l'offset, dans ce même
 * `annotatedString`, où commence la phrase d'index global `i` (même
 * indexation que `chapter.paragraphs.flatMap { it.sentences }`).
 */
data class ChapterMeasurement(
    val annotatedString: AnnotatedString,
    val lines: List<LineGeometry>,
    val sentenceStartOffsets: List<Int>,
)

/**
 * Adaptateur Compose du moteur de pagination (Tâche 3a.1, étapes 1-4) :
 * construit l'AnnotatedString du chapitre en préservant les
 * `ParagraphStyle` réels de l'EPUB (voir `spanStyleFor` — 3a.2 restaure
 * leur rendu, les conserver ici évite de les reperdre au passage),
 * mesure via `TextMeasurer` avec le `TextStyle` réellement appliqué au
 * rendu, puis extrait la géométrie ligne par ligne du `TextLayoutResult`
 * pour le moteur pur (`VirtualPaginationEngine`).
 *
 * Écart déclaré (3a.1) : cet adaptateur dépend de `TextMeasurer`
 * (Compose UI / mesure de police Android réelle), donc non exécutable en
 * test JVM pur — ni Robolectric ni test instrumenté ne sont configurés
 * sur `feature/reader` aujourd'hui. La logique de découpage elle-même
 * (`VirtualPaginationEngine`) est testée en JVM sur des fixtures de
 * `LineGeometry` (Tâche 3a.4) ; ce fichier n'est couvert que par les
 * tests Compose listés en 3a.4 (à écrire en 3a.2, quand ce mesureur sera
 * réellement branché au rendu).
 */
class ChapterTextMeasurer(private val textMeasurer: TextMeasurer) {

    fun measure(chapter: Chapter, baseStyle: TextStyle, maxWidthPx: Int): ChapterMeasurement {
        val sentenceStartOffsets = mutableListOf<Int>()
        val annotatedString = buildAnnotatedString {
            chapter.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                if (paragraphIndex > 0) append("\n")
                paragraph.sentences.forEachIndexed { indexInParagraph, sentence ->
                    if (indexInParagraph > 0) append(" ")
                    sentenceStartOffsets.add(length)
                    withStyle(spanStyleFor(paragraph.style)) {
                        append(sentence.text)
                    }
                }
            }
        }

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
}
