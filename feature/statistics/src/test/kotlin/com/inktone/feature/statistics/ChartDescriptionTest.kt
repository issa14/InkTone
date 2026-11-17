package com.inktone.feature.statistics

import com.inktone.domain.model.DailyReadingStats
import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit UI P1-8 : les graphiques disent à TalkBack ce qu'ils montrent. */
class ChartDescriptionTest {

    private val nnbsp = ' '

    @Test
    fun l_histogramme_resume_la_duree_totale_et_la_part_d_ecoute() {
        val days = listOf(
            DailyReadingStats("2026-11-16", visualMs = 90 * 60_000L, ttsMs = 0),
            DailyReadingStats("2026-11-17", visualMs = 40 * 60_000L, ttsMs = 70 * 60_000L),
        )
        assertEquals(
            "2 derniers jours : 3${nnbsp}h${nnbsp}20, dont 1${nnbsp}h${nnbsp}10 d'écoute.",
            histogramDescription(days),
        )
    }

    @Test
    fun sans_ecoute_l_histogramme_ne_cite_que_la_duree() {
        val days = listOf(DailyReadingStats("2026-11-17", visualMs = 25 * 60_000L, ttsMs = 0))
        assertEquals("Aujourd'hui : 25${nnbsp}min.", histogramDescription(days))
    }

    @Test
    fun la_heatmap_annonce_son_creneau_le_plus_actif() {
        assertEquals("Habitudes de lecture : moment le plus actif vers 14h.", heatmapDescription(2))
        assertEquals("Habitudes de lecture : aucune activité sur la période.", heatmapDescription(null))
    }
}
