package com.inktone.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.service.ImportResultEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 5.7 — tests de l'interface de retour d'import (Palier B).
 *
 * 1. Un lot sans échec n'affiche aucune catégorie à zéro.
 * 2. Doublon et fichier corrompu sont rendus sur des registres
 *    visuellement distincts (contentDescription "Doublon" / "Alerte").
 * 3. « Détails » ouvre la liste ; chaque ligne porte un nom de fichier réel.
 * 4. Depuis un doublon, l'ouverture du livre existant navigue vers la
 *    bonne publication (`existingPublicationId`).
 * 5. Aucun bouton d'action sur un cas non réessayable (DRM, format non
 *    supporté).
 */
class ImportResultComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun entry(
        fileName: String,
        resultType: String,
        message: String? = null,
        existingPublicationId: String? = null,
    ) = ImportResultEntry(
        fileName = fileName,
        resultType = resultType,
        message = message,
        existingPublicationId = existingPublicationId,
    )

    @Test
    fun lot_sans_echec_n_affiche_aucune_categorie_a_zero() {
        composeTestRule.setContent {
            ImportResultSummary(
                results = listOf(entry("a.epub", "success"), entry("b.epub", "success")),
                onDetailsClick = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("2 importés").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 doublon", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("0 échec", substring = true).assertDoesNotExist()
    }

    @Test
    fun doublon_et_corrompu_sont_sur_des_registres_visuellement_distincts() {
        composeTestRule.setContent {
            ImportResultDetail(
                results = listOf(
                    entry("duplicata.epub", "duplicate", existingPublicationId = "pub-1"),
                    entry("corrompu.epub", "corrupted", message = "Fichier illisible"),
                ),
                onOpenPublication = {},
                onDismiss = {},
            )
        }

        // Registre informationnel (doublon) vs registre alerte (corrompu)
        composeTestRule.onNodeWithContentDescription("Doublon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Alerte").assertIsDisplayed()
    }

    @Test
    fun details_declenche_l_ouverture_de_la_liste() {
        var detailsClicked = false
        composeTestRule.setContent {
            ImportResultSummary(
                results = listOf(
                    entry("mon-livre.epub", "corrupted", message = "Echec de lecture"),
                    entry("autre.epub", "duplicate", existingPublicationId = "pub-2"),
                ),
                onDetailsClick = { detailsClicked = true },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Détails").performClick()
        assertEquals(true, detailsClicked)
    }

    @Test
    fun chaque_ligne_du_detail_porte_un_nom_de_fichier_reel() {
        composeTestRule.setContent {
            ImportResultDetail(
                results = listOf(
                    entry("mon-livre.epub", "corrupted", message = "Echec de lecture"),
                    entry("autre.epub", "duplicate", existingPublicationId = "pub-2"),
                ),
                onOpenPublication = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("mon-livre.epub").assertIsDisplayed()
        composeTestRule.onNodeWithText("autre.epub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Echec de lecture").assertIsDisplayed()
    }

    @Test
    fun ouvrir_depuis_un_doublon_navigue_vers_la_bonne_publication() {
        var openedId: String? = null
        composeTestRule.setContent {
            ImportResultDetail(
                results = listOf(
                    entry("duplicata.epub", "duplicate", existingPublicationId = "pub-123"),
                    entry("ok.epub", "success"),
                ),
                onOpenPublication = { openedId = it },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Ouvrir").performClick()
        assertEquals("pub-123", openedId)
    }

    @Test
    fun aucun_bouton_d_action_sur_un_cas_non_reessayable() {
        composeTestRule.setContent {
            ImportResultDetail(
                results = listOf(
                    entry("drm.epub", "drm_protected", message = "Protégé par DRM"),
                    entry("inconnu.xyz", "unsupported_format"),
                    entry("corrompu.epub", "corrupted", message = "Echec"),
                ),
                onOpenPublication = {},
                onDismiss = {},
            )
        }

        // Ni "Ouvrir" ni "Réessayer" sur des cas qui échoueront à l'identique
        composeTestRule.onNodeWithText("Ouvrir").assertDoesNotExist()
        composeTestRule.onNodeWithText("Réessayer").assertDoesNotExist()
    }
}
