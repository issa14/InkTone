package com.inktone.feature.sync

import com.inktone.domain.model.SyncUiState

/** État minimal (Tâche 11.6) : wrapper autour de [SyncUiState], observé directement — même patron que d'autres écrans qui exposent un modèle domaine sans couche de traduction supplémentaire. */
data class SyncScreenState(
    val syncUiState: SyncUiState = SyncUiState.Unconfigured,
)
