package com.inktone.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Lot 22, Palier B — la clé de cache TTS est une dimension d'invalidation :
 * chaque composant (voix, règles de prononciation, moteur, offset de
 * phrase, publication, chapitre) doit produire une clé distincte. Un cache
 * périmé servirait un surlignage faux (ADR-021) — ces tests le figent.
 */
class TtsCacheKeyTest {

    @Test
    fun `la meme entree produit la meme cle - deterministe`() {
        val a = ttsCacheKey("pub", 1, 10, "vp", "rules", 1)
        val b = ttsCacheKey("pub", 1, 10, "vp", "rules", 1)
        assertEquals(a, b)
    }

    @Test
    fun `chaque dimension d'invalidation change la cle`() {
        val base = ttsCacheKey("pub", 1, 10, "vp", "rules", 1)

        assertNotEquals(base, ttsCacheKey("pub2", 1, 10, "vp", "rules", 1)) // publication
        assertNotEquals(base, ttsCacheKey("pub", 2, 10, "vp", "rules", 1)) // chapitre
        assertNotEquals(base, ttsCacheKey("pub", 1, 11, "vp", "rules", 1)) // offset de phrase (texte source)
        assertNotEquals(base, ttsCacheKey("pub", 1, 10, "vp2", "rules", 1)) // voix
        assertNotEquals(base, ttsCacheKey("pub", 1, 10, "vp", "rules2", 1)) // règles de prononciation
        assertNotEquals(base, ttsCacheKey("pub", 1, 10, "vp", "rules", 2)) // version du moteur
    }
}
