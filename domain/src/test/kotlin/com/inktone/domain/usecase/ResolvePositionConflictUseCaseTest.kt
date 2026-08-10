package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeConflictQueueRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.PositionConflict
import com.inktone.domain.model.ReadingPositionSnapshot
import com.inktone.domain.model.ReadingState
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 11, tâche 11.11, point 1 — le choix de l'utilisateur est appliqué et le conflit retiré de la file. */
class ResolvePositionConflictUseCaseTest {

    private fun conflict() = PositionConflict(
        publicationId = "pub-1",
        bookTitle = "Le Grand Livre",
        local = ReadingPositionSnapshot(Locator("ch1.xhtml", 1, null, 0), "Cet appareil", 50L, 1, 20),
        remote = ReadingPositionSnapshot(Locator("ch8.xhtml", 8, null, 0), "Tablette B", 200L, 8, 20),
    )

    @Test
    fun invoke_applique_la_position_choisie_et_retire_le_conflit_de_la_file() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        readingStateRepository.save(ReadingState("pub-1", Locator("ch1.xhtml", 1, null, 0), lastReadAt = 50L))
        val conflictQueueRepository = FakeConflictQueueRepository()
        val conflict = conflict()
        conflictQueueRepository.enqueue(conflict)
        val useCase = ResolvePositionConflictUseCase(readingStateRepository, conflictQueueRepository)

        useCase(conflict, conflict.remote.locator)

        assertEquals(8, readingStateRepository.get("pub-1")?.locator?.chapterIndex)
        assertTrue(conflictQueueRepository.listPending().isEmpty())
    }

    @Test
    fun invoke_choisit_la_position_locale_sans_la_modifier_mais_retire_quand_meme_le_conflit() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        readingStateRepository.save(ReadingState("pub-1", Locator("ch1.xhtml", 1, null, 0), lastReadAt = 50L))
        val conflictQueueRepository = FakeConflictQueueRepository()
        val conflict = conflict()
        conflictQueueRepository.enqueue(conflict)
        val useCase = ResolvePositionConflictUseCase(readingStateRepository, conflictQueueRepository)

        useCase(conflict, conflict.local.locator)

        assertEquals(1, readingStateRepository.get("pub-1")?.locator?.chapterIndex)
        assertTrue(conflictQueueRepository.listPending().isEmpty())
    }
}
