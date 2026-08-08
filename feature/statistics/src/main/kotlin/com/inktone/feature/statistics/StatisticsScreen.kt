package com.inktone.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.InkToneSpacing
import com.inktone.domain.usecase.StatisticsUiState

/**
 * Tableau de bord statistiques (Lot Statistiques Palier 2).
 *
 * L'état est exposé par le ViewModel en sealed interface
 * ([StatisticsUiState.Loading] / [StatisticsUiState.Ready]).
 * Les durées arrivent déjà formatées du ViewModel (ex: "14h 32m").
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is StatisticsUiState.Loading -> {
            // Rien à afficher en chargement — le ViewModel résout en ~ms
        }
        is StatisticsUiState.Ready -> {
            StatisticsContent(s.kpi, s.activity, s.currentBook)
        }
    }
}

@Composable
private fun StatisticsContent(
    kpi: com.inktone.domain.usecase.KpiState,
    activity: com.inktone.domain.usecase.ActivityChartState,
    currentBook: com.inktone.domain.usecase.CurrentBookState?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(InkToneSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
    ) {
        // Objectif du jour avec jauge
        val goalProgress = (kpi.todayReadingMinutes.toFloat() / kpi.dailyGoalMinutes).coerceIn(0f, 1f)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(InkToneSpacing.cardPadding)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Objectif du jour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${kpi.todayReadingMinutes} / ${kpi.dailyGoalMinutes} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // Série + record
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(icon = Icons.Outlined.CalendarMonth, label = "Série", value = "${kpi.currentStreakDays} j", modifier = Modifier.weight(1f))
            StatCard(icon = Icons.Outlined.TrendingUp, label = "Record", value = "${kpi.maxStreakDays} j", modifier = Modifier.weight(1f))
        }

        StatCard(icon = Icons.Outlined.Speed, label = "Vitesse moyenne", value = "${kpi.averageWpm} WPM")
        StatCard(icon = Icons.Outlined.Schedule, label = "Temps visuel", value = kpi.totalVisualTimeFormatted)
        StatCard(icon = Icons.Outlined.Schedule, label = "Temps TTS", value = kpi.totalTtsTimeFormatted)
        StatCard(icon = Icons.Outlined.CheckCircle, label = "Livres terminés", value = kpi.booksFinished.toString())
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(InkToneSpacing.cardPadding),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = InkToneSpacing.md)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
