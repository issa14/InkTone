package com.inktone

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Point d'entrée unique vers Crashlytics.
 *
 * `init()` est un no-op silencieux si aucun projet Firebase n'est configuré
 * (pas de google-services.json → FirebaseApp.initializeApp renvoie null) :
 * un clone du dépôt sans les identifiants Firebase du mainteneur continue de
 * builder et de tourner normalement, simplement sans remontée de crash.
 */
object CrashReporter {
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context) {
        if (FirebaseApp.initializeApp(context) == null) return
        crashlytics = FirebaseCrashlytics.getInstance().apply {
            setCustomKey("version_name", BuildConfig.VERSION_NAME)
            setCustomKey("git_commit", BuildConfig.GIT_COMMIT)
        }
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }
}
