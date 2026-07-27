plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.inktone.benchmark"
    compileSdk = 34
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // :app n'a pour l'instant qu'un build type debug (donc
        // debuggable=true), sur lequel androidx.benchmark refuse de
        // mesurer par defaut (resultats non fiables en debuggable).
        // Supprime volontairement pour cette premiere mise en place de
        // l'outillage (Tache 4.9) - un vrai build type "benchmark" non
        // debuggable est le sujet d'une tache separee, pas de celle-ci.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
