package com.inktone.feature.reader.pagination

/**
 * Contrat du moteur de pagination virtuelle (Tâche 3a.1). Indépendant du
 * mode de rendu — défilement ou pagé (Blueprint, décision actée en amont
 * du lot 3a) : ce moteur calcule des frontières de page à partir du
 * texte, du style et des dimensions du viewport, sans savoir comment le
 * résultat sera affiché.
 *
 * `pageIndexAt` est la seule opération qui compte pour l'ancrage de la
 * position de lecture : la position persistée reste toujours l'index de
 * phrase (`Locator`), jamais un index de page — ce dernier n'a de sens
 * que pour un couple (style, viewport) donné.
 */
interface VirtualPagination {
    fun pageCount(chapterIndex: Int): Int
    fun pageIndexAt(chapterIndex: Int, sentenceIndex: Int): Int
    fun sentenceRangeOf(chapterIndex: Int, pageIndex: Int): IntRange
}

/**
 * Géométrie réelle d'une ligne de texte layoutée — issue de
 * `TextLayoutResult.getLineTop`/`getLineBottom`/`getLineStart`/`getLineEnd`
 * côté production (voir `ChapterTextMeasurer`), mais sans dépendance
 * Compose ici : c'est ce qui permet au moteur de pagination de rester
 * testable en JVM pur (Tâche 3a.4).
 */
data class LineGeometry(
    val top: Float,
    val bottom: Float,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Clé d'invalidation du cache de pagination (Tâche 3a.1). Recalculer si
 * et seulement si l'une de ces valeurs change. Le thème est
 * délibérément absent : les couleurs ne déplacent pas le texte.
 */
data class PaginationStyleKey(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val fontFamilyKey: String,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val paddingPx: Int,
)
