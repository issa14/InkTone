plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.media"
}

dependencies {
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    // P5 — couverture du livre dans la notification et sur l'écran verrouillé.
    // Coil est déjà le chargeur d'images de l'app : passer par lui réutilise
    // son cache plutôt que de décompresser la couverture une seconde fois.
    implementation(libs.coil.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
