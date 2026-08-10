package com.inktone.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.PositionConflict
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.usecase.ResolvePositionConflictUseCase
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tâche 11.10 — charge la file au démarrage (une seule fois, à
 * l'ouverture de l'app) : un conflit détecté en arrière-plan attend
 * jusque-là, jamais résolu tout seul. [conflicts] présente les
 * conflits successivement (un seul à la fois côté UI) — aucun n'est
 * perdu, [resolve] retire uniquement celui qui vient d'être tranché.
 */
@HiltViewModel
class PendingConflictsViewModel @Inject constructor(
    private val conflictQueueRepository: ConflictQueueRepository,
    private val resolvePositionConflict: ResolvePositionConflictUseCase,
) : ViewModel() {

    private val _conflicts = MutableStateFlow<List<PositionConflict>>(emptyList())
    val conflicts: StateFlow<List<PositionConflict>> = _conflicts.asStateFlow()

    init {
        viewModelScope.launch {
            _conflicts.value = conflictQueueRepository.listPending()
        }
    }

    fun resolve(conflict: PositionConflict, chosenLocator: Locator) {
        viewModelScope.launch {
            resolvePositionConflict(conflict, chosenLocator)
            _conflicts.value = _conflicts.value.filterNot { it.publicationId == conflict.publicationId }
        }
    }
}
