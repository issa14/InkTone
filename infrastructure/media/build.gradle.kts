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

    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
