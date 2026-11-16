package com.inktone.feature.reader.rendering

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.feature.reader.selection.LocalVisibleTextRegistry
import com.inktone.feature.reader.selection.SelectionHandle
import com.inktone.feature.reader.selection.VisibleTextRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Lot 25 — un appui long en mode défilement doit publier le mot visé ET des
 * bornes de popup : sans bornes, `ReaderScreen` ne monte jamais
 * `SelectionActionPopup` (régression de `40d9c650`). Les bornes viennent du
 * [VisibleTextRegistry] (ADR-029).
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
        val registry = VisibleTextRegistry()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVisibleTextRegistry provides registry) {
                BookBlockItem(
                    block = block,
                    baseTextStyle = TextStyle(fontSize = 20.sp),
                    wordHighlightColor = Color.Yellow,
                    freeSelectedRange = rememberUpdatedState(selection),
                    onFreeSelectionChanged = { anchor, focus -> selection = minOf(anchor, focus)..maxOf(anchor, focus) },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(text)
            .performTouchInput { longClick(centerLeft + Offset(10f, 0f)) }
        composeTestRule.waitForIdle()

        assertEquals("le mot « Premier », en offsets absolus", 100..106, selection)
        assertNotNull("bornes du popup", composeTestRule.runOnIdle { registry.boundsInWindow(selection!!) })
    }

    @Test
    fun le_registre_relie_deux_paragraphes() {
        val second = "Second bloc, plus bas."
        val secondBlock = BookBlock.ParagraphBlock(
            richText = StyledText.plain(second),
            globalOffsetRange = 200 until 200 + second.length,
        )
        val registry = VisibleTextRegistry()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVisibleTextRegistry provides registry) {
                Column {
                    BookBlockItem(block = block, baseTextStyle = TextStyle(fontSize = 20.sp), wordHighlightColor = Color.Yellow)
                    BookBlockItem(block = secondBlock, baseTextStyle = TextStyle(fontSize = 20.sp), wordHighlightColor = Color.Yellow)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            val first = registry.boundsInWindow(100..106)!!
            val both = registry.boundsInWindow(100..205)!!
            assertTrue("l'union descend dans le second paragraphe", both.bottom > first.bottom)
            val start = registry.handleAnchor(100, SelectionHandle.START)!!
            val end = registry.handleAnchor(206, SelectionHandle.END)!!
            assertTrue("la poignée de fin est sous celle de début", end.top > start.top)
            assertEquals("le curseur sous l'ancre de début", 100, registry.caretAt(start.center))
            assertEquals("un point sous le dernier bloc vise le dernier bloc", true,
                registry.caretAt(end.center.copy(y = end.bottom + 500f))!! in 200..222)
        }
    }

    @Test
    fun un_tap_apres_la_selection_l_efface() {
        var selection by mutableStateOf<IntRange?>(null)
        var cleared = false
        composeTestRule.setContent {
            BookBlockItem(
                block = block,
                baseTextStyle = TextStyle(fontSize = 20.sp),
                wordHighlightColor = Color.Yellow,
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
