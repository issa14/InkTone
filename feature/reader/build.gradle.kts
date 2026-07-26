plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.reader"

    defaultConfig {
        // Tache 3.6 : tests instrumentes Hilt (@HiltAndroidTest) - exige
        // HiltTestApplication comme application de test.
        testInstrumentationRunner = "com.inktone.feature.reader.HiltTestRunner"
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // infrastructure/database et infrastructure/tts : uniquement pour les
    // tests instrumentes Hilt de la Tache 3.6 (le graphe complet doit
    // pouvoir s'assembler dans ce module isole) - jamais en implementation,
    // feature/* ne depend que de domain/core (Blueprint, regles de dependance).
    androidTestImplementation(project(":infrastructure:database"))
    androidTestImplementation(project(":infrastructure:tts"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
}
