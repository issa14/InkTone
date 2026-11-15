package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReadingCreditTest {

    @Test
    fun `un defilement instantane ne credite que la marge`() {
        assertEquals(CREDIT_SLACK_WORDS, plausibleWordLimit(0))
        assertEquals(CREDIT_SLACK_WORDS, plausibleWordLimit(-500))
    }

    @Test
    fun `la limite croit avec le temps ecoule`() {
        // Une minute : au plus 900 mots, plus la marge.
        assertEquals(MAX_PLAUSIBLE_READING_WPM + CREDIT_SLACK_WORDS, plausibleWordLimit(60_000))
        assertTrue(plausibleWordLimit(30_000) < plausibleWordLimit(60_000))
    }

    @Test
    fun `un lecteur lent n'est jamais ecarte`() {
        // Un paragraphe de 120 mots franchi en 20 s est un rythme de 360 mots par minute.
        assertTrue(plausibleWordLimit(20_000) >= 120)
    }
}
