plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.player"
}

dependencies {
    // P2 : le mini-lecteur consomme le contrat domaine PlaybackSession
    // (expose par Hilt), plus aucun MediaController media3 - l'ancien
    // PlayerViewModel parlait par SessionToken/ComponentName a un service
    // qui ne jouait rien (code mort, constat §1 du plan de polissage).
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
}
