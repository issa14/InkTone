package com.inktone.feature.reader

import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 9, Tâche 9.3 point 4 — la bascule cyclique du lecteur reste bornée
 * sur `ReadingTheme.CYCLE` (3 ambiances, tranché Tâche 9.2) et revient à
 * son point de départ en un nombre fixe de taps, comme l'ancien enum
 * LIGHT→DARK→SEPIA→LIGHT.
 */
class NextReadingThemeTest {

    @Test
    fun le_cycle_boucle_sur_lui_meme_en_trois_taps() {
        val start = ReadingTheme.PAPIER_CLAIR.id
        val afterOne = nextReadingTheme(start)
        val afterTwo = nextReadingTheme(afterOne)
        val afterThree = nextReadingTheme(afterTwo)

        assertEquals(ReadingTheme.OBSIDIENNE.id, afterOne)
        assertEquals(ReadingTheme.SEPIA_VINTAGE.id, afterTwo)
        assertEquals(start, afterThree)
    }

    @Test
    fun un_id_hors_cycle_repart_sur_la_premiere_ambiance() {
        // Thème personnalisé actif, ou ancien id migré non couvert par le
        // cycle : ne doit jamais rester coincé hors cycle.
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, nextReadingTheme("un-theme-personnalise"))
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, nextReadingTheme(ReadingTheme.SAUGE_OLIVE.id))
    }
}
