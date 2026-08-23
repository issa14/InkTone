import java.util.Properties

plugins {
    id("inktone.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.inktone.infrastructure.sync"

    // Lot 11, tâche 11.4 — clientId/redirectUri OAuth Google fournis
    // séparément par Issa (prérequis hors périmètre de Claude Code, voir
    // docs/execution/LOT_11_SYNCHRONISATION.md), jamais codés en dur.
    // Lus depuis local.properties (déjà gitignoré, comme keystore
    // .properties) : absents par défaut pour quiconque clone le dépôt,
    // exactement le même patron que `firebaseConfigured` dans
    // app/build.gradle.kts. Une valeur vide rend la configuration
    // manquante explicite (GoogleAuthConfig.isConfigured) plutôt que de
    // faire planter le build ou l'app silencieusement.
    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    // AppAuth declare `${appAuthRedirectScheme}` dans son propre manifeste
    // (RedirectUriReceiverActivity). Ce module en depend, donc son APK de
    // test instrumente assemble lui aussi un manifeste final : le
    // placeholder doit etre defini ici, pas seulement dans `app`. Meme
    // valeur de repli que la, pour la meme raison — un placeholder absent
    // fait echouer la fusion pour quiconque clone le depot.
    val oauthRedirectScheme = localProperties
        .getProperty("GOOGLE_OAUTH_REDIRECT_SCHEME", "")
        .ifBlank { "inktone.oauth.unconfigured" }

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = oauthRedirectScheme
        buildConfigField(
            "String", "GOOGLE_OAUTH_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_OAUTH_CLIENT_ID", "")}\"",
        )
        buildConfigField(
            "String", "GOOGLE_OAUTH_REDIRECT_SCHEME",
            "\"${localProperties.getProperty("GOOGLE_OAUTH_REDIRECT_SCHEME", "")}\"",
        )
    }
    buildFeatures {
        buildConfig = true
    }
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
