package com.inktone.infrastructure.sync.auth

import android.content.Intent
import android.net.Uri
import com.inktone.domain.service.TokenProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Endpoints Google fixes — pas de découverte OIDC au démarrage, inutile pour une portée unique (`drive.appdata`) connue à l'avance. */
private val GOOGLE_SERVICE_CONFIG = AuthorizationServiceConfiguration(
    Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
    Uri.parse("https://oauth2.googleapis.com/token"),
)

/**
 * Implémentation AppAuth-Android (tâche 11.4) — choisie plutôt que la
 * couche Authorization API de Google Identity Services : ne dépend pas
 * de Google Play Services (contrairement à Identity Services/Credential
 * Manager), poids ajouté à l'APK minime (~100 Ko), et son API est
 * dédiée exactement à ce flux (code d'autorisation + PKCE + jeton de
 * rafraîchissement) sans wrapper Google-spécifique supplémentaire à
 * maintenir.
 *
 * Implémente aussi [TokenProvider] : [GoogleDriveSyncProvider] (tâche
 * 11.5) consomme cette même instance sans connaître AppAuth.
 */
@Singleton
class AppAuthGoogleAuthRepository @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val store: AuthStateStore,
) : GoogleAuthRepository, TokenProvider {

    override fun isLinked(): Boolean = store.read()?.refreshToken != null

    override fun buildAuthorizationIntent(): Intent {
        check(GoogleAuthConfig.isConfigured) { "GoogleAuthConfig non configuré (clientId/redirectScheme absents)" }
        val request = AuthorizationRequest.Builder(
            GOOGLE_SERVICE_CONFIG, GoogleAuthConfig.clientId, ResponseTypeValues.CODE,
            Uri.parse(GoogleAuthConfig.redirectUri),
        ).setScope(GoogleAuthConfig.SCOPE).build()
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    override suspend fun handleAuthorizationResponse(intent: Intent): GoogleAuthResult {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        if (response == null) {
            return GoogleAuthResult.Failed(exception?.errorDescription ?: "Autorisation refusée ou annulée")
        }
        val authState = AuthState(response, exception)
        return try {
            val tokenResponse = performTokenRequest(response)
            authState.update(tokenResponse, null)
            store.write(authState)
            GoogleAuthResult.Success
        } catch (e: AuthorizationException) {
            authState.update(null as TokenResponse?, e)
            GoogleAuthResult.Failed(e.errorDescription ?: "Échec de l'échange du code d'autorisation")
        }
    }

    override suspend fun disconnect() {
        store.clear()
    }

    /**
     * [TokenProvider] — rafraîchit de façon transparente via AppAuth,
     * échoue explicitement (jeton révoqué, réseau) plutôt que de rendre
     * un jeton périmé. Un jeton révoqué depuis le compte Google se
     * traduit ici par une [AuthorizationException] : l'appelant
     * ([com.inktone.infrastructure.sync.drive.GoogleDriveSyncProvider])
     * doit la traduire en repassage à `Unconfigured`, jamais en boucle
     * de nouvelles tentatives.
     */
    override suspend fun getValidToken(): String {
        val authState = store.read() ?: error("Aucun compte Google lié")
        return suspendCancellableCoroutine { continuation ->
            authState.performActionWithFreshTokens(authorizationService) { accessToken, _, exception ->
                when {
                    exception != null -> continuation.resumeWithException(exception)
                    accessToken == null -> continuation.resumeWithException(IllegalStateException("Jeton d'accès absent"))
                    else -> {
                        store.write(authState)
                        continuation.resume(accessToken)
                    }
                }
            }
        }
    }

    private suspend fun performTokenRequest(response: AuthorizationResponse): TokenResponse =
        suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                when {
                    tokenResponse != null -> continuation.resume(tokenResponse)
                    exception != null -> continuation.resumeWithException(exception)
                    else -> continuation.resumeWithException(IllegalStateException("Réponse de jeton vide"))
                }
            }
        }
}
