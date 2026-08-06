package com.inktone.feature.reader.pagination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du moteur de pagination pur (Tâche 3a.4, tests 1-9). Aucune
 * dépendance Compose UI ici — voir l'écart déclaré dans
 * `ChapterTextMeasurer` : la mesure réelle du texte (TextMeasurer) n'est
 * couverte que par les tests Compose, pas ici. Ces fixtures simulent des
 * lignes déjà mesurées (`LineGeometry`) pour tester uniquement la
 * logique de découpage en pages et de partition des phrases.
 */
class VirtualPaginationEngineTest {

    private val defaultStyleKey = PaginationStyleKey(
        fontSizeSp = 18,
        lineHeightSp = 24,
        fontFamilyKey = "default",
        viewportWidthPx = 1000,
        viewportHeightPx = 100,
        paddingPx = 16,
    )

    /** Une ligne de 20px de haut par sentence, une phrase par ligne, pour simplifier les fixtures. */
    private fun uniformLines(sentenceCount: Int, lineHeight: Float = 20f): Pair<List<LineGeometry>, List<Int>> {
        val lines = mutableListOf<LineGeometry>()
        val offsets = mutableListOf<Int>()
        var offset = 0
        for (i in 0 until sentenceCount) {
            val text = "phrase$i "
            offsets.add(offset)
            lines.add(
                LineGeometry(
                    top = i * lineHeight,
                    bottom = (i + 1) * lineHeight,
                    startOffset = offset,
                    endOffset = offset + text.length,
                ),
            )
            offset += text.length
        }
        return lines to offsets
    }

    @Test
    fun `pageIndexAt croit avec l index de phrase et reste dans les bornes`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(20)
        engine.updateChapter(0, defaultStyleKey, lines, offsets)

