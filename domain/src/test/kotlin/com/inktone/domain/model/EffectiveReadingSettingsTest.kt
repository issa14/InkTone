package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lot 9 — theme est un id (String), plus un enum : les tests comparent des ids. */
class EffectiveReadingSettingsTest {

    private val globalPrefs = UserPreferences(theme = ReadingTheme.PAPIER_CLAIR.id, fontSize = 16)

    @Test
    fun `sans surcharge, les reglages globaux s'appliquent integralement`() {
        val effective = EffectiveReadingSettings.resolve(overrides = null, global = globalPrefs)
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, effective.theme)
        assertEquals(16, effective.fontSize)
    }

    @Test
    fun `une surcharge partielle (theme seul) prime sur ce seul champ`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.SEPIA_VINTAGE.id, fontSize = null)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.SEPIA_VINTAGE.id, effective.theme)
        assertEquals(16, effective.fontSize) // reste global : pas de surcharge sur ce champ
    }

    @Test
    fun `une surcharge complete prime entierement sur le global`() {
        val overrides = ReadingOverrides(theme = ReadingTheme.OBSIDIENNE.id, fontSize = 22)
        val effective = EffectiveReadingSettings.resolve(overrides, globalPrefs)
        assertEquals(ReadingTheme.OBSIDIENNE.id, effective.theme)
        assertEquals(22, effective.fontSize)
    }

    @Test
    fun `fontFamily suit toujours la preference globale, jamais de surcharge par publication`() {
        val prefs = globalPrefs.copy(fontFamily = FontFamily.OPEN_DYSLEXIC)
        val effective = EffectiveReadingSettings.resolve(overrides = ReadingOverrides(theme = ReadingTheme.OBSIDIENNE.id), global = prefs)
        assertEquals(FontFamily.OPEN_DYSLEXIC, effective.fontFamily)
    }
}
