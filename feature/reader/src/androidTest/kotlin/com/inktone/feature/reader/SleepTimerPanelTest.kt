package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 3d.6, test 7 — les puces et la roue du panneau Minuteur produisent la
 * MÊME sorte d'état (`ReaderIntent.SetSleepTimer`, un seul canal), annuler
 * remet à zéro, plus de comportement cyclique (`nextSleepTimerMinutes`
 * retiré en 3d.4).
 */
class SleepTimerPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun une_puce_emet_setSleepTimer_avec_sa_valeur() {
        var received: Int? = -1
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMs = null,
                armedMinutes = null,
                onSetSleepTimer = { minutes -> received = minutes },
                eyeRestReminderEnabled = true,
                eyeRestReminderIntervalMinutes = 60,
                onSetEyeRestReminderEnabled = {},
                onSetEyeRestReminderInterval = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("30 min").performClick()

        assertEquals(30, received)
    }

    @Test
    fun annuler_emet_setSleepTimer_null() {
        var received: Int? = 15
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMs = 15 * 60_000L,
                armedMinutes = 15,
                onSetSleepTimer = { minutes -> received = minutes },
                eyeRestReminderEnabled = true,
                eyeRestReminderIntervalMinutes = 60,
                onSetEyeRestReminderEnabled = {},
                onSetEyeRestReminderInterval = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Annuler").performClick()

        assertNull(received)
    }

    @Test
    fun la_roue_personnalisee_emet_le_meme_intent_setSleepTimer() {
        var received: Int? = null
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMs = null,
                armedMinutes = null,
                onSetSleepTimer = { minutes -> received = minutes },
                eyeRestReminderEnabled = true,
                eyeRestReminderIntervalMinutes = 60,
                onSetEyeRestReminderEnabled = {},
                onSetEyeRestReminderInterval = {},
                onDismiss = {},
            )
        }

        // Valeurs par défaut de la roue : 0h30 (voir CustomDurationWheel).
        composeTestRule.onNodeWithText("Valider").performClick()

        assertEquals(30, received)
    }

    /**
     * Régression signalée par Issa : « le bouton Valider de la durée
     * personnalisée ne s'active jamais, même quand on ajuste un temps ».
     *
     * Cause réelle, plus large que le bouton : la valeur sélectionnée ne
     * suivait pas la roue. L'ancienne condition de lecture — « défilement
     * terminé ET offset exactement nul » — n'était pratiquement jamais vraie
     * après un geste, si bien que l'état restait figé sur sa valeur initiale.
     * Le bouton n'était que le symptôme visible.
     *
     * Ce test fait défiler la roue pour de vrai : la valeur confirmée doit
     * avoir changé.
     */
    @Test
    fun faire_defiler_la_roue_change_la_duree_confirmee() {
        var received: Int? = null
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMs = null,
                armedMinutes = null,
                onSetSleepTimer = { minutes -> received = minutes },
                eyeRestReminderEnabled = true,
                eyeRestReminderIntervalMinutes = 60,
                onSetEyeRestReminderEnabled = {},
                onSetEyeRestReminderInterval = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag(WHEEL_MINUTES_TAG).performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Valider").performClick()

        assertNotNull("la roue doit avoir émis une durée", received)
        assertNotEquals(
            "la durée confirmée doit suivre la roue, pas rester à sa valeur initiale",
            30,
            received,
        )
    }

    @Test
    fun stepper_repos_oculaire_augmente_par_pas_de_15_minutes() {
        var received: Int? = null
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMs = null,
                armedMinutes = null,
                onSetSleepTimer = {},
                eyeRestReminderEnabled = true,
                eyeRestReminderIntervalMinutes = 60,
                onSetEyeRestReminderEnabled = {},
                onSetEyeRestReminderInterval = { minutes -> received = minutes },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("1h").assertExists()
        composeTestRule.onNodeWithContentDescription("Augmenter l'intervalle").performClick()

        assertEquals(75, received)
    }
}
