package com.inktone.feature.reader

import com.inktone.domain.model.AnnotationColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 22, tâche 12 — `withRecentColor` place la couleur choisie en tête
 * sans doublon, plafonné à 3 (le reste de la palette suit dans
 * `AnnotationColorPicker`, jamais tronqué par ce plafond).
 */
class AnnotationColorPickerTest {

    @Test
    fun `une premiere couleur devient seule en tete`() {
        assertEquals(listOf(AnnotationColor.YELLOW), emptyList<AnnotationColor>().withRecentColor(AnnotationColor.YELLOW))
    }

    @Test
    fun `une couleur deja recente remonte en tete sans doublon`() {
        val recent = listOf(AnnotationColor.YELLOW, AnnotationColor.BLUE)
        assertEquals(
            listOf(AnnotationColor.BLUE, AnnotationColor.YELLOW),
            recent.withRecentColor(AnnotationColor.BLUE),
        )
    }

    @Test
    fun `le plafond de 3 evince la plus ancienne`() {
        val recent = listOf(AnnotationColor.YELLOW, AnnotationColor.BLUE, AnnotationColor.GREEN)
        assertEquals(
            listOf(AnnotationColor.PINK, AnnotationColor.YELLOW, AnnotationColor.BLUE),
            recent.withRecentColor(AnnotationColor.PINK),
        )
    }
}
