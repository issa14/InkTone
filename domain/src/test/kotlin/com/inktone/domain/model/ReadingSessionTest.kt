package com.inktone.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingSessionTest {

    @Test
    fun `endedAt anterieur a startedAt est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingSession(
                id = "s1", publicationId = "pub-1",
                startedAt = 1_000L, endedAt = 500L,
                mode = ReadingMode.AUDIO,
            )
        }
    }

    @Test
    fun `une session sans endedAt (en cours) est valide`() {
        // Ne doit pas lever d'exception.
        ReadingSession(
            id = "s1", publicationId = "pub-1",
            startedAt = 1_000L, endedAt = null,
            mode = ReadingMode.VISUAL,
        )
    }
}
