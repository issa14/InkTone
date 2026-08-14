plugins {
    id("inktone.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.inktone.data"

    // androidTest tire transitivement infrastructure/parser et
    // infrastructure/worker (Readium), qui exigent le desugaring
    // (meme raison que infrastructure/worker/build.gradle.kts).
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":infrastructure:database"))
    implementation(project(":infrastructure:storage"))
    implementation(project(":infrastructure:parser"))
    implementation(project(":infrastructure:tts"))
    implementation(project(":infrastructure:media"))
    implementation(project(":infrastructure:worker"))
    // Lot 13 — le client OPDS est construit dans data/network (NetworkModule),
    // d'où la dépendance vers le module qui porte le qualificateur @OpdsClient.
    implementation(project(":infrastructure:opds"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
