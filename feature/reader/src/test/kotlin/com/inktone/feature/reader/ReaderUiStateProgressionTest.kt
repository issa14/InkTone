package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.Sentence
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tache 9bis.3.2 — `bookProgression` doit refleter la position dans le
 * LIVRE ENTIER (chapitres precedents inclus), pas seulement dans le
 * chapitre courant, contrairement a la barre par chapitre du legacy.
 */
class ReaderUiStateProgressionTest {

    private fun chapterOf(index: Int, text: String): Chapter = Chapter(
        index = index,
        href = "chapter$index.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain(text),
                    globalOffsetRange = 0 until text.length,
                ),
            ),
        ),
        sentences = listOf(Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)),
    )

    @Test
    fun progression_tient_compte_des_chapitres_precedents() {
        // 3 chapitres de 10 caracteres chacun (30 au total) - positionne
        // au debut du 2e chapitre : 10/30, pas 0/10 (progression par
        // chapitre, ce que faisait le legacy).
        val chapters = listOf(chapterOf(0, "0123456789"), chapterOf(1, "abcdefghij"), chapterOf(2, "ABCDEFGHIJ"))
        val state = ReaderUiState(
            chapters = chapters,
            currentChapterIndex = 1,
            currentSentenceIndex = 0,
            // §3.4 — le cumul est précalculé (computeChapterCharPrefix) et
            // porté par l'état ; le test le pose explicitement.
            chapterCharPrefix = computeChapterCharPrefix(chapters),
        )

        assertEquals(10f / 30f, state.bookProgression, 0.001f)
    }

    @Test
    fun progression_vide_sans_chapitres() {
        assertEquals(0f, ReaderUiState().bookProgression, 0.001f)
    }
}
