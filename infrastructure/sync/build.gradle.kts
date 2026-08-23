import java.util.Properties

plugins {
    id("inktone.android.library")
    alias(libs.plugins.kotlin.serialization)
}

// Lot 11, tâche 11.4 — clientId/redirectUri OAuth Google fournis
// séparément par Issa (prérequis hors périmètre de Claude Code, voir
// docs/execution/LOT_11_SYNCHRONISATION.md), jamais codés en dur.
// Lus depuis local.properties (déjà gitignoré, comme keystore
// .properties) : absents par défaut pour quiconque clone le dépôt,
// exactement le même patron que `firebaseConfigured` dans
// app/build.gradle.kts. Une valeur vide rend la configuration
// manquante explicite (GoogleAuthConfig.isConfigured) plutôt que de
// faire planter le build ou l'app silencieusement.
val syncLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Un client OAuth Android est lié à un couple `package + SHA-1` : celui
// enregistré avec l'empreinte de la clé debug ne peut pas autoriser un
// build signé par inktone-release.jks, et inversement. Il en faut donc un
// par clé de signature, et le build doit choisir le bon selon le
// buildType — sinon brancher la synchronisation en release la casse en
// debug, ce qui est exactement ce qui est arrivé (voir
// docs/device-verification/chemin-release-v1.0.0.md).
//
// Les clés `*_DEBUG` sont facultatives : sans elles, le debug retombe sur
// le client release. La synchronisation n'y fonctionnera pas (le SHA-1 ne
// correspondra pas) mais le build reste vert, y compris pour quiconque
// clone le dépôt sans aucun de ces secrets.
fun oauthProperty(name: String, debug: Boolean): String {
    val debugValue = if (debug) syncLocalProperties.getProperty("${name}_DEBUG", "") else ""
    return debugValue.ifBlank { syncLocalProperties.getProperty(name, "") }
}

// AppAuth déclare `${appAuthRedirectScheme}` dans son propre manifeste
// (RedirectUriReceiverActivity). Ce module en dépend, donc son APK de test
// instrumenté assemble lui aussi un manifeste final : le placeholder doit
// être défini ici, pas seulement dans `app`. Un placeholder absent fait
// échouer la fusion pour quiconque clone le dépôt, d'où la valeur de repli.
fun redirectSchemeOrFallback(debug: Boolean): String =
    oauthProperty("GOOGLE_OAUTH_REDIRECT_SCHEME", debug).ifBlank { "inktone.oauth.unconfigured" }

android {
    namespace = "com.inktone.infrastructure.sync"

    buildTypes {
        getByName("debug") { configureOauth(debug = true) }
        getByName("release") { configureOauth(debug = false) }
    }

    buildFeatures {
        buildConfig = true
    }
}

/**
 * Pose, pour un buildType, le couple OAuth qui lui correspond : les deux
 * `BuildConfig` lus par `GoogleAuthConfig` et le placeholder de manifeste
 * exigé par AppAuth. Les trois valeurs doivent venir du MÊME client —
 * les séparer produirait une redirection qui n'appartient pas au clientId
 * annoncé, donc un `invalid_request` opaque à l'exécution.
 */
fun com.android.build.api.dsl.LibraryBuildType.configureOauth(debug: Boolean) {
    manifestPlaceholders["appAuthRedirectScheme"] = redirectSchemeOrFallback(debug)
    buildConfigField(
        "String", "GOOGLE_OAUTH_CLIENT_ID",
        "\"${oauthProperty("GOOGLE_OAUTH_CLIENT_ID", debug)}\"",
    )
    buildConfigField(
        "String", "GOOGLE_OAUTH_REDIRECT_SCHEME",
        "\"${oauthProperty("GOOGLE_OAUTH_REDIRECT_SCHEME", debug)}\"",
    )
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.appauth)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
