import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * Matrice de dépendances autorisées, transposition directe du Blueprint
 * §12.4. Clé = préfixe de chemin du module qui déclare la dépendance ;
 * valeur = préfixes de chemin qu'il a le droit de dépendre.
 */
private val ALLOWED_DEPENDENCIES: Map<String, Set<String>> = mapOf(
    ":domain" to emptySet(),
    ":core:common" to emptySet(),
    ":core:testing" to setOf(":core:common", ":domain"),
    ":core:designsystem" to setOf(":core:common"),
    ":core:ui" to setOf(":core:common", ":core:designsystem"),
    ":data" to setOf(":domain", ":infrastructure"),
    ":infrastructure" to setOf(":domain"),
    ":feature" to setOf(":domain", ":core"),
    ":app" to setOf(":feature", ":data", ":infrastructure", ":core"),
)

class InkToneArchitectureCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("checkArchitectureRules", ArchitectureCheckTask::class.java)
        target.tasks.named("check").configure {
            dependsOn("checkArchitectureRules")
        }
    }
}

abstract class ArchitectureCheckTask : DefaultTask() {
    @TaskAction
    fun verify() {
        val modulePath = project.path
        val ownRule = ALLOWED_DEPENDENCIES.entries
            .firstOrNull { (prefix, _) -> modulePath.startsWith(prefix) }
            ?: return // module hors matrice (ex. build-logic lui-même) : ignoré

        val violations = mutableListOf<String>()

        project.configurations
            .filter { it.name in setOf("implementation", "api") }
            .forEach { config ->
                config.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .forEach { dep ->
                        val targetPath = dep.dependencyProject.path
                        val allowed = ownRule.value.any { targetPath.startsWith(it) }
                        if (!allowed) {
                            violations += "$modulePath -> $targetPath (règle : ${ownRule.key} " +
                                "n'autorise que ${ownRule.value})"
                        }
                    }
            }

        check(violations.isEmpty()) {
            "Violation(s) de la règle de dépendance (Blueprint §12.4) :\n" +
                violations.joinToString("\n") { "  - $it" }
        }
    }
}
