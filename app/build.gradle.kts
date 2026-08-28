import java.util.Properties

plugins {
    id("inktone.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// K10/ADR-014 — le plugin Crashlytics (et google-services, qui genere les
// valeurs d'initialisation FirebaseApp consommees au runtime) echoue le
// build s'il est applique sans google-services.json. Application
// CONDITIONNELLE : ce fichier est gitignore (jamais commis, voir
// .gitignore et CLAUDE.md), donc absent par defaut pour quiconque clone
// le depot — `./gradlew build` doit rester vert sans lui.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    pluginManager.apply(libs.plugins.google.services.get().pluginId)
    pluginManager.apply(libs.plugins.firebase.crashlytics.get().pluginId)
}

// Lot 11, tâche 11.4 — même lecture que infrastructure/sync/build.gradle
// .kts (local.properties, gitignoré, absent par défaut pour quiconque
// clone le dépôt). AppAuth a besoin du schéma de redirection comme
// manifestPlaceholder pour que son RedirectUriReceiverActivity (fusionné
// depuis son propre manifeste) intercepte le retour du navigateur. Il faut
// le poser dans chaque module qui assemble un manifeste final : ici pour
// l'application, et dans infrastructure/sync pour son APK de test
// instrumenté.
val syncLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// AppAuth déclare `${appAuthRedirectScheme}` dans son propre manifeste
// (RedirectUriReceiverActivity) : un placeholder non défini fait
// échouer la fusion de manifeste pour QUICONQUE clone le dépôt, même
// sans jamais toucher à la synchronisation. Toujours défini, avec un
// schéma factice tant que local.properties n'en fournit pas un réel —
// GoogleAuthConfig.isConfigured (clientId vide) empêche de toute façon
// tout flux d'authentification de démarrer dans ce cas.
// Un couple de clés par buildType, pour la raison detaillée dans
// infrastructure/sync/build.gradle.kts : un client OAuth Android est lié
// à un couple `package + SHA-1`, donc le client debug et le client
// release sont deux clients distincts. Ce fichier ne pose que le
// placeholder de manifeste ; les BuildConfig correspondants vivent dans
// infrastructure/sync, et les DEUX doivent designer le même client.
fun googleOAuthRedirectScheme(debug: Boolean): String {
    val debugValue = if (debug) {
        syncLocalProperties.getProperty("GOOGLE_OAUTH_REDIRECT_SCHEME_DEBUG", "")
    } else {
        ""
    }
    return debugValue
        .ifBlank { syncLocalProperties.getProperty("GOOGLE_OAUTH_REDIRECT_SCHEME", "") }
        .ifBlank { "inktone.oauth.unconfigured" }
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // Valeur du client release : elle vaut pour `release` et
        // `benchmark`, `debug` la remplacant ci-dessous.
        manifestPlaceholders["appAuthRedirectScheme"] = googleOAuthRedirectScheme(debug = false)
        // `app` n'a pas le droit de dépendre de `domain` (Blueprint
        // §12.4) : ce booléen traverse la frontière de module vers
        // `CrashReporterModule` (infrastructure/crashreporting, qui PEUT
        // dépendre de domain) via une ressource Android plutôt qu'un
        // BuildConfig — un BuildConfig aurait forcé du code Kotlin dans
        // `app` pour le lire, l'aurait donc exposé à cette dépendance
        // interdite par transitivité. Remplace la valeur par défaut
        // (false) déclarée dans infrastructure/crashreporting/res.
        resValue("bool", "firebase_crashlytics_configured", firebaseConfigured.toString())
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["appAuthRedirectScheme"] = googleOAuthRedirectScheme(debug = true)
        }
    }

    // Tache 9.3 : trouve par la mesure reelle de la taille de l'AAB
    // (196 Mo, tres au-dessus du budget Blueprint §11.2 <= 60 Mo) -
    // libonnxruntime.so pese ~20-33 Mo PAR ABI et etait embarque pour
    // les 4 ABI (x86_64/x86/arm64-v8a/armeabi-v7a, ~107 Mo a lui seul)
    // alors qu'infrastructure/tts (voir son build.gradle.kts) ne compile
    // son propre code natif que pour arm64-v8a - aucun binaire natif de
    // ce projet ne fonctionne sur les 3 autres ABI de toute facon.
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Meme conflit que infrastructure/tts (voir son build.gradle.kts) :
    // libonnxruntime.so existe en deux exemplaires possibles (sherpa-onnx
    // vendore vs AAR onnxruntime-android:1.27.0). La regle packaging
    // d'un module bibliotheque ne s'applique qu'a SON propre merge - le
    // module qui assemble l'APK final (celui-ci) doit redeclarer sa
    // propre regle, sinon `app:mergeDebugNativeLibs` echoue avec "2 files
    // found with path 'lib/arm64-v8a/libonnxruntime.so'" (verifie en CI,
    // pas suppose). Meme decision qu'infrastructure/tts §8.1/§9.3
    // (PROTOTYPE_ALIGNEMENT_CTC.md) : garder un seul binaire, peu importe
    // lequel des deux gagne ici puisque meme version (1.27.0) des deux
    // cotes.
    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // P2 : mini-lecteur persistant, affiche sous le contenu de tous les
    // ecrans hors Lecteur (InkToneNavHost). Le module etait jusqu'ici
    // inclus dans settings.gradle.kts sans qu'aucun module n'en depende.
    implementation(project(":feature:player"))

    // Tache 3.7 : MainActivity minimale pour rendre le test de bout en
    // bout manuel possible (heberge ReaderScreen) - pas de navigation
    // complete, hors de portee de la marche a blanc (Phase 4).
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    // AUDIT_REACTIVITE_UX §5.5 — collectAsStateWithLifecycle : les
    // collecteurs de MainActivity/InkToneNavHost restaient actifs écran
    // éteint et application en arrière-plan avec collectAsState().
    implementation(libs.androidx.lifecycle.runtime.compose)
    // P3 (plan polissage Pareto) — installe au demarrage le Baseline
    // Profile (profil de reference) compile a la livraison, pour reduire
    // le temps de demarrage a froid sur Snapdragon 680 (Blueprint §11.2).
    implementation(libs.androidx.profileinstaller)

    // Tache 9bis.0.1/9bis.2 : Compose Navigation 2.8+ a routes typees
    // (@Serializable), remplace l'etat AppScreen a 3 cas (Phase 7).
    // Navigation 3 ecarte : encore en alpha (1.0.0-alpha07) mi-2026.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Tache 9.0.2 : calculateWindowSizeClass(this) dans MainActivity,
    // fondation LocalWindowSizeClass (core:designsystem) - pas de mode
    // tablette double page ici, juste la valeur disponible partout.
    implementation("androidx.compose.material3:material3-window-size-class")

    // Tache 6.2 : Configuration.Provider (InkToneApplication) cable
    // HiltWorkerFactory pour que ImportWorker (@HiltWorker) recoive ses
    // dependances via le graphe Hilt plutot que le WorkerFactory par defaut.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // AUDIT_REACTIVITE_UX §3.1 — Baseline Profile : dépendance vers le module
    // producteur (générateur du profil). Le plugin androidx.baselineprofile
    // (alias ci-dessus) crée la configuration `baselineProfile`.
    baselineProfile(project(":benchmark"))
}

// AUDIT_REACTIVITE_UX §3.1 — désactive la génération automatique pendant le
// build : par défaut le plugin câble `generateBaselineProfile` (donc
// `connectedNonMinified*AndroidTest`) dans `check`/`build`, ce qui fait échouer
// `./gradlew build` sans appareil. La génération reste manuelle
// (`./gradlew :app:generateReleaseBaselineProfile`, appareil requis).
baselineProfile {
    automaticGenerationDuringBuild = false
}
