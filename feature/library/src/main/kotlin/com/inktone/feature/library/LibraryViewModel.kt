package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.service.ImportProgressObserver
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val importProgressObserver: ImportProgressObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var observeJob: Job? = null

    init {
        observePublications(FilterMode.ALL)
        // Job independant de observeJob (Tache 6.8) - un changement de
        // filtre ne doit jamais interrompre l'observation de la
        // progression d'import, les deux sont sans rapport.
        viewModelScope.launch {
            importProgressObserver.observe().collect { progress ->
                _state.value = _state.value.copy(importProgress = progress)
            }
        }
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.OpenPublication -> viewModelScope.launch {
                _effects.send(LibraryEffect.NavigateToReader(intent.publicationId))
            }
            is LibraryIntent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(intent.publicationId, intent.isFavorite)
            }
            is LibraryIntent.ChangeFilter -> observePublications(intent.filter, intent.value)
            is LibraryIntent.SetSearchQuery -> _state.value = _state.value.copy(searchQuery = intent.query)
            is LibraryIntent.SetSortOrder -> _state.value = _state.value.copy(sortOrder = intent.order)
            is LibraryIntent.SetLayoutMode -> _state.value = _state.value.copy(layoutMode = intent.mode)
            is LibraryIntent.ToggleFileFormat -> _state.value = _state.value.copy(
                selectedFormats = _state.value.selectedFormats.let {
                    if (intent.format in it) it - intent.format else it + intent.format
                },
            )
            is LibraryIntent.ClearFileFormats -> _state.value = _state.value.copy(selectedFormats = emptySet())
            is LibraryIntent.Refresh -> observePublications(
                _state.value.activeFilter,
                _state.value.filterValue,
            )
            is LibraryIntent.DismissError -> _state.value = _state.value.copy(errorMessage = null)
            is LibraryIntent.RegenerateCovers -> regenerateCovers()
            is LibraryIntent.ResetCovers -> resetCovers()
        }
    }

    /** C.3 — Régénère toutes les couvertures (TODO: appel réel au repository). */
    private fun regenerateCovers() {
        // TODO: publicationRepository.regenerateAllCovers() avec progression
    }

    /** C.3 — Réinitialise les couvertures aux valeurs par défaut (TODO: dialogue confirmation). */
    private fun resetCovers() {
        // TODO: publicationRepository.resetCoversToDefault() avec dialogue confirmation
    }

    /**
     * Appelé par [LibraryScreen] à chaque ON_RESUME du NavBackStackEntry
     * (Phase 4 — rafraîchissement au retour du Reader). Force une
     * ré-observation du filtre actif pour mettre à jour les badges de
     * progression et la carte "Reprendre la lecture".
     */
    fun refreshOnResume() {
        observePublications(_state.value.activeFilter, _state.value.filterValue)
    }

    private fun observePublications(filter: FilterMode, value: String? = null) {
        observeJob?.cancel()
        _state.value = _state.value.copy(
            isLoading = true,
            activeFilter = filter,
            filterValue = value,
            errorMessage = null,
        )
        observeJob = viewModelScope.launch {
            publicationRepository.observeFiltered(filter, value)
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Erreur de chargement de la bibliothèque",
                    )
                }
                .collect { publications ->
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
