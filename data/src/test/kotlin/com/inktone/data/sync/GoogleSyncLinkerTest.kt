package com.inktone.data.sync

import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.domain.model.SyncProviderId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lot 11, tâche 11.6/11.7 — écrit le compte après une authentification Google réussie ; l'oublie sur déconnexion (repasse à Unconfigured). */
class GoogleSyncLinkerTest {

    @Test
    fun link_persiste_un_compte_Google_Drive_configure() = runTest {
        val syncAccountRepository = FakeSyncAccountRepository()
        val linker = GoogleSyncLinker(syncAccountRepository)

        linker.link(accountLabel = "Compte Google connecté")

        val account = syncAccountRepository.get()
        assertEquals(SyncProviderId.GOOGLE_DRIVE, account?.provider)
        assertEquals("Compte Google connecté", account?.accountLabel)
    }

    @Test
    fun unlink_repasse_a_Unconfigured() = runTest {
        val syncAccountRepository = FakeSyncAccountRepository()
        val linker = GoogleSyncLinker(syncAccountRepository)
        linker.link(accountLabel = "Compte Google connecté")

        linker.unlink()

        assertNull(syncAccountRepository.get())
    }
}
