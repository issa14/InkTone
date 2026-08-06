package com.inktone.feature.library

import com.inktone.domain.model.Publication
import com.inktone.domain.model.ReadingState

/**
 * A.4 — Progression basée sur chapterIndex / chapterCount, avec
 * garde-fou contre la division par zéro (EPUB à 1 chapitre). Partagé
 * entre [LibraryViewModel] et `LibraryDetailViewModel` (lot 2a.4) —
 * même calcul, ne pas dupliquer.
 */
internal fun computeProgressMap(publications: List<Publication>, readingStates: List<ReadingState>): Map<String, Int> {
    val stateMap = readingStates.associateBy { it.publicationId }
    return publications.associate { pub ->
        val rs = stateMap[pub.id]
        val pct = if (rs != null && pub.chapterCount > 0) {
            val divisor = (pub.chapterCount - 1).coerceAtLeast(1)
            (rs.locator.chapterIndex * 100 / divisor).coerceIn(0, 100)
        } else 0
        pub.id to if (pct < 1 && pct > 0) 1 else pct // ≥1% si lecture commencée
    }
}
