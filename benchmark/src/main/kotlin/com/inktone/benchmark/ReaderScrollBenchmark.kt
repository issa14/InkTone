package com.inktone.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lot 25, palier 25.1 — fluidité du défilement du Lecteur sur build `benchmark`
 * (non débogable, profil de référence), cible Blueprint §11.2. Les chiffres
 * qui portent les critères du lot viennent d'ici ; le script debug ne sert
 * qu'aux comparaisons rapides avant/après.
 *
 * Prérequis : le premier livre de la bibliothèque s'ouvre en mode défilement
 * (réglage du lecteur) et aucun travail de fond ne tourne (réindexation).
 * Autant de balayages vers le haut que vers le bas : la position de lecture
 * revient à peu près à son point de départ.
 *
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.inktone.benchmark.ReaderScrollBenchmark`
 */
@RunWith(AndroidJUnit4::class)
class ReaderScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun defilementDuLecteur() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
            check(openFirstBook()) { "Aucun livre dans la bibliothèque" }
            device.waitForIdle()
        },
    ) {
        repeat(SWIPES) {
            readingSwipe(down = true)
            device.waitForIdle()
        }
        repeat(SWIPES) {
            readingSwipe(down = false)
            device.waitForIdle()
        }
    }

    private companion object {
        const val ITERATIONS = 3
        const val SWIPES = 5
    }
}
