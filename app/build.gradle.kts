plugins {
    id("inktone.application")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    // Tache 9.3 : trouve par la mesure reelle de la taille de l'AAB
    // (196 Mo, tres au-dessus du budget Blueprint §11.2 <= 60 Mo) -
    // libonnxruntime.so pese ~20-33 Mo PAR ABI et etait embarque pour
    // les 4 ABI (x86_64/x86/arm64-v8a/armeabi-v7a, ~107 Mo a lui seul)
    // alors qu'infrastructure/tts (voir son build.gradle.kts) ne compile
    // son propre code natif que pour arm64-v8a - aucun binaire natif de
    // ce projet ne fonctionne sur les 3 autres ABI de toute facon.
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Meme conflit que infrastructure/tts (voir son build.gradle.kts) :
    // libonnxruntime.so existe en deux exemplaires possibles (sherpa-onnx
    // vendore vs AAR onnxruntime-android:1.27.0). La regle packaging
    // d'un module bibliotheque ne s'applique qu'a SON propre merge - le
    // module qui assemble l'APK final (celui-ci) doit redeclarer sa
    // propre regle, sinon `app:mergeDebugNativeLibs` echoue avec "2 files
    // found with path 'lib/arm64-v8a/libonnxruntime.so'" (verifie en CI,
    // pas suppose). Meme decision qu'infrastructure/tts §8.1/§9.3
    // (PROTOTYPE_ALIGNEMENT_CTC.md) : garder un seul binaire, peu importe
    // lequel des deux gagne ici puisque meme version (1.27.0) des deux
    // cotes.
    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tache 3.7 : MainActivity minimale pour rendre le test de bout en
    // bout manuel possible (heberge ReaderScreen) - pas de navigation
    // complete, hors de portee de la marche a blanc (Phase 4).
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(libs.androidx.hilt.navigation.compose)

    // Tache 9.0.2 : calculateWindowSizeClass(this) dans MainActivity,
    // fondation LocalWindowSizeClass (core:designsystem) - pas de mode
    // tablette double page ici, juste la valeur disponible partout.
    implementation("androidx.compose.material3:material3-window-size-class")

    // Tache 6.2 : Configuration.Provider (InkToneApplication) cable
    // HiltWorkerFactory pour que ImportWorker (@HiltWorker) recoive ses
    // dependances via le graphe Hilt plutot que le WorkerFactory par defaut.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
