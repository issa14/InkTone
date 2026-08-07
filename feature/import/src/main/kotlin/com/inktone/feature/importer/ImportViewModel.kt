package com.inktone.feature.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.service.ImportResultsStore
import com.inktone.domain.service.ImportScheduler
import com.inktone.domain.service.ImportSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Déclenche un import (Tâche 6.2bis) — dépend uniquement de
 * [ImportScheduler] (domain), jamais de WorkManager ni d'`ImportWorker`
 * directement (Blueprint §12.4 : `feature` -> `domain`/`core`
 * uniquement, jamais `infrastructure`).
 *
 * Expose désormais [state] contenant le [sessionId] de l'import en
 * cours (Palier A, Lot 5), pour que la couche UI puisse observer les
 * résultats via [ImportResultsStore].
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importScheduler: ImportScheduler,
    private val importResultsStore: ImportResultsStore,
    private val importSessionStore: ImportSessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /**
     * Appelée avec les URI déjà choisies par le sélecteur SAF (déclenché
     * depuis le Composable, pas depuis le ViewModel — `ActivityResult`
     * est lié au cycle de vie Activity/Compose, pas testable proprement
     * en dehors de cette couche).
     *
     * Au démarrage d'une nouvelle session (Lot 5) : purge des résultats
     * des sessions précédentes via [ImportResultsStore.beginSession].
     */
    fun enqueueImport(uris: List<String>) {
        val sessionId = importScheduler.enqueue(uris)
        if (sessionId.isNotEmpty()) {
            importSessionStore.setSessionId(sessionId)
            _state.value = _state.value.copy(lastSessionId = sessionId)
            // Purge asynchrone, race-safe : ne supprime que les sessions
            // autres que la courante (jamais les résultats du worker).
            viewModelScope.launch { importResultsStore.beginSession(sessionId) }
        }
    }
}

data class ImportUiState(
    val lastSessionId: String? = null,
)
