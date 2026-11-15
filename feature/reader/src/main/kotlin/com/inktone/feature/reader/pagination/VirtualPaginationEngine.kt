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
 * coupure de ligne à l'intérieur de la phrase. Cette règle d'affectation
 * garantit *par construction* qu'il n'y a jamais de recouvrement entre
 * pages au niveau des phrases — un test qui se contente de le vérifier
 * est donc tautologique, pas un garde-fou. Le vrai garde-fou est sur les
 * lignes, seul niveau où l'algorithme peut réellement se tromper : voir
 * `computePageLineRanges`.
 */
class VirtualPaginationEngine : VirtualPagination {

    private data class ChapterEntry(
        val styleKey: PaginationStyleKey,
        val pages: List<IntRange>,
        val pageOffsetRanges: List<IntRange>,
        /** Index d'image par page (voir [ImageAnchor]), `null` pour une page de texte. */
        val pageImages: List<Int?>,
    )

    private val cache = mutableMapOf<Int, ChapterEntry>()

    /**
     * Recalcule la pagination du chapitre si la clé de style a changé
     * depuis le dernier appel. Aucun effet si le cache est déjà à jour
     * pour ce chapitre — c'est l'invalidation décrite en 3a.1.
     *
     * [force] outrepasse ce court-circuit à clé de style égale — la
     * mesure en deux temps de 3a.3 appelle plusieurs fois `updateChapter`
     * pour le **même** chapitre et la **même** clé de style (page 1
     * immédiate, puis chapitre complet une fois mesuré en arrière-plan) :
     * sans `force`, le deuxième appel serait vu comme un cache hit et
     * silencieusement ignoré.
     *
     * Retourne `true` si un recalcul a effectivement eu lieu (utile pour
     * vérifier l'invalidation, Tâche 3a.4 test 7).
     */
    fun updateChapter(
        chapterIndex: Int,
        styleKey: PaginationStyleKey,
        lines: List<LineGeometry>,
        sentenceStartOffsets: List<Int>,
        force: Boolean = false,
        imageAnchors: List<ImageAnchor> = emptyList(),
    ): Boolean {
        val existing = cache[chapterIndex]
        if (!force && existing != null && existing.styleKey == styleKey) return false
        val computed = computePages(lines, sentenceStartOffsets, styleKey.viewportHeightPx.toFloat(), imageAnchors)
        cache[chapterIndex] = ChapterEntry(styleKey, computed.sentenceRanges, computed.offsetRanges, computed.images)
        return true
    }

    override fun pageCount(chapterIndex: Int): Int =
        cache[chapterIndex]?.pages?.size?.coerceAtLeast(1) ?: 1

    override fun pageIndexAt(chapterIndex: Int, sentenceIndex: Int): Int {
        val pages = cache[chapterIndex]?.pages ?: return 0
        if (pages.isEmpty()) return 0
        // Le début du chapitre est sa première page, illustration d'ouverture
        // comprise : sinon l'ouvrir ou y arriver par un swipe la sauterait (D48).
        if (sentenceIndex == 0) return 0
        val found = pages.indexOfFirst { sentenceIndex in it }
        if (found >= 0) return found
        return if (sentenceIndex < (pages.firstOrNull { !it.isEmpty() }?.first ?: 0)) 0 else pages.lastIndex
    }

    /** Index de l'image affichée par cette page (voir [ImageAnchor]), `null` pour une page de texte. */
    fun pageImageIndex(chapterIndex: Int, pageIndex: Int): Int? =
        cache[chapterIndex]?.pageImages?.getOrNull(pageIndex)

    override fun sentenceRangeOf(chapterIndex: Int, pageIndex: Int): IntRange =
        cache[chapterIndex]?.pages?.getOrNull(pageIndex) ?: IntRange.EMPTY

    /**
     * Fenêtre de caractères `[start, end]`, dans le repère de
     * l'`AnnotatedString` mesuré par `ChapterTextMeasurer`, couverte par
     * cette page. Utilisée par le rendu (3a.2) pour trancher
     * l'`AnnotatedString` du chapitre et pour déterminer, pendant le TTS,
     * si le mot en cours de lecture est physiquement sur cette page —
     * y compris quand sa phrase déborde sur la page suivante (voir la
     * KDoc de classe).
     */
    fun pageOffsetRange(chapterIndex: Int, pageIndex: Int): IntRange =
        cache[chapterIndex]?.pageOffsetRanges?.getOrNull(pageIndex) ?: IntRange.EMPTY

    /** Index de la page dont la fenêtre d'offsets contient [charOffset], ou -1 si aucune. */
    fun pageIndexAtOffset(chapterIndex: Int, charOffset: Int): Int =
        cache[chapterIndex]?.pageOffsetRanges?.indexOfFirst { charOffset in it } ?: -1

