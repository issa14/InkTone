package com.inktone.data.preanalysis

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.Span
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Lot 22, Palier A — la pré-analyse persistée est la pierre angulaire du
 * lot : un round-trip exact (blocs + phrases + styles), et une
 * invalidation stricte (hash divergent → `null`, jamais une source servie
 * pour un autre fichier).
 */
class FilePreAnalysisStoreTest {

    private fun chapter(index: Int, href: String, text: String): Chapter = Chapter(
        index = index,
        href = href,
        title = "Chapitre $index",
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText(
                        plainText = text,
                        spans = listOf(Span(SpanStyles.STRONG, 0, 3, null)),
                    ),
                    globalOffsetRange = 0 until text.length,
                ),
            ),
        ),
        sentences = listOf(
            Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length, blockIndex = 0),
        ),
    )

    private fun tempStore(): FilePreAnalysisStore {
        val dir = File.createTempFile("preanalysis", "test").apply { delete() }
        dir.mkdirs()
        return FilePreAnalysisStore(dir)
    }

    @Test
    fun `un round-trip sauvegarde puis relit les chapitres a l'identique`() = runTest {
        val store = tempStore()
        val chapters = listOf(
            chapter(0, "chap1.xhtml", "Bonjour monde."),
            chapter(1, "chap2.xhtml", "Deuxieme chapitre."),
        )

        store.save("pub-1", "hash-abc", chapters)
        val loaded = store.load("pub-1", "hash-abc")

        requireNotNull(loaded)
        assertEquals(2, loaded.size)
        assertEquals("chap1.xhtml", loaded[0].href)
        assertEquals("chap2.xhtml", loaded[1].href)
        // Le contenu riche et les phrases survivent au round-trip.
        val blocks = (loaded[0].content as ChapterContent.Rich).blocks
        assertEquals("Bonjour monde.", (blocks[0] as BookBlock.ParagraphBlock).richText.plainText)
        assertEquals(SpanStyles.STRONG, (blocks[0] as BookBlock.ParagraphBlock).richText.spans[0].styles)
        assertEquals(1, loaded[0].sentences.size)
        assertEquals("Bonjour monde.", loaded[0].sentences[0].text)
    }

    @Test
    fun `un hash divergent invalide le cache - jamais servi pour une autre source`() = runTest {
        val store = tempStore()
        store.save("pub-1", "hash-original", listOf(chapter(0, "chap1.xhtml", "Texte")))

        assertNull(store.load("pub-1", "hash-different"))
    }

    @Test
    fun `un fichier absent ou corrompu retourne null`() = runTest {
        val store = tempStore()
        assertNull(store.load("pub-absent", "hash"))

        // Fichier corrompu : load doit retourner null, jamais lever.
        val dir = File.createTempFile("preanalysis", "test").apply { delete() }
        dir.mkdirs()
        val base = File(dir, "preanalysis").apply { mkdirs() }
        File(base, "pub-1.prea").writeText("{ pas du json valide")
        assertNull(FilePreAnalysisStore(dir).load("pub-1", "hash"))
    }

    @Test
    fun `la purge supprime le fichier de pre-analyse`() = runTest {
        val store = tempStore()
        store.save("pub-1", "hash-abc", listOf(chapter(0, "chap1.xhtml", "Texte")))

        store.delete("pub-1")

        assertNull(store.load("pub-1", "hash-abc"))
    }
}
