plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.opds"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
