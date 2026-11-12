package com.inktone.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.inktone.domain.model.cleanedForDisplay
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.SearchResult
import androidx.lifecycle.viewModelScope
import com.inktone.domain.usecase.SearchPublicationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tâche 7.5 — `debounce(300)` sur le flux de requête : une recherche FTS
 * à chaque frappe sur une bibliothèque large serait coûteuse et inutile
 * avant que l'utilisateur ait fini de taper.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchPublication: SearchPublicationUseCase,
    private val publicationRepository: PublicationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Livre ouvert dans le Lecteur qui a lancé la recherche, ou null pour une
    // recherche dans toute la bibliothèque (audit UX A3).
    private val publicationId: String? = savedStateHandle.get<String>(PUBLICATION_ID_ARG)

    private val _state = MutableStateFlow(SearchUiState(isBookScoped = publicationId != null))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow.debounce(300).collectLatest { query ->
                val results = searchPublication(query, publicationId)
                // Recherche globale : chaque résultat dit de quel livre il vient.
                val titles = if (publicationId != null) emptyMap() else bookTitlesFor(results)
                _state.value = _state.value.copy(results = results, bookTitles = titles, isSearching = false)
            }
        }
    }

    private suspend fun bookTitlesFor(results: List<SearchResult>): Map<String, String> =
        results.mapTo(HashSet()) { it.publicationId }.associateWith { id ->
            publicationRepository.getById(id)?.title?.cleanedForDisplay().orEmpty()
        }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                _state.value = _state.value.copy(query = intent.query, isSearching = intent.query.isNotBlank())
                queryFlow.value = intent.query
            }
            is SearchIntent.NavigateToResult -> viewModelScope.launch {
                _effects.send(SearchEffect.NavigateToReader(intent.result.publicationId, intent.result.locator))
            }
        }
    }
}

/** Nom de l'argument de navigation portant le livre de la recherche (`SearchRoute.publicationId`). */
const val PUBLICATION_ID_ARG = "publicationId"
