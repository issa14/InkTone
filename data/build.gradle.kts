plugins {
    id("inktone.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.inktone.data"
}

dependencies {
    implementation(project(":infrastructure:database"))
    implementation(project(":infrastructure:storage"))
    implementation(project(":infrastructure:parser"))
    implementation(project(":infrastructure:tts"))
    implementation(project(":infrastructure:media"))
    implementation(project(":infrastructure:worker"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
