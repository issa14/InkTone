import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

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

                // Audit de consolidation v1.0.0 (AUDIT_CONSOLIDATION_V1.md) :
                // versionName aligne sur la release. versionCode incremente a
                // chaque artifact distribue (v1.0.0-beta.1 = 1, publiee sur
                // GitHub Releases le 2026-08-23) — jamais reutilise, meme hors
                // Play Store, pour que les mises a jour restent ordonnees.
                defaultConfig {
                    applicationId = "com.inktone.app"
                    minSdk = 26
                    // targetSdk 35 : exigence Play, et bascule de comportement
                    // — Android 15 impose l'edge-to-edge et rend
                    // `window.statusBarColor`/`navigationBarColor` sans effet.
                    // L'app ne les utilise plus : `MainActivity` appelle
                    // `enableEdgeToEdge()`, les barres système sont
                    // transparentes et leur couleur vient du contenu dessiné
                    // derrière (voir `SystemBarIconsEffect`).
                    targetSdk = 35
                    versionCode = 2
                    versionName = "1.0.0-beta.2"
                }

                // ── Signature release (audit v1.0.0) ────────────────────────────
                // Aucune config de signature n'existait : `assembleRelease`
                // produisait un APK/AAB non signable. Lecture conditionnelle de
                // `keystore.properties` (gitignore, absent par defaut pour
                // quiconque clone le depot) — meme pattern que
                // `firebaseConfigured` plus haut : sans le fichier, la release
                // reste NON signee mais `./gradlew build` reste vert en CI.
                // Ne jamais committer les secrets ; ne jamais afficher leurs
                // valeurs (CLAUDE.md).
                val keystoreProperties = Properties().apply {
                    val file = rootProject.file("keystore.properties")
                    if (file.exists()) file.inputStream().use { load(it) }
                }
                val releaseSigningConfigured =
                    keystoreProperties.getProperty("storeFile") != null &&
                    keystoreProperties.getProperty("storePassword") != null &&
                    keystoreProperties.getProperty("keyAlias") != null &&
                    keystoreProperties.getProperty("keyPassword") != null

                if (releaseSigningConfigured) {
                    signingConfigs {
                        create("release") {
                            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                            storePassword = keystoreProperties.getProperty("storePassword")
                            keyAlias = keystoreProperties.getProperty("keyAlias")
                            keyPassword = keystoreProperties.getProperty("keyPassword")
                        }
                    }
                }

                buildTypes {
                    getByName("release") {
                        // R8/minify VOLONTAIREMENT PAS active. Explicitement
                        // false pour qu'aucune activation accidentelle ne se
                        // glisse.
                        //
                        // La justification d'origine — « AAB 196 Mo contre un
                        // budget Blueprint §11.2 de 60 Mo » — ne tient PLUS :
                        // le probleme de taille a ete regle autrement, par le
                        // filtre `abiFilters arm64-v8a` (voir app/build.gradle
                        // .kts), et l'AAB mesure aujourd'hui 30 Mo. Ne pas
                        // reprendre cet argument.
                        //
                        // Ce qui reste vrai, et seul motif du report : activer
                        // R8 sans valider sur appareil les regles de
                        // conservation de Readium et d'onnxruntime (reflexion,
                        // JNI) risque des pannes que les tests JVM ne verraient
                        // pas. Le gain attendu est reel — ~40 Mo de classes*.dex
                        // dans l'APK — donc l'activation vaut une campagne de
                        // test dediee, pas un report indefini.
                        isMinifyEnabled = false
                        isShrinkResources = false
                        if (releaseSigningConfigured) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                    // P3 (plan polissage Pareto) — build type non debuggable
                    // pour des mesures macrobenchmark fiables. Le module
                    // `benchmark` (com.android.test) ciblait jusqu'ici le type
                    // `debug` (debogable) en masquant le refus de
                    // androidx.benchmark par suppressErrors=DEBUGGABLE : les
                    // chiffres de demarrage etaient donc non representatifs.
                    // matchingFallbacks vers release : les modules bibliotheque
                    // n'exposent que debug/release.
                    create("benchmark") {
                        isDebuggable = false
                        matchingFallbacks += listOf("release")
                        if (releaseSigningConfigured) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }

                // buildConfig : expose BuildConfig.VERSION_NAME, lu par
                // BackupViewModel (estampille de l'export de sauvegarde) et par
                // l'ecran A propos (InkToneNavHost).
                //
                // Ce n'est plus BuildConfig.DEBUG qui le justifie : le
                // scaffolding de marche a blanc (BootstrapAndOpenFixture) qu'il
                // gardait a ete retire au Lot 10, et plus aucun code de `app`
                // ne lit DEBUG.
                buildFeatures { compose = true; buildConfig = true }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }

            // AUDIT_REACTIVITE_UX.md §6.2 : les modèles du domaine vivent dans
            // un module JVM pur, inconnus du compilateur Compose → déclarés
            // stables (compose-stability.conf à la racine) pour autoriser le
            // saut de recomposition. Rapports du compilateur activés à la
            // demande (coût de génération) : -Pinktone.composeCompilerReports=true.
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFile.set(rootProject.file("compose-stability.conf"))
                if (rootProject.findProperty("inktone.composeCompilerReports") == "true") {
                    val reportsDir = layout.buildDirectory.dir("reports/compose_compiler")
                    reportsDestination.set(reportsDir)
                    metricsDestination.set(reportsDir)
                }
            }

            dependencies {
                listOf(
                    "feature:library", "feature:reader", "feature:player",
                    "feature:search", "feature:import", "feature:settings",
                    "feature:statistics", "feature:onboarding", "feature:sync",
                    "feature:opds",
                    "data",
                    "infrastructure:database", "infrastructure:storage",
                    "infrastructure:parser", "infrastructure:tts",
                    "infrastructure:media", "infrastructure:worker",
                    "infrastructure:crashreporting", "infrastructure:sync",
                    "infrastructure:opds",
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
