package com.inktone.infrastructure.crashreporting

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.inktone.domain.service.CrashReporter
import javax.inject.Inject

/**
 * Adaptateur Firebase Crashlytics (ADR-014). N'est lié par Hilt que
 * lorsque `google-services.json` était présent au moment du build (voir
 * `CrashReporterModule`, module `app`) — mais reste défensif ici même
 * dans ce cas : `FirebaseCrashlytics.getInstance()` peut échouer
 * (`FirebaseApp` non initialisé) si le fichier était présent mais
 * invalide/incomplet. Un rapporteur de crash qui ferait planter l'app
 * qu'il est censé surveiller serait le pire des résultats possibles —
 * jamais laisser cette classe être la source d'un crash.
 */
class FirebaseCrashReporter @Inject constructor() : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled) }
    }

    override fun recordException(throwable: Throwable) {
        runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
    }

    override fun log(message: String) {
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }
}
