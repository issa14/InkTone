package com.inktone.data.repository

import com.inktone.core.testing.fake.FakeSyncProvider
import com.inktone.data.sync.ACTIVITY_LOG_MAX_EVENTS
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 11, tâche 11.9, point 3 — le journal est plafonné, les plus anciens événements sortent au-delà. */
class RemoteSyncActivityLogRepositoryTest {

    private fun event(id: String, occurredAt: Long) =
        SyncActivityEvent(id, SyncActivityEventType.SUCCESS, "Événement $id", occurredAt)

    @Test
    fun appendEvent_place_le_plus_recent_en_tete() = runTest {
        val repository = RemoteSyncActivityLogRepository(FakeSyncProvider())

        repository.appendEvent(event("e1", 100L))
        repository.appendEvent(event("e2", 200L))

        val events = repository.listEvents()
        assertEquals(listOf("e2", "e1"), events.map { it.id })
    }

    @Test
    fun appendEvent_plafonne_le_journal_et_expulse_les_plus_anciens() = runTest {
        val repository = RemoteSyncActivityLogRepository(FakeSyncProvider())

        repeat(ACTIVITY_LOG_MAX_EVENTS + 5) { index ->
            repository.appendEvent(event("e$index", occurredAt = index.toLong()))
        }

        val events = repository.listEvents()
        assertEquals(ACTIVITY_LOG_MAX_EVENTS, events.size)
        // Les 5 plus anciens (e0..e4) doivent avoir disparu.
        assertTrue(events.none { it.id == "e0" })
        assertTrue(events.any { it.id == "e${ACTIVITY_LOG_MAX_EVENTS + 4}" })
    }
}
