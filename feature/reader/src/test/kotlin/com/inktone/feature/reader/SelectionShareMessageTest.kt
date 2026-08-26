package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 21, tâche 7 — construction du message partagé (ACTION_SEND) :
 * texte sélectionné entre guillemets français + contexte
 * titre/auteur/chapitre sur une ligne dédiée. Pure et déterministe.
 *
 * Correctif — espace insécable (`\u00A0`) entre le guillemet et le texte,
 * typographie française, même convention que `XmlOpdsFeedParser`.
 */
class SelectionShareMessageTest {

    private val nbsp = '\u00A0'

    @Test
    fun sans_contexte_on_partage_le_texte_seul() {
        assertEquals("«$nbsp"+"Un passage.$nbsp»", buildShareMessage("Un passage.", null))
    }

    @Test
    fun avec_contexte_le_texte_est_suivi_d_une_ligne_dediee() {
        assertEquals(
            "«$nbsp"+"Un passage.$nbsp»\n\n— Titre — Auteur — Chapitre 2",
            buildShareMessage("Un passage.", "Titre — Auteur — Chapitre 2"),
        )
    }

    @Test
    fun contexte_blank_est_ignore() {
        assertEquals("«$nbsp"+"Un passage.$nbsp»", buildShareMessage("Un passage.", "   "))
    }
}
