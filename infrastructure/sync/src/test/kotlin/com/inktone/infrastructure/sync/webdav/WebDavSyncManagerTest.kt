package com.inktone.infrastructure.sync.webdav

import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.service.SyncOperationResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class RecordingWebDavCredentialsStore : WebDavCredentialsStoreContract {
    var credentials: WebDavCredentials? = null
    override fun read(): WebDavCredentials? = credentials
    override fun write(credentials: WebDavCredentials) { this.credentials = credentials }
    override fun clear() { credentials = null }
}

/** Lot 19 — `connect` teste puis persiste (compte + identifiants) seulement en cas de succès. */
class WebDavSyncManagerTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun connect_sur_succes_persiste_compte_et_identifiants() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        val store = RecordingWebDavCredentialsStore()
        val accountRepository = FakeSyncAccountRepository()
        val provider = WebDavSyncProvider(OkHttpClient(), store)
        val manager = WebDavSyncManager(provider, store, accountRepository)

        val result = manager.connect("${server.url("/webdav/")}", "issa", "secret")

        assertTrue(result is SyncOperationResult.Success)
        val account = accountRepository.get()
        assertEquals(SyncProviderId.WEBDAV, account?.provider)
        assertEquals("localhost", account?.accountLabel)
        assertEquals("secret", store.credentials?.password)
    }

    @Test
    fun connect_sur_echec_ne_persiste_rien() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val store = RecordingWebDavCredentialsStore()
        val accountRepository = FakeSyncAccountRepository()
        val provider = WebDavSyncProvider(OkHttpClient(), store)
        val manager = WebDavSyncManager(provider, store, accountRepository)

        val result = manager.connect("${server.url("/webdav/")}", "issa", "mauvais")

        assertTrue(result is SyncOperationResult.Failed)
        assertNull(accountRepository.get())
        assertNull(store.credentials)
    }

    @Test
    fun disconnect_efface_identifiants_et_compte_webdav() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        val store = RecordingWebDavCredentialsStore()
        val accountRepository = FakeSyncAccountRepository()
        val provider = WebDavSyncProvider(OkHttpClient(), store)
        val manager = WebDavSyncManager(provider, store, accountRepository)

        manager.connect("${server.url("/webdav/")}", "issa", "secret")
        manager.disconnect()

        assertNull(accountRepository.get())
        assertNull(store.credentials)
    }
}
