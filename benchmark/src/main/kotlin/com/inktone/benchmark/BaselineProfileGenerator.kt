package com.inktone.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P3 (plan polissage Pareto) — générateur du Baseline Profile.
 *
 * Le profil liste les classes et méthodes à compiler en avance (AOT) à
 * l'installation, au lieu de les laisser interpréter puis compiler à chaud au
 * premier lancement. C'est le seul élément du plan dont le gain se chiffre
 * directement, et il porte surtout sur la cible matérielle du projet
 * (Snapdragon 680, Blueprint §11.2) où l'écart interprété/compilé est le plus
 * large.
 *
 * ## Ce que ce parcours doit couvrir, et pourquoi
 *
 * Un profil ne vaut que ce que vaut le chemin réellement parcouru ici : tout
 * code non exercé reste interprété au premier lancement. Le parcours suit donc
 * le chemin critique du premier usage — démarrage, bibliothèque, ouverture d'un
 * livre, premier rendu de page, défilement — et pas seulement le démarrage,
 * qui ne dirait rien du coût de la pagination.
 *
 * ## Prérequis d'exécution
 *
 * `./gradlew :app:generateBaselineProfile`, appareil réel branché, Android 13+
 * (ou appareil rooté) : en dessous, la collecte du profil n'est pas autorisée
 * par la plateforme. La bibliothèque doit contenir au moins un livre — un
 * appareil à bibliothèque vide produit un profil qui ne couvre ni l'ouverture
 * ni la pagination, sans que rien ne le signale.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun parcoursDuPremierUsage() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
    ) {
        pressHome()
        startActivityAndWait()

        // Bibliothèque affichée : couvre la grille, le chargement des
        // couvertures (Coil) et la requête groupée de progression (K8).
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        device.waitForIdle()

        // Défilement de la grille : exerce la virtualisation et le décodage
        // des couvertures hors du premier écran.
        device.findObject(By.scrollable(true))?.let { grid ->
            grid.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            grid.scroll(Direction.DOWN, SCROLL_PERCENT)
            device.waitForIdle()
        }

        // Ouverture d'un livre : parsing Readium, pagination, premier rendu.
        // Le premier élément cliquable de la grille suffit — le profil ne
        // dépend pas de QUEL livre, seulement du code traversé.
        val opened = openFirstBook()
        if (opened) {
            device.waitForIdle()
            // Tourne quelques pages : c'est ce qui fait entrer le moteur de
            // pagination et la mesure de texte dans le profil. Sans cela, le
            // coût le plus visible du premier usage resterait interprété.
            repeat(PAGE_TURNS) {
                device.swipe(
                    device.displayWidth * SWIPE_FROM_X / SWIPE_DENOMINATOR,
                    device.displayHeight / 2,
                    device.displayWidth * SWIPE_TO_X / SWIPE_DENOMINATOR,
                    device.displayHeight / 2,
                    SWIPE_STEPS,
                )
                device.waitForIdle()
            }
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Ouvre le premier livre de la bibliothèque. Retourne `false` si aucun
     * n'est trouvé — la collecte se poursuit alors sur le seul démarrage,
     * plutôt que d'échouer : un profil partiel reste meilleur qu'aucun profil,
     * et l'absence de livre est un état d'appareil, pas un défaut de l'app.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.openFirstBook(): Boolean {
        val candidate = device.findObject(By.clickable(true).longClickable(true))
            ?: device.findObject(By.clickable(true))
            ?: return false
        candidate.click()
        return device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
    }

    private companion object {
        const val PACKAGE_NAME = "com.inktone.app"
        const val TIMEOUT_MS = 10_000L
        const val PAGE_TURNS = 3
        const val SCROLL_PERCENT = 0.8f
        const val GESTURE_MARGIN_DIVISOR = 5
        const val SWIPE_FROM_X = 8
        const val SWIPE_TO_X = 2
        const val SWIPE_DENOMINATOR = 10
        const val SWIPE_STEPS = 10
    }
}
