plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.tts"
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
