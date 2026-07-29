package com.inktone.feature.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.concurrent.TimeUnit

/**
 * Tache 8.6 — "livre termine" reutilise exactement la definition de
 * `FilterMode.READ` (Tache 6.5.2) via `GetStatisticsUseCase`, jamais une
 * deuxieme heuristique inventee ici.
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Statistiques de lecture")
        Text("Temps de lecture total : ${formatDuration(state.totalReadingTimeMs)}")
        Text("Livres termines : ${state.booksFinished}")
        Text("Serie de jours consecutifs : ${state.currentStreakDays}")
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    return "${hours}h ${minutes}min"
}
