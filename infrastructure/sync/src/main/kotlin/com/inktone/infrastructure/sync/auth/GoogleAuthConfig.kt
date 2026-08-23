package com.inktone.infrastructure.sync.auth

import com.inktone.infrastructure.sync.BuildConfig

/**
 * `clientId`/`redirectScheme` lus depuis `local.properties` au moment du
 * build (voir `infrastructure/sync/build.gradle.kts`), jamais codés en
 * dur (tâche 11.4). Le couple dépend du buildType : un client OAuth
 * Android est lié à un couple `package + SHA-1`, donc le build debug et
 * le build release en utilisent deux distincts. `isConfigured` rend l'absence de configuration
 * explicite : `GoogleAuthRepository` doit s'appuyer dessus plutôt que de
 * laisser AppAuth échouer avec une erreur réseau opaque sur un
 * `clientId` vide.
 */
object GoogleAuthConfig {
    /** Seule portée demandée (décision actée du plan) — jamais étendue sans le consigner explicitement. */
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    val clientId: String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
    private val redirectScheme: String = BuildConfig.GOOGLE_OAUTH_REDIRECT_SCHEME

    val isConfigured: Boolean get() = clientId.isNotBlank() && redirectScheme.isNotBlank()

    /** N'appeler que si [isConfigured] — un schéma vide produirait une URI invalide. */
    val redirectUri: String get() = "$redirectScheme:/oauth2redirect"
}
