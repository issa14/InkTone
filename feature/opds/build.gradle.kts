plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.opds"
}

dependencies {
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
