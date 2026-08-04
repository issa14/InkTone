plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.reader"
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Tache 3.6 : tests instrumentes Hilt (@HiltAndroidTest) - exige
        // HiltTestApplication comme application de test.
        testInstrumentationRunner = "com.inktone.feature.reader.HiltTestRunner"
    }

    // Tache 3a.4 : Robolectric pour ChapterTextMeasurerTest - TextMeasurer
    // exige une resolution de police reelle (android.graphics), impossible
    // en JVM pur. isIncludeAndroidResources fournit les ressources/polices
    // systeme necessaires au shadow de Robolectric.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // data (androidTest, ci-dessous) tire transitivement infrastructure/parser
    // et donc Readium, qui exige le desugaring (Tache 3.2) - propage jusqu'ici.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    // Tache 3a.4 : ChapterTextMeasurerTest exige TextMeasurer (mesure de
    // police Android reelle) - Robolectric est le seul moyen de l'executer
    // en test JVM plutot qu'en instrumente (device/emulateur).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Tache 7.0 : SelectionContainer (androidx.compose.foundation.text.selection)
    implementation("androidx.compose.foundation:foundation")
    // Tache 9bis.3.1 : WindowInsetsControllerCompat (mode immersif)
    implementation(libs.androidx.core.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // data (RepositoryModule/UseCaseModule), infrastructure/database
    // (DatabaseModule) et infrastructure/tts (TtsModule) : uniquement pour
    // les tests instrumentes Hilt de la Tache 3.6 (le graphe complet doit
    // pouvoir s'assembler dans ce module isole, y compris les bindings dont
    // ReaderViewModel depend) - jamais en implementation, feature/* ne
    // depend que de domain/core (Blueprint, regles de dependance).
    androidTestImplementation(project(":data"))
    androidTestImplementation(project(":infrastructure:database"))
    androidTestImplementation(project(":infrastructure:tts"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
}
