package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.FilterMode
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var observeJob: Job? = null

    init {
        observePublications(FilterMode.ALL)
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.OpenPublication -> viewModelScope.launch {
                _effects.send(LibraryEffect.NavigateToReader(intent.publicationId))
            }
            is LibraryIntent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(intent.publicationId, intent.isFavorite)
            }
            is LibraryIntent.ChangeFilter -> observePublications(intent.filter)
        }
    }

    private fun observePublications(filter: FilterMode) {
        observeJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, activeFilter = filter)
        observeJob = viewModelScope.launch {
            publicationRepository.observeFiltered(filter).collect { publications ->
                _state.value = _state.value.copy(publications = publications, isLoading = false)
            }
        }
    }
}
