package com.inktone.feature.statistics

import com.inktone.domain.usecase.CurrentBookState
import com.inktone.domain.usecase.KpiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsEmptyStateTest {

    private fun kpi(visual: String = "0h 0m", tts: String = "0h 0m", finished: Int = 0, todayMinutes: Long = 0L) = KpiState(
        totalVisualTimeFormatted = visual, totalTtsTimeFormatted = tts, booksFinished = finished,
        currentStreakDays = 0, maxStreakDays = 0, averageWpm = 0, todayReadingMinutes = todayMinutes,
        dailyGoalMinutes = 30, regularityLabel = "Pas de série en cours",
    )

    @Test
    fun `un compte neuf est vide`() {
        assertTrue(isStatisticsEmpty(kpi(), currentBook = null))
    }

    @Test
    fun `la moindre lecture rend les statistiques non vides`() {
        assertFalse(isStatisticsEmpty(kpi(visual = "0h 5m"), null))
        assertFalse(isStatisticsEmpty(kpi(tts = "1h 0m"), null))
        assertFalse(isStatisticsEmpty(kpi(finished = 1), null))
        assertFalse(isStatisticsEmpty(kpi(todayMinutes = 3L), null))
        assertFalse(
            isStatisticsEmpty(
                kpi(),
                CurrentBookState(id = "b", title = "T", coverUri = null, progressPercent = 0.1f, remainingTimeFormatted = null),
            ),
        )
    }
}
