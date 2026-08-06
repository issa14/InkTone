package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tâche 3b.7, tests 1 et 9 — formatage de la ligne de statut et alignement de l'horloge. */
class StatusLineBarTest {

    @Test
    fun `formatProgressionFr une decimale et virgule francaise`() {
        assertEquals("34,7%", formatProgressionFr(0.347f))
        assertEquals("0,0%", formatProgressionFr(0f))
        assertEquals("100,0%", formatProgressionFr(1f))
    }

    @Test
    fun `formatProgressionFr jamais de point ni deux decimales`() {
        val result = formatProgressionFr(0.347f)
        assertEquals(false, result.contains("."))
        // Une seule decimale : exactement un chiffre apres la virgule.
        val afterComma = result.substringAfter(',').substringBefore('%')
        assertEquals(1, afterComma.length)
    }

    @Test
    fun `alignedDelayToNextMinuteMillis n est pas un delai fixe de 60000`() {
        // A 12s dans la minute, il reste 48s avant la minute pleine - pas 60s.
        val nowAt12SecondsIntoMinute = 12_000L
        assertEquals(48_000L, alignedDelayToNextMinuteMillis(nowAt12SecondsIntoMinute))
    }

    @Test
    fun `alignedDelayToNextMinuteMillis pile sur la minute donne un delai complet`() {
        assertEquals(60_000L, alignedDelayToNextMinuteMillis(0L))
        assertEquals(60_000L, alignedDelayToNextMinuteMillis(120_000L))
    }
}
