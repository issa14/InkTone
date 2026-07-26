plugins {
    `kotlin-dsl`
}

group = "com.inktone.buildlogic"

dependencies {
    compileOnly("com.android.tools.build:gradle:8.6.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.20")
}

gradlePlugin {
    plugins {
        register("inktoneDomain") {
            id = "inktone.domain"
            implementationClass = "InkToneDomainConventionPlugin"
        }
        register("inktoneJvm") {
            id = "inktone.jvm"
            implementationClass = "InkToneJvmConventionPlugin"
        }
        register("inktoneAndroidLibrary") {
            id = "inktone.android.library"
            implementationClass = "InkToneAndroidLibraryConventionPlugin"
        }
        register("inktoneFeature") {
            id = "inktone.feature"
            implementationClass = "InkToneFeatureConventionPlugin"
        }
        register("inktoneApplication") {
            id = "inktone.application"
            implementationClass = "InkToneApplicationConventionPlugin"
        }
        register("inktoneArchitectureCheck") {
            id = "inktone.architecture.check"
            implementationClass = "InkToneArchitectureCheckPlugin"
        }
    }
}
