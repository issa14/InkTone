package com.inktone.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Détenteur partagé de l'identifiant de session d'import en cours
 * (Lot 5). [ImportViewModel] y écrit le `sessionId` au moment de
 * `enqueue`, [LibraryViewModel] le lit pour charger les résultats
 * à la fin de l'import.
 *
 * Deux ViewModels distincts ne peuvent pas partager un état
 * directement (scopes différents) — un singleton du domaine résout
 * le problème sans couplage entre modules `feature`.
 *
 * Les annotations DI (`@Singleton`, `@Inject`) sont absentes du
 * domaine (Blueprint §5.2) — le binding est déclaré dans
 * `data/di/ImportSessionStoreModule.kt`.
 */
class ImportSessionStore {
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    fun setSessionId(id: String) {
        _sessionId.value = id
    }

    fun clear() {
        _sessionId.value = null
    }
}
