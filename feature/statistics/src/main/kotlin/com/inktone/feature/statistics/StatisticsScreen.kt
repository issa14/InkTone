package com.inktone.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.InkToneSpacing
import com.inktone.domain.model.DailyReadingStats

/**
 * Tableau de bord statistiques (Lot Statistiques Palier 3).
 *
 * Trois sections rendues dans une [LazyColumn] avec
 * [safeDrawingPadding] pour ne pas être mangé par la barre de navigation.
 * L'état est consommé via [collectAsStateWithLifecycle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is com.inktone.domain.usecase.StatisticsUiState.Loading -> LoadingContent()
        is com.inktone.domain.usecase.StatisticsUiState.Ready -> DashboardContent(s)
    }
}

// ───── Loading ─────

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ───── Dashboard ─────

@Composable
private fun DashboardContent(state: com.inktone.domain.usecase.StatisticsUiState.Ready) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = InkToneSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item { Section1Kpis(state.kpi) }
        item { Section2Charts(state.activity) }
        if (state.currentBook != null) {
            item { Section3CurrentBook(state.currentBook!!) }
        }
        item { ExportButton() }
    }
}

// ═══════════════════════════════════════════════
// Section 1 — KPIs & Objectifs
// ═══════════════════════════════════════════════

@Composable
private fun Section1Kpis(kpi: com.inktone.domain.usecase.KpiState) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        Text("Objectifs & KPIs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        // Jauge circulaire + streak
        DailyGoalGauge(kpi)

        // Cartes de volumes : visuel, TTS, livres, streak max
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(
                icon = Icons.Outlined.Visibility, label = "Visuel",
                value = kpi.totalVisualTimeFormatted, modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.Headphones, label = "TTS",
                value = kpi.totalTtsTimeFormatted, modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(
                icon = Icons.Outlined.CheckCircle, label = "Livres finis",
                value = kpi.booksFinished.toString(), modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.Speed, label = "WPM",
                value = "${kpi.averageWpm} WPM", modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(
                icon = Icons.Outlined.TrendingUp, label = "Record",
                value = "${kpi.maxStreakDays} j", modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.CalendarMonth, label = "Série",
                value = "${kpi.currentStreakDays} j", modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DailyGoalGauge(kpi: com.inktone.domain.usecase.KpiState) {
    val progress = (kpi.todayReadingMinutes.toFloat() / kpi.dailyGoalMinutes).coerceIn(0f, 1f)
    val progressColor = if (progress >= 1f) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiary

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(InkToneSpacing.cardPadding).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Objectif du jour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 8.dp,
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeCap = StrokeCap.Round,
                    color = progressColor,
                    strokeWidth = 8.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${kpi.todayReadingMinutes} / ${kpi.dailyGoalMinutes} min",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Section 2 — Graphiques d'Activité
// ═══════════════════════════════════════════════

@Composable
private fun Section2Charts(activity: com.inktone.domain.usecase.ActivityChartState) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        // En-tête avec variation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val isPositive = activity.variationPercent.startsWith("+")
            val positiveGreen = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
            val varColor = when {
                activity.variationPercent == "—" -> MaterialTheme.colorScheme.onSurfaceVariant
                isPositive -> positiveGreen
                else -> MaterialTheme.colorScheme.error
            }
            Text(activity.variationPercent, style = MaterialTheme.typography.labelLarge, color = varColor)
        }

        // Heatmap
        HeatmapChart(activity.heatmapSlots)

        // Histogramme
        HistogramChart(activity.dailyStats)
    }
}

// ───── Heatmap Canvas ─────

@Composable
private fun HeatmapChart(slots: List<com.inktone.domain.usecase.HeatmapSlot>) {
    val dayLabels = listOf("L", "Ma", "Me", "J", "V", "S", "D")
    fun displayIndex(sqlDay: Int) = if (sqlDay == 0) 6 else sqlDay - 1

    // Pic horaire : créneau le plus actif (caching via remember)
    val peakSlot = remember(slots) { slots.maxByOrNull { it.intensity } }
    val slotNames = listOf("6h", "10h", "14h", "18h", "22h")
    val peakLabel = if (peakSlot != null) "Pic : ${slotNames[peakSlot.slotIndex]}" else ""

    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(InkToneSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Habitudes de lecture", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (peakLabel.isNotEmpty()) {
                    Text(peakLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))

            val accent = MaterialTheme.colorScheme.primary

            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val cols = 7
                val rows = 5
                val cellW = size.width / cols
                val cellH = size.height / rows

                val sepX = 5 * cellW
                drawLine(accent.copy(alpha = 0.15f), Offset(sepX, 0f), Offset(sepX, size.height), strokeWidth = 2.dp.toPx())

                for (slot in slots) {
                    val x = displayIndex(slot.dayOfWeek) * cellW
                    val y = slot.slotIndex * cellH
                    drawRoundRect(
                        color = accent.copy(alpha = slot.intensity.coerceIn(0f, 1f)),
                        topLeft = Offset(x + 2.dp.toPx(), y + 2.dp.toPx()),
                        size = Size(cellW - 4.dp.toPx(), cellH - 4.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                dayLabels.forEach { day ->
                    Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Matin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Soir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            // Légende d'intensité
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Inactif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                for (i in 0..4) {
                    Canvas(Modifier.size(10.dp)) {
                        drawRoundRect(accent.copy(alpha = (i + 1) / 5f), cornerRadius = CornerRadius(2.dp.toPx()))
                    }
                    if (i < 4) Spacer(Modifier.width(2.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("Très actif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───── Histogramme Canvas ─────

@Composable
private fun HistogramChart(dailyStats: List<DailyReadingStats>) {
    val visualColor = MaterialTheme.colorScheme.primary
    val ttsColor = MaterialTheme.colorScheme.tertiary

    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(InkToneSpacing.cardPadding)) {
            val maxMs = dailyStats.maxOfOrNull { it.visualMs + it.ttsMs }?.coerceAtLeast(1L) ?: 1L
            val barCount = dailyStats.size.coerceAtMost(30)

            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                if (barCount == 0) return@Canvas
                val barW = (size.width / barCount) * 0.7f
                val gap = (size.width / barCount) * 0.3f

                // Ligne de repère pointillée à 50%
                val midY = size.height * 0.5f
                var dashX = 0f
                while (dashX < size.width) {
                    drawLine(visualColor.copy(alpha = 0.2f), Offset(dashX, midY), Offset((dashX + 8.dp.toPx()).coerceAtMost(size.width), midY), strokeWidth = 1.dp.toPx())
                    dashX += 16.dp.toPx()
                }

                dailyStats.takeLast(barCount).forEachIndexed { i, day ->
                    val x = i * (barW + gap) + gap / 2
                    val ttsH = (day.ttsMs.toFloat() / maxMs * size.height)
                    val visualH = (day.visualMs.toFloat() / maxMs * size.height)
                    val totalH = ttsH + visualH

                    // Marqueur jour courant
                    if (i == barCount - 1 && barCount > 0) {
                        drawRect(
                            visualColor.copy(alpha = 0.3f),
                            Offset(x - 2.dp.toPx(), 0f),
                            Size(barW + 4.dp.toPx(), size.height),
                        )
                    }

                    // Empilement vertical : TTS en bas, visuel posé dessus.
                    // Le Y du visuel est dicté par la hauteur TTS en dessous.
                    if (ttsH > 1f) {
                        drawRect(ttsColor, Offset(x, size.height - ttsH), Size(barW, ttsH))
                    }
                    if (visualH > 1f) {
                        drawRect(visualColor, Offset(x, size.height - totalH), Size(barW, visualH))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // Légende en pied
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawRect(visualColor) }
                    Spacer(Modifier.width(4.dp))
                    Text("Visuel", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawRect(ttsColor) }
                    Spacer(Modifier.width(4.dp))
                    Text("TTS", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Section 3 — Livre en cours & Export
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Section3CurrentBook(book: com.inktone.domain.usecase.CurrentBookState) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        Text("Livre en cours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        ElevatedCard(
            onClick = { /* Navigation vers le Reader — TODO */ },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(InkToneSpacing.cardPadding).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    val progressPct = (book.progressPercent * 100).toInt()
                    Text(
                        "${progressPct}% · ${book.remainingTimeFormatted ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportButton() {
    var showSheet by remember { mutableStateOf(false) }

    Button(
        onClick = { showSheet = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Exporter les statistiques")
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Text(
                "Format d'export",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = InkToneSpacing.screenHorizontal, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text("Format CSV") },
                supportingContent = { Text("Récapitulatif des sessions") },
                leadingContent = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                modifier = Modifier.clickable { showSheet = false },
            )
            ListItem(
                headlineContent = { Text("Format JSON") },
                supportingContent = { Text("Données brutes d'événements") },
                leadingContent = { Icon(Icons.Outlined.ImportContacts, contentDescription = null) },
                modifier = Modifier.clickable { showSheet = false },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ───── Carte statistique générique ─────

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
