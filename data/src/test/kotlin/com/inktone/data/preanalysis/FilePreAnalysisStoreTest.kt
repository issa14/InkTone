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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val first = requireNotNull(store.loadChapter("pub-1", "hash-abc", "chap1.xhtml"))
        val second = requireNotNull(store.loadChapter("pub-1", "hash-abc", "chap2.xhtml"))

        assertEquals("chap1.xhtml", first.href)
        assertEquals("chap2.xhtml", second.href)
        // Le contenu riche et les phrases survivent au round-trip.
        val blocks = (first.content as ChapterContent.Rich).blocks
        assertEquals("Bonjour monde.", (blocks[0] as BookBlock.ParagraphBlock).richText.plainText)
        assertEquals(SpanStyles.STRONG, (blocks[0] as BookBlock.ParagraphBlock).richText.spans[0].styles)
        assertEquals(1, first.sentences.size)
        assertEquals("Bonjour monde.", first.sentences[0].text)
        assertEquals("Deuxieme chapitre.", second.sentences[0].text)
        // Un href absent d'une pré-analyse valide : null, jamais une erreur.
        assertNull(store.loadChapter("pub-1", "hash-abc", "absent.xhtml"))
    }

    @Test
    fun `un hash divergent invalide le cache - jamais servi pour une autre source`() = runTest {
        val store = tempStore()
        store.save("pub-1", "hash-original", listOf(chapter(0, "chap1.xhtml", "Texte")))

        assertNull(store.loadChapter("pub-1", "hash-different", "chap1.xhtml"))
    }

    @Test
    fun `un fichier absent ou corrompu retourne null`() = runTest {
        val store = tempStore()
        assertNull(store.loadChapter("pub-absent", "hash", "chap1.xhtml"))

        // En-tête corrompu : null, jamais une exception.
        val dir = File.createTempFile("preanalysis", "test").apply { delete() }
        dir.mkdirs()
        val pubDir = File(dir, "preanalysis/pub-1").apply { mkdirs() }
        File(pubDir, "header.json").writeText("{ pas du json valide")
        assertNull(FilePreAnalysisStore(dir).loadChapter("pub-1", "hash", "chap1.xhtml"))
        assertFalse(FilePreAnalysisStore(dir).isFresh("pub-1", "hash"))
    }

    @Test
    fun `isFresh suit les memes criteres que load sans relire les chapitres`() = runTest {
        val store = tempStore()
        assertFalse("absent", store.isFresh("pub-1", "hash-abc"))

        store.save("pub-1", "hash-abc", listOf(chapter(0, "chap1.xhtml", "Texte")))

        assertTrue(store.isFresh("pub-1", "hash-abc"))
        assertFalse("hash divergent", store.isFresh("pub-1", "hash-autre"))
    }

    /**
     * Cas réel trouvé sur appareil : les fichiers plats des anciens formats
     * (`<id>.prea`) doivent être jugés périmés — donc régénérés —, jamais
     * servis ni levés en exception, puis supprimés à la réécriture.
     */
    @Test
    fun `un fichier d'ancien format est perime puis remplace`() = runTest {
        val dir = File.createTempFile("preanalysis", "test").apply { delete() }
        dir.mkdirs()
        val base = File(dir, "preanalysis").apply { mkdirs() }
        File(base, "pub-1.prea").writeText(
            """{"header":{"formatVersion":1,"parserVersion":2,"fileHash":"hash-abc"},"chapters":[]}""",
        )
        val store = FilePreAnalysisStore(dir)

        assertFalse(store.isFresh("pub-1", "hash-abc"))
        assertNull(store.loadChapter("pub-1", "hash-abc", "chap1.xhtml"))

        store.save("pub-1", "hash-abc", listOf(chapter(0, "chap1.xhtml", "Texte")))
        assertTrue(store.isFresh("pub-1", "hash-abc"))
        assertFalse("l'ancien fichier plat doit être supprimé", File(base, "pub-1.prea").exists())
    }

    @Test
    fun `la purge supprime le fichier de pre-analyse`() = runTest {
        val store = tempStore()
        store.save("pub-1", "hash-abc", listOf(chapter(0, "chap1.xhtml", "Texte")))

        store.delete("pub-1")

        assertNull(store.loadChapter("pub-1", "hash-abc", "chap1.xhtml"))
        assertFalse(store.isFresh("pub-1", "hash-abc"))
    }
}
