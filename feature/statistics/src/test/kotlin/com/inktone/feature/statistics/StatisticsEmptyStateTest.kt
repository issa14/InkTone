package com.inktone.feature.statistics

import com.inktone.domain.usecase.CurrentBookState
import com.inktone.domain.usecase.KpiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsEmptyStateTest {

    private fun kpi(visual: String = "0\u202Fmin", tts: String = "0\u202Fmin", finished: Int = 0, todayMinutes: Long = 0L) = KpiState(
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
        assertFalse(isStatisticsEmpty(kpi(visual = "5\u202Fmin"), null))
        assertFalse(isStatisticsEmpty(kpi(tts = "1\u202Fh\u202F00"), null))
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
