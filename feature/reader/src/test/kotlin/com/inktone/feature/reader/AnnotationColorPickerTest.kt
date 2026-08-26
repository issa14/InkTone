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

    @Test
    fun `une couleur personnalisee rejoint les couleurs recentes comme les preregles`() {
        val custom = hexRgbToAnnotationColor("#123456")
        assertEquals(listOf(custom), emptyList<AnnotationColor>().withRecentColor(custom))
    }
}

/**
 * Lot 23, tâche 10 — `hexRgbToAnnotationColor` : opacité toujours pleine
 * (`FF`), sensible à la casse hexadécimale (majuscules/minuscules
 * équivalentes), jamais de couleur translucide pour une annotation.
 */
class HexRgbToAnnotationColorTest {

    @Test
    fun `un hex minuscule et majuscule produit la meme couleur`() {
        assertEquals(hexRgbToAnnotationColor("#1a2b3c"), hexRgbToAnnotationColor("#1A2B3C"))
    }

    @Test
    fun `l'opacite est toujours pleine`() {
        val color = hexRgbToAnnotationColor("#123456")
        assertEquals(0xFF, (color.argb ushr 24) and 0xFF)
    }

    @Test
    fun `blanc et noir produisent les bornes ARGB attendues`() {
        assertEquals(0xFFFFFFFF.toInt(), hexRgbToAnnotationColor("#FFFFFF").argb)
        assertEquals(0xFF000000.toInt(), hexRgbToAnnotationColor("#000000").argb)
    }
}
