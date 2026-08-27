plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
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
