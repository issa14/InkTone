package com.inktone.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sous-lot 2a — palette de marque violette (Deadly Depths). Vérifie que
 * chaque rôle de la famille `primary` respecte les seuils WCAG 2.x sur
 * les fonds réels du thème. Les assertions documentent le contrat :
 * - primary utilisable en icône/bouton (non-texte) : >= 3:1
 * - onPrimary (étiquette sur bouton rempli) : >= 4.5:1
 * - onPrimaryContainer (texte sur container) : >= 4.5:1
 * - garde-fou sombre : primary NE passe PAS le seuil texte (3.88:1) → le
 *   texte accentué en sombre doit utiliser l'accent-container (Cont.300).
 */
class PaletteContrastTest {

    // Fonds réels du thème (Color.kt)
    private val lightBg = Color(0xFFFFFBF5)   // Color.kt background
    private val darkBg = Color(0xFF0F1419)    // Color.kt background sombre

    // Nouvelles couleurs primary (Deadly Depths)
    private val primaryLight = Color(0xFF2C1E67)        // Accent700
    private val primaryDark = Color(0xFF7661D1)         // Accent500
    private val containerLight = Color(0xFFE4DFF6)      // Cont.100
    private val onContainerLight = Color(0xFF19113B)    // Accent900
    private val containerDark = Color(0xFF2C1E67)       // Accent700
    private val onContainerDark = Color(0xFFA698E1)     // Cont.300

    @Test
    fun primary_sur_fond_clair_est_non_texte() {
        val ratio = calculateContrastRatio(lightBg, primaryLight)
        assertTrue("primary clair / fond : $ratio < 3.0", ratio >= 3.0)
    }

    @Test
    fun primary_sur_fond_sombre_est_non_texte() {
        val ratio = calculateContrastRatio(darkBg, primaryDark)
        assertTrue("primary sombre / fond : $ratio < 3.0", ratio >= 3.0)
    }

    @Test
    fun onPrimary_sur_primary_clair_passe_aa_texte() {
        val ratio = calculateContrastRatio(primaryLight, Color.White)
        assertTrue("onPrimary clair : $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun onPrimary_sur_primary_sombre_passe_aa_texte() {
        val ratio = calculateContrastRatio(primaryDark, Color.White)
        assertTrue("onPrimary sombre : $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun onPrimaryContainer_sur_container_clair_passe_aa_texte() {
        val ratio = calculateContrastRatio(containerLight, onContainerLight)
        assertTrue("onPrimaryContainer clair : $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun onPrimaryContainer_sur_container_sombre_passe_aa_texte() {
        val ratio = calculateContrastRatio(containerDark, onContainerDark)
        assertTrue("onPrimaryContainer sombre : $ratio < 4.5", ratio >= 4.5)
    }

    // GARDE-FOU sombre : primary NE passe PAS le seuil texte (3.88:1)
    @Test
    fun primary_sombre_ne_passe_pas_le_seuil_texte() {
        val ratio = calculateContrastRatio(darkBg, primaryDark)
        assertTrue("primary sombre devrait être < 4.5 mais vaut $ratio", ratio < 4.5)
    }

    @Test
    fun accent_texte_sombre_doit_etre_container300() {
        val ratio = calculateContrastRatio(darkBg, onContainerDark)
        assertTrue("accent-texte sombre (Cont.300) : $ratio < 4.5", ratio >= 4.5)
    }
}
