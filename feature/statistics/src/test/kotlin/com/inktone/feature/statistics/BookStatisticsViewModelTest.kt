package com.inktone.feature.statistics

import androidx.lifecycle.SavedStateHandle
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
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

/**
 * Tache 7.3 — ventilation des sessions mixtes dans l'historique par ouvrage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookStatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private suspend fun publicationRepoWith(id: String) = FakePublicationRepository().apply {
        insert(
            Publication(
                id = id, title = "Titre $id", format = PublicationFormat.EPUB,
                fileUri = "content://$id", fileHash = "hash-$id", fileSize = 100L,
                chapterCount = 5, importDate = 0L,
            ),
        )
    }

    @Test
    fun session_mixte_la_somme_des_ventilations_egale_toujours_le_total_affiche() = runTest {
        val sessionRepo = FakeReadingSessionRepository()
        // 30 min de lecture visuelle + 15 min de TTS dans une même session.
        sessionRepo.insert(
            ReadingSession(
                id = "s1", publicationId = "pub-1", startedAt = 0L, endedAt = 45 * 60_000L,
                mode = ReadingMode.VISUAL,
                visualDurationMs = 30 * 60_000L, ttsDurationMs = 15 * 60_000L,
            ),
        )
        val publicationRepo = publicationRepoWith("pub-1")

        val vm = BookStatisticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "pub-1")),
            readingSessionRepository = sessionRepo,
            publicationRepository = publicationRepo,
        )

        val state = vm.state.first { it is BookDetailUiState.Ready } as BookDetailUiState.Ready
        val item = state.history.single()

        assertTrue(item.isMixed)
        assertEquals(item.totalMinutes, item.visualMinutes + item.ttsMinutes)
        assertEquals(45, item.totalMinutes)
    }

    @Test
    fun session_mixte_avec_repartition_non_ronde_reste_coherente() = runTest {
        val sessionRepo = FakeReadingSessionRepository()
        // Répartition qui, arrondie indépendamment, romprait la somme
        // (ex. 10 min visuel + 5.4 min TTS -> 10 + 5 = 15, alors que le
        // total arrondi peut être 15 ou 16 selon les millisecondes).
        sessionRepo.insert(
            ReadingSession(
                id = "s1", publicationId = "pub-1", startedAt = 0L, endedAt = 924_000L,
                mode = ReadingMode.VISUAL,
                visualDurationMs = 600_000L, ttsDurationMs = 324_000L,
            ),
        )
        val publicationRepo = publicationRepoWith("pub-1")

        val vm = BookStatisticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "pub-1")),
            readingSessionRepository = sessionRepo,
            publicationRepository = publicationRepo,
        )

        val state = vm.state.first { it is BookDetailUiState.Ready } as BookDetailUiState.Ready
        val item = state.history.single()

        assertEquals(item.totalMinutes, item.visualMinutes + item.ttsMinutes)
    }

    @Test
    fun session_non_mixte_n_est_pas_marquee_mixte() = runTest {
        val sessionRepo = FakeReadingSessionRepository()
        sessionRepo.insert(
            ReadingSession(
                id = "s1", publicationId = "pub-1", startedAt = 0L, endedAt = 600_000L,
                mode = ReadingMode.VISUAL, visualDurationMs = 600_000L, ttsDurationMs = 0L,
            ),
        )
        val publicationRepo = publicationRepoWith("pub-1")

        val vm = BookStatisticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "pub-1")),
            readingSessionRepository = sessionRepo,
            publicationRepository = publicationRepo,
        )

        val state = vm.state.first { it is BookDetailUiState.Ready } as BookDetailUiState.Ready
        val item = state.history.single()

        assertTrue(item.isVisual)
        assertTrue(!item.isTts)
        assertTrue(!item.isMixed)
        assertTrue(item.accessibilityLabel.contains("lecture visuelle"))
    }
}
