package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveReadingSettingsTest {

    private val globalPrefs = UserPreferences(theme = ReadingTheme.LIGHT, fontSize = 16)

    @Test
    fun `sans surcharge, les reglages globaux s'appliquent integralement`() {
        val effective = EffectiveReadingSettings.resolve(overrides = null, global = globalPrefs)
        assertEquals(ReadingTheme.LIGHT, effective.theme)
        assertEquals(16, effective.fontSize)
    }

    @Test
    fun `une surcharge partielle (theme seul) prime sur ce seul champ`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.SEPIA, fontSize = null)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.SEPIA, effective.theme)
        assertEquals(16, effective.fontSize) // reste global : pas de surcharge sur ce champ
    }

    @Test
    fun `une surcharge complete prime entierement sur le global`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.DARK, fontSize = 22)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.DARK, effective.theme)
        assertEquals(22, effective.fontSize)
    }
}
