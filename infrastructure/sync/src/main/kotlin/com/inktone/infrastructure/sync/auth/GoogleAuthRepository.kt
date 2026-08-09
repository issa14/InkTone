package com.inktone.infrastructure.sync.auth

import android.content.Intent

sealed interface GoogleAuthResult {
    data object Success : GoogleAuthResult
    data class Failed(val message: String) : GoogleAuthResult
}

/**
 * Authentification Google OAuth (tâche 11.4), consommée uniquement
 * depuis `app` (seul module qui héberge une `Activity` pour lancer
 * l'intent d'autorisation et recevoir le retour). Ni `domain` ni
 * aucun module feature ne connaît ce type — le module `domain` n'expose que
 * [com.inktone.domain.service.TokenProvider], que cette interface
 * implémente aussi.
 */
interface GoogleAuthRepository {
    /** `true` si un jeton de rafraîchissement est persisté — ne garantit pas qu'il est encore valide (voir [com.inktone.domain.service.TokenProvider.getValidToken], qui peut échouer sur révocation). */
    fun isLinked(): Boolean

    /** @throws IllegalStateException si [GoogleAuthConfig.isConfigured] est faux — vérifier avant d'appeler. */
    fun buildAuthorizationIntent(): Intent

    /** À appeler depuis `Activity.onActivityResult`/le callback d'`ActivityResultContract` avec l'intent reçu. */
    suspend fun handleAuthorizationResponse(intent: Intent): GoogleAuthResult

    /** Oublie l'`AuthState` local. Ne révoque pas côté serveur — l'utilisateur peut toujours le faire depuis son compte Google (voir tâche 11.4, révocation détectée au prochain jeton refusé). */
    suspend fun disconnect()
}
