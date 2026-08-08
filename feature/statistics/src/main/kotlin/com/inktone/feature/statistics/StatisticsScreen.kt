package com.inktone.feature.statistics

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.InkToneSpacing
import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.service.ExportFormat
import com.inktone.domain.usecase.StatsPeriod
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * Tableau de bord statistiques (Lot Statistiques Palier 3).
 *
 * Trois sections rendues dans une [LazyColumn] avec
 * [safeDrawingPadding] pour ne pas être mangé par la barre de navigation.
 * L'état est consommé via [collectAsStateWithLifecycle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateToBookDetail: (String) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { event ->
            when (event) {
                is ExportEvent.Share -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.format.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
                is ExportEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    when (val s = state) {
        is com.inktone.domain.usecase.StatisticsUiState.Loading -> LoadingContent()
        is com.inktone.domain.usecase.StatisticsUiState.Ready -> DashboardContent(s, onNavigateToBookDetail, viewModel)
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
private fun DashboardContent(state: com.inktone.domain.usecase.StatisticsUiState.Ready, onNavigateToBookDetail: (String) -> Unit, viewModel: StatisticsViewModel) {
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
        item { Section2Charts(state.activity, onPeriodSelected = viewModel::onPeriodSelected) }
        if (state.currentBook != null) {
            item { Section3CurrentBook(state.currentBook!!, onNavigateToBookDetail) }
        }
        item { ExportButton(viewModel) }
    }
}

// ═══════════════════════════════════════════════
// Section 1 — KPIs & Objectifs
// ═══════════════════════════════════════════════

@Composable
private fun Section1Kpis(kpi: com.inktone.domain.usecase.KpiState) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        // Bloc 1 — Objectif du jour : jauge circulaire, Série et Record en
        // regard, libellé de régularité (Tache 7.2).
        DailyGoalGauge(kpi)

        // Bloc 2 — Ventilation : Lecture visuelle · Écoute TTS (conforme à la
        // cible, inchangé depuis le palier précédent).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(
                icon = AppIcons.VisualReading, label = "Visuel",
                value = kpi.totalVisualTimeFormatted, modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = AppIcons.TtsListening, label = "TTS",
                value = kpi.totalTtsTimeFormatted, modifier = Modifier.weight(1f),
            )
        }

        // Bloc 3 — Volumes parcourus : Livres finis · Pages lues · Mots
        // parcourus (format abrégé). Le WPM sort du tableau de bord — il
        // vit désormais uniquement au niveau de l'ouvrage (Section 4).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
            StatCard(
                icon = Icons.Outlined.CheckCircle, label = "Livres finis",
                value = kpi.booksFinished.toString(), modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = AppIcons.Reading, label = "Pages lues",
                value = kpi.totalPagesReadFormatted, modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.Article, label = "Mots parcourus",
                value = kpi.totalWordsReadFormatted, modifier = Modifier.weight(1f),
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
            Text("Objectif du jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                GoalStreakItem(icon = AppIcons.Streak, label = "Série", value = "${kpi.currentStreakDays} j")
                GoalStreakItem(icon = Icons.Outlined.TrendingUp, label = "Record", value = "${kpi.maxStreakDays} j")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                kpi.regularityLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalStreakItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════════════════════
// Section 2 — Graphiques d'Activité
// ═══════════════════════════════════════════════

@Composable
private fun Section2Charts(activity: com.inktone.domain.usecase.ActivityChartState, onPeriodSelected: (StatsPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        // En-tête : titre + total de la période + variation, sélecteur Semaine/Mois (Tache 7.4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Activité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        activity.periodTotalFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    val isPositive = activity.variationPercent.startsWith("+")
                    val positiveGreen = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
                    val varColor = when {
                        activity.variationPercent == "—" -> MaterialTheme.colorScheme.onSurfaceVariant
                        isPositive -> positiveGreen
                        else -> MaterialTheme.colorScheme.error
                    }
                    Text(activity.variationPercent, style = MaterialTheme.typography.labelLarge, color = varColor)
                }
            }
            PeriodSelector(selected = activity.period, onSelected = onPeriodSelected)
        }

        // Heatmap
        HeatmapChart(activity.heatmapSlots, activity.peakSlotIndex)

        // Histogramme
        HistogramChart(activity.dailyStats)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelected: (StatsPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = selected == StatsPeriod.WEEK,
            onClick = { onSelected(StatsPeriod.WEEK) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("Semaine") },
        )
        SegmentedButton(
            selected = selected == StatsPeriod.MONTH,
            onClick = { onSelected(StatsPeriod.MONTH) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("Mois") },
        )
    }
}

// ───── Constantes de heatmap (top-level, pas d'allocation par recomposition) ─────

private val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
private val SLOT_NAMES = listOf("6h", "10h", "14h", "18h", "22h")

// ───── Heatmap Canvas ─────

// Grille de la heatmap : hauteur totale et hauteur par ligne partagées entre
// le Canvas et l'axe des créneaux horaires pour qu'ils restent alignés.
private val HEATMAP_GRID_HEIGHT = 150.dp
private val HEATMAP_ROW_HEIGHT = HEATMAP_GRID_HEIGHT / SLOT_NAMES.size

