package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
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
                remainingMinutes = null,
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
                remainingMinutes = 15,
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
                remainingMinutes = null,
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

    @Test
    fun stepper_repos_oculaire_augmente_par_pas_de_15_minutes() {
        var received: Int? = null
        composeTestRule.setContent {
            SleepTimerPanel(
                remainingMinutes = null,
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
