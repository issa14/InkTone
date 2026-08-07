plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.settings"
}

dependencies {
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    // Lot 6, Palier B — SettingsViewModel injecte Context (cache Vider le
    // cache/taille) : Robolectric fournit un vrai Context.cacheDir en test
    // JVM plutôt qu'un test instrumenté (même pattern que feature/reader).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
