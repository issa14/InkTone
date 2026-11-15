package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetCurrentBookUseCaseTest {

    /** Audit UX D33 : le titre n'y était pas nettoyé comme dans la Bibliothèque et le Lecteur. */
    @Test
    fun `le titre du livre en cours est nettoye pour l'affichage`() = runTest {
        val publications = FakePublicationRepository()
        publications.insert(
            Publication(
                id = "pub", title = "-La première loi - Tome 1 ", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "h", fileSize = 10, chapterCount = 3, importDate = 0L,
            ),
        )
        val sessions = FakeReadingSessionRepository()
        sessions.insert(
            ReadingSession(id = "s", publicationId = "pub", startedAt = 0L, endedAt = 1_000L, mode = ReadingMode.VISUAL),
        )

        val book = GetCurrentBookUseCase(sessions, publications, FakeReadingStateRepository())().first()

        assertEquals("La première loi - Tome 1", book?.title)
    }
}
