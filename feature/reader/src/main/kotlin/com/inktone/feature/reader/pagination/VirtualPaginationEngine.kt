package com.inktone.feature.reader.pagination

/**
 * Implémentation du moteur de pagination virtuelle (Tâche 3a.1).
 * Ne dépend que de géométrie de ligne déjà mesurée (`LineGeometry`) et
 * des offsets de début de chaque phrase **dans ce même repère** — la
 * mesure réelle du texte (`TextMeasurer`, `AnnotatedString`) est
 * produite en amont par `ChapterTextMeasurer`, côté Compose. Cette
 * séparation est ce qui rend le calcul de pagination testable en JVM pur
 * (Tâche 3a.4), sans dépendre d'un runtime Android pour mesurer des
 * polices.
 *
 * Volontairement pas de dépendance à `domain.model.Sentence` ici : ses
 * `startOffset`/`endOffset` sont exprimés dans la ressource EPUB
 * d'origine, pas dans le texte concaténé mesuré (qui insère ses propres
 * séparateurs entre phrases/paragraphes). Réutiliser directement
 * `Sentence.startOffset` serait un bug d'espace de coordonnées — l'appelant
 * (`ChapterTextMeasurer`) doit fournir les offsets locaux au texte qu'il
 * a lui-même construit.
 *
 * Découpage : accumulation des hauteurs de ligne réelles jusqu'à
 * dépasser la hauteur utile du viewport, jamais une hauteur de ligne
 * constante (3a.1, point 3). Une fois les frontières de page obtenues en
 * offsets de caractère, les phrases sont réparties par leur offset de
 * début : une phrase appartient entièrement à la page où elle commence,
 * même si son rendu visuel déborde sur la page suivante à cause d'une
 * coupure de ligne à l'intérieur de la phrase. C'est ce qui garantit une
 * partition stricte des phrases entre pages (Tâche 3a.4, test 6) —
 * l'ancrage de position (`pageIndexAt`) reste ainsi non ambigu.
 */
class VirtualPaginationEngine : VirtualPagination {

    private data class ChapterEntry(
        val styleKey: PaginationStyleKey,
        val pages: List<IntRange>,
    )

    private val cache = mutableMapOf<Int, ChapterEntry>()

    /**
     * Recalcule la pagination du chapitre si la clé de style a changé
     * depuis le dernier appel. Aucun effet si le cache est déjà à jour
     * pour ce chapitre — c'est l'invalidation décrite en 3a.1.
     *
     * Retourne `true` si un recalcul a effectivement eu lieu (utile pour
     * vérifier l'invalidation, Tâche 3a.4 test 7).
     */
    fun updateChapter(
        chapterIndex: Int,
        styleKey: PaginationStyleKey,
        lines: List<LineGeometry>,
        sentenceStartOffsets: List<Int>,
    ): Boolean {
        val existing = cache[chapterIndex]
        if (existing != null && existing.styleKey == styleKey) return false
        val pages = computePages(lines, sentenceStartOffsets, styleKey.viewportHeightPx.toFloat())
        cache[chapterIndex] = ChapterEntry(styleKey, pages)
        return true
    }

    override fun pageCount(chapterIndex: Int): Int =
        cache[chapterIndex]?.pages?.size?.coerceAtLeast(1) ?: 1

    override fun pageIndexAt(chapterIndex: Int, sentenceIndex: Int): Int {
        val pages = cache[chapterIndex]?.pages ?: return 0
        if (pages.isEmpty()) return 0
        val found = pages.indexOfFirst { sentenceIndex in it }
        if (found >= 0) return found
        return if (sentenceIndex < (pages.firstOrNull()?.first ?: 0)) 0 else pages.lastIndex
    }

    override fun sentenceRangeOf(chapterIndex: Int, pageIndex: Int): IntRange =
        cache[chapterIndex]?.pages?.getOrNull(pageIndex) ?: IntRange.EMPTY

    private fun computePages(
        lines: List<LineGeometry>,
        sentenceStartOffsets: List<Int>,
        viewportHeightPx: Float,
    ): List<IntRange> {
        if (lines.isEmpty() || sentenceStartOffsets.isEmpty()) return listOf(IntRange.EMPTY)
        val pageEndOffsets = computePageEndOffsets(lines, viewportHeightPx)
        return computeSentenceRanges(sentenceStartOffsets, pageEndOffsets)
    }

    private fun computePageEndOffsets(lines: List<LineGeometry>, viewportHeightPx: Float): List<Int> {
        val boundaries = mutableListOf<Int>()
        var pageTop = lines.first().top
        var linesOnPage = 0
        for (i in lines.indices) {
            val line = lines[i]
            val heightIfAdded = line.bottom - pageTop
            if (heightIfAdded > viewportHeightPx && linesOnPage > 0) {
                boundaries.add(lines[i - 1].endOffset)
                pageTop = line.top
                linesOnPage = 0
            }
            linesOnPage++
        }
        boundaries.add(lines.last().endOffset)
        return boundaries
    }

    private fun computeSentenceRanges(sentenceStartOffsets: List<Int>, pageEndOffsets: List<Int>): List<IntRange> {
        val lastSentenceIndex = sentenceStartOffsets.lastIndex
        if (pageEndOffsets.isEmpty()) return listOf(0..lastSentenceIndex)

        val ranges = mutableListOf<IntRange>()
        var cursor = 0
        for (pageIndex in pageEndOffsets.indices) {
            if (cursor > lastSentenceIndex) continue
            val isLastPage = pageIndex == pageEndOffsets.lastIndex
            val boundary = pageEndOffsets[pageIndex]
            val pageStart = cursor
            while (cursor <= lastSentenceIndex && (isLastPage || sentenceStartOffsets[cursor] < boundary)) {
                cursor++
            }
            if (cursor == pageStart) {
                // Garantit la progression : au moins une phrase par page tant
                // qu'il en reste, même si la frontière de ligne tombe avant le
                // début de la phrase suivante (page très courte).
                cursor++
            }
            ranges.add(pageStart..(cursor - 1))
        }
        if (cursor <= lastSentenceIndex) {
            val last = ranges.removeAt(ranges.lastIndex)
            ranges.add(last.first..lastSentenceIndex)
        }
        return ranges
    }
}
