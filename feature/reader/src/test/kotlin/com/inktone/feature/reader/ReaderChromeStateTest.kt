package com.inktone.feature.reader

import androidx.compose.ui.geometry.Rect
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règles de bascule du bandeau (HUD) et des surcouches du Lecteur — jusqu'ici
 * des fonctions locales de `ReaderScreen`, sans aucun test.
 */
class ReaderChromeStateTest {

    @Test
    fun hors_lecture_audio_un_appui_masque_puis_reaffiche_le_bandeau() {
        val chrome = ReaderChromeState()
        assertTrue("bandeau visible à l'ouverture", chrome.isHudVisible)

        chrome.onReadingAreaTap(isPlaying = false)
        assertFalse(chrome.isHudVisible)

        val tickBefore = chrome.hudActivityTick
        chrome.onReadingAreaTap(isPlaying = false)
        assertTrue(chrome.isHudVisible)
        assertTrue("réafficher le bandeau relance son délai", chrome.hudActivityTick > tickBefore)
    }

    @Test
    fun pendant_la_lecture_audio_l_appui_suit_un_cycle_a_trois_temps() {
        val chrome = ReaderChromeState()

        // 1er appui (bandeau visible, barre pilule) : rappelle le panneau complet.
        chrome.onReadingAreaTap(isPlaying = true)
        assertTrue(chrome.showFullPanelOverlay)
        assertTrue(chrome.isHudVisible)

        // 2e : referme tout.
        chrome.onReadingAreaTap(isPlaying = true)
        assertFalse(chrome.showFullPanelOverlay)
        assertFalse(chrome.isHudVisible)

        // 3e : ramène la barre pilule, dépliée.
        chrome.onReadingAreaTap(isPlaying = true)
        assertTrue(chrome.isHudVisible)
        assertFalse(chrome.showFullPanelOverlay)
        assertFalse(chrome.isPillCollapsed)
    }

    @Test
    fun un_appui_pendant_le_reglage_de_luminosite_ferme_la_barre_sans_rouvrir_le_bandeau() {
        val chrome = ReaderChromeState()
        chrome.openBrightnessBar()
        assertTrue(chrome.showBrightnessBar)
        assertFalse("la barre de luminosité prend la place du bandeau", chrome.isHudVisible)

        chrome.onReadingAreaTap(isPlaying = false)

        assertFalse(chrome.showBrightnessBar)
        assertFalse(chrome.isHudVisible)
    }

    @Test
    fun l_inactivite_replie_la_barre_pilule_pendant_la_lecture_et_masque_le_bandeau_sinon() {
        val reading = ReaderChromeState()
        reading.onHudAutoHide(isPlaying = true)
        assertTrue(reading.isPillCollapsed)
        assertTrue("la lecture reste pilotable via le bouton replié", reading.isHudVisible)

        val idle = ReaderChromeState()
        idle.onHudAutoHide(isPlaying = false)
        assertFalse(idle.isHudVisible)
        assertFalse(idle.isPillCollapsed)
    }

    @Test
    fun l_inactivite_masque_le_bandeau_quand_le_panneau_complet_est_rappele_pendant_la_lecture() {
        val chrome = ReaderChromeState()
        chrome.onReadingAreaTap(isPlaying = true) // panneau complet rappelé

        chrome.onHudAutoHide(isPlaying = true)

        assertFalse(chrome.isHudVisible)
        assertFalse(chrome.isPillCollapsed)
    }

    @Test
    fun la_fin_de_lecture_efface_l_overlay_et_deplie_la_barre() {
        val chrome = ReaderChromeState()
        chrome.onHudAutoHide(isPlaying = true)
        chrome.onReadingAreaTap(isPlaying = true)

        chrome.onPlaybackStopped()

        assertFalse(chrome.showFullPanelOverlay)
        assertFalse(chrome.isPillCollapsed)
    }

    @Test
    fun le_menu_d_annotation_s_ouvre_et_se_ferme_avec_ses_bornes() {
        val chrome = ReaderChromeState()
        val annotation = Annotation(
            id = "a-1",
            publicationId = "pub-1",
            startLocator = Locator(resourceHref = "c.xhtml", chapterIndex = 0, charOffset = 0),
            endLocator = Locator(resourceHref = "c.xhtml", chapterIndex = 0, charOffset = 5),
            color = AnnotationColor.YELLOW,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val bounds = Rect(0f, 0f, 10f, 10f)

        chrome.showAnnotationMenu(annotation, bounds)
        assertEquals(annotation, chrome.tappedAnnotation)
        assertEquals(bounds, chrome.tappedAnnotationBounds)

        chrome.dismissAnnotationMenu()
        assertNull(chrome.tappedAnnotation)
        assertNull(chrome.tappedAnnotationBounds)
    }
}
