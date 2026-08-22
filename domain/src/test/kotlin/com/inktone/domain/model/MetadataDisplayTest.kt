package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataDisplayTest {

    @Test
    fun `retire le tiret et les espaces en tete`() {
        assertEquals(
            "La première loi - Tome 1 - Premier sang",
            "-La première loi - Tome 1 - Premier sang".cleanedForDisplay(),
        )
    }

    @Test
    fun `conserve les tirets INTERNES, qui sont signifiants`() {
        // Le tiret qui sépare le titre du tome n'est pas un artefact.
        assertEquals("Tome 1 - Premier sang", " Tome 1 - Premier sang ".cleanedForDisplay())
    }

    @Test
    fun `traite les tirets demi-cadratin et cadratin`() {
        assertEquals("Titre", "– Titre".cleanedForDisplay())
        assertEquals("Titre", "—Titre".cleanedForDisplay())
    }

    @Test
    fun `rogne aussi la fin`() {
        assertEquals("Titre", "Titre -".cleanedForDisplay())
    }

    @Test
    fun `laisse intact un titre déjà propre`() {
        assertEquals("Le problème à trois corps", "Le problème à trois corps".cleanedForDisplay())
    }

    @Test
    fun `un champ entièrement fait d artefacts devient vide`() {
        assertEquals("", " - ".cleanedForDisplay())
    }

    @Test
    fun `les auteurs vides après nettoyage disparaissent de la liste`() {
        assertEquals(
            "Joe Abercrombie, Albert Camus",
            listOf("-Joe Abercrombie", " - ", "Albert Camus ").cleanedAuthorsForDisplay(),
        )
    }
}
