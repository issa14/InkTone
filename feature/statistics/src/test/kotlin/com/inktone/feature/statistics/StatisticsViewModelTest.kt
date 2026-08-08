package com.inktone.feature.statistics

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.usecase.GetStatisticsUseCase
import com.inktone.domain.usecase.StatisticsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String, chapterCount: Int = 10) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = chapterCount, importDate = 0L,
    )

    @Test
    fun etat_initial_est_Loading() = runTest {
        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            publicationRepository = FakePublicationRepository(),
            readingStateRepository = FakeReadingStateRepository(),
        )

        // Tant que le combine n'a pas émis, l'état initial (seed) de stateIn est Loading
        assertTrue(vm.state.value is StatisticsUiState.Loading)
    }

    @Test
    fun passe_a_Ready_avec_des_KPIs_formattes() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()

        // Une session pour avoir des stats non nulles
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", System.currentTimeMillis(), System.currentTimeMillis() + 60_000,
                ReadingMode.VISUAL, visualDurationMs = 30_000, ttsDurationMs = 0),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            readingSessionRepository = readingSessionRepo,
            publicationRepository = FakePublicationRepository(),
            readingStateRepository = FakeReadingStateRepository(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready

        // Les durées brutes sont transformées en Strings formatées
        assertEquals("0h 0m", ready.kpi.totalVisualTimeFormatted)
        assertEquals("0h 0m", ready.kpi.totalTtsTimeFormatted)
        assertTrue(ready.kpi.currentStreakDays >= 0)
        assertTrue(ready.kpi.averageWpm >= 0)
    }

    @Test
    fun livre_en_cours_est_null_sans_session() = runTest {
        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            publicationRepository = FakePublicationRepository(),
            readingStateRepository = FakeReadingStateRepository(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready
        assertEquals(null, ready.currentBook)
    }

    @Test
    fun livre_en_cours_affiche_titre_et_progression() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val publicationRepo = FakePublicationRepository()
        publicationRepo.insert(publication("pub-1", chapterCount = 5))

        // Une session récente pour désigner pub-1 comme livre en cours
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", System.currentTimeMillis(), System.currentTimeMillis() + 60_000,
                ReadingMode.VISUAL, visualDurationMs = 30_000),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), publicationRepo),
            readingSessionRepository = readingSessionRepo,
            publicationRepository = publicationRepo,
            readingStateRepository = FakeReadingStateRepository(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready
        val book = ready.currentBook

        assertNotNull(book)
        assertEquals("pub-1", book!!.id)
        assertEquals("Titre pub-1", book.title)
    }

    @Test
    fun formatage_des_durees_est_en_heures_et_minutes() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", 0L, 3_600_000L + 1_800_000L, // 1h30
                ReadingMode.VISUAL, visualDurationMs = 3_600_000L, ttsDurationMs = 1_800_000L),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            readingSessionRepository = readingSessionRepo,
            publicationRepository = FakePublicationRepository(),
            readingStateRepository = FakeReadingStateRepository(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready

        assertEquals("1h 0m", ready.kpi.totalVisualTimeFormatted)
        assertEquals("0h 30m", ready.kpi.totalTtsTimeFormatted)
    }
}
