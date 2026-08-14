package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeOpdsCatalogRepository
import com.inktone.core.testing.fake.FakeOpdsCredentialsStore
import com.inktone.domain.model.OpdsCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 13, tâche 13.4.3 — suppression d'un catalogue purge entité ET identifiants, jamais d'identifiants orphelins. */
class RemoveCatalogUseCaseTest {

    private fun catalog(id: String) = OpdsCatalog(
        id = id, name = "Catalogue", rootUrl = "https://ex.com/opds",
        searchTemplateUrl = null, hasCredentials = true,
    )

    @Test
    fun supprimer_un_catalogue_purge_aussi_ses_identifiants() = runTest {
        val repo = FakeOpdsCatalogRepository()
        val store = FakeOpdsCredentialsStore()
        repo.add(catalog("cat-1"))
        store.setCredentials("cat-1", "alice", "s3cret")
        assertTrue(store.hasCredentials("cat-1"))

        RemoveCatalogUseCase(repo, store)("cat-1")

        assertEquals(emptyList<OpdsCatalog>(), repo.observeAll().first())
        assertFalse(store.hasCredentials("cat-1"))
    }
}
