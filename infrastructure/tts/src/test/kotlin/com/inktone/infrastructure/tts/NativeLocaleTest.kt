package com.inktone.infrastructure.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NativeLocaleTest {

    @Test
    fun `suit la langue du livre`() {
        assertEquals("fr", nativeLocaleFor("fr-FR").language)
        assertEquals("FR", nativeLocaleFor("fr-FR").country)
        assertEquals("en", nativeLocaleFor("en").language)
        assertEquals("en", nativeLocaleFor("en_US").language)
    }

    @Test
    fun `lit un code ISO 639-2 a trois lettres`() {
        assertEquals("en", nativeLocaleFor("eng").language)
        assertEquals("fr", nativeLocaleFor("fra").language)
        assertEquals("de", nativeLocaleFor("deu").language)
    }

    @Test
    fun `retombe sur le francais quand la langue est vide ou illisible`() {
        assertEquals(Locale.FRENCH.language, nativeLocaleFor("").language)
        assertEquals(Locale.FRENCH.language, nativeLocaleFor("   ").language)
    }
}