    private data class ComputedPages(
        val sentenceRanges: List<IntRange>,
        val offsetRanges: List<IntRange>,
        val images: List<Int?>,
    )

    /**
     * Pages de texte coupées à chaque [ImageAnchor], une page par image
     * intercalée à sa place. Une page d'image n'a ni phrase ni plage
     * d'offsets : le texte, ses offsets et la partition des phrases restent
     * exactement ceux d'un chapitre sans image.
     */
    private fun computePages(
        lines: List<LineGeometry>,
        sentenceStartOffsets: List<Int>,
        viewportHeightPx: Float,
        imageAnchors: List<ImageAnchor>,
    ): ComputedPages {
        val hasText = lines.isNotEmpty() && sentenceStartOffsets.isNotEmpty()
        if (!hasText && imageAnchors.isEmpty()) {
            return ComputedPages(listOf(IntRange.EMPTY), listOf(IntRange.EMPTY), listOf(null))
        }
        if (imageAnchors.isEmpty()) {
            val pageLineRanges = computePageLineRanges(lines, viewportHeightPx)
            val offsetRanges = pageLineRanges.map { range -> lines[range.first].startOffset..lines[range.last].endOffset }
            val sentenceRanges = computeSentenceRanges(sentenceStartOffsets, offsetRanges.map { it.last })
            return ComputedPages(sentenceRanges, offsetRanges, sentenceRanges.map { null })
        }
        // Séquence des pages : un index dans `textPageLines`, ou une image.
        val textPageLines = mutableListOf<IntRange>()
        val sequence = mutableListOf<Int?>()
        val sequenceImages = mutableListOf<Int?>()
        fun addText(fromLine: Int, toLineExclusive: Int) {
            if (!hasText || fromLine >= toLineExclusive) return
            computePageLineRanges(lines.subList(fromLine, toLineExclusive), viewportHeightPx).forEach { range ->
                sequence.add(textPageLines.size)
                sequenceImages.add(null)
                textPageLines.add((range.first + fromLine)..(range.last + fromLine))
            }
        }
        var lineCursor = 0
        for (anchor in imageAnchors.sortedBy { it.textOffset }) {
            var segmentEnd = lineCursor
            if (hasText) {
                while (segmentEnd < lines.size && lines[segmentEnd].startOffset < anchor.textOffset) segmentEnd++
            }
            addText(lineCursor, segmentEnd)
            lineCursor = segmentEnd
            sequence.add(null)
            sequenceImages.add(anchor.imageIndex)
        }
        addText(lineCursor, lines.size)

        val textOffsetRanges = textPageLines.map { range -> lines[range.first].startOffset..lines[range.last].endOffset }
        val textSentenceRanges = if (hasText) {
            computeSentenceRanges(sentenceStartOffsets, textOffsetRanges.map { it.last })
        } else {
            emptyList()
        }
        // Comme sans image : une page de texte sans début de phrase (fin d'une
        // très longue dernière phrase) n'est pas comptée.
        val kept = sequence.indices.filter { i -> sequence[i].let { it == null || it < textSentenceRanges.size } }
        return ComputedPages(
            sentenceRanges = kept.map { i -> sequence[i]?.let { textSentenceRanges[it] } ?: IntRange.EMPTY },
            offsetRanges = kept.map { i -> sequence[i]?.let { textOffsetRanges[it] } ?: IntRange.EMPTY },
            images = kept.map { i -> sequenceImages[i] },
        )
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

/**
 * Découpe les lignes mesurées en pages par accumulation de hauteur
 * réelle (3a.1, point 3) — le cœur du moteur, isolé en fonction pure
 * top-level pour être testé directement sur la géométrie de ligne, sans
 * passer par la partition des phrases qui en découle. C'est le garde-fou
 * principal du lot (Tâche 3a.4, test 6 révisé) : la partition des
 * phrases par offset de début est correcte *par construction* de
 * l'algorithme d'affectation, donc ne peut plus jamais échouer une fois
 * qu'on lui fait confiance — un test qui ne porte que sur elle ne garde
 * plus rien. Le découpage en lignes, lui, peut réellement se tromper
 * (page trop pleine, page à moitié vide, ligne perdue) : c'est lui qu'il
 * faut vérifier.
 */
internal fun computePageLineRanges(lines: List<LineGeometry>, viewportHeightPx: Float): List<IntRange> {
    if (lines.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var pageStart = 0
    var pageTop = lines[0].top
    for (i in lines.indices) {
        val heightIfAdded = lines[i].bottom - pageTop
        if (heightIfAdded > viewportHeightPx && i > pageStart) {
            ranges.add(pageStart until i)
            pageStart = i
            pageTop = lines[i].top
        }
    }
    ranges.add(pageStart..lines.lastIndex)
    return ranges
}
