package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetBackupTest {

    @Test
    fun `l'etat d'avant le mode sombre fait l'aller-retour`() {
        val backup = DarkModePresetBackup(theme = "sepia_vintage", appTheme = AppTheme.LIGHT)
        assertEquals(backup, DarkModePresetBackup.decode(backup.encode()))
    }

    @Test
    fun `l'etat d'avant l'accessibilite fait l'aller-retour`() {
        val backup = AccessibilityPresetBackup(
            fontSize = 21, theme = "sepia_vintage", fontFamily = FontFamily.SERIF,
            reduceMotion = true, readingRulerEnabled = false, libraryLayoutMode = "GRID_COMPACT",
        )
        assertEquals(backup, AccessibilityPresetBackup.decode(backup.encode()))
    }

    @Test
    fun `une valeur absente ou illisible n'est pas restauree`() {
        assertNull(DarkModePresetBackup.decode(null))
        assertNull(DarkModePresetBackup.decode("seul"))
        assertNull(DarkModePresetBackup.decode("papier_clair|INCONNU"))
        assertNull(AccessibilityPresetBackup.decode(null))
        assertNull(AccessibilityPresetBackup.decode("21|a|SERIF"))
        assertNull(AccessibilityPresetBackup.decode("x|a|SERIF|true|false|GRID"))
    }
}
