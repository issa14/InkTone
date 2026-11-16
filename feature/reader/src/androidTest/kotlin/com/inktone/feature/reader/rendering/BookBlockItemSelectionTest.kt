package com.inktone.feature.reader.rendering

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Lot 25, palier 25.0 — un appui long en mode défilement doit publier le mot
 * visé ET les bornes du popup d'actions : sans bornes, `ReaderScreen` ne monte
 * jamais `SelectionActionPopup` (régression de `40d9c650`).
 */
class BookBlockItemSelectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val text = "Premier bloc de texte."
    private val block = BookBlock.ParagraphBlock(
        richText = StyledText.plain(text),
        globalOffsetRange = 100 until 100 + text.length,
    )

    @Test
    fun appui_long_publie_le_mot_et_les_bornes_du_popup() {
        var selection by mutableStateOf<IntRange?>(null)
        var bounds: Rect? = null
        composeTestRule.setContent {
            BookBlockItem(
                block = block,
                baseTextStyle = TextStyle(fontSize = 20.sp),
                freeSelectedRange = rememberUpdatedState(selection),
                onFreeSelectionChanged = { anchor, focus -> selection = minOf(anchor, focus)..maxOf(anchor, focus) },
                onFreeSelectionBoundsInWindow = { _, b -> bounds = b },
            )
        }

        composeTestRule.onNodeWithContentDescription(text)
            .performTouchInput { longClick(centerLeft + Offset(10f, 0f)) }
        composeTestRule.waitForIdle()

        assertEquals("le mot « Premier », en offsets absolus", 100..106, selection)
        assertNotNull("bornes du popup publiées", bounds)
    }

    @Test
    fun un_tap_apres_la_selection_l_efface() {
        var selection by mutableStateOf<IntRange?>(null)
        var cleared = false
        composeTestRule.setContent {
            BookBlockItem(
                block = block,
                baseTextStyle = TextStyle(fontSize = 20.sp),
                freeSelectedRange = rememberUpdatedState(selection),
                onFreeSelectionChanged = { anchor, focus -> selection = minOf(anchor, focus)..maxOf(anchor, focus) },
                onFreeSelectionCleared = {
                    cleared = true
                    selection = null
                },
            )
        }

        val node = composeTestRule.onNodeWithContentDescription(text)
        node.performTouchInput { longClick(centerLeft + Offset(10f, 0f)) }
        composeTestRule.waitForIdle()
        node.performTouchInput { click(centerRight - Offset(10f, 0f)) }
        composeTestRule.waitForIdle()

        assertTrue("sélection effacée par le tap", cleared)
    }
}
