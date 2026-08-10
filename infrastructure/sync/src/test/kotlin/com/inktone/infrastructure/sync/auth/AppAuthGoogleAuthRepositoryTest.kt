package com.inktone.infrastructure.sync.auth

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeAuthStateStore : AuthStateStore {
    private var state: AuthState? = null
    override fun read(): AuthState? = state
    override fun write(state: AuthState) {
        this.state = state
    }
    override fun clear() {
        state = null
    }
}

private val SERVICE_CONFIG = AuthorizationServiceConfiguration(
    Uri.parse("https://example.tld/auth"),
    Uri.parse("https://example.tld/token"),
)

/** Un jeton de rafraîchissement minimal, sans échange réseau réel. */
private fun refreshTokenResponse(): TokenResponse {
    val request = TokenRequest.Builder(SERVICE_CONFIG, "client-id")
        .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
        .setAuthorizationCode("code")
        .setRedirectUri(Uri.parse("app:/redirect"))
        .build()
    return TokenResponse.Builder(request)
        .setAccessToken("access-token")
        .setRefreshToken("refresh-token")
        .setAccessTokenExpirationTime(System.currentTimeMillis() + 3_600_000)
        .build()
}

/**
 * Lot 11, tâche 11.7 — « le jeton n'est jamais persisté en clair » est
 * vérifié structurellement par [SecureAuthStateStore] (Keystore Android,
 * non instanciable en JVM pur) ; ici, la logique métier autour du
 * store est testée via [FakeAuthStateStore] (en mémoire).
 */
@RunWith(RobolectricTestRunner::class)
class AppAuthGoogleAuthRepositoryTest {

    private fun authorizationService(): AuthorizationService =
        AuthorizationService(ApplicationProvider.getApplicationContext())

    @Test
    fun isLinked_est_faux_tant_qu_aucun_etat_n_est_persiste() {
        val repository = AppAuthGoogleAuthRepository(authorizationService(), FakeAuthStateStore())

        assertFalse(repository.isLinked())
    }

    @Test
    fun isLinked_est_vrai_apres_un_etat_avec_jeton_de_rafraichissement() {
        val store = FakeAuthStateStore()
        val authState = AuthState(SERVICE_CONFIG)
        authState.update(refreshTokenResponse(), null)
        store.write(authState)

        val repository = AppAuthGoogleAuthRepository(authorizationService(), store)

        assertTrue(repository.isLinked())
    }

    @Test
    fun getValidToken_echoue_explicitement_sans_compte_lie() = runTest {
        val repository = AppAuthGoogleAuthRepository(authorizationService(), FakeAuthStateStore())

        try {
            repository.getValidToken()
            fail("devait lever une exception plutôt que rendre un jeton")
        } catch (e: IllegalStateException) {
            // attendu — pas de compte lié, échec explicite
        }
    }

    @Test
    fun disconnect_efface_l_etat_persiste_sans_revoquer_cote_serveur() = runTest {
        val store = FakeAuthStateStore()
        val authState = AuthState(SERVICE_CONFIG)
        authState.update(refreshTokenResponse(), null)
        store.write(authState)
        val repository = AppAuthGoogleAuthRepository(authorizationService(), store)

        repository.disconnect()

        assertNull(store.read())
    }
}
