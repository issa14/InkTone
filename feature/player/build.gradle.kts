plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.player"
}

dependencies {
    // MediaController (Tache 5.5) parle a AudioPlaybackService (infrastructure/media)
    // via SessionToken/ComponentName par nom de classe (string), jamais par
    // dependance de compilation directe sur ce module - respecte le sens des
    // dependances (Blueprint §12.4 : feature ne depend jamais d'infrastructure).
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
