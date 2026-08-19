package com.inktone.feature.library

import com.inktone.core.testing.fake.FakeImportProgressObserver
import com.inktone.core.testing.fake.FakeImportResultsStore
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.core.testing.fake.FakeSyncNowService
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.ImportSessionStore
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.RegenerateCoversUseCase
import com.inktone.domain.usecase.SynchronizeNowUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = 0L, coverUri = "cover-$id.jpg",
    )

    private fun viewModel(
        publicationRepository: FakePublicationRepository = FakePublicationRepository(),
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        importProgressObserver: FakeImportProgressObserver = FakeImportProgressObserver(),
        syncAccountRepository: FakeSyncAccountRepository = FakeSyncAccountRepository(),
        syncNowService: FakeSyncNowService = FakeSyncNowService(),
        publicationParser: FakePublicationParser = FakePublicationParser(),
    ): LibraryViewModel = LibraryViewModel(
        publicationRepository,
        FakeReadingStateRepository(),
        ToggleFavoriteUseCase(publicationRepository),
        TogglePinUseCase(publicationRepository),
        DeletePublicationUseCase(publicationRepository),
        importProgressObserver,
        FakeImportResultsStore(),
        ImportSessionStore(),
        preferencesRepository,
        SynchronizeNowUseCase(syncNowService),
        syncAccountRepository,
        RegenerateCoversUseCase(publicationRepository, publicationParser),
    )

    @Test
    fun `expose les publications observees par le repository`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val viewModel = viewModel(publicationRepository = repository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.publications.size)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `ouvrir une publication emet un effet de navigation`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val viewModel = viewModel(publicationRepository = repository)
        dispatcher.scheduler.advanceUntilIdle()

        var effect: LibraryEffect? = null
        val job = launch(dispatcher) { viewModel.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()
        assertTrue(effect is LibraryEffect.NavigateToReader)
        assertEquals("pub-1", (effect as LibraryEffect.NavigateToReader).publicationId)
    }

    @Test
    fun `reflete la progression d'import observee`() = runTest {
        val importProgressObserver = FakeImportProgressObserver()
        val viewModel = viewModel(importProgressObserver = importProgressObserver)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImportProgress(), viewModel.state.value.importProgress)

        importProgressObserver.emit(ImportProgress(current = 3, total = 10, hasQueuedChunks = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ImportProgress(current = 3, total = 10, hasQueuedChunks = true), viewModel.state.value.importProgress)
    }

    @Test
    fun `la disposition suit les preferences persistees et s y reecrit`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val viewModel = viewModel(preferencesRepository = preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryLayoutMode.GRID_COVERS, viewModel.state.value.layoutMode)

        preferencesRepository.update(preferencesRepository.get().copy(libraryLayoutMode = "LIST"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryLayoutMode.LIST, viewModel.state.value.layoutMode)

        viewModel.onIntent(LibraryIntent.SetLayoutMode(LibraryLayoutMode.GRID_COVERS))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("GRID_COVERS", preferencesRepository.get().libraryLayoutMode)
    }

    // ──── Lot 19 ────

    @Test
    fun `ouvrir un livre au hasard emet une navigation vers un livre affiche`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        repository.insert(publication("pub-2"))
        val viewModel = viewModel(publicationRepository = repository)
        dispatcher.scheduler.advanceUntilIdle()

        var effect: LibraryEffect? = null
        val job = launch(dispatcher) { viewModel.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.OpenRandomBook)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(effect is LibraryEffect.NavigateToReader)
        assertTrue((effect as LibraryEffect.NavigateToReader).publicationId in setOf("pub-1", "pub-2"))
    }

    @Test
    fun `synchroniser sans compte emet une navigation vers la configuration`() = runTest {
        val syncNowService = FakeSyncNowService()
        val viewModel = viewModel(syncAccountRepository = FakeSyncAccountRepository(), syncNowService = syncNowService)
        dispatcher.scheduler.advanceUntilIdle()

        var effect: LibraryEffect? = null
        val job = launch(dispatcher) { viewModel.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.SyncNow)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(effect is LibraryEffect.NavigateToSync)
        assertEquals(0, syncNowService.callCount)
    }

    @Test
    fun `synchroniser avec compte lance la synchronisation`() = runTest {
        val syncAccountRepository = FakeSyncAccountRepository()
        syncAccountRepository.save(SyncAccount(SyncProviderId.GOOGLE_DRIVE, "issa@example.com", linkedAt = 0L))
        val syncNowService = FakeSyncNowService()
        val viewModel = viewModel(syncAccountRepository = syncAccountRepository, syncNowService = syncNowService)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.SyncNow)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, syncNowService.callCount)
    }

    @Test
    fun `reinitialiser les couvertures remet toutes les couvertures a defaut`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val viewModel = viewModel(publicationRepository = repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.ResetCovers)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repository.getById("pub-1")?.coverUri)
    }

    @Test
    fun `reconstruire les couvertures ecrit la couverture extraite et emet un effet`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val parser = FakePublicationParser()
        parser.setCoverResult("cover-reconstruit.jpg")
        val viewModel = viewModel(publicationRepository = repository, publicationParser = parser)
        dispatcher.scheduler.advanceUntilIdle()

        var effect: LibraryEffect? = null
        val job = launch(dispatcher) { viewModel.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.RegenerateCovers)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("cover-reconstruit.jpg", repository.getById("pub-1")?.coverUri)
        assertTrue(effect is LibraryEffect.CoversRegenerated)
    }
}
