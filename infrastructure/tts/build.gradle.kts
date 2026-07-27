plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.tts"

    // FallbackTtsEngine (Tache 5.8) appelle android.util.Log.w() sur le
    // chemin de repli, exerce par un test JVM pur (FallbackTtsEngineTest) -
    // sans ceci, Log.w() leve une RuntimeException ("not mocked") en
    // dehors d'un environnement instrumente. N'affecte que l'execution
    // des tests, jamais le comportement de production.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    defaultConfig {
        // TtsSynthesisBenchmarkTest (Tache 5.9) : androidx.benchmark
        // refuse par defaut de mesurer un module debuggable (resultats
        // non fiables) - meme ecart deja rencontre et documente pour le
        // module benchmark (Tache 4.9). Supprime pour cette premiere
        // mise en place ; un vrai build type non debuggable reste a
        // faire separement pour des mesures de reference fiables.
        // ACTIVITY-MISSING supprime aussi : ce module bibliotheque n'a
        // aucune Activity (verification normalement destinee a garantir
        // un etat CPU stable via un premier plan reel) - acceptable pour
        // ce microbenchmark en process, pas une mesure de bout en bout
        // avec rendu UI.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE,ACTIVITY-MISSING"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.benchmark.junit4)

    testImplementation(libs.kotlinx.coroutines.test)
}
