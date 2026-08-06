package com.inktone.infrastructure.crashreporting

import com.inktone.domain.service.CrashReporter
import javax.inject.Inject

/**
 * Implémentation par défaut (K10, ADR-014) : aucun réseau, aucun
 * identifiant Firebase requis. Liée par Hilt quand
 * `BuildConfig.FIREBASE_CRASHLYTICS_ENABLED` (module `app`, posé selon la
 * présence de `google-services.json` au moment du build) est faux — voir
 * `CrashReporterModule` dans `app`.
 */
class NoOpCrashReporter @Inject constructor() : CrashReporter {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
}
