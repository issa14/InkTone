import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Module `app` uniquement : point d'entrée (Application, navigation, DI,
 * splash — §12.3), aucune logique métier. Dépend de tous les modules
 * `feature`, ainsi que de `data` et `infrastructure`, pour assembler le
 * graphe de DI (§12.4).
 */
class InkToneApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("inktone.architecture.check")

            extensions.configure<ApplicationExtension> {
                namespace = "com.inktone.app"
                compileSdk = 35

                defaultConfig {
                    applicationId = "com.inktone.app"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1
                    versionName = "0.1.0"
                }

                // buildConfig : expose BuildConfig.DEBUG a MainActivity, pour
                // que le scaffolding de marche a blanc (BootstrapAndOpenFixture)
                // ne puisse jamais s'executer sur un build de release (revue
                // suite au bug OnConflictStrategy.REPLACE/CASCADE, voir
                // PublicationDao).
                buildFeatures { compose = true; buildConfig = true }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }

            dependencies {
                listOf(
                    "feature:library", "feature:reader", "feature:player",
                    "feature:search", "feature:import", "feature:settings",
                    "feature:statistics", "feature:onboarding",
                    "data",
                    "infrastructure:database", "infrastructure:storage",
                    "infrastructure:parser", "infrastructure:tts",
                    "infrastructure:media", "infrastructure:worker",
                    "infrastructure:crashreporting",
                    "core:designsystem", "core:ui", "core:common",
                ).forEach { add("implementation", project(":$it")) }

                add("implementation", "com.google.dagger:hilt-android:2.52")
                add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
                add("implementation", platform("androidx.compose:compose-bom:2024.09.02"))
                // C.5 — surcharge animation 1.8.0 pour SharedTransition (sharedElement/rememberSharedContentState)
                add("implementation", "androidx.compose.animation:animation:1.8.0")
                add("implementation", "androidx.compose.animation:animation-core:1.8.0")
                add("implementation", "androidx.compose.ui:ui")
                add("implementation", "androidx.compose.ui:ui-tooling-preview")
                add("implementation", "androidx.compose.material3:material3")
                add("testImplementation", "junit:junit:4.13.2")
            }
        }
    }
}
