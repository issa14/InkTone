package com.inktone.domain.service

import com.inktone.domain.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou du comptage de mots (chantier statistiques V1).
 *
 * Le compteur de mots du tracker est ce qui alimente `ReadingSession.wordsRead`,
 * resté à zéro en base pendant toute la vie du projet — et avec lui tout KPI qui
 * en dérive. Ces cas fixent son contrat : ce qui s'accumule, ce qui repart à
 * zéro, et ce qui n'est jamais retiré.
 */
class ReadingSessionTrackerTest {

    /** Horloge manuelle : ces cas portent sur les compteurs, pas sur la cadence. */
    private class TestClock(var nowMs: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = nowMs
    }

    @Test
    fun les_mots_parcourus_s_accumulent_sur_toute_la_session() {
        val tracker = ReadingSessionTracker("pub1", TestClock())

        tracker.addProgress(words = 12)
        tracker.addProgress(words = 8)

        assertEquals(20, tracker.wordsRead)
        assertEquals(2, tracker.sentencesRead)
    }

    @Test
    fun les_mots_lus_a_l_ecran_sont_comptes_a_part_des_mots_narres() {
        val tracker = ReadingSessionTracker("pub1", TestClock())

        tracker.addProgress(words = 12) // lecture visuelle (mode par défaut)
        tracker.switchMode(ReadingMode.AUDIO)
        tracker.addProgress(words = 30)
        tracker.switchMode(ReadingMode.VISUAL)
        tracker.addProgress(words = 8)

        assertEquals(50, tracker.wordsRead)
        assertEquals(20, tracker.visualWordsRead)
        assertEquals(20, tracker.snapshot().visualWords)
    }

    @Test
    fun un_recul_de_position_ne_retire_jamais_de_mots_deja_lus() {
        val tracker = ReadingSessionTracker("pub1", TestClock())
        tracker.addProgress(words = 30)

        tracker.addProgress(words = -10)
        tracker.addProgress(words = 0)

        assertEquals(
            "revenir en arrière n'annule pas une lecture déjà faite",
            30,
            tracker.wordsRead,
        )
        assertEquals(1, tracker.sentencesRead)
    }

    @Test
    fun l_instantane_remonte_les_mots_et_les_durees_ensemble() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)
        tracker.resume(ReadingMode.VISUAL)
        tracker.addProgress(words = 250, sentences = 10)
        clock.nowMs += 60_000L

        val snapshot = tracker.snapshot()

        assertEquals(250, snapshot.words)
        assertEquals(10, snapshot.sentences)
        assertEquals(60_000L, snapshot.visualMs)
        assertEquals(0L, snapshot.ttsMs)
        assertEquals(60_000L, snapshot.totalMs)
    }

    @Test
    fun un_fragment_sauvegarde_repart_de_zero_sans_recompter_ses_mots() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)
        tracker.resume(ReadingMode.VISUAL)
        tracker.addProgress(words = 250, sentences = 10)
        clock.nowMs += 60_000L
        tracker.snapshot()

        tracker.reset()
        tracker.addProgress(words = 40, sentences = 2)
        clock.nowMs += 10_000L
        val second = tracker.snapshot()

        assertEquals(
            "les mots du fragment déjà persisté ne doivent pas être comptés deux fois",
            40,
            second.words,
        )
        assertEquals(2, second.sentences)
        assertEquals(10_000L, second.visualMs)
    }

    @Test
    fun le_temps_ecoute_est_impute_au_TTS_sans_toucher_au_compteur_de_mots() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)
        tracker.resume(ReadingMode.VISUAL)
        clock.nowMs += 10_000L

        tracker.switchMode(ReadingMode.AUDIO)
        tracker.addProgress(words = 100, sentences = 4)
        clock.nowMs += 20_000L

        val snapshot = tracker.snapshot()

        assertEquals(10_000L, snapshot.visualMs)
        assertEquals(20_000L, snapshot.ttsMs)
        assertEquals(
            "les mots comptent quel que soit le mode qui les a fait défiler",
            100,
            snapshot.words,
        )
    }

    /**
     * Bug réel corrigé (audit UX D25) : un livre laissé ouvert écran allumé
     * comptait tout ce temps comme de la lecture.
     */
    @Test
    fun la_lecture_visuelle_cesse_de_compter_apres_cinq_minutes_sans_geste() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub", clock)

        clock.nowMs += 3 * 3_600_000L // trois heures sans aucun geste

        assertEquals(5 * 60_000L, tracker.snapshot().visualMs)
    }

    @Test
    fun un_geste_relance_le_decompte_visuel_sans_compter_l_inactivite_precedente() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub", clock)
        clock.nowMs += 60 * 60_000L // une heure d'inactivité : 5 min comptées
        tracker.noteActivity()

        clock.nowMs += 2 * 60_000L // deux minutes de lecture active

        assertEquals(7 * 60_000L, tracker.snapshot().visualMs)
    }

    @Test
    fun l_ecoute_compte_meme_sans_aucun_geste() {
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub", clock)
        tracker.switchMode(ReadingMode.AUDIO)

        clock.nowMs += 3_600_000L

        assertEquals(3_600_000L, tracker.snapshot().ttsMs)
    }
}
