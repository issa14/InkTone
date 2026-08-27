plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.inktone.benchmark"
    compileSdk = 35
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // P3 (plan polissage Pareto) — le suppressErrors=DEBUGGABLE qui
        // masquait la mesure sur un build debogable est retire : :app
        // expose desormais un build type `benchmark` non debuggable (voir
        // InkToneApplicationConventionPlugin), cible reelle des mesures.
    }

    // Build type `benchmark` non debuggable, aligne sur le build type du
    // meme nom de :app (targetProjectPath) : c'est lui qui produit les
    // chiffres de demarrage representatifs, sans suppressErrors.
    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
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

// AUDIT_REACTIVITE_UX §3.1 — le plugin producteur androidx.baselineprofile câble
// collectNonMinified*BaselineProfile et connectedNonMinified*AndroidTest dans
// `assemble`/`build`, ce qui exige un appareil et fait échouer
// `./gradlew build` sans device. On les désactive pendant le build, et on ne
// les réactive que quand la génération de profil est explicitement demandée
// (`:app:generate*BaselineProfile`, appareil requis).
gradle.taskGraph.whenReady {
    val generating = allTasks.any { it.name.startsWith("generate") && it.name.contains("BaselineProfile") }
    if (!generating) {
        allTasks
            .filter { it.name.startsWith("connectedNonMinified") || it.name.startsWith("collectNonMinified") }
            .forEach { it.setEnabled(false) }
    }
}
