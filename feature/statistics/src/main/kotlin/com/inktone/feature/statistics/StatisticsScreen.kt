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
import java.util.concurrent.TimeUnit

/**
 * Tache 9bis.6 — porte la structure visuelle (cartes) plutot que le
 * texte brut de la Tache 8.6. Pas de graphique temporel (courbe/barres
 * par jour) : `StatisticsUiState` n'expose que 3 valeurs agregees,
 * aucune serie temporelle en base pour l'alimenter honnetement - ajouter
 * un graphique ici afficherait des donnees inventees, contraire au
 * principe du projet (Blueprint §17.2).
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(InkToneSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
    ) {
        // D.4 — Objectif du jour avec jauge
        val goalProgress = (state.todayReadingMinutes.toFloat() / state.dailyGoalMinutes).coerceIn(0f, 1f)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(InkToneSpacing.cardPadding)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Objectif du jour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.todayReadingMinutes} / ${state.dailyGoalMinutes} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // D.4 — Série + record
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(icon = Icons.Outlined.CalendarMonth, label = "Série", value = "${state.currentStreakDays} j", modifier = Modifier.weight(1f))
            StatCard(icon = Icons.Outlined.TrendingUp, label = "Record", value = "${state.maxStreakDays} j", modifier = Modifier.weight(1f))
        }

        StatCard(icon = Icons.Outlined.Speed, label = "Vitesse moyenne", value = "${state.averageWpm} WPM")
        StatCard(icon = Icons.Outlined.Schedule, label = "Temps de lecture total", value = formatDuration(state.totalReadingTimeMs))
        StatCard(icon = Icons.Outlined.CheckCircle, label = "Livres terminés", value = state.booksFinished.toString())
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

private fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    return "${hours}h ${minutes}min"
}
