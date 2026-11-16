package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePreAnalysisBuilder
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.SEARCH_INDEX_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Régénération des pré-analyses périmées : sans elle, une évolution du
 * parseur (PARSER_VERSION 1 → 2) rendait la pré-analyse inutilisable pour
 * tous les livres déjà importés, définitivement.
 */
class RefreshStalePreAnalysesUseCaseTest {

    private fun publication(
        id: String,
        format: PublicationFormat = PublicationFormat.EPUB,
        searchIndexVersion: Int = SEARCH_INDEX_VERSION,
    ) = Publication(
        id = id, title = "Titre $id", format = format,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 2, importDate = 0L, searchIndexVersion = searchIndexVersion,
    )

    private fun shell(href: String) =
        Chapter(index = 0, href = href, title = null, content = ChapterContent.Rich(blocks = emptyList()))

    private fun parserWithTwoChapters() = FakePublicationParser(
        result = ParseResult.Success(
            documentModel = DocumentModel(
                chapters = listOf(shell("cover.xhtml"), shell("chap1.xhtml")),
                tableOfContents = emptyList(),
                resources = emptyList(),
            ),
            isDrmProtected = false,
        ),
    )

    @Test
    fun `regenere une pre-analyse absente avec les memes chapitres que l'import`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val store = FakePreAnalysisStore()
        val builder = FakePreAnalysisBuilder()
        val search = FakeSearchService()

        val outcome = RefreshStalePreAnalysesUseCase(repository, parserWithTwoChapters(), builder, store, search)()

        assertEquals(RefreshStalePreAnalysesUseCase.Outcome(refreshed = 1, alreadyFresh = 0, failed = 0), outcome)
        assertEquals("l'index du livre est vidé puis refait", listOf("pub-1"), search.deletedPublications)
        assertEquals(listOf(0 to "cover.xhtml", 1 to "chap1.xhtml"), search.indexedChapters)
        assertEquals(listOf("cover.xhtml", "chap1.xhtml"), store.savedChapters("pub-1")?.map { it.href })
        assertTrue("la régénération doit être enregistrée sous le hash courant", store.isFresh("pub-1", "hash-pub-1"))
        assertEquals(
            "la mémoïsation du Lecteur doit être vidée après réécriture",
            listOf("pub-1"),
            builder.rewrittenPublicationIds,
        )
    }

    @Test
    fun `une structure qui change met a jour le nombre de chapitres et leurs longueurs`() = runTest {
        // Le livre avait 3 chapitres (couverture ajoutée) ; la structure actuelle en compte 2.
        val repository = FakePublicationRepository().apply {
            insert(publication("pub-1").copy(chapterCount = 3, searchIndexVersion = 0))
        }

        RefreshStalePreAnalysesUseCase(
            repository, parserWithTwoChapters(), FakePreAnalysisBuilder(), FakePreAnalysisStore(), FakeSearchService(),
        )()

        val stored = repository.observeAll().first().single()
        assertEquals(2, stored.chapterCount)
        assertEquals(2, stored.chapterLengths?.values?.size)
    }

    @Test
    fun `une pre-analyse a jour n'est jamais refaite`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val store = FakePreAnalysisStore().apply { save("pub-1", "hash-pub-1", listOf(shell("chap1.xhtml"))) }
        val builder = FakePreAnalysisBuilder()

        val outcome = RefreshStalePreAnalysesUseCase(repository, parserWithTwoChapters(), builder, store, FakeSearchService())()

        assertEquals(RefreshStalePreAnalysesUseCase.Outcome(refreshed = 0, alreadyFresh = 1, failed = 0), outcome)
        assertTrue(builder.analyzedFileUris.isEmpty())
    }

    @Test
    fun `un index a refaire est reconstruit meme si la pre-analyse est a jour`() = runTest {
        val repository = FakePublicationRepository().apply { insert(publication("pub-1", searchIndexVersion = 0)) }
        val store = FakePreAnalysisStore().apply { save("pub-1", "hash-pub-1", listOf(shell("chap1.xhtml"))) }
        val search = FakeSearchService()

        val outcome = RefreshStalePreAnalysesUseCase(repository, parserWithTwoChapters(), FakePreAnalysisBuilder(), store, search)()

        assertEquals(RefreshStalePreAnalysesUseCase.Outcome(refreshed = 1, alreadyFresh = 0, failed = 0), outcome)
        assertEquals(listOf(0 to "cover.xhtml", 1 to "chap1.xhtml"), search.indexedChapters)
        assertEquals(
            "l'index complet est déclaré à jour",
            SEARCH_INDEX_VERSION,
            repository.observeAll().first().single().searchIndexVersion,
        )
    }

    @Test
    fun `seul l'EPUB a une pre-analyse - PDF et TXT sont ignores`() = runTest {
        val repository = FakePublicationRepository().apply {
            insert(publication("pdf", PublicationFormat.PDF))
            insert(publication("txt", PublicationFormat.TXT))
        }
        val builder = FakePreAnalysisBuilder()

        val outcome = RefreshStalePreAnalysesUseCase(repository, parserWithTwoChapters(), builder, FakePreAnalysisStore(), FakeSearchService())()

        assertEquals(RefreshStalePreAnalysesUseCase.Outcome(refreshed = 0, alreadyFresh = 0, failed = 0), outcome)
        assertTrue(builder.analyzedFileUris.isEmpty())
    }

    @Test
    fun `un livre illisible est compte en echec sans bloquer les suivants`() = runTest {
        val repository = FakePublicationRepository().apply {
            insert(publication("pub-1"))
            insert(publication("pub-2"))
        }
        val store = FakePreAnalysisStore()
        val builder = FakePreAnalysisBuilder(failingFileUris = setOf("content://fake/pub-1"))

        val outcome = RefreshStalePreAnalysesUseCase(repository, parserWithTwoChapters(), builder, store, FakeSearchService())()

        assertEquals(RefreshStalePreAnalysesUseCase.Outcome(refreshed = 1, alreadyFresh = 0, failed = 1), outcome)
        assertTrue(store.isFresh("pub-2", "hash-pub-2"))
    }
}
