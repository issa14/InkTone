package com.inktone.infrastructure.crashreporting.di

import android.content.Context
import com.inktone.domain.service.CrashReporter
import com.inktone.infrastructure.crashreporting.FirebaseCrashReporter
import com.inktone.infrastructure.crashreporting.NoOpCrashReporter
import com.inktone.infrastructure.crashreporting.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Choisit entre Firebase Crashlytics et le no-op (K10/ADR-014). Vit ici
 * (`infrastructure`, autorisé à dépendre de `domain`, Blueprint §12.4) et
 * non dans `app` — `app` n'a pas le droit de dépendre de `domain`
 * directement, et `checkArchitectureRules` le fait échouer si c'est le
 * cas. Le signal « google-services.json était présent au moment du
 * build » (connu seulement de `app/build.gradle.kts`) traverse la
 * frontière de module via une ressource Android (`R.bool
 * .firebase_crashlytics_configured`, valeur par défaut `false` ici,
 * remplacée par `app` via `resValue`) plutôt qu'un `BuildConfig` — ce
 * dernier aurait forcé `app` à importer ce module en Kotlin pour
 * l'utiliser, l'aurait donc traversé vers `domain` par transitivité.
 */
@Module
@InstallIn(SingletonComponent::class)
object CrashReporterModule {
    @Provides
    @Singleton
    fun provideCrashReporter(
        @ApplicationContext context: Context,
        firebase: FirebaseCrashReporter,
        noOp: NoOpCrashReporter,
    ): CrashReporter =
        if (context.resources.getBoolean(R.bool.firebase_crashlytics_configured)) firebase else noOp
}
