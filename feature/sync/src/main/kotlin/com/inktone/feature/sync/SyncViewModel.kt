package com.inktone.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.usecase.ObserveSyncUiStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    observeSyncUiStateUseCase: ObserveSyncUiStateUseCase,
) : ViewModel() {
    val state: StateFlow<SyncScreenState> = observeSyncUiStateUseCase()
        .map { SyncScreenState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncScreenState())
}
