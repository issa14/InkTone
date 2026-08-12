plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.parser"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    // pdfiumandroid (io.legere) embarque un binding natif PDFium (Lot 12,
    // tache 12.1) - alignement strict sur l'unique ABI ciblee par le
    // projet (Snapdragon 680, voir infrastructure/tts, app/build.gradle.kts),
    // quelles que soient les ABI presentes dans l'AAR upstream.
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}

configurations.all {
    resolutionStrategy {
        // pdfiumandroid (io.legere) tire kotlin-stdlib:2.4.10 en transitif -
        // metadata Kotlin incompatible avec le plugin Kotlin du projet
        // (2.0.20), fait echouer kspDebugKotlin (verifie par build reel,
        // Lot 12 tache 12.1). Force le stdlib du projet plutot que de
        // laisser Gradle resoudre vers le plus recent demande.
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.pdfium.android)
    implementation(libs.jsoup)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":core:testing"))

    testImplementation(libs.kotlinx.coroutines.test)
}
