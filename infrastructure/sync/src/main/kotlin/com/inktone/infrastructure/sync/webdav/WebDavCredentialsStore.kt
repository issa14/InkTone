package com.inktone.infrastructure.sync.webdav

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Identifiants WebDAV persistés — le mot de passe n'est jamais stocké en clair. */
data class WebDavCredentials(
    val url: String,
    val username: String,
    val password: String,
)

/**
 * Persiste les identifiants WebDAV chiffrés au repos via le Keystore
 * Android (`EncryptedSharedPreferences`) — même discipline que
 * [SecureAuthStateStore] pour le jeton OAuth (tâche 11.4/11.7) : jamais
 * de `SharedPreferences` en clair pour un secret.
 */
@Singleton
class WebDavCredentialsStore @Inject constructor(@ApplicationContext context: Context) : WebDavCredentialsStoreContract {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, FILE_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): WebDavCredentials? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return WebDavCredentials(url = url, username = username, password = password)
    }

    override fun write(credentials: WebDavCredentials) {
        prefs.edit()
            .putString(KEY_URL, credentials.url)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_URL).remove(KEY_USERNAME).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val FILE_NAME = "inktone_webdav_credentials"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
