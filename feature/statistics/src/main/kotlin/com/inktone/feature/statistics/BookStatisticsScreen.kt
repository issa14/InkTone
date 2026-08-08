package com.inktone.feature.statistics

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.InkToneSpacing

/**
 * Écran de détail par ouvrage (Lot Statistiques Palier 4).
 *
 * Affiche l'historique temporel des sessions d'un livre, ses KPIs
 * (WPM, temps restant), et un sélecteur d'ouvrage dans la TopBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookStatisticsScreen(
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    viewModel: BookStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when (val s = state) {
                        is BookDetailUiState.Loading -> Text("Chargement…")
                        is BookDetailUiState.Ready -> BookSelectorTitle(
                            currentTitle = s.bookTitle,
                            books = s.availableBooks,
                            onSelect = onSelectBook,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            is BookDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BookDetailUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = InkToneSpacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
                    contentPadding = PaddingValues(
                        top = InkToneSpacing.md,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                ) {
                    // KPIs
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
                        ) {
                            KpiCard(
                                icon = Icons.Outlined.Speed, label = "Vitesse",
                                value = s.wpmFormatted, modifier = Modifier.weight(1f),
                            )
                            KpiCard(
                                icon = Icons.Outlined.Timer, label = "Temps restant",
                                value = s.remainingTimeFormatted, modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Historique
                    item {
                        Text(
                            "Historique des sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(s.history, key = { "${it.dateFormatted}_${it.timeRange}" }) { item ->
                        SessionHistoryRow(item)
                    }
                }
            }
        }
    }
}

// ───── Sélecteur d'ouvrage ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSelectorTitle(
    currentTitle: String,
    books: List<BookSelectorItem>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            modifier = Modifier.menuAnchor().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                currentTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            books.forEach { book ->
                DropdownMenuItem(
                    text = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(book.id)
                    },
                )
            }
        }
    }
}

// ───── Carte KPI ─────

@Composable
private fun KpiCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
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

// ───── Ligne d'historique de session ─────

@Composable
private fun SessionHistoryRow(item: SessionHistoryItem) {
    ListItem(
        headlineContent = { Text(item.dateFormatted) },
        supportingContent = { Text(item.timeRange) },
        leadingContent = {
            // Tache 7.3 — icône(s) de mode à taille pleine (24 dp, taille par
            // défaut d'Icon) : une session mixte affiche les deux icônes,
            // sans réduction — pas de notion de "mode dominant".
            if (item.isMixed) {
                Row {
                    Icon(AppIcons.VisualReading, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(AppIcons.TtsListening, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
            } else if (item.isVisual) {
                Icon(AppIcons.VisualReading, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(AppIcons.TtsListening, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
        },
        trailingContent = {
            // Durée totale au-dessus, ventilation par mode en dessous pour
            // les sessions mixtes. `clearAndSetSemantics` remplace
            // l'annonce TalkBack morcelée (nombres nus) par une phrase
            // unique — ex. "45 minutes, dont 30 en lecture et 15 en écoute".
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clearAndSetSemantics { contentDescription = item.accessibilityLabel },
            ) {
                Text(item.durationFormatted, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.isMixed) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.visualMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(2.dp))
                        Icon(AppIcons.VisualReading, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${item.ttsMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(2.dp))
                        Icon(AppIcons.TtsListening, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
