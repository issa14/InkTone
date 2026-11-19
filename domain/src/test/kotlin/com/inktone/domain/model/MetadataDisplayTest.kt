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

    @Test
    fun une_forme_de_tri_collee_est_remise_dans_l_ordre_prenom_nom() {
        assertEquals("Brian Aldiss", "Aldiss,Brian".authorForDisplay())
        assertEquals("Philip K. Dick", "Dick,Philip K.".authorForDisplay())
        assertEquals("Antoine de Saint-Exupéry", "Saint-Exupéry,Antoine de".authorForDisplay())
    }

    @Test
    fun une_virgule_suivie_d_une_espace_ou_plusieurs_virgules_ne_sont_pas_inversees() {
        assertEquals("Dupont, Durand", "Dupont, Durand".authorForDisplay())
        assertEquals("A,B,C", "A,B,C".authorForDisplay())
        assertEquals("Aldiss,", "Aldiss,".authorForDisplay())
        assertEquals(",Brian", ",Brian".authorForDisplay())
        assertEquals("Brian Aldiss", "Brian Aldiss".authorForDisplay())
    }

    @Test
    fun la_liste_d_auteurs_applique_l_inversion_a_chaque_entree() {
        assertEquals("Brian Aldiss, Philip K. Dick", listOf("Aldiss,Brian", "Dick,Philip K.").cleanedAuthorsForDisplay())
    }
}
