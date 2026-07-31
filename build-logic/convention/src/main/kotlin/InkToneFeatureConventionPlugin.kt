import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class InkToneFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("inktone.architecture.check")

            extensions.configure<LibraryExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                buildFeatures { compose = true }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }

            dependencies {
                add("implementation", project(":domain"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:common"))
                add("implementation", "com.google.dagger:hilt-android:2.52")
                add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
                add("implementation", platform("androidx.compose:compose-bom:2024.09.02"))
                // C.5 — surcharge animation 1.8.0 pour SharedTransition
                add("implementation", "androidx.compose.animation:animation:1.8.0")
                add("implementation", "androidx.compose.animation:animation-core:1.8.0")
                add("implementation", "androidx.compose.ui:ui")
                add("implementation", "androidx.compose.ui:ui-tooling-preview")
                add("implementation", "androidx.compose.material3:material3")
                add("testImplementation", project(":core:testing"))
                add("testImplementation", "junit:junit:4.13.2")
                add("androidTestImplementation", "androidx.test:runner:1.6.2")

                // Tache 9.1 : audit d'accessibilite systematique - tests
                // Compose reels (pas une checklist remplie de memoire) sur
                // les composables sans-etat de chaque ecran.
                add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
                add("debugImplementation", "androidx.compose.ui:ui-test-manifest")
            }
        }
    }
}
