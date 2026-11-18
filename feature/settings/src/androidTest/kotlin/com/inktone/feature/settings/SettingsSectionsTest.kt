package com.inktone.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.inktone.domain.model.UserPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Audit UI P1-12 : cinq sections, règles de prononciation en une ligne, plus de préréglage Mode sombre. */
class SettingsSectionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun les_cinq_sections_remplacent_les_prereglages() {
        composeTestRule.setContent {
            MaterialTheme { SettingsContent(preferences = UserPreferences(), onIntent = {}) }
        }

        for (section in listOf("Narration", "Apparence", "Habitudes de lecture", "Données", "Confidentialité")) {
            composeTestRule.onNodeWithText(section).performScrollTo()
        }
        composeTestRule.onNodeWithText("Préréglages").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mode sombre").assertDoesNotExist()
        composeTestRule.onNodeWithText("Profil Accessibilité").performScrollTo()
    }

    @Test
    fun la_ligne_des_regles_de_prononciation_ouvre_leur_ecran() {
        var opened = false
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(
                    preferences = UserPreferences(),
                    onIntent = {},
                    onOpenPronunciationRules = { opened = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Aucune règle").performScrollTo()
        composeTestRule.onNodeWithText("Règles de prononciation").performScrollTo().performClick()
        assertTrue(opened)
    }
}
