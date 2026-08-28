package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import com.inktone.domain.repository.LibraryItemRepository
import com.inktone.domain.usecase.DeleteLibraryItemUseCase
import com.inktone.domain.usecase.ToggleLibraryItemPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryItemsViewModel @Inject constructor(
    private val libraryItemRepository: LibraryItemRepository,
    private val deleteLibraryItem: DeleteLibraryItemUseCase,
    private val toggleLibraryItemPin: ToggleLibraryItemPinUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryItemsUiState())
    val state: StateFlow<LibraryItemsUiState> = _state.asStateFlow()

    private val _effects = Channel<LibraryItemsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        // Tache 4.4 — recherche/filtre/tri au niveau requete : un
        // changement de l'un des trois relance l'observation SQL, jamais
        // un filter() sur une liste deja chargee en memoire.
        // AUDIT_REACTIVITE_UX §6.1 — visibleLimit s'ajoute à la clé : une
        // requête bornée, relancée avec une borne plus large sur LoadMore,
        // jamais une requête sans fin par défaut.
        viewModelScope.launch {
            _state.map { LibraryItemsQueryKey(it.filter, it.searchQuery, it.sortOrder, it.visibleLimit) }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    libraryItemRepository.observe(key.filter, key.searchQuery, key.sortOrder, key.limit)
                }
                .collect { items -> _state.value = _state.value.copy(items = items, isLoading = false) }
        }
    }

    fun onIntent(intent: LibraryItemsIntent) {
        when (intent) {
            is LibraryItemsIntent.SetSearchQuery -> _state.value = _state.value.copy(
                searchQuery = intent.query,
                visibleLimit = LIBRARY_ITEMS_PAGE_SIZE,
            )
            is LibraryItemsIntent.ToggleSearchExpanded -> _state.value = _state.value.copy(
                isSearchExpanded = !_state.value.isSearchExpanded,
                searchQuery = if (_state.value.isSearchExpanded) "" else _state.value.searchQuery,
                visibleLimit = LIBRARY_ITEMS_PAGE_SIZE,
            )
            is LibraryItemsIntent.SetFilter -> _state.value = _state.value.copy(
                filter = intent.filter,
                visibleLimit = LIBRARY_ITEMS_PAGE_SIZE,
            )
            is LibraryItemsIntent.SetSortOrder -> _state.value = _state.value.copy(
                sortOrder = intent.sortOrder,
                visibleLimit = LIBRARY_ITEMS_PAGE_SIZE,
            )
            is LibraryItemsIntent.OpenItem -> viewModelScope.launch { emitNavigateToReader(intent.item) }
            is LibraryItemsIntent.RequestDelete -> _state.value = _state.value.copy(pendingDelete = intent.item)
            is LibraryItemsIntent.CancelDelete -> _state.value = _state.value.copy(pendingDelete = null)
            is LibraryItemsIntent.ConfirmDelete -> confirmDelete()
            is LibraryItemsIntent.TogglePin -> viewModelScope.launch {
                toggleLibraryItemPin(intent.item.type, intent.item.id, !intent.item.isPinned)
            }
            is LibraryItemsIntent.LoadMore -> {
                if (!_state.value.canLoadMore) return
                _state.value = _state.value.copy(visibleLimit = _state.value.visibleLimit + LIBRARY_ITEMS_PAGE_SIZE)
            }
        }
    }

    private fun confirmDelete() {
        val item = _state.value.pendingDelete ?: return
        _state.value = _state.value.copy(pendingDelete = null)
        viewModelScope.launch { deleteLibraryItem(item.type, item.id) }
    }

    private data class LibraryItemsQueryKey(
        val filter: LibraryItemFilter,
        val searchQuery: String,
        val sortOrder: LibraryItemSortOrder,
        val limit: Int,
    )

    private suspend fun emitNavigateToReader(item: LibraryItem) {
        _effects.send(
            LibraryItemsEffect.NavigateToReader(
                publicationId = item.publicationId,
                resourceHref = item.startLocator.resourceHref,
                chapterIndex = item.startLocator.chapterIndex,
                charOffset = item.startLocator.charOffset,
            ),
        )
    }
}
