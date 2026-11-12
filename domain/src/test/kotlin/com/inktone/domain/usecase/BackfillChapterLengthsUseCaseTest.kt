package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rattrapage des longueurs de chapitres pour les livres importés avant
 * `MIGRATION_32_33` : sans lui, leur progression resterait inconnue à vie.
 */
class BackfillChapterLengthsUseCaseTest {

    private fun publication(id: String, format: PublicationFormat = PublicationFormat.EPUB, chapterCount: Int = 2) = Publication(
        id = id, title = "Titre $id", format = format,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = chapterCount, importDate = 0L,
    )

    private fun chapter(href: String, length: Int) = Chapter(
        index = 0, href = href, title = null, content = ChapterContent.Rich(emptyList()),
        sentences = if (length == 0) emptyList() else listOf(Sentence(index = 0, text = "x", startOffset = 0, endOffset = length)),
    )

    private fun parserReturning(vararg chapters: Chapter) = FakePublicationParser(
        result = ParseResult.Success(
            documentModel = DocumentModel(chapters = chapters.toList(), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false,
        ),
    )

    @Test
    fun `un EPUB recoit ses longueurs depuis sa pre-analyse`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val store = FakePreAnalysisStore().apply {
            save("pub-1", "hash-pub-1", listOf(chapter("cover.xhtml", 0), chapter("ch1.xhtml", 420)))
        }

        val outcome = BackfillChapterLengthsUseCase(repository, parserReturning(), store)()

        assertEquals(BackfillChapterLengthsUseCase.Outcome(filled = 1, pending = 0, failed = 0), outcome)
        assertEquals(ChapterLengths(listOf(0, 420)), repository.getById("pub-1")?.chapterLengths)
    }

    @Test
    fun `un EPUB sans pre-analyse valide reste en attente, jamais rempli de valeurs inventees`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1")) }

        val outcome = BackfillChapterLengthsUseCase(repository, parserReturning(), FakePreAnalysisStore())()

        assertEquals(BackfillChapterLengthsUseCase.Outcome(filled = 0, pending = 1, failed = 0), outcome)
        assertNull(repository.getById("pub-1")?.chapterLengths)
    }

    @Test
    fun `un TXT recoit ses longueurs depuis son parseur`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("txt", PublicationFormat.TXT, chapterCount = 1)) }

        BackfillChapterLengthsUseCase(repository, parserReturning(chapter("txt", 77)), FakePreAnalysisStore())()

        assertEquals(ChapterLengths(listOf(77)), repository.getById("txt")?.chapterLengths)
    }

    @Test
    fun `une structure divergente de l'import est un echec, pas des longueurs decalees`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1", chapterCount = 3)) }
        val store = FakePreAnalysisStore().apply { save("pub-1", "hash-pub-1", listOf(chapter("ch1.xhtml", 10))) }

        val outcome = BackfillChapterLengthsUseCase(repository, parserReturning(), store)()

        assertEquals(BackfillChapterLengthsUseCase.Outcome(filled = 0, pending = 0, failed = 1), outcome)
        assertNull(repository.getById("pub-1")?.chapterLengths)
    }

    @Test
    fun `le PDF et les livres deja pourvus sont ignores`() = runTest {
        val known = publication("connu").copy(chapterLengths = ChapterLengths(listOf(1, 2)))
        val repository = FakePublicationRepository().apply {
            insert(publication("pdf", PublicationFormat.PDF))
            insert(known)
        }

        val outcome = BackfillChapterLengthsUseCase(repository, parserReturning(), FakePreAnalysisStore())()

        assertEquals(BackfillChapterLengthsUseCase.Outcome(filled = 0, pending = 0, failed = 0), outcome)
        assertNull(repository.getById("pdf")?.chapterLengths)
        assertEquals(known.chapterLengths, repository.getById("connu")?.chapterLengths)
    }
}
