package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.SearchService
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private class InMemoryPublicationRepository : PublicationRepository {
    private val state = MutableStateFlow<List<Publication>>(emptyList())

    override fun observeAll(): Flow<List<Publication>> = state
    override fun observeFiltered(mode: FilterMode, value: String?): Flow<List<Publication>> = state
    override suspend fun getById(id: String): Publication? = state.value.find { it.id == id }
    override suspend fun getByFileHash(hash: String): Publication? = state.value.find { it.fileHash == hash }
    override suspend fun insert(publication: Publication) {
        state.value = state.value + publication
    }

    override suspend fun update(publication: Publication) {
        state.value = state.value.map { if (it.id == publication.id) publication else it }
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit
    override suspend fun setPinned(id: String, isPinned: Boolean) = Unit
    override suspend fun countFiltered(mode: FilterMode): Int = state.value.size
}

private class NoOpSearchService : SearchService {
    override suspend fun search(query: String, publicationId: String?) = emptyList<com.inktone.domain.service.SearchResult>()
    override suspend fun indexPublication(publicationId: String, documentModel: DocumentModel) = Unit
    override suspend fun indexSentences(
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        sentences: List<com.inktone.domain.model.Sentence>,
    ) = Unit
}

/**
 * Lot 12, tache 12.6 - import de bout en bout d'un PDF reel via le vrai
 * PdfPublicationParser (pas un fake, contrairement a
 * ImportPublicationUseCaseTest en domain/src/test) : verifie que
 * l'orchestration produit une Publication PDF correctement peuplee.
 */
@RunWith(AndroidJUnit4::class)
class ImportPublicationUseCasePdfTest {

    @Test
    fun importe_un_pdf_de_bout_en_bout_avec_pageCount_et_couverture() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileStorageService = LocalFileStorageService()
        val repository = InMemoryPublicationRepository()
        val importPublication = ImportPublicationUseCase(
            publicationParser = PdfPublicationParser(fileStorageService, context),
            publicationRepository = repository,
            fileStorageService = fileStorageService,
            searchService = NoOpSearchService(),
            // Format PDF : indexation via indexPublication (eager), le
            // chapterParser n'est exercé que pour l'EPUB.
            chapterParser = com.inktone.core.testing.fake.FakeChapterParser(),
        )

        val file = File(context.cacheDir, "fixture-valid.pdf").apply {
            context.assets.open("fixture-valid.pdf").use { i -> outputStream().use { i.copyTo(it) } }
        }

        val result = importPublication(file.absolutePath)

        check(result is ImportResult.Success)
        val publication = result.publication
        assertEquals(PublicationFormat.PDF, publication.format)
        assertEquals(publication.chapterCount, publication.pageCount)
        assertFalse("pageCount doit etre renseigne pour un PDF", publication.pageCount == null)
        assertNotNull("une couverture doit etre generee", publication.coverUri)
    }
}
