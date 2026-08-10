package com.inktone.feature.sync

import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncUiState

/**
 * Tâche 11.8 — étend l'état minimal de la tâche 11.6 avec ce que l'écran
 * Opérationnel (Dashboard) affiche : flotte, journal, les deux
 * interrupteurs, et `reduceMotion` (pilote la transition
 * Configuration/Opérationnel, `AnimatedContent`).
 */
data class SyncScreenState(
    val syncUiState: SyncUiState = SyncUiState.Unconfigured,
    val reduceMotion: Boolean = false,
    val currentDeviceId: String? = null,
    val fleet: List<DeviceFleetEntry> = emptyList(),
    val activityLog: List<SyncActivityEvent> = emptyList(),
    val syncAutoEnabled: Boolean = false,
    val syncWifiOnly: Boolean = false,
)

sealed interface SyncIntent {
    data object SynchronizeNow : SyncIntent
    data class SetAutoSyncEnabled(val enabled: Boolean) : SyncIntent
    data class SetWifiOnly(val enabled: Boolean) : SyncIntent
    data class RemoveDevice(val deviceId: String) : SyncIntent
    data object RefreshDashboard : SyncIntent
}
