plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.tts"
    ndkVersion = "27.2.12479018"

    // FallbackTtsEngine (Tache 5.8) appelle android.util.Log.w() sur le
    // chemin de repli, exerce par un test JVM pur (FallbackTtsEngineTest) -
    // sans ceci, Log.w() leve une RuntimeException ("not mocked") en
    // dehors d'un environnement instrumente. N'affecte que l'execution
    // des tests, jamais le comportement de production.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // libonnxruntime.so existe en double source possible : celui deja
    // vendore par sherpa-onnx (jniLibs/, v1.13.4 = ONNX Runtime 1.27.0,
    // identique bit a bit a la release officielle - voir
    // PROTOTYPE_ALIGNEMENT_CTC.md §8.1) et celui embarque dans l'AAR
    // onnxruntime-android:1.27.0 (meme version, build officiel Microsoft
    // different - PAS identique bit a bit, verifie par sha256). Decision
    // documentee (§8.1, confirmee ici en pratique Tache 5.2-prod) :
    // garder le .so deja vendore par sherpa-onnx plutot que celui de
    // l'AAR - un seul .so charge au runtime, jamais deux copies
    // differentes du meme fichier dans l'APK. Les classes Kotlin de
    // l'AAR (OrtEnvironment/OrtSession/OnnxTensor) restent utilisables :
    // meme version ONNX Runtime des deux cotes, donc meme ORT_API_VERSION.
    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                abiFilters += "arm64-v8a"
            }
        }

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
    // Version 1.27.0 choisie pour matcher exactement l'ONNX Runtime deja
    // vendore par sherpa-onnx (pas une version recente au hasard) - voir
    // le commentaire packaging.jniLibs.pickFirsts ci-dessus.
    implementation(libs.onnxruntime.android)

    // Lot 14, Tache 1.2 (spike Edge TTS) — OkHttp pour le client WebSocket
    // Bing. Choix delibere : ni Retrofit ni Ktor, meme sobriete que
    // infrastructure/sync et infrastructure/opds qui construisent deja
    // leurs clients sur OkHttpClient brut. Les bibliotheques externes ne
    // sont pas bornees par checkArchitectureRules (:infrastructure ->
    // :domain seul concerne les dependances inter-modules).
    implementation(libs.okhttp)

    // Lot 14, Tache 2.1 — MockWebServer pour les tests JVM du client
    // EdgeTtsClient (protocole WebSocket, auth, retry) sans reseau reel.
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(project(":core:testing"))

    testImplementation(libs.kotlinx.coroutines.test)
}
