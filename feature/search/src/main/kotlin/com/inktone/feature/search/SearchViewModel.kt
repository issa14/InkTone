package com.inktone.feature.search

import androidx.lifecycle.ViewModel
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
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow.debounce(300).collectLatest { query ->
                val results = searchPublication(query)
                _state.value = _state.value.copy(results = results, isSearching = false)
            }
        }
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
