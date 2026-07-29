plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.worker"

    // Tache 6.9 : androidTest tire transitivement infrastructure/parser
    // (Readium), qui exige le desugaring (meme raison que
    // feature/reader/build.gradle.kts).
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    // Meme conflit que app/build.gradle.kts et infrastructure/tts : androidTest
    // tire transitivement infrastructure/tts (via :data), donc libonnxruntime.so
    // existe en double (sherpa-onnx vendore vs AAR onnxruntime-android:1.27.0).
    // Le module qui merge (celui-ci, pour son propre APK de test) doit
    // redeclarer sa propre regle - verifie en echouant sans elle (Tache 6.9).
    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tache 6.9 (benchmark import) uniquement - pipeline reel de bout en
    // bout (Room+WAL, Readium, SAF), meme precedent que
    // feature/reader/build.gradle.kts (androidTestImplementation(":data")
    // pour assembler un graphe complet dans un module isole) - jamais en
    // implementation, checkArchitectureRules ne verifie que
    // implementation/api de toute facon (infrastructure -> domain
    // uniquement en dehors des tests).
    androidTestImplementation(project(":data"))
    androidTestImplementation(project(":infrastructure:database"))
    androidTestImplementation(project(":infrastructure:parser"))
    androidTestImplementation(project(":infrastructure:storage"))
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
    androidTestImplementation("com.google.guava:guava:33.3.1-android")
}
