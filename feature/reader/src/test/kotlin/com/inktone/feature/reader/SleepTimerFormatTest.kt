package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le décompte du minuteur affichait des minutes entières tronquées : un
 * minuteur de 30 minutes montrait « 29 min » pendant une minute pleine, puis
 * sautait à 28. Rien ne permettait de voir qu'il tournait réellement.
 */
class SleepTimerFormatTest {

    @Test
    fun affiche_les_secondes_en_dessous_dune_heure() {
        assertEquals("29 min 52 s", formatSleepTimerRemaining(29 * 60_000L + 52_000L))
    }

    @Test
    fun sous_la_minute_seules_les_secondes_restent() {
        assertEquals("7 s", formatSleepTimerRemaining(7_000L))
    }

    @Test
    fun au_dela_dune_heure_les_secondes_disparaissent() {
        // À cette échelle elles n'apprennent rien et allongent la ligne.
        assertEquals("1 h 5 min", formatSleepTimerRemaining(65 * 60_000L))
    }

    @Test
    fun arrondi_superieur_pour_ne_jamais_afficher_zero_avant_la_fin() {
        // 500 ms restantes : il RESTE du temps, afficher « 0 s » laisserait
        // croire que le minuteur est déjà passé.
        assertEquals("1 s", formatSleepTimerRemaining(500L))
    }

    @Test
    fun zero_reste_zero() {
        assertEquals("0 s", formatSleepTimerRemaining(0L))
    }

    @Test
    fun une_valeur_negative_ne_produit_pas_daffichage_absurde() {
        assertEquals("0 s", formatSleepTimerRemaining(-1_000L))
    }
}
