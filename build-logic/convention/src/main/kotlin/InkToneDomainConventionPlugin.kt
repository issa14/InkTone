import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class InkToneDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("inktone.architecture.check")

            dependencies {
                add("testImplementation", "junit:junit:4.13.2")
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }

            // Garde-fou immédiat : si un jour quelqu'un ajoute
            // com.android.tools.build ou un plugin Android à ce module,
            // le build échoue avant même la résolution des dépendances.
            afterEvaluate {
                check(!pluginManager.hasPlugin("com.android.library")) {
                    "Le module domain ne peut pas appliquer de plugin Android " +
                        "(Blueprint §4.6 — le domaine ne dépend jamais d'Android)."
                }
            }
        }
    }
}
