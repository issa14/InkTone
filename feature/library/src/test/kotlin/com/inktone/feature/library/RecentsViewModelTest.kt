package com.inktone.feature.library

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lot 8 — Tâche 8.3, les 7 points de vérification listés dans LOT_8_RECENTS.md, couverts autant que possible en test unitaire. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String, chapterCount: Int = 10, lastOpened: Long? = null) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = chapterCount, importDate = 0L, lastOpened = lastOpened,
    )

    private fun readingState(publicationId: String, chapterIndex: Int) = ReadingState(
        publicationId = publicationId,
        locator = Locator(resourceHref = "chap$chapterIndex.xhtml", chapterIndex = chapterIndex, charOffset = 0),
        lastReadAt = 0L,
    )

    private fun viewModel(publicationRepository: FakePublicationRepository, readingStateRepository: FakeReadingStateRepository) =
        RecentsViewModel(
            publicationRepository, readingStateRepository,
            ToggleFavoriteUseCase(publicationRepository), TogglePinUseCase(publicationRepository),
            DeletePublicationUseCase(publicationRepository, FakePreAnalysisStore()),
            // Audit v1.0.0 (P5) : même StandardTestDispatcher que setMain.
            dispatcher,
        )

    @Test
    fun un_livre_jamais_ouvert_est_absent_un_livre_entame_apparait() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        publicationRepository.insert(publication("jamais-ouvert", lastOpened = 1_000L))
        publicationRepository.insert(publication("entame", lastOpened = 2_000L))
        readingStateRepository.save(readingState("entame", chapterIndex = 1))

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("entame"), vm.state.value.displayedPublications.map { it.id })
    }

    @Test
    fun l_ordre_suit_lastOpened_decroissant_pas_la_date_d_import_ni_le_titre() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        // Titres et ordre d'insertion volontairement inverses de lastOpened,
        // pour prouver que le tri ne retombe pas sur eux par accident.
        publicationRepository.insert(publication("z-ancien", lastOpened = 1_000L))
        publicationRepository.insert(publication("a-recent", lastOpened = 3_000L))
        publicationRepository.insert(publication("m-moyen", lastOpened = 2_000L))
        listOf("z-ancien", "a-recent", "m-moyen").forEach { readingStateRepository.save(readingState(it, chapterIndex = 1)) }

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("a-recent", "m-moyen", "z-ancien"), vm.state.value.displayedPublications.map { it.id })
    }

    @Test
    fun avec_35_livres_eligibles_seuls_les_30_plus_recents_sont_affiches() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        repeat(35) { i ->
            val id = "pub-$i"
            publicationRepository.insert(publication(id, lastOpened = i.toLong()))
            readingStateRepository.save(readingState(id, chapterIndex = 1))
        }

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()

        val displayed = vm.state.value.displayedPublications
        assertEquals(30, displayed.size)
        // Les 30 plus recents : lastOpened de 5 a 34 (les 5 plus anciens, 0..4, exclus).
        assertTrue(displayed.all { it.lastOpened!! >= 5L })
    }

    @Test
    fun un_livre_termine_a_100_pourcent_reste_affiche() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        publicationRepository.insert(publication("termine", chapterCount = 10, lastOpened = 1_000L))
        // chapterIndex au dernier chapitre (divisor = chapterCount - 1 = 9) => 100%.
        readingStateRepository.save(readingState("termine", chapterIndex = 9))

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("termine"), vm.state.value.displayedPublications.map { it.id })
        assertEquals(100, vm.state.value.progressMap["termine"])
    }

    @Test
    fun ouvrir_un_livre_envoie_l_effet_de_navigation() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        publicationRepository.insert(publication("livre-1", lastOpened = 1_000L))
        readingStateRepository.save(readingState("livre-1", chapterIndex = 1))

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(RecentsIntent.OpenPublication("livre-1"))
        dispatcher.scheduler.advanceUntilIdle()
        val effect = vm.effects.first()

        assertEquals(RecentsEffect.NavigateToReader("livre-1"), effect)
    }

    @Test
    fun rouvrir_un_livre_met_a_jour_lastOpened_et_le_remonte_en_tete() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingStateRepository = FakeReadingStateRepository()
        publicationRepository.insert(publication("ancien-mais-relu", lastOpened = 1_000L))
        publicationRepository.insert(publication("recent", lastOpened = 2_000L))
        listOf("ancien-mais-relu", "recent").forEach { readingStateRepository.save(readingState(it, chapterIndex = 1)) }

        val vm = viewModel(publicationRepository, readingStateRepository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("recent", "ancien-mais-relu"), vm.state.value.displayedPublications.map { it.id })

        // Simule la mise a jour de lastOpened par le Reader (le VM observe
        // un Flow Room live : la publication mise a jour remonte seule,
        // aucune action explicite cote Recents).
        publicationRepository.update(publication("ancien-mais-relu", lastOpened = 3_000L))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("ancien-mais-relu", "recent"), vm.state.value.displayedPublications.map { it.id })
    }
}
