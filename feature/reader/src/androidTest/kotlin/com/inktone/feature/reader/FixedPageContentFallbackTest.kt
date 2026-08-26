package com.inktone.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lot 21, tâche 8 — repli gracieux du rendu PDF : une page dont
 * `renderPage` échoue (page corrompue, OOM) affiche un placeholder
 * lisible au lieu de faire tomber l'écran ou de rester un écran noir
 * muet. Test : page volontairement invalide.
 */
@RunWith(AndroidJUnit4::class)
class FixedPageContentFallbackTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun content(renderPage: suspend (Int, Int) -> com.inktone.domain.model.RenderedPage?, isRenderReady: Boolean) {
        composeTestRule.setContent {
            Box(modifier = Modifier.requiredSize(400.dp)) {
                FixedPageContent(
                    pageCount = 1,
                    currentPageIndex = 0,
                    onPageIndexChanged = {},
                    onPageOffsetChanged = {},
                    renderPage = renderPage,
                    invertColors = { false },
                    reduceMotion = true,
                    isRenderReady = isRenderReady,
                )
            }
        }
    }

    @Test
    fun page_dont_le_rendu_leve_une_exception_affiche_le_repli_lisible() {
        content(
            renderPage = { _, _ -> throw RuntimeException("page corrompue") },
            isRenderReady = true,
        )
        composeTestRule.onNodeWithText("Page illisible").assertExists()
    }

    @Test
    fun page_dont_le_rendu_retourne_null_affiche_le_repli_lisible() {
        content(
            renderPage = { _, _ -> null },
            isRenderReady = true,
        )
        composeTestRule.onNodeWithText("Page illisible").assertExists()
    }

    @Test
    fun document_pas_encore_ouvert_n_affiche_pas_le_repli() {
        // isRenderReady = false : le document PDFium n'est pas encore
        // ouvert, l'absence de rendu est transitoire, PAS un échec — le
        // repli ne doit pas apparaître à tort pendant l'ouverture.
        content(
            renderPage = { _, _ -> null },
            isRenderReady = false,
        )
        composeTestRule.onNodeWithText("Page illisible").assertDoesNotExist()
    }
}
