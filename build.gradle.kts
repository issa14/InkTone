plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    // K10/ADR-014 — resolus ici (classpath disponible pour tous les
    // sous-projets) mais jamais appliques globalement : seul `app`
    // les applique, et seulement si `google-services.json` est present
    // au moment du build (voir app/build.gradle.kts). Un module sans ce
    // fichier reste vert sans ces deux plugins.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Tache 9.4.1 : couverture agregee de tous les modules de production
// (exclut :benchmark - module com.android.test sans code metier propre,
// uniquement des scenarios macrobenchmark). Le plugin Kover doit etre
// applique a CHAQUE sous-projet agrege (pas seulement ici) pour exposer
// la configuration consommable "kover".
subprojects {
    if (path != ":benchmark") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

dependencies {
    subprojects
        .filter { it.path != ":benchmark" }
        .forEach { kover(project(it.path)) }
}

// Tache 9.4.1 : seuil mesure PUIS fixe, jamais l'inverse. Couverture
// ligne reelle mesuree le 2026-07-29 (./gradlew koverHtmlReport,
// build/reports/kover/html/index.html) : 15.2% (760/4997) - couvre
// uniquement les tests JVM unitaires (domain/data/fakes), pas les
// tests instrumentes (DAO Room, Compose UI, migrations) qui s'executent
// sur device reel hors de la portee par defaut de koverHtmlReport.
// Seuil fixe a 10%, marge de ~5 points sous le chiffre mesure - pas un
// chiffre arbitraire par aspiration (voir docs/execution/PHASE_9_HARDENING.md,
// Tache 9.4.1, verifie par un test negatif volontaire avant d'etre retenu).
kover {
    reports {
        verify {
            rule {
                minBound(10)
            }
        }
    }
}