// Légende d'intensité à 5 paliers ; le premier (le plus faible) est aussi
// la teinte par défaut des cases sans aucune activité — sans quoi une case
// "inactif" et une case "pas de données" sont visuellement indissociables
// (transparente = fond de la carte, dans les deux cas).
private const val HEATMAP_LEGEND_STEPS = 5
private const val HEATMAP_INACTIVE_ALPHA = 1f / HEATMAP_LEGEND_STEPS

@Composable
private fun HeatmapChart(slots: List<com.inktone.domain.usecase.HeatmapSlot>, peakSlotIndex: Int?) {
    fun displayIndex(sqlDay: Int) = if (sqlDay == 0) 6 else sqlDay - 1

    val peakLabel = peakSlotIndex?.let { "Pic : ${SLOT_NAMES[it]}" } ?: ""
    // Intensité par case (dayOfWeek SQL, slotIndex) — absente si aucune
    // activité, auquel cas la case est peinte avec HEATMAP_INACTIVE_ALPHA.
    val intensityByCell = remember(slots) { slots.associate { (it.dayOfWeek to it.slotIndex) to it.intensity } }

    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(InkToneSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Habitudes de lecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (peakLabel.isNotEmpty()) {
                    Text(peakLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))

            val accent = MaterialTheme.colorScheme.primary

            Row(modifier = Modifier.fillMaxWidth()) {
                // Axe des créneaux horaires — une ligne de texte par ligne de
                // grille, alignée via la même hauteur (HEATMAP_ROW_HEIGHT).
                Column(modifier = Modifier.width(28.dp)) {
                    SLOT_NAMES.forEach { name ->
                        Box(modifier = Modifier.height(HEATMAP_ROW_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(HEATMAP_GRID_HEIGHT)) {
                        val cols = 7
                        val rows = SLOT_NAMES.size
                        val cellW = size.width / cols
                        val cellH = size.height / rows

                        val sepX = 5 * cellW
                        drawLine(accent.copy(alpha = 0.15f), Offset(sepX, 0f), Offset(sepX, size.height), strokeWidth = 2.dp.toPx())

                        // Les 35 cases (7 jours × 5 créneaux) sont toujours
                        // dessinées — jamais seulement celles avec activité,
                        // sans quoi "inactif" et "sans données" sont
                        // indiscernables (toutes deux transparentes).
                        for (sqlDay in 0..6) {
                            for (slotIndex in SLOT_NAMES.indices) {
                                val intensity = intensityByCell[sqlDay to slotIndex] ?: HEATMAP_INACTIVE_ALPHA
                                val x = displayIndex(sqlDay) * cellW
                                val y = slotIndex * cellH
                                drawRoundRect(
                                    color = accent.copy(alpha = intensity),
                                    topLeft = Offset(x + 2.dp.toPx(), y + 2.dp.toPx()),
                                    size = Size(cellW - 4.dp.toPx(), cellH - 4.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DAY_LABELS.forEach { day ->
                            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Légende d'intensité
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Inactif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                for (i in 0 until HEATMAP_LEGEND_STEPS) {
                    Canvas(Modifier.size(10.dp)) {
                        drawRoundRect(accent.copy(alpha = (i + 1) * HEATMAP_INACTIVE_ALPHA), cornerRadius = CornerRadius(2.dp.toPx()))
                    }
                    if (i < HEATMAP_LEGEND_STEPS - 1) Spacer(Modifier.width(2.dp))
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
            val maxMs = remember(dailyStats) {
                dailyStats.maxOfOrNull { it.visualMs + it.ttsMs }?.coerceAtLeast(1L) ?: 1L
            }
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
private fun Section3CurrentBook(book: com.inktone.domain.usecase.CurrentBookState, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md)) {
        Text("Livre en cours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        ElevatedCard(
            onClick = { onNavigate(book.id) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(InkToneSpacing.cardPadding).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrentBookCoverThumbnail(book.coverUri)
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

// Tache 7.5 — miniature de couverture réelle, repli sur l'icône générique
// si `coverUri` est absent ou si le décodage échoue (même résolution
// URI que `BookCover` de `feature/library`, dupliquée ici car les modules
// de feature ne dépendent pas les uns des autres).
@Composable
private fun CurrentBookCoverThumbnail(coverUri: String?) {
    val context = LocalContext.current
    val coverModel: Any? = remember(coverUri) {
        when {
            coverUri == null -> null
            coverUri.startsWith("content://") -> Uri.parse(coverUri)
            else -> File(coverUri).takeIf { it.exists() }
        }
    }

    if (coverModel == null) {
        Icon(
            AppIcons.Reading, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(coverModel).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
        error = {
            Icon(
                AppIcons.Reading, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportButton(viewModel: StatisticsViewModel) {
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
                modifier = Modifier.clickable {
                    showSheet = false
                    viewModel.export(ExportFormat.CSV)
                },
            )
            ListItem(
                headlineContent = { Text("Format JSON") },
                supportingContent = { Text("Données brutes d'événements") },
                leadingContent = { Icon(Icons.Outlined.ImportContacts, contentDescription = null) },
                modifier = Modifier.clickable {
                    showSheet = false
                    viewModel.export(ExportFormat.JSON)
                },
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
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
