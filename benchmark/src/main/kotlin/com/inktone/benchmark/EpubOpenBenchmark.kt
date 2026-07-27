package com.inktone.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Budget cible (Blueprint §11.2) : ouverture d'un EPUB de 5 Mo -> premier
 * rendu <= 800ms sur baseline Snapdragon 680. Ce benchmark mesure le
 * temps reel sur device de test ; le comparer au budget est un geste
 * manuel pour l'instant (pas d'assertion automatique de seuil — a
 * envisager une fois plusieurs mesures de reference accumulees, pas des
 * la premiere execution).
 */
@RunWith(AndroidJUnit4::class)
class EpubOpenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun ouvertureEpub5Mo() = benchmarkRule.measureRepeated(
        packageName = "com.inktone.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        // Necessite un point d'entree UI pour ouvrir un EPUB de test
        // fixe (5 Mo) depuis l'ecran de demarrage — a cabler une fois
        // feature/library existant (Phase 6). Placeholder de mesure de
        // demarrage pur pour l'instant, pas encore le scenario complet
        // "ouverture EPUB" annonce par le budget §11.2.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000)
    }
}
