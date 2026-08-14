package com.inktone.feature.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.usecase.AddCatalogUseCase
import com.inktone.domain.usecase.GetCatalogsUseCase
import com.inktone.domain.usecase.RemoveCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lot 13, tâche 13.5 — pilote l'écran OPDS (ADR-012, MVI). Un seul
 * `StateFlow<OpdsUiState>` (jamais deux flux divergents) ; la pile de
 * navigation (`urlStack`) est un `ArrayDeque<String>` d'URLs — un état
 * de présentation pur, jamais persisté comme position de lecture
 * (ADR-023 : la navigation OPDS ne touche jamais `Locator`).
 *
 * Palier 1 : le Dashboard est branché sur le vrai repository ; la
 * transition Dashboard↔Feed est en place sur données de mock (le flux
 * réel arrive au Palier 2 via `BrowseOpdsFeedUseCase`).
 */
@HiltViewModel
class OpdsViewModel @Inject constructor(
    getCatalogsUseCase: GetCatalogsUseCase,
    private val addCatalog: AddCatalogUseCase,
    private val removeCatalog: RemoveCatalogUseCase,
) : ViewModel() {

    private val catalogs = getCatalogsUseCase()

    /** Pile des URLs parcourues dans le flux courant — racine en bas. */
    private val urlStack = ArrayDeque<String>()

    /** Titres alignés sur [urlStack] pour le fil d'Ariane du flux. */
    private val titleStack = ArrayDeque<String>()

    private val navigation = MutableStateFlow<OpdsUiState>(OpdsUiState.Dashboard())

    val state: StateFlow<OpdsUiState> = combine(navigation, catalogs) { nav, list ->
        when (nav) {
            is OpdsUiState.Dashboard -> nav.copy(catalogs = list, isLoading = false)
            is OpdsUiState.Feed -> nav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpdsUiState.Dashboard())

    private val _effects = MutableSharedFlow<OpdsEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<OpdsEffect> = _effects

    fun onIntent(intent: OpdsIntent) {
        when (intent) {
            is OpdsIntent.OpenCatalog -> openCatalog(intent.catalog)
            OpdsIntent.GoBack -> goBack()
            is OpdsIntent.AddCatalog -> viewModelScope.launch {
                addCatalog(intent.name, intent.rootUrl, intent.username, intent.password)
            }
            is OpdsIntent.RemoveCatalog -> viewModelScope.launch { removeCatalog(intent.id) }
        }
    }

    private fun openCatalog(catalog: OpdsCatalog) {
        urlStack.clear()
        titleStack.clear()
        urlStack.addLast(catalog.rootUrl)
        titleStack.addLast(catalog.name)
        navigation.value = OpdsUiState.Feed(
            title = catalog.name,
            breadcrumb = titleStack.toList(),
            isLoading = true,
        )
        // Palier 2 — remplacer le mock par un vrai `BrowseOpdsFeedUseCase`.
    }

    private fun goBack() {
        if (urlStack.isEmpty()) {
            viewModelScope.launch { _effects.emit(OpdsEffect.CloseScreen) }
            return
        }
        urlStack.removeLast()
        titleStack.removeLast()
        if (urlStack.isEmpty()) {
            navigation.value = OpdsUiState.Dashboard()
        } else {
            navigation.value = OpdsUiState.Feed(
                title = titleStack.last(),
                breadcrumb = titleStack.toList(),
                isLoading = true,
            )
            // Palier 2 — re-naviguer sur l'URL parente via BrowseOpdsFeedUseCase.
        }
    }
}
