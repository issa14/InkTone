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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lot 8 — même patron que [LibraryDetailViewModel] : filtre serveur
 * unique (ALL, pas de sous-filtre changeant), tout le reste (seuil,
 * tri, limite) dérivé dans [RecentsUiState.displayedPublications].
 * `observeFiltered` est un Flow Room live : rouvrir un livre met à jour
 * `lastOpened` en base, qui remonte automatiquement ici sans action
 * explicite (Tâche 8.3, point 7).
 */
@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val togglePin: TogglePinUseCase,
    private val deletePublication: DeletePublicationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RecentsUiState())
    val state: StateFlow<RecentsUiState> = _state.asStateFlow()

    private val _effects = Channel<RecentsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            publicationRepository.observeFiltered(FilterMode.ALL).collect { publications ->
                val progressMap = computeProgressMap(publications, readingStateRepository.getAll())
                _state.value = _state.value.copy(
                    publications = publications,
                    progressMap = progressMap,
                    isLoading = false,
                )
            }
        }
    }

    fun onIntent(intent: RecentsIntent) {
        when (intent) {
            is RecentsIntent.OpenPublication -> viewModelScope.launch {
                _effects.send(RecentsEffect.NavigateToReader(intent.publicationId))
            }
            is RecentsIntent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(intent.publicationId, intent.isFavorite)
            }
            is RecentsIntent.TogglePin -> viewModelScope.launch {
                togglePin(intent.publicationId, intent.isPinned)
            }
            is RecentsIntent.DeletePublication -> viewModelScope.launch {
                deletePublication(intent.publicationId)
            }
        }
    }
}