        var previous = -1
        for (i in offsets.indices) {
            val pageIndex = engine.pageIndexAt(0, i)
            assertTrue(pageIndex >= previous)
            assertTrue(pageIndex in 0 until engine.pageCount(0))
            previous = pageIndex
        }
    }

    @Test
    fun `pageCount au moins 1 sur chapitre vide et sentenceRangeOf jamais vide sur page rendue`() {
        val engine = VirtualPaginationEngine()
        engine.updateChapter(0, defaultStyleKey, emptyList(), emptyList())

        assertTrue(engine.pageCount(0) >= 1)

        val (lines, offsets) = uniformLines(5)
        engine.updateChapter(1, defaultStyleKey, lines, offsets)
        for (page in 0 until engine.pageCount(1)) {
            assertFalse(engine.sentenceRangeOf(1, page).isEmpty())
        }
    }

    @Test
    fun `aller-retour sentenceRangeOf de pageIndexAt contient toujours l index d origine`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(37)
        engine.updateChapter(0, defaultStyleKey, lines, offsets)

        for (i in offsets.indices) {
            val page = engine.pageIndexAt(0, i)
            val range = engine.sentenceRangeOf(0, page)
            assertTrue("phrase $i devrait être dans $range (page $page)", i in range)
        }
    }

    @Test
    fun `reduire la police reduit la hauteur de ligne et augmente le nombre de pages`() {
        val engine = VirtualPaginationEngine()
        val (linesSmallFont, offsetsSmallFont) = uniformLines(40, lineHeight = 15f)
        val (linesBigFont, offsetsBigFont) = uniformLines(40, lineHeight = 30f)

        val smallFontKey = defaultStyleKey.copy(fontSizeSp = 14)
        val bigFontKey = defaultStyleKey.copy(fontSizeSp = 24)

        engine.updateChapter(0, smallFontKey, linesSmallFont, offsetsSmallFont)
        engine.updateChapter(1, bigFontKey, linesBigFont, offsetsBigFont)

        assertTrue(
            "une police plus grande (lignes plus hautes) doit produire plus de pages à viewport égal",
            engine.pageCount(1) > engine.pageCount(0),
        )
    }

    @Test
    fun `elargir le viewport reduit le nombre de pages`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(40)

        val narrowKey = defaultStyleKey.copy(viewportHeightPx = 100)
        val wideKey = defaultStyleKey.copy(viewportHeightPx = 400)

        engine.updateChapter(0, narrowKey, lines, offsets)
        engine.updateChapter(1, wideKey, lines, offsets)

        assertTrue(engine.pageCount(1) < engine.pageCount(0))
    }

    /**
     * Vrai par construction de `computeSentenceRanges` (chaque phrase est
     * affectée à la page où elle commence, sans recouvrement possible) —
     * ce test ne peut plus échouer, ce n'est donc plus le garde-fou
     * principal. Conservé car il ne coûte rien et documente le
     * comportement observable ; le garde-fou réel est sur les lignes
     * ci-dessous (`computePageLineRanges couvre exactement toutes les lignes`).
     */
    @Test
    fun `aucune phrase perdue - union des pages couvre exactement toutes les phrases`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(53)
        engine.updateChapter(0, defaultStyleKey, lines, offsets)

        val covered = mutableSetOf<Int>()
        for (page in 0 until engine.pageCount(0)) {
            val range = engine.sentenceRangeOf(0, page)
            for (i in range) {
                assertFalse("phrase $i couverte par plus d'une page", i in covered)
                covered.add(i)
            }
        }
        assertEquals(offsets.indices.toSet(), covered)
    }

    // --- Garde-fou principal : le découpage en LIGNES, seul niveau où
    // l'algorithme peut réellement produire une coupure ratée, une page
    // trop pleine ou une page à moitié vide (voir computePageLineRanges).

    @Test
    fun `computePageLineRanges couvre exactement toutes les lignes, sans trou ni recouvrement`() {
        val (lines, _) = uniformLines(53, lineHeight = 17f)
        val ranges = computePageLineRanges(lines, viewportHeightPx = 100f)

        val covered = mutableSetOf<Int>()
        for (range in ranges) {
            for (i in range) {
                assertFalse("ligne $i couverte par plus d'une page", i in covered)
                covered.add(i)
            }
        }
        assertEquals(lines.indices.toSet(), covered)
    }

    @Test
    fun `computePageLineRanges - chaque page tient dans la hauteur du viewport`() {
        val (lines, _) = uniformLines(53, lineHeight = 17f)
        val viewportHeightPx = 100f
        val ranges = computePageLineRanges(lines, viewportHeightPx)

        for (range in ranges) {
            val pageHeight = range.sumOf { (lines[it].bottom - lines[it].top).toDouble() }
            assertTrue(
                "la page $range dépasse la hauteur du viewport ($pageHeight > $viewportHeightPx)",
                pageHeight <= viewportHeightPx,
            )
        }
    }

    @Test
    fun `computePageLineRanges - aucune page n est evitablement a moitie vide`() {
        val (lines, _) = uniformLines(53, lineHeight = 17f)
        val viewportHeightPx = 100f
        val ranges = computePageLineRanges(lines, viewportHeightPx)

        for (pageIndex in 0 until ranges.lastIndex) {
            val range = ranges[pageIndex]
            val nextLineIndex = range.last + 1
            val pageTop = lines[range.first].top
            val heightWithNextLine = lines[nextLineIndex].bottom - pageTop
            assertTrue(
                "la page $range aurait pu accueillir la ligne $nextLineIndex sans dépasser $viewportHeightPx",
                heightWithNextLine > viewportHeightPx,
            )
        }
    }

    @Test
    fun `invalidation du cache - meme cle ne recalcule pas, cle differente recalcule`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(10)

        assertTrue(engine.updateChapter(0, defaultStyleKey, lines, offsets))
        // Même clé : pas de recalcul (simule un changement de thème, qui ne
        // fait pas partie de PaginationStyleKey).
        assertFalse(engine.updateChapter(0, defaultStyleKey, lines, offsets))

        // Interligne différent : recalcul.
        val differentLineHeightKey = defaultStyleKey.copy(lineHeightSp = 30)
        assertTrue(engine.updateChapter(0, differentLineHeightKey, lines, offsets))
    }

    @Test
    fun `force outrepasse le court-circuit a cle de style egale`() {
        val engine = VirtualPaginationEngine()
        val (partialLines, partialOffsets) = uniformLines(3)
        val (fullLines, fullOffsets) = uniformLines(10)

        assertTrue(engine.updateChapter(0, defaultStyleKey, partialLines, partialOffsets))
        assertEquals(2, engine.sentenceRangeOf(0, engine.pageCount(0) - 1).last)

        // Même clé, force=false : ignoré, la pagination reste celle des 3 phrases.
        assertFalse(engine.updateChapter(0, defaultStyleKey, fullLines, fullOffsets))

        // Même clé, force=true : recalcule bel et bien avec les nouvelles données.
        assertTrue(engine.updateChapter(0, defaultStyleKey, fullLines, fullOffsets, force = true))
        val covered = (0 until engine.pageCount(0)).flatMap { engine.sentenceRangeOf(0, it).toList() }.toSet()
        assertEquals(fullOffsets.indices.toSet(), covered)
    }

    @Test
    fun `hauteurs mixtes - un titre plus haut change le nombre de pages a texte egal`() {
        val engine = VirtualPaginationEngine()

        // Chapitre "plat" : 30 lignes de 20px.
        val (flatLines, flatOffsets) = uniformLines(30, lineHeight = 20f)

        // Même nombre de phrases, mais la première ligne (le titre) est
        // deux fois plus haute — simule un ParagraphStyle.HEADING mesuré
        // avec sa vraie géométrie plutôt qu'une hauteur de ligne constante.
        val mixedLines = flatLines.mapIndexed { index, line ->
            if (index == 0) {
                line.copy(bottom = line.top + 40f)
            } else {
                val shift = 20f // décalage dû à la première ligne plus haute
                line.copy(top = line.top + shift, bottom = line.bottom + shift)
            }
        }

        val viewportKey = defaultStyleKey.copy(viewportHeightPx = 100)
        engine.updateChapter(0, viewportKey, flatLines, flatOffsets)
        engine.updateChapter(1, viewportKey, mixedLines, flatOffsets)

        assertFalse(
            "une hauteur de ligne constante ne distinguerait pas un titre plus haut",
            engine.pageCount(0) == engine.pageCount(1),
        )
    }

    @Test
    fun `re-indexation - pageIndexAt retrouve la bonne page apres changement de viewport`() {
        val engine = VirtualPaginationEngine()
        val (lines, offsets) = uniformLines(60)

        val portraitKey = defaultStyleKey.copy(viewportHeightPx = 150, viewportWidthPx = 400)
        val landscapeKey = defaultStyleKey.copy(viewportHeightPx = 250, viewportWidthPx = 800)

        engine.updateChapter(0, portraitKey, lines, offsets)
        val currentSentenceIndex = 27
        val portraitPage = engine.pageIndexAt(0, currentSentenceIndex)
        assertTrue(currentSentenceIndex in engine.sentenceRangeOf(0, portraitPage))

        engine.updateChapter(0, landscapeKey, lines, offsets)
        val landscapePage = engine.pageIndexAt(0, currentSentenceIndex)
        assertTrue(currentSentenceIndex in engine.sentenceRangeOf(0, landscapePage))
    }
}
