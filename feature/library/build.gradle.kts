plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.library"
}

dependencies {
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
