package com.inktone.infrastructure.opds

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inktone.domain.service.OpdsCredentials
import com.inktone.domain.service.OpdsCredentialsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste les identifiants Basic Auth d'un catalogue OPDS chiffrés au
 * repos via le Keystore Android (`EncryptedSharedPreferences`), keyés par
 * `catalogId` — jamais en `SharedPreferences` en clair, jamais en base
 * Room (Lot 13, tâche 13.2). Même famille que `SecureAuthStateStore`
 * (Lot 11) mais fichier distinct : un catalogue OPDS n'est pas un compte
 * de sync.
 */
@Singleton
class SecureOpdsCredentialsStore @Inject constructor(@ApplicationContext context: Context) : OpdsCredentialsStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, FILE_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun hasCredentials(catalogId: String): Boolean =
        prefs.contains(key(catalogId, KEY_USERNAME))

    override fun getCredentials(catalogId: String): OpdsCredentials? {
        val username = prefs.getString(key(catalogId, KEY_USERNAME), null) ?: return null
        val password = prefs.getString(key(catalogId, KEY_PASSWORD), null) ?: return null
        return OpdsCredentials(username, password)
    }

    override fun setCredentials(catalogId: String, username: String, password: String) {
        prefs.edit()
            .putString(key(catalogId, KEY_USERNAME), username)
            .putString(key(catalogId, KEY_PASSWORD), password)
            .apply()
    }

    override fun clearCredentials(catalogId: String) {
        prefs.edit()
            .remove(key(catalogId, KEY_USERNAME))
            .remove(key(catalogId, KEY_PASSWORD))
            .apply()
    }

    private fun key(catalogId: String, field: String) = "$catalogId.$field"

    private companion object {
        const val FILE_NAME = "inktone_opds_credentials"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
