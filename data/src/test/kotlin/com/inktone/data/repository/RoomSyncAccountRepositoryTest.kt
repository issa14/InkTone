package com.inktone.data.repository

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 11, tâche 11.2 — le compte de synchronisation est un sous-ensemble de UserPreferences ; `save` matérialise l'exclusivité mutuelle en remplaçant tout compte existant. */
class RoomSyncAccountRepositoryTest {

    @Test
    fun aucun_compte_lie_rend_null() = runTest {
        val repository = RoomSyncAccountRepository(FakePreferencesRepository())
        assertNull(repository.get())
        assertNull(repository.observe().first())
    }

    @Test
    fun save_puis_get_restitue_le_compte_et_remplace_tout_compte_precedent() = runTest {
        val repository = RoomSyncAccountRepository(FakePreferencesRepository())
        repository.save(SyncAccount(SyncProviderId.GOOGLE_DRIVE, "issa@example.com", linkedAt = 100L))
        repository.save(SyncAccount(SyncProviderId.WEBDAV, "https://exemple.tld", linkedAt = 200L))

        val account = repository.get()
        assertEquals(SyncProviderId.WEBDAV, account?.provider)
        assertEquals("https://exemple.tld", account?.accountLabel)
    }

    @Test
    fun clear_repasse_a_null() = runTest {
        val repository = RoomSyncAccountRepository(FakePreferencesRepository())
        repository.save(SyncAccount(SyncProviderId.GOOGLE_DRIVE, "issa@example.com", linkedAt = 0L))
        repository.clear()

        assertNull(repository.get())
    }

    @Test
    fun markSyncSucceeded_efface_l_echec_precedent() = runTest {
        val repository = RoomSyncAccountRepository(FakePreferencesRepository())
        repository.save(SyncAccount(SyncProviderId.GOOGLE_DRIVE, "issa@example.com", linkedAt = 0L))
        repository.markSyncFailed()
        assertTrue(repository.get()?.lastAutoSyncFailed == true)

        repository.markSyncSucceeded(at = 999L)

        val account = repository.get()
        assertEquals(999L, account?.lastSyncAt)
        assertEquals(false, account?.lastAutoSyncFailed)
    }
}
