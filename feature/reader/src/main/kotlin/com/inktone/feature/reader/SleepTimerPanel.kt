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
import androidx.compose.runtime.derivedStateOf
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    /** Temps restant du minuteur en cours, `null` si aucun. */
    remainingMs: Long?,
    /** Durée initialement armée, pour que la puce choisie reste sélectionnée pendant tout le décompte. */
    armedMinutes: Int?,
    onSetSleepTimer: (Int?) -> Unit,
    eyeRestReminderEnabled: Boolean,
    eyeRestReminderIntervalMinutes: Int,
    onSetEyeRestReminderEnabled: (Boolean) -> Unit,
    onSetEyeRestReminderInterval: (Int) -> Unit,
    onDismiss: () -> Unit,
    // Lot 12, tache 12.10 — le minuteur de sommeil n'a pas de sens sans
    // lecture audio a mettre en pause (decision actee 16 du plan) ; le
    // repos oculaire (Section 2 ci-dessous), independant du TTS, reste
    // toujours accessible — c'est pourquoi ce panneau reste ouvrable pour
    // un PDF plutot que de masquer son bouton declencheur en bloc.
    showSleepTimer: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (showSleepTimer) {
                Text(
                    "Minuteur de sommeil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))

                if (remainingMs != null) {
                    Text(
                        "Actif — ${formatSleepTimerRemaining(remainingMs)} restantes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onSetSleepTimer(null) }) { Text("Annuler") }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SLEEP_TIMER_CHIP_MINUTES.forEach { minutes ->
                        FilterChip(
                            // Comparée à la durée ARMÉE, pas au temps restant :
                            // sinon la puce choisie se désélectionnerait au bout
                            // d'une seconde de décompte.
                            selected = armedMinutes == minutes,
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
            }

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

/**
 * Temps restant du minuteur, **secondes comprises** : « 29 min 52 s ».
 *
 * L'affichage se contentait des minutes entières (division tronquée), si bien
 * qu'un minuteur de 30 minutes affichait « 29 min restantes » pendant une
 * minute entière puis sautait à 28 — impossible de voir qu'il tournait
 * réellement. L'ordonnanceur décompte déjà à la seconde ; il ne manquait que
 * de l'afficher.
 *
 * Les secondes disparaissent au-delà d'une heure : à cette échelle elles
 * n'apprennent rien et rendent la ligne illisible.
 */
internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + 999L) / 1000L // arrondi SUPÉRIEUR : jamais « 0 s » tant qu'il reste du temps
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min $seconds s"
        else -> "$seconds s"
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

internal const val WHEEL_HOURS_TAG = "sleep-timer-wheel-hours"
internal const val WHEEL_MINUTES_TAG = "sleep-timer-wheel-minutes"

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
        WheelColumn(
            range = 0..5,
            selected = hours,
            onSelect = { hours = it },
            // Étiquettes de test : la sélection de ces roues dépend de la
            // géométrie réelle (voir WheelColumn), elle ne se prouve donc que
            // par un test Compose qui les fait défiler pour de vrai.
            modifier = Modifier.width(56.dp).testTag(WHEEL_HOURS_TAG),
        )
        Text("h", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        WheelColumn(
            range = 0..59,
            selected = minutes,
            onSelect = { minutes = it },
            modifier = Modifier.width(56.dp).testTag(WHEEL_MINUTES_TAG),
        )
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

    // Bug réel signalé par Issa : le bouton « Valider » restait grisé quoi
    // qu'on règle, et la valeur en gras ne suivait pas la roue.
    //
    // L'ancienne condition — « pas de défilement en cours ET
    // `firstVisibleItemScrollOffset == 0` » — n'était pratiquement jamais vraie
    // après un geste : un `contentPadding` décale l'origine du contenu, et un
    // glissement relâché sans lancer ne repasse pas forcément par un offset
    // exactement nul. `onSelect` ne se rappelait donc plus jamais, l'état
    // interne restait figé à sa valeur initiale, et il suffisait qu'il vaille
    // zéro pour que le bouton soit définitivement inerte. Pire, l'affichage
    // mentait : la roue montrait une valeur au centre pendant que l'état en
    // retenait une autre.
    //
    // La position centrée est désormais DÉDUITE de la géométrie réelle
    // (`layoutInfo`) : l'item dont le centre est le plus proche du centre du
    // viewport. Vrai à tout instant, pendant le geste comme au repos — donc
    // aucune condition d'arrêt à satisfaire, et la valeur affichée en gras est
    // par construction celle qui est retenue.
    val centeredValue by remember(range) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2f) - viewportCenter) }
                ?.let { (range.first + it.index).coerceIn(range) }
        }
    }

    LaunchedEffect(centeredValue) {
        centeredValue?.let(onSelect)
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
