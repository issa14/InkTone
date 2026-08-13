package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanStylesTest {

    @Test
    fun `NONE est le masque zero`() {
        assertEquals(0, SpanStyles.NONE.mask)
    }

    @Test
    fun `les constantes ont des bits distincts`() {
        val all = listOf(
            SpanStyles.STRONG,
            SpanStyles.EMPHASIS,
            SpanStyles.INSERTED,
            SpanStyles.DELETED,
            SpanStyles.SUPERSCRIPT,
            SpanStyles.SUBSCRIPT,
            SpanStyles.REFERENCE,
        )
        val masks = all.map { it.mask }
        // Vérifier que tous les bits sont distincts (pas de collision)
        assertEquals(masks.size, masks.distinct().size)
        // Vérifier que chaque masque a exactement un bit
        masks.forEach { mask ->
            assertEquals(1, Integer.bitCount(mask))
        }
    }

    @Test
    fun `plus combine deux masques`() {
        val combined = SpanStyles.STRONG + SpanStyles.EMPHASIS
        assertEquals(SpanStyles.STRONG.mask or SpanStyles.EMPHASIS.mask, combined.mask)
    }

    @Test
    fun `contains detecte un style dans une combinaison`() {
        val combined = SpanStyles.STRONG + SpanStyles.EMPHASIS
        assertTrue(SpanStyles.STRONG in combined)
        assertTrue(SpanStyles.EMPHASIS in combined)
        assertFalse(SpanStyles.DELETED in combined)
    }

    @Test
    fun `NONE ne contient aucun style`() {
        assertFalse(SpanStyles.STRONG in SpanStyles.NONE)
        assertFalse(SpanStyles.EMPHASIS in SpanStyles.NONE)
    }

    @Test
    fun `isEmpty est vrai pour NONE`() {
        assertTrue(SpanStyles.NONE.isEmpty())
        assertFalse(SpanStyles.STRONG.isEmpty())
    }

    @Test
    fun `combinaison de trois styles`() {
        val triple = SpanStyles.STRONG + SpanStyles.EMPHASIS + SpanStyles.DELETED
        assertTrue(SpanStyles.STRONG in triple)
        assertTrue(SpanStyles.EMPHASIS in triple)
        assertTrue(SpanStyles.DELETED in triple)
        assertFalse(SpanStyles.INSERTED in triple)
    }

    @Test
    fun `plus est commutatif`() {
        val a = SpanStyles.STRONG + SpanStyles.EMPHASIS
        val b = SpanStyles.EMPHASIS + SpanStyles.STRONG
        assertEquals(a, b)
    }

    @Test
    fun `toString affiche les styles actifs`() {
        val combined = SpanStyles.STRONG + SpanStyles.EMPHASIS
        val str = combined.toString()
        assertTrue(str.contains("STRONG"))
        assertTrue(str.contains("EMPHASIS"))
    }
}
