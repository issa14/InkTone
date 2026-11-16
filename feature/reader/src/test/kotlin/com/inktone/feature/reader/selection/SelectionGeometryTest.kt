package com.inktone.feature.reader.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lot 25, palier 25.3 — logique pure du moteur de sélection (ADR-029). */
class SelectionGeometryTest {

    // Plage 10..19 : curseurs de début 10 et de fin 20.
    private val range = 10..19

    @Test
    fun `la poignee de debut etend la selection vers l'arriere`() {
        assertEquals(HandleDrag(4..19, SelectionHandle.START), dragHandle(range, SelectionHandle.START, caret = 4))
    }

    @Test
    fun `la poignee de fin etend la selection au paragraphe suivant`() {
        assertEquals(HandleDrag(10..149, SelectionHandle.END), dragHandle(range, SelectionHandle.END, caret = 150))
    }

    @Test
    fun `la poignee de debut qui croise la fin devient la poignee de fin`() {
        assertEquals(HandleDrag(20..24, SelectionHandle.END), dragHandle(range, SelectionHandle.START, caret = 25))
    }

    @Test
    fun `la poignee de fin qui croise le debut devient la poignee de debut`() {
        assertEquals(HandleDrag(3..9, SelectionHandle.START), dragHandle(range, SelectionHandle.END, caret = 3))
    }

    @Test
    fun `une plage vide est refusee`() {
        assertEquals(HandleDrag(range, SelectionHandle.START), dragHandle(range, SelectionHandle.START, caret = 20))
        assertEquals(HandleDrag(range, SelectionHandle.END), dragHandle(range, SelectionHandle.END, caret = 10))
    }

    @Test
    fun `reduire la selection a un caractere reste possible`() {
        assertEquals(HandleDrag(19..19, SelectionHandle.START), dragHandle(range, SelectionHandle.START, caret = 19))
    }

    private val spans = listOf(BlockSpan(0, 100f, 200f), BlockSpan(1, 220f, 400f))

    @Test
    fun `le bloc qui contient l'ordonnee est choisi`() {
        assertEquals(1, nearestBlock(spans, 300f)?.key)
    }

    @Test
    fun `entre deux blocs le plus proche est choisi`() {
        assertEquals(0, nearestBlock(spans, 205f)?.key)
        assertEquals(1, nearestBlock(spans, 215f)?.key)
    }

    @Test
    fun `hors des blocs visibles le bord le plus proche est choisi`() {
        assertEquals(0, nearestBlock(spans, 10f)?.key)
        assertEquals(1, nearestBlock(spans, 900f)?.key)
    }

    @Test
    fun `aucun bloc visible donne null`() {
        assertNull(nearestBlock(emptyList(), 10f))
    }

    @Test
    fun `pas de defilement automatique hors des marges`() {
        assertEquals(0f, autoScrollStep(y = 500f, top = 0f, bottom = 1000f, edge = 100f, maxStep = 20f))
    }

    @Test
    fun `le defilement automatique croit avec la profondeur dans la marge`() {
        assertEquals(10f, autoScrollStep(y = 950f, top = 0f, bottom = 1000f, edge = 100f, maxStep = 20f))
        assertEquals(-10f, autoScrollStep(y = 50f, top = 0f, bottom = 1000f, edge = 100f, maxStep = 20f))
    }

    @Test
    fun `le defilement automatique est borne hors de la zone`() {
        assertEquals(20f, autoScrollStep(y = 1300f, top = 0f, bottom = 1000f, edge = 100f, maxStep = 20f))
        assertEquals(-20f, autoScrollStep(y = -40f, top = 0f, bottom = 1000f, edge = 100f, maxStep = 20f))
    }
}
