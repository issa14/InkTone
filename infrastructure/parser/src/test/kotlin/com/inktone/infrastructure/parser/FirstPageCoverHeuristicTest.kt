package com.inktone.infrastructure.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repli de couverture par la première page : retenue pour une vraie page de
 * couverture (« Sauve-moi », image 532 × 883 seule), écartée pour une page
 * de titre ornée d'un portrait (« La Guerre des mondes », 223 × 261).
 */
class FirstPageCoverHeuristicTest {

    @Test
    fun une_page_sans_texte_ou_a_simple_legende_est_une_page_de_couverture() {
        assertTrue(EpubGuideCoverResolver.isCoverLikePage(""))
        assertTrue(EpubGuideCoverResolver.isCoverLikePage("  Page titre \n"))
    }

    @Test
    fun une_page_de_texte_n_est_pas_une_page_de_couverture() {
        val firstParagraph = "Personne n'aurait cru, dans les dernières années du XIXe siècle, que les choses humaines"
        assertFalse(EpubGuideCoverResolver.isCoverLikePage(firstParagraph))
    }

    @Test
    fun une_image_de_couverture_est_en_portrait_et_assez_grande() {
        assertTrue(EpubGuideCoverResolver.isCoverLikeImage(532, 883))
    }

    @Test
    fun un_portrait_de_page_de_titre_ou_un_logo_n_est_pas_une_couverture() {
        assertFalse("portrait de 223 × 261", EpubGuideCoverResolver.isCoverLikeImage(223, 261))
        assertFalse("bandeau en paysage", EpubGuideCoverResolver.isCoverLikeImage(1200, 500))
        assertFalse("image illisible", EpubGuideCoverResolver.isCoverLikeImage(-1, -1))
    }
}
