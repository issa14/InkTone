package com.inktone.feature.reader

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `bookProgression` reflète la position dans le LIVRE ENTIER, calculée sur
 * les longueurs de TOUS les chapitres (`ChapterLengths`, posées à l'import),
 * jamais sur les seuls chapitres chargés en mémoire.
 */
class ReaderUiStateProgressionTest {

    private fun loadedChapter(index: Int, text: String): Chapter = Chapter(
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

    /** Coquille d'un chapitre pas encore chargé (chargement paresseux) : aucune phrase. */
    private fun unloadedChapter(index: Int): Chapter = Chapter(
        index = index,
        href = "chapter$index.xhtml",
        title = null,
        content = ChapterContent.Rich(blocks = emptyList()),
    )

    @Test
    fun progression_tient_compte_des_chapitres_precedents() {
        // 3 chapitres de 10 caractères (30 au total), position au début du
        // 2e chapitre : 10/30, pas 0/10.
        val chapters = listOf(loadedChapter(0, "0123456789"), loadedChapter(1, "abcdefghij"), loadedChapter(2, "ABCDEFGHIJ"))
        val state = ReaderUiState(
            chapters = chapters,
            currentChapterIndex = 1,
            currentSentenceIndex = 0,
            chapterLengths = ChapterLengths.of(chapters),
        )

        assertEquals(10f / 30f, state.bookProgression!!, 0.001f)
    }

    /**
     * Bug réel corrigé : livre rouvert au chapitre 2 sur 3, les chapitres 0
     * et 1 jamais chargés dans cette session. L'ancien dénominateur ne
     * comptait que les chapitres chargés : on affichait la progression DANS
     * le chapitre (ici 50 %) comme celle du livre.
     */
    @Test
    fun progression_juste_meme_si_les_chapitres_precedents_ne_sont_pas_charges() {
        val current = Chapter(
            index = 2,
            href = "chapter2.xhtml",
            title = null,
            content = ChapterContent.Rich(emptyList()),
            sentences = listOf(
                Sentence(index = 0, text = "Début.", startOffset = 0, endOffset = 50),
                Sentence(index = 1, text = "Milieu.", startOffset = 50, endOffset = 100),
            ),
        )
        val state = ReaderUiState(
            chapters = listOf(unloadedChapter(0), unloadedChapter(1), current),
            currentChapterIndex = 2,
            currentSentenceIndex = 1,
            // Longueurs du livre entier, connues dès l'import.
            chapterLengths = ChapterLengths(listOf(100, 100, 100)),
        )

        // 200 caractères avant le chapitre + 50 dans le chapitre, sur 300.
        assertEquals(250f / 300f, state.bookProgression!!, 0.001f)
    }

    @Test
    fun progression_inconnue_sans_longueurs_jamais_une_estimation() {
        val chapters = listOf(loadedChapter(0, "0123456789"))
        val state = ReaderUiState(chapters = chapters, currentChapterIndex = 0, chapterLengths = null)

        assertNull(state.bookProgression)
    }

    @Test
    fun progression_inconnue_sans_chapitres() {
        assertNull(ReaderUiState().bookProgression)
    }
}
