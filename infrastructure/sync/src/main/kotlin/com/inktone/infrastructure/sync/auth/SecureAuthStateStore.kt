package com.inktone.infrastructure.sync.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste l'`AuthState` d'AppAuth (jeton d'accès, jeton de
 * rafraîchissement) chiffré au repos via le Keystore Android
 * (`EncryptedSharedPreferences`) — jamais en `SharedPreferences` en
 * clair (tâche 11.4/11.7 : « le jeton n'est jamais persisté en clair »).
 * Aucun mot de passe utilisateur ici : la clé est liée au Keystore de
 * l'appareil, contrairement au chiffrement E2EE de la sauvegarde locale
 * (`BackupCrypto`, tâche 11.1) qui dérive sa clé d'un mot de passe.
 */
@Singleton
class SecureAuthStateStore @Inject constructor(@ApplicationContext context: Context) : AuthStateStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, FILE_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): AuthState? = prefs.getString(KEY_AUTH_STATE, null)?.let { AuthState.jsonDeserialize(it) }

    override fun write(state: AuthState) {
        prefs.edit().putString(KEY_AUTH_STATE, state.jsonSerializeString()).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_AUTH_STATE).apply()
    }

    private companion object {
        const val FILE_NAME = "inktone_google_auth_state"
        const val KEY_AUTH_STATE = "auth_state"
    }
}
