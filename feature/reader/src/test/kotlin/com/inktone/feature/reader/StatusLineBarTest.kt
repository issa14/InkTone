package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tâche 3b.7, tests 1 et 9 — formatage de la ligne de statut et alignement de l'horloge. */
class StatusLineBarTest {

    @Test
    fun `formatProgressionFr suit le pourcentage partage avec la Bibliotheque`() {
        // Audit UX A19 : une décimale ici, l'entier ailleurs — trois arrondis
        // pour une même position.
        assertEquals("35%", formatProgressionFr(0.347f))
        assertEquals("0%", formatProgressionFr(0f))
        assertEquals("un livre terminé affiche 100 %", "100%", formatProgressionFr(0.985f))
        assertEquals("100%", formatProgressionFr(1f))
    }

    @Test
    fun `formatProgressionFr affiche un entier sans separateur decimal`() {
        val result = formatProgressionFr(0.347f)
        assertEquals(false, result.contains("."))
        assertEquals(false, result.contains(","))
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

    @Test
    fun `chapterCounterText affiche le compteur quand showPageCounter est vrai`() {
        assertEquals("Chapitre 12 (54/54)", chapterCounterText(12, 54, 54, showPageCounter = true))
        assertEquals("Chapitre 3 (1/47)", chapterCounterText(3, 1, 47, showPageCounter = true))
    }

    @Test
    fun `chapterCounterText masque le compteur quand showPageCounter est faux`() {
        // Mesure partielle : jamais de total partiel présenté comme final.
        assertEquals("Chapitre 12", chapterCounterText(12, 54, 54, showPageCounter = false))
    }

    // Audit UX B3 : un PDF n'a pas de chapitres.
    @Test
    fun `fixedPageCounterText ecrit Page N sur total`() {
        assertEquals("Page 31 / 356", fixedPageCounterText(31, 356))
        assertEquals("Page 31", fixedPageCounterText(31, 0))
    }
}
