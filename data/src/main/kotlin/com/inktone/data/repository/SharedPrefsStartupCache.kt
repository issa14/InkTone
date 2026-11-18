package com.inktone.data.repository

import android.content.Context
import androidx.core.content.edit
import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.repository.StartupPrefsReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Côté écriture du cache de démarrage, séparé pour tester le repository sans Android. */
interface StartupPrefsWriter {
    fun write(preferences: UserPreferences)
}

/**
 * [StartupPrefsReader] sur SharedPreferences : lu sans attendre Room au
 * démarrage, le splash n'attend plus l'ouverture de la base dès le
 * deuxième lancement. Alimenté par [RoomPreferencesRepository].
 */
@Singleton
class SharedPrefsStartupCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : StartupPrefsReader, StartupPrefsWriter {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun readHasSeenOnboarding(): Boolean? =
        if (prefs.contains(KEY_HAS_SEEN_ONBOARDING)) prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false) else null

    override fun readDynamicColorEnabled(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)

    override fun readAppTheme(): AppTheme =
        prefs.getString(KEY_APP_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.SYSTEM

    /**
     * `observe()` a plusieurs collecteurs : on n'écrit que si la copie
     * diffère, pour ne pas multiplier les écritures disque à chaque émission.
     */
    override fun write(preferences: UserPreferences) {
        if (readHasSeenOnboarding() == preferences.hasSeenOnboarding &&
            readDynamicColorEnabled() == preferences.dynamicColorEnabled &&
            readAppTheme() == preferences.appTheme
        ) return
        prefs.edit {
            putBoolean(KEY_HAS_SEEN_ONBOARDING, preferences.hasSeenOnboarding)
            putBoolean(KEY_DYNAMIC_COLOR, preferences.dynamicColorEnabled)
            putString(KEY_APP_THEME, preferences.appTheme.name)
        }
    }

    private companion object {
        const val PREFS_NAME = "startup_prefs_cache"
        const val KEY_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"
        const val KEY_DYNAMIC_COLOR = "dynamicColorEnabled"
        const val KEY_APP_THEME = "appTheme"
    }
}
