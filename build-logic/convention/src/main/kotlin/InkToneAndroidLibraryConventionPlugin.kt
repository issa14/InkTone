import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Modules Android sans Compose : `infrastructure` (tous sous-modules) et
 * `data`. Dépendance projet par défaut vers `domain` uniquement (§12.4) ;
 * `data` ajoute ses dépendances vers les modules `infrastructure`
 * explicitement dans son propre build.gradle.kts, la matrice de la
 * §12.4 l'y autorise spécifiquement.
 */
class InkToneAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("inktone.architecture.check")

            extensions.configure<LibraryExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
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
                add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
                add("implementation", "com.google.dagger:hilt-android:2.52")
                add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
                add("testImplementation", project(":core:testing"))
                add("testImplementation", "junit:junit:4.13.2")
                add("androidTestImplementation", "androidx.test:runner:1.6.2")
            }
        }
    }
}
