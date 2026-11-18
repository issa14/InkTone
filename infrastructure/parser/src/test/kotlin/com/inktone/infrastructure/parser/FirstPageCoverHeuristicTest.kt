package com.inktone.infrastructure.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repli de couverture par la première page, sur les cas relevés dans une
 * bibliothèque réelle : page de couverture Sigil, portrait d'auteur
 * (« La Guerre des mondes »), couverture en tête d'un livre d'un seul
 * fichier ; à écarter : logo d'éditeur en bandeau, image après du texte.
 */
class FirstPageCoverHeuristicTest {

    @Test
    fun une_page_qui_s_ouvre_sur_l_image_n_a_aucun_texte_avant_elle() {
        val sigilCover = """<html><head><title>Couverture</title><style>img{}</style></head>
            |<body><div><img src="../Images/image001.jpg" alt="x"/></div></body></html>""".trimMargin()
        assertEquals("", EpubGuideCoverResolver.textBeforeFirstImage(sigilCover))
    }

    @Test
    fun le_texte_qui_precede_l_image_est_compte() {
        val titlePage = "<html><body><h1>L'Éthique</h1><p>Baruch Spinoza</p><img src='a.png'/></body></html>"
        assertEquals("L'Éthique Baruch Spinoza", EpubGuideCoverResolver.textBeforeFirstImage(titlePage))
    }

    @Test
    fun une_image_svg_compte_comme_image() {
        val svgCover = """<body><svg><image xlink:href="cover.jpg"/></svg></body>"""
        assertEquals("", EpubGuideCoverResolver.textBeforeFirstImage(svgCover))
    }

    @Test
    fun une_page_sans_image_n_a_pas_de_couverture() {
        assertNull(EpubGuideCoverResolver.textBeforeFirstImage("<body><p>Chapitre premier</p></body>"))
    }

    @Test
    fun couvertures_et_portraits_d_auteur_sont_retenus() {
        assertTrue("Sauve-moi", EpubGuideCoverResolver.isCoverLikeImage(532, 883))
        assertTrue("La Guerre des mondes", EpubGuideCoverResolver.isCoverLikeImage(223, 261))
        assertTrue("Le Grand Chef des Aucas", EpubGuideCoverResolver.isCoverLikeImage(150, 180))
    }

    @Test
    fun logo_en_bandeau_icone_et_image_illisible_sont_ecartes() {
        assertFalse("logo Feedbooks", EpubGuideCoverResolver.isCoverLikeImage(187, 70))
        assertFalse("icône", EpubGuideCoverResolver.isCoverLikeImage(64, 96))
        assertFalse("image illisible", EpubGuideCoverResolver.isCoverLikeImage(-1, -1))
    }
}
