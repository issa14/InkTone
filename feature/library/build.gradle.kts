plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.library"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
