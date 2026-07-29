plugins {
    id("inktone.feature")
}

android {
    namespace = "com.inktone.feature.importer"
}

dependencies {
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation("androidx.activity:activity-compose:1.9.1")
}
