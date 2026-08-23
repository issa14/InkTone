package com.inktone.feature.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.model.OpdsItem
import com.inktone.domain.usecase.AddCatalogUseCase
import com.inktone.domain.usecase.BrowseOpdsFeedUseCase
import com.inktone.domain.usecase.DownloadOpdsBookUseCase
import com.inktone.domain.usecase.GetCatalogsUseCase
import com.inktone.domain.usecase.OpdsBrowseResult
import com.inktone.domain.usecase.RemoveCatalogUseCase
import com.inktone.domain.usecase.SearchOpdsFeedUseCase
import com.inktone.domain.usecase.UpdateCatalogUseCase
import com.inktone.domain.service.OpdsDownloadObserver
import com.inktone.domain.service.OpdsHttpClient
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
 */
@HiltViewModel
class OpdsViewModel @Inject constructor(
    getCatalogsUseCase: GetCatalogsUseCase,
    private val addCatalog: AddCatalogUseCase,
    private val removeCatalog: RemoveCatalogUseCase,
    private val updateCatalog: UpdateCatalogUseCase,
    private val browse: BrowseOpdsFeedUseCase,
    private val search: SearchOpdsFeedUseCase,
    private val downloadBook: DownloadOpdsBookUseCase,
    private val downloadObserver: OpdsDownloadObserver,
    /** Exposé à l'écran pour construire l'ImageLoader Coil des couvertures (interface domaine, comme `epubResourceResolver` du reader). */
    val httpClient: OpdsHttpClient,
) : ViewModel() {

    private val catalogs = getCatalogsUseCase()

    /** Pile des URLs parcourues dans le flux courant — racine en bas. */
    private val urlStack = ArrayDeque<String>()

    /** Titres alignés sur [urlStack] pour le fil d'Ariane du flux. */
    private val titleStack = ArrayDeque<String>()

    private var currentCatalogId: String? = null

    /** Vrai quand l'utilisateur regarde des résultats de recherche : le retour re-navigue, ne dépile pas. */
    private var isSearchResult = false

    private val navigation = MutableStateFlow<OpdsUiState>(OpdsUiState.Dashboard())

    val state: StateFlow<OpdsUiState> = combine(navigation, catalogs) { nav, list ->
        when (nav) {
            is OpdsUiState.Dashboard -> nav.copy(catalogs = list, isLoading = false)
            is OpdsUiState.Feed -> nav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpdsUiState.Dashboard())

    private val _effects = MutableSharedFlow<OpdsEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<OpdsEffect> = _effects

    init {
        // Fin de téléchargement (worker → UI) : snackbar « Lire maintenant ».
        viewModelScope.launch {
            downloadObserver.observe().collect { event ->
                if (event.success) {
                    val publicationId = event.publicationId
                    if (publicationId != null) {
                        _effects.emit(OpdsEffect.DownloadComplete(event.bookTitle, publicationId))
                    }
                } else {
                    _effects.emit(OpdsEffect.ShowMessage("Échec du téléchargement de « ${event.bookTitle} »"))
                }
            }
        }
    }

    fun onIntent(intent: OpdsIntent) {
        when (intent) {
            is OpdsIntent.OpenCatalog -> openCatalog(intent.catalog)
            is OpdsIntent.OpenNavigation -> openNavigation(intent.item)
            OpdsIntent.GoBack -> goBack()
            is OpdsIntent.AddCatalog -> viewModelScope.launch {
                addCatalog(intent.name, intent.rootUrl, intent.username, intent.password)
            }
            is OpdsIntent.RemoveCatalog -> viewModelScope.launch { removeCatalog(intent.id) }
            is OpdsIntent.UpdateCatalog -> viewModelScope.launch {
                updateCatalog(intent.id, intent.name, intent.rootUrl, intent.username, intent.password)
            }
            is OpdsIntent.LoadNextPage -> loadNextPage(intent.nextPageUrl)
            OpdsIntent.RetryFeed -> urlStack.lastOrNull()?.let { loadFeed(it) }
            is OpdsIntent.Search -> doSearch(intent.query)
            is OpdsIntent.DownloadBook -> onDownloadBook(intent.item)
        }
    }

    private fun onDownloadBook(item: OpdsItem.Book) {
        viewModelScope.launch {
            when (val result = downloadBook(item, currentCatalogId)) {
                is com.inktone.domain.usecase.DownloadOpdsBookResult.Scheduled ->
                    _effects.emit(OpdsEffect.ShowMessage("Téléchargement de « ${item.title} » démarré"))
                is com.inktone.domain.usecase.DownloadOpdsBookResult.Failure ->
                    _effects.emit(OpdsEffect.ShowMessage(result.message))
            }
        }
    }

    private fun openCatalog(catalog: OpdsCatalog) {
        browse.resetSession()
        urlStack.clear()
        titleStack.clear()
        currentCatalogId = catalog.id
        isSearchResult = false
        urlStack.addLast(catalog.rootUrl)
        titleStack.addLast(catalog.name)
        loadFeed(catalog.rootUrl)
    }

    private fun openNavigation(item: OpdsItem.Navigation) {
        isSearchResult = false
        urlStack.addLast(item.href)
        titleStack.addLast(item.title)
        loadFeed(item.href)
    }

    private fun goBack() {
        if (isSearchResult) {
            // Retour depuis les résultats de recherche : re-naviguer sur le flux courant.
            isSearchResult = false
            if (urlStack.isEmpty()) {
                navigation.value = OpdsUiState.Dashboard()
            } else {
                loadFeed(urlStack.last())
            }
            return
        }
        if (urlStack.isEmpty()) {
            viewModelScope.launch { _effects.emit(OpdsEffect.CloseScreen) }
            return
        }
        urlStack.removeLast()
        titleStack.removeLast()
        if (urlStack.isEmpty()) {
            currentCatalogId = null
            navigation.value = OpdsUiState.Dashboard()
        } else {
            loadFeed(urlStack.last())
        }
    }

    private fun loadFeed(url: String) {
        val breadcrumb = titleStack.toList()
        navigation.value = OpdsUiState.Feed(
            title = breadcrumb.lastOrNull() ?: "",
            breadcrumb = breadcrumb,
            isLoading = true,
            catalogId = currentCatalogId,
        )
        viewModelScope.launch {
            when (val result = browse(url, currentCatalogId)) {
                is OpdsBrowseResult.Success -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(
                        title = result.feed.title.ifBlank { breadcrumb.lastOrNull() ?: "" },
                        items = result.feed.items,
                        nextPageUrl = result.feed.nextPageUrl,
                        searchTemplateUrl = result.feed.searchTemplateUrl,
                        isLoading = false,
                        error = null,
                    )
                }
                is OpdsBrowseResult.Failure -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoading = false, error = OpdsErrorUi(result.reason, result.message))
                }
                OpdsBrowseResult.LoopDetected -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoading = false)
                }
            }
        }
    }

    private fun loadNextPage(nextPageUrl: String) {
        val current = navigation.value as? OpdsUiState.Feed ?: return
        if (current.isLoadingMore) return
        navigation.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            when (val result = browse.loadNextPage(nextPageUrl, currentCatalogId)) {
                is OpdsBrowseResult.Success -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(
                        items = s.items + result.feed.items,
                        nextPageUrl = result.feed.nextPageUrl,
                        isLoadingMore = false,
                    )
                }
                OpdsBrowseResult.LoopDetected -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoadingMore = false, nextPageUrl = null)
                }
                is OpdsBrowseResult.Failure -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoadingMore = false, error = OpdsErrorUi(result.reason, result.message))
                }
            }
        }
    }

    private fun doSearch(query: String) {
        val current = navigation.value as? OpdsUiState.Feed ?: return
        val template = current.searchTemplateUrl ?: return
        isSearchResult = true
        navigation.value = current.copy(
            title = "Recherche : $query",
            breadcrumb = current.breadcrumb + "Recherche",
            items = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            error = null,
        )
        viewModelScope.launch {
            when (val result = search(query, template, currentCatalogId)) {
                is OpdsBrowseResult.Success -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(
                        items = result.feed.items,
                        nextPageUrl = result.feed.nextPageUrl,
                        isLoading = false,
                    )
                }
                OpdsBrowseResult.LoopDetected -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoading = false)
                }
                is OpdsBrowseResult.Failure -> {
                    val s = navigation.value as? OpdsUiState.Feed ?: return@launch
                    navigation.value = s.copy(isLoading = false, error = OpdsErrorUi(result.reason, result.message))
                }
            }
        }
    }
}
