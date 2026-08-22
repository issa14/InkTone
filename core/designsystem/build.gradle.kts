plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("inktone.architecture.check")
}

android {
    namespace = "com.inktone.core.designsystem"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    // StatusBarColorEffect : WindowCompat.getInsetsController (contraste des
    // icones systeme deduit de la luminance de la couleur posee).
    implementation(libs.androidx.core.ktx)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    // AppIcons (Tache 9bis.1.1) : icones hors du sous-ensemble Icons.Filled/
    // Outlined de base embarque par material3. api() et non implementation() :
    // AppIcons expose des ImageVector de ce module dans sa propre API
    // publique, les modules consommateurs (feature/*) en ont besoin sur
    // leur propre classpath de compilation (Tache 9bis.3.3, decouvert en
    // essayant d'utiliser Icons.Filled.Pause/Timer hors de ce module).
    api("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
}
