import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Modules JVM purs sans dépendance projet par défaut : `core:common`,
 * `core:testing`. Aucune dépendance Android, comme `domain`, mais sans
 * la garde-fou "zéro dépendance projet" propre au domaine — un module
 * `core:testing` a le droit de dépendre de `core:common` (§12.4).
 */
class InkToneJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("inktone.architecture.check")

            dependencies {
                add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                add("testImplementation", "junit:junit:4.13.2")
            }
        }
    }
}
