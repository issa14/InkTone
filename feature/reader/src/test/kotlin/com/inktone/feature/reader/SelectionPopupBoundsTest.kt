package com.inktone.feature.reader

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Arbitrage des bornes du popup de sélection entre unités adressables
 * concurrentes (`resolveSelectionPopupBounds`, voir sa KDoc).
 *
 * Les deux modes montent plusieurs unités simultanément — pages voisines
 * préchargées en PAGED, chapitre entier composé d'un coup en SCROLL — qui
 * écrivent toutes dans le même emplacement de bornes. Ces tests fixent la
 * règle : une écriture non nulle gagne toujours, une écriture nulle
 * n'efface que si son émetteur est encore propriétaire.
 *
 * Logique pure (aucun Compose runtime) : vérifiable ici, sans appareil,
 * contrairement au reste du cycle de vie de la sélection.
 */
class SelectionPopupBoundsTest {

    private val boundsA = Rect(0f, 0f, 10f, 10f)
    private val boundsB = Rect(20f, 20f, 30f, 30f)

    @Test
    fun des_bornes_non_nulles_prennent_la_propriete_quand_il_n_y_a_rien() {
        val result = resolveSelectionPopupBounds(current = null, ownerKey = 100, bounds = boundsA)

        assertEquals(SelectionPopupBounds(100, boundsA), result)
    }

    @Test
    fun le_dernier_geste_termine_gagne_meme_si_une_autre_unite_etait_proprietaire() {
        val current = SelectionPopupBounds(100, boundsA)

        val result = resolveSelectionPopupBounds(current = current, ownerKey = 500, bounds = boundsB)

        assertEquals(SelectionPopupBounds(500, boundsB), result)
    }

    @Test
    fun le_proprietaire_peut_effacer_ses_propres_bornes() {
        val current = SelectionPopupBounds(100, boundsA)

        val result = resolveSelectionPopupBounds(current = current, ownerKey = 100, bounds = null)

        assertNull(result)
    }

    /**
     * Le cas qui motive toute cette identité : l'utilisateur sélectionne
     * un mot dans l'unité B alors que A était sélectionnée. Le `hide()` de
     * A (perte de focus) peut arriver APRÈS le `showMenu()` de B — l'ordre
     * de ces appels internes à `BasicTextField` n'est pas un contrat. Sans
     * la garde de propriété, le popup de B disparaissait aussitôt ouvert.
     */
    @Test
    fun le_hide_tardif_d_une_ancienne_unite_n_efface_pas_le_popup_de_la_nouvelle() {
        val afterBShowedItsMenu = SelectionPopupBounds(500, boundsB)

        val result = resolveSelectionPopupBounds(current = afterBShowedItsMenu, ownerKey = 100, bounds = null)

        assertEquals(afterBShowedItsMenu, result)
    }

    @Test
    fun effacer_alors_qu_aucun_popup_n_est_ouvert_reste_nul() {
        val result = resolveSelectionPopupBounds(current = null, ownerKey = 100, bounds = null)

        assertNull(result)
    }
}
