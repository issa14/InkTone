package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.ImportProgressObserver
import com.inktone.domain.service.ImportResultsStore
import com.inktone.domain.service.ImportSessionStore
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.RegenerateCoversUseCase
import com.inktone.domain.usecase.SynchronizeNowUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import com.inktone.feature.library.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val togglePin: TogglePinUseCase,
    private val deletePublication: DeletePublicationUseCase,
    private val importProgressObserver: ImportProgressObserver,
    private val importResultsStore: ImportResultsStore,
    private val importSessionStore: ImportSessionStore,
    private val preferencesRepository: PreferencesRepository,
    // Lot 19 — actions du menu 3-points
    private val synchronizeNow: SynchronizeNowUseCase,
    private val syncAccountRepository: SyncAccountRepository,
    private val regenerateCovers: RegenerateCoversUseCase,
    // Audit v1.0.0 (P5) — calcul de progression hors Main (injectable
    // pour les tests, voir di/IoDispatcher.kt).
    @IoDispatcher private val defaultDispatcher: CoroutineDispatcher,
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
            var previousTotal = 0
            importProgressObserver.observe().collect { progress ->
                _state.value = _state.value.copy(importProgress = progress)

                // Lot 5 — détection de fin d'import : quand le total
                // passe de >0 à 0 et qu'aucun lot n'est en attente,
                // l'import est terminé — charger les résultats.
                val wasActive = previousTotal > 0 || _state.value.importProgress.hasQueuedChunks
                val isDone = progress.total == 0 && !progress.hasQueuedChunks
                if (wasActive && isDone) {
                    loadImportResults()
                }
                previousTotal = progress.total
            }
        }
        // Lot 6 — la disposition est persistée pour que le préréglage
        // d'accessibilité (SettingsViewModel) puisse la piloter à distance ;
        // toute modification manuelle (SetLayoutMode) est réécrite dans les
        // préférences pour rester la même source de vérité que le préréglage.
        viewModelScope.launch {
            preferencesRepository.observe().collect { prefs ->
                val mode = runCatching { LibraryLayoutMode.valueOf(prefs.libraryLayoutMode) }
                    .getOrDefault(LibraryLayoutMode.GRID_COVERS)
                if (mode != _state.value.layoutMode) {
                    _state.value = _state.value.copy(layoutMode = mode)
                }
            }
        }
        // Lot 5 — observer le sessionId partagé avec ImportViewModel
        viewModelScope.launch {
            importSessionStore.sessionId.collect { sessionId ->
                _state.value = _state.value.copy(importSessionId = sessionId)
            }
        }
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.OpenPublication -> viewModelScope.launch {
                _effects.send(LibraryEffect.NavigateToReader(intent.publicationId, intent.autoStartTts))
            }
            is LibraryIntent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(intent.publicationId, intent.isFavorite)
            }
            is LibraryIntent.TogglePin -> viewModelScope.launch {
                togglePin(intent.publicationId, intent.isPinned)
            }
            is LibraryIntent.DeletePublication -> viewModelScope.launch {
                deletePublication(intent.publicationId)
            }
            is LibraryIntent.ChangeFilter -> observePublications(intent.filter, intent.value)
            is LibraryIntent.SetSearchQuery -> _state.value = _state.value.copy(searchQuery = intent.query)
            is LibraryIntent.SetSortOrder -> _state.value = _state.value.copy(sortOrder = intent.order)
            is LibraryIntent.SetLayoutMode -> {
                _state.value = _state.value.copy(layoutMode = intent.mode)
                viewModelScope.launch {
                    val current = preferencesRepository.observe().first()
                    preferencesRepository.update(current.copy(libraryLayoutMode = intent.mode.name))
                }
            }
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
            is LibraryIntent.DismissImportResults -> {
                _state.value = _state.value.copy(importResults = emptyList(), showImportDetails = false)
                val sessionId = _state.value.importSessionId
                if (sessionId != null) {
                    viewModelScope.launch { importResultsStore.clearSession(sessionId) }
                    importSessionStore.clear()
                }
            }
            is LibraryIntent.OpenImportDetails -> {
                _state.value = _state.value.copy(showImportDetails = true)
            }
            LibraryIntent.OpenRandomBook -> {
                val random = _state.value.displayedPublications.randomOrNull()
                if (random != null) {
                    viewModelScope.launch { _effects.send(LibraryEffect.NavigateToReader(random.id)) }
                } else {
                    viewModelScope.launch { _effects.send(LibraryEffect.RandomBookUnavailable) }
                }
            }
            LibraryIntent.SyncNow -> viewModelScope.launch {
                // UX §Bottom sheet 3-points (tranché) : si aucun service de
                // sync n'est configuré, le tap ouvre directement l'écran de
                // configuration — jamais un bouton désactivé.
                if (syncAccountRepository.get() == null) {
                    _effects.send(LibraryEffect.NavigateToSync)
                } else {
                    val result = synchronizeNow()
                    _effects.send(LibraryEffect.SyncCompleted(result))
                }
            }
            LibraryIntent.RegenerateCovers -> viewModelScope.launch {
                if (_state.value.isRegeneratingCovers) return@launch
                _state.value = _state.value.copy(isRegeneratingCovers = true, coverRegeneration = CoverRegenerationProgress(0, 0))
                val result = regenerateCovers { processed, total ->
                    _state.value = _state.value.copy(coverRegeneration = CoverRegenerationProgress(processed, total))
                }
                _state.value = _state.value.copy(isRegeneratingCovers = false, coverRegeneration = null)
                _effects.send(LibraryEffect.CoversRegenerated(result))
            }
            LibraryIntent.ResetCovers -> viewModelScope.launch {
                publicationRepository.resetAllCoversToDefault()
                _effects.send(LibraryEffect.CoversReset)
            }
        }
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

    /**
     * Charge les résultats d'import depuis [ImportResultsStore] pour
     * la session en cours (Lot 5). Appelé quand l'import se termine.
     */
    private fun loadImportResults() {
        val sessionId = _state.value.importSessionId ?: return
        viewModelScope.launch {
            val results = importResultsStore.getResults(sessionId)
            _state.value = _state.value.copy(importResults = results)
        }
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
                    // Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, P5) : la
                    // 2e requête (getAll des ReadingState) et la boucle O(n)
                    // de computeProgressMap s'exécutaient sur Main à CHAQUE
                    // émission du Flow Room (500 émissions pendant un import
                    // groupé). Déplacé sur Default ; seuls les mises à jour
                    // d'état repassent sur Main.
                    val progressMap = withContext(defaultDispatcher) {
                        computeProgressMap(publications, readingStateRepository.getAll())
                    }
                    _state.value = _state.value.copy(
                        publications = publications,
                        progressMap = progressMap,
                        isLoading = false,
                    )
                }
        }
    }
}
