plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inktone.jvm")
}

dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
