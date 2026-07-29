package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class GetStatisticsUseCaseTest {

    @Test
    fun livre_termine_reutilise_exactement_la_definition_de_FilterMode_READ() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Termine", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "h1", fileSize = 10, chapterCount = 3, importDate = 0L,
            ),
        )
        publicationRepository.insert(
            Publication(
                id = "pub-2", title = "En cours", format = PublicationFormat.EPUB,
                fileUri = "content://y", fileHash = "h2", fileSize = 10, chapterCount = 3, importDate = 0L,
            ),
        )
        // pub-1 : dernier chapitre atteint (chapterIndex 2 == chapterCount-1) -> termine.
        publicationRepository.setChapterProgress("pub-1", 2)
        // pub-2 : pas encore au dernier chapitre -> pas termine.
        publicationRepository.setChapterProgress("pub-2", 0)

        readingSessionRepository.insert(
            ReadingSession(
                id = "s1", publicationId = "pub-1", startedAt = 0L, endedAt = 1000L,
                mode = ReadingMode.VISUAL, durationMs = 1000L,
            ),
        )

        val useCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository)
        val result = useCase()

        assertEquals(1, result.booksFinished)
        assertEquals(1000L, result.totalReadingTimeMs)
    }

    @Test
    fun serie_de_jours_consecutifs_incluant_aujourd_hui() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)

        readingSessionRepository.insert(
            ReadingSession(
                id = "s1", publicationId = "p", startedAt = now, endedAt = now,
                mode = ReadingMode.AUDIO, durationMs = 0L,
            ),
        )
        readingSessionRepository.insert(
            ReadingSession(
                id = "s2", publicationId = "p", startedAt = now - oneDayMs, endedAt = now - oneDayMs,
                mode = ReadingMode.AUDIO, durationMs = 0L,
            ),
        )

        val useCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository)
        val result = useCase()

        assertEquals(2, result.currentStreakDays)
    }
}
