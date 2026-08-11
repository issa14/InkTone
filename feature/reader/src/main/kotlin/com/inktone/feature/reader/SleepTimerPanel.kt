package com.inktone.feature.reader

import com.inktone.core.designsystem.AppIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 3d.4 — Panneau Minuteur : deux fonctions sous une seule icône (voir doc
 * du lot 3d, tâche 3d.4, et `UX_FLOW_DESIGN.md` §Minuteur). Section 1 —
 * minuteur de sommeil TTS (ce fichier) : puces 15/30/45 + roue de sélection
 * personnalisée (heures/minutes), remplace le cycle `nextSleepTimerMinutes`
 * retiré de `ReaderScreen`. Puces et roue émettent le MÊME intent
 * (`ReaderIntent.SetSleepTimer`) — même sorte d'état, pas deux mécanismes
 * distincts.
 *
 * La valeur 60 des anciennes puces (enfouies dans le panneau Voix avant ce
 * lot) n'est pas reprise ici : la roue la couvre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerPanel(
    remainingMinutes: Int?,
    onSetSleepTimer: (Int?) -> Unit,
    eyeRestReminderEnabled: Boolean,
    eyeRestReminderIntervalMinutes: Int,
    onSetEyeRestReminderEnabled: (Boolean) -> Unit,
    onSetEyeRestReminderInterval: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Minuteur de sommeil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            if (remainingMinutes != null) {
                Text(
                    "Actif — $remainingMinutes min restantes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { onSetSleepTimer(null) }) { Text("Annuler") }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SLEEP_TIMER_CHIP_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = remainingMinutes == minutes,
                        onClick = { onSetSleepTimer(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Durée personnalisée", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            CustomDurationWheel(onConfirm = { totalMinutes -> onSetSleepTimer(totalMinutes) })

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Section 2 — Repos oculaire, indépendant du TTS (3d.5) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Rappel de repos oculaire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(checked = eyeRestReminderEnabled, onCheckedChange = onSetEyeRestReminderEnabled)
            }

            if (eyeRestReminderEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("Intervalle", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSetEyeRestReminderInterval((eyeRestReminderIntervalMinutes - 15).coerceAtLeast(15)) },
                    ) { Icon(AppIcons.Remove, contentDescription = "Diminuer l'intervalle") }
                    Text(formatEyeRestInterval(eyeRestReminderIntervalMinutes), style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = { onSetEyeRestReminderInterval(eyeRestReminderIntervalMinutes + 15) },
                    ) { Icon(AppIcons.Add, contentDescription = "Augmenter l'intervalle") }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 3d.5 — format `1h30`, pas seulement des heures rondes (UX_FLOW_DESIGN.md §Minuteur). */
private fun formatEyeRestInterval(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}min"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h$minutes"
    }
}

private val SLEEP_TIMER_CHIP_MINUTES = listOf(15, 30, 45)

/**
 * Roue à deux colonnes (heures/minutes), pas de pas de 15 minutes — la
 * cible distingue explicitement la roue personnalisée des puces fixes
 * (`UX_FLOW_DESIGN.md` §Minuteur, section 1). `rememberSnapFlingBehavior`
 * (foundation, pas de dépendance nouvelle) centre l'item le plus proche
 * après un lâcher, `firstVisibleItemScrollOffset == 0` au repos signale la
 * valeur sélectionnée — patron standard de wheel-picker Compose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomDurationWheel(onConfirm: (totalMinutes: Int) -> Unit) {
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(30) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        WheelColumn(range = 0..5, selected = hours, onSelect = { hours = it }, modifier = Modifier.width(56.dp))
        Text("h", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        WheelColumn(range = 0..59, selected = minutes, onSelect = { minutes = it }, modifier = Modifier.width(56.dp))
        Text("min", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        TextButton(
            onClick = { onConfirm((hours * 60 + minutes).coerceAtLeast(1)) },
            enabled = hours > 0 || minutes > 0,
        ) { Text("Valider") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    range: IntRange,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 36.dp
    val visibleItems = 3
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - range.first).coerceIn(0, range.last - range.first))
    val flingBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (isScrolling, index, offset) ->
                if (!isScrolling && offset == 0) {
                    onSelect((range.first + index).coerceIn(range))
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(itemHeight * visibleItems),
        contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
    ) {
        items(range.last - range.first + 1) { i ->
            val value = range.first + i
            Box(
                modifier = Modifier.height(itemHeight).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(value),
                    fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
