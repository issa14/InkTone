plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inktone.domain")
}

dependencies {
    testImplementation(project(":core:testing"))
}
