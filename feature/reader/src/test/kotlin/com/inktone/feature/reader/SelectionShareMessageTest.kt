package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 21, tâche 7 — construction du message partagé (ACTION_SEND) :
 * texte sélectionné entre guillemets français + contexte
 * titre/auteur/chapitre sur une ligne dédiée. Pure et déterministe.
 */
class SelectionShareMessageTest {

    @Test
    fun sans_contexte_on_partage_le_texte_seul() {
        assertEquals("«Un passage.»", buildShareMessage("Un passage.", null))
    }

    @Test
    fun avec_contexte_le_texte_est_suivi_d_une_ligne_dediee() {
        assertEquals(
            "«Un passage.»\n\n— Titre — Auteur — Chapitre 2",
            buildShareMessage("Un passage.", "Titre — Auteur — Chapitre 2"),
        )
    }

    @Test
    fun contexte_blank_est_ignore() {
        assertEquals("«Un passage.»", buildShareMessage("Un passage.", "   "))
    }
}
