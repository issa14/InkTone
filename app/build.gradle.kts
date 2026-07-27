plugins {
    id("inktone.application")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tache 3.7 : MainActivity minimale pour rendre le test de bout en
    // bout manuel possible (heberge ReaderScreen) - pas de navigation
    // complete, hors de portee de la marche a blanc (Phase 4).
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(libs.androidx.hilt.navigation.compose)

    // Tache 5.1 : scaffolding de validation SherpaOnnxTtsEngine (bouton
    // "Tester Palier 2", MainActivity) - domain n'est expose que
    // transitivement en implementation par les modules feature/infra,
    // jamais remonte a :app sans cette dependance directe.
    implementation(project(":domain"))
}
