package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.FilterMode
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lot 2a.4 — même patron que [LibraryViewModel] (filtre serveur unique
 * SERIES/TAG, fixé par la route, jamais changé depuis cet écran) plutôt
 * qu'une réutilisation directe : [LibraryUiState] porte des champs
 * (drawer, disposition) sans rapport avec cet écran à vue Liste fixe.
 */
@HiltViewModel
class LibraryDetailViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val togglePin: TogglePinUseCase,
    private val deletePublication: DeletePublicationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryDetailUiState())
    val state: StateFlow<LibraryDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<LibraryDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var observeJob: Job? = null

    fun onIntent(intent: LibraryDetailIntent) {
        when (intent) {
            is LibraryDetailIntent.Load -> load(intent.category, intent.value)
            is LibraryDetailIntent.OpenPublication -> viewModelScope.launch {
                _effects.send(LibraryDetailEffect.NavigateToReader(intent.publicationId))
            }
            is LibraryDetailIntent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(intent.publicationId, intent.isFavorite)
            }
            is LibraryDetailIntent.TogglePin -> viewModelScope.launch {
                togglePin(intent.publicationId, intent.isPinned)
            }
            is LibraryDetailIntent.DeletePublication -> viewModelScope.launch {
                deletePublication(intent.publicationId)
            }
            is LibraryDetailIntent.SetSearchQuery -> _state.value = _state.value.copy(searchQuery = intent.query)
            is LibraryDetailIntent.SetSortOrder -> _state.value = _state.value.copy(sortOrder = intent.order)
            is LibraryDetailIntent.ToggleFileFormat -> _state.value = _state.value.copy(
                selectedFormats = _state.value.selectedFormats.let {
                    if (intent.format in it) it - intent.format else it + intent.format
                },
            )
            is LibraryDetailIntent.ClearFileFormats -> _state.value = _state.value.copy(selectedFormats = emptySet())
        }
    }

    private fun load(category: LibraryDetailCategory, value: String) {
        if (_state.value.category == category && _state.value.value == value) return
        observeJob?.cancel()
        _state.value = _state.value.copy(category = category, value = value, isLoading = true)
        val filterMode = when (category) {
            LibraryDetailCategory.SERIES -> FilterMode.SERIES
            LibraryDetailCategory.TAG -> FilterMode.TAG
        }
        observeJob = viewModelScope.launch {
            publicationRepository.observeFiltered(filterMode, value).collect { publications ->
                val progressMap = computeProgressMap(publications, readingStateRepository.getAll())
                _state.value = _state.value.copy(
                    publications = publications,
                    progressMap = progressMap,
                    isLoading = false,
                )
            }
        }
    }
}
