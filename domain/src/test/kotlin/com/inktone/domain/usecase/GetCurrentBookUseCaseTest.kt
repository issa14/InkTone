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
import org.junit.Assert.assertNull
import org.junit.Test

class GetCurrentBookUseCaseTest {

    private fun publication(id: String, title: String = id, lastOpened: Long? = null) = Publication(
        id = id, title = title, format = PublicationFormat.EPUB,
        fileUri = "content://$id", fileHash = "h-$id", fileSize = 10, chapterCount = 3, importDate = 0L,
        lastOpened = lastOpened,
    )

    /** Audit UX D33 : le titre n'y était pas nettoyé comme dans la Bibliothèque et le Lecteur. */
    @Test
    fun `le titre du livre en cours est nettoye pour l'affichage`() = runTest {
        val publications = FakePublicationRepository()
        publications.insert(publication("pub", title = "-La première loi - Tome 1 ", lastOpened = 1L))

        val book = GetCurrentBookUseCase(FakeReadingSessionRepository(), publications, FakeReadingStateRepository())().first()

        assertEquals("La première loi - Tome 1", book?.title)
    }

    /** Audit UX D33 : même livre que la carte « Reprendre » de la Bibliothèque, session ou pas. */
    @Test
    fun `le livre en cours est le dernier ouvert, pas le dernier avec une session`() = runTest {
        val publications = FakePublicationRepository()
        publications.insert(publication("ancien", lastOpened = 100L))
        publications.insert(publication("ouvert-a-l-instant", lastOpened = 200L))
        val sessions = FakeReadingSessionRepository()
        sessions.insert(
            ReadingSession(id = "s", publicationId = "ancien", startedAt = 0L, endedAt = 1_000L, mode = ReadingMode.VISUAL),
        )

        val book = GetCurrentBookUseCase(sessions, publications, FakeReadingStateRepository())().first()

        assertEquals("ouvert-a-l-instant", book?.id)
    }

    /** Audit UX D33 : sans état de lecture, la progression est inconnue, pas 0 %. */
    @Test
    fun `sans etat de lecture la progression est inconnue`() = runTest {
        val publications = FakePublicationRepository()
        publications.insert(publication("pub", lastOpened = 1L))

        val book = GetCurrentBookUseCase(FakeReadingSessionRepository(), publications, FakeReadingStateRepository())().first()

        assertNull(book?.progressPercent)
    }

    @Test
    fun `aucun livre jamais ouvert donne null`() = runTest {
        val publications = FakePublicationRepository()
        publications.insert(publication("pub"))

        val book = GetCurrentBookUseCase(FakeReadingSessionRepository(), publications, FakeReadingStateRepository())().first()

        assertNull(book)
    }
}
