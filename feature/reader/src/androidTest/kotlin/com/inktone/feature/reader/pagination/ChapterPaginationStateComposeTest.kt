package com.inktone.feature.reader.pagination

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.Sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3b.7, test 3 — garde-fou de la Tâche 3b.1 : pour un même
 * chapitre, un même style et une même hauteur utile, le `pageCount` est
 * identique quelle que soit l'instance qui le calcule. Avant 3b.1, la
 * mesure vivait *dans* `PagedChapterContent` — jamais montée en mode
 * défilement, donc sans équivalent à comparer ; ce test vérifie que le
 * calcul désormais hissé est déterministe et reproductible à partir des
 * mêmes entrées, condition nécessaire pour que défilement et pagé
 * s'accordent sur le même total.
 */
class ChapterPaginationStateComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fixtureChapter(): Chapter {
        val texts = (0 until 20).map { i -> "Phrase numéro $i, suffisamment longue pour occuper une ligne entière du viewport de test." }
        var offset = 0
        val blocks = texts.mapIndexed { i, text ->
            val len = text.length
            val block = BookBlock.ParagraphBlock(
                richText = StyledText.plain(text),
                globalOffsetRange = offset until (offset + len),
            )
            offset += len
            block
        }
        val sentences = texts.mapIndexed { i, text ->
            Sentence(index = i, text = text, startOffset = i * (text.length + 10), endOffset = i * (text.length + 10) + text.length)
        }
        return Chapter(
            index = 0, href = "c.xhtml", title = null,
            content = ChapterContent.Rich(blocks = blocks),
            sentences = sentences,
        )
    }

    @Test
    fun meme_chapitre_meme_style_meme_hauteur_donne_le_meme_pageCount() {
        val chapter = fixtureChapter()
        var pageCountA by mutableIntStateOf(-1)
        var pageCountB by mutableIntStateOf(-1)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(320.dp, 180.dp)) {
                val paginationA = rememberChapterPaginationState(
                    chapter = chapter,
                    nextChapter = null,
                    currentSentenceIndex = 0,
                    fontSizeSp = 20,
                    lineHeightSp = 20,
                    viewportWidthPx = 900,
                    viewportHeightPx = 500,
                    paddingPx = 40,
                )
                pageCountA = paginationA.pageCount(chapter.index)
            }
            Box(modifier = Modifier.size(320.dp, 180.dp)) {
                val paginationB = rememberChapterPaginationState(
                    chapter = chapter,
                    nextChapter = null,
                    currentSentenceIndex = 0,
                    fontSizeSp = 20,
                    lineHeightSp = 20,
                    viewportWidthPx = 900,
                    viewportHeightPx = 500,
                    paddingPx = 40,
                )
                pageCountB = paginationB.pageCount(chapter.index)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { pageCountA > 1 && pageCountB > 1 }

        assertTrue("la fixture doit produire plusieurs pages pour que ce test soit significatif", pageCountA > 1)
        assertEquals(pageCountA, pageCountB)
    }
}
