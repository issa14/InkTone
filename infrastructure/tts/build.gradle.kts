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
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlinx.coroutines.test)
}
