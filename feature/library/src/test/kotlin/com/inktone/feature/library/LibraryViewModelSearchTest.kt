package com.inktone.feature.library

import com.inktone.core.testing.fake.FakeImportProgressObserver
import com.inktone.core.testing.fake.FakeImportResultsStore
import com.inktone.core.testing.fake.FakePlaybackSession
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeRenderedPageCache
import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.core.testing.fake.FakeSyncNowService
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ImportSessionStore
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.RegenerateCoversUseCase
import com.inktone.domain.usecase.SynchronizeNowUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Barre de recherche de la Bibliothèque portée par l'état de l'écran.
 *
 * Avant, elle vivait dans un `remember` du composable alors que la requête
 * vivait dans le ViewModel : au retour du Lecteur (ou après une rotation),
 * la barre disparaissait et la grille restait filtrée sans rien qui
 * l'indique.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelSearchTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String, title: String) = Publication(
        id = id, title = title, format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = 0L,
    )

    private fun viewModel(publicationRepository: FakePublicationRepository) = LibraryViewModel(
        publicationRepository,
        FakeReadingStateRepository(),
        ToggleFavoriteUseCase(publicationRepository),
        TogglePinUseCase(publicationRepository),
        DeletePublicationUseCase(publicationRepository, FakePreAnalysisStore(), FakeTtsSegmentCache(), FakeRenderedPageCache()),
        FakeImportProgressObserver(),
        FakeImportResultsStore(),
        ImportSessionStore(),
        FakePreferencesRepository(),
        SynchronizeNowUseCase(FakeSyncNowService()),
        FakeSyncAccountRepository(),
        RegenerateCoversUseCase(publicationRepository, FakePublicationParser()),
        FakePlaybackSession(),
        dispatcher,
    )

    @Test
    fun la_barre_de_recherche_reste_ouverte_tant_que_la_requete_filtre_la_grille() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("1", "Dune"))
        repository.insert(publication("2", "Germinal"))
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.SetSearchActive(true))
        viewModel.onIntent(LibraryIntent.SetSearchQuery("dune"))
        dispatcher.scheduler.advanceUntilIdle()

        // L'état survit au composable (retour du Lecteur, rotation) : la
        // barre ouverte et le filtre restent cohérents.
        assertTrue(viewModel.state.value.isSearchActive)
        assertEquals(listOf("1"), viewModel.state.value.displayedPublications.map { it.id })
    }

    @Test
    fun fermer_la_recherche_efface_la_requete_et_rend_toute_la_bibliotheque_sans_delai() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("1", "Dune"))
        repository.insert(publication("2", "Germinal"))
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onIntent(LibraryIntent.SetSearchActive(true))
        viewModel.onIntent(LibraryIntent.SetSearchQuery("dune"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.SetSearchActive(false))

        // Sans avancer l'horloge : pas d'attente du délai de frappe.
        assertFalse(viewModel.state.value.isSearchActive)
        assertEquals("", viewModel.state.value.searchQuery)
        assertEquals(setOf("1", "2"), viewModel.state.value.displayedPublications.map { it.id }.toSet())
    }
}
