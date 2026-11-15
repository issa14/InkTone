package com.inktone.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfInversionTest {

    @Test
    fun `reconnait les fonds sombres, pas seulement le noir absolu`() {
        assertTrue(isDarkBackground("#000000"))
        assertTrue(isDarkBackground("#0F1419"))
        assertTrue(isDarkBackground("#2B2B2B"))
        assertTrue(isDarkBackground("#FF1A1A2E"))
    }

    @Test
    fun `ne tient pas un fond clair ou mal forme pour sombre`() {
        assertFalse(isDarkBackground("#FFFFFF"))
        assertFalse(isDarkBackground("#F4ECD8"))
        assertFalse(isDarkBackground("#808080"))
        assertFalse(isDarkBackground("noir"))
        assertFalse(isDarkBackground("#12"))
    }
}
