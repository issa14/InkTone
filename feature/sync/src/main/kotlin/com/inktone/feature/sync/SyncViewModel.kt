package com.inktone.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncUiState
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.usecase.ObserveSyncUiStateUseCase
import com.inktone.domain.usecase.SynchronizeNowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tâche 11.8 — `syncUiState`/`reduceMotion`/les deux interrupteurs sont
 * réactifs (combinaison de Flows) ; flotte/journal/`currentDeviceId` sont
 * chargés à la demande (fichiers distants, pas une source observable en
 * continu) — au premier passage en `Configured`/`Syncing` et après
 * chaque action qui les affecte ([SyncIntent.SynchronizeNow],
 * [SyncIntent.RemoveDevice], [SyncIntent.RefreshDashboard]).
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    observeSyncUiStateUseCase: ObserveSyncUiStateUseCase,
    private val synchronizeNow: SynchronizeNowUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val syncFleetRepository: SyncFleetRepository,
    private val syncActivityLogRepository: SyncActivityLogRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
) : ViewModel() {

    private val dashboardState = MutableStateFlow(DashboardData())

    val state: StateFlow<SyncScreenState> = combine(
        observeSyncUiStateUseCase(),
        preferencesRepository.observe(),
        dashboardState,
    ) { syncUiState, preferences, dashboard ->
        SyncScreenState(
            syncUiState = syncUiState,
            reduceMotion = preferences.reduceMotion,
            currentDeviceId = dashboard.currentDeviceId,
            fleet = dashboard.fleet,
            activityLog = dashboard.activityLog,
            syncAutoEnabled = preferences.syncAutoEnabled,
            syncWifiOnly = preferences.syncWifiOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncScreenState())

    init {
        viewModelScope.launch {
            state.collect { screenState ->
                // Charge le tableau de bord dès qu'on entre en Configured/Syncing
                // (compte lié) et jamais tant qu'il ne l'est pas — inutile
                // d'interroger le fournisseur distant sans compte (tâche 11.9, point 4).
                if (screenState.syncUiState !is SyncUiState.Unconfigured &&
                    screenState.syncUiState !is SyncUiState.Authenticating &&
                    dashboardState.value.currentDeviceId == null
                ) {
                    refreshDashboard()
                }
            }
        }
    }

    fun onIntent(intent: SyncIntent) {
        when (intent) {
            SyncIntent.SynchronizeNow -> viewModelScope.launch {
                synchronizeNow()
                refreshDashboard()
            }
            is SyncIntent.SetAutoSyncEnabled -> viewModelScope.launch {
                val current = preferencesRepository.get()
                preferencesRepository.update(
                    current.copy(
                        syncAutoEnabled = intent.enabled,
                        // Éteindre l'auto-sync neutralise aussi "Wi-Fi
                        // uniquement" côté planification (SyncScheduleObserver
                        // n'appelle plus schedule()) — remettre la valeur à
                        // false ici serait une perte de préférence inutile,
                        // on la laisse telle quelle, seulement grisée côté UI.
                    ),
                )
            }
            is SyncIntent.SetWifiOnly -> viewModelScope.launch {
                val current = preferencesRepository.get()
                preferencesRepository.update(current.copy(syncWifiOnly = intent.enabled))
            }
            is SyncIntent.RemoveDevice -> viewModelScope.launch {
                syncFleetRepository.removeDevice(intent.deviceId)
                refreshDashboard()
            }
            SyncIntent.RefreshDashboard -> viewModelScope.launch { refreshDashboard() }
        }
    }

    private suspend fun refreshDashboard() {
        val device = deviceIdentityRepository.getOrCreate()
        dashboardState.value = DashboardData(
            currentDeviceId = device.id,
            fleet = syncFleetRepository.listDevices(),
            activityLog = syncActivityLogRepository.listEvents(),
        )
    }

    private data class DashboardData(
        val currentDeviceId: String? = null,
        val fleet: List<DeviceFleetEntry> = emptyList(),
        val activityLog: List<SyncActivityEvent> = emptyList(),
    )
}
