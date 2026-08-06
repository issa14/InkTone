plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.crashreporting"
}

// K10/ADR-014 — dependance de compilation inconditionnelle : le SDK
// Crashlytics compile et s'utilise sans google-services.json (ce fichier
// ne pilote que le plugin Gradle appliqué côté `app`, qui génère les
// valeurs d'initialisation FirebaseApp consommées au runtime). Ce module
// reste donc compilable et testable pour tout le monde, avec ou sans le
// fichier — seul `app` sait, via BuildConfig, s'il faut réellement lier
// FirebaseCrashReporter (voir CrashReporterModule, module app).
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}
