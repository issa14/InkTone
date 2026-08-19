package com.inktone.infrastructure.sync.webdav

import com.inktone.domain.service.SyncFailureReason
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

/** Faux en mémoire — permet de tester [WebDavSyncProvider] sans Keystore Android. */
private class InMemoryWebDavCredentialsStore : WebDavCredentialsStoreContract {
    private var credentials: WebDavCredentials? = null
    override fun read(): WebDavCredentials? = credentials
    override fun write(credentials: WebDavCredentials) { this.credentials = credentials }
    override fun clear() { credentials = null }
}

/**
 * Lot 19 — le client WebDAV pointe vers l'URL des identifiants (pas un
 * hôte en dur comme Google Drive) : les tests peuvent donc diriger
 * directement vers [MockWebServer] via l'URL stockée, sans interceptor.
 */
class WebDavSyncProviderTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun store(url: String = server.url("/webdav/").toString()) =
        InMemoryWebDavCredentialsStore().apply {
            write(WebDavCredentials(url = url, username = "issa", password = "secret"))
        }

    private val multistatus = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response><D:href>/webdav/</D:href></D:response>
          <D:response>
            <D:href>/webdav/snapshot-a.json</D:href>
            <D:propstat><D:prop>
              <D:getcontentlength>1234</D:getcontentlength>
              <D:getlastmodified>Mon, 19 Aug 2026 10:00:00 GMT</D:getlastmodified>
            </D:prop></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun list_parse_le_multistatus_et_ignore_la_racine() = runTest {
        server.enqueue(MockResponse().setBody(multistatus))
        val provider = WebDavSyncProvider(OkHttpClient(), store())

        val files = provider.list()

        assertEquals(1, files.size)
        assertEquals("snapshot-a.json", files.first().name)
        assertEquals(1234L, files.first().sizeBytes)
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
    }

    @Test
    fun upload_envoie_un_PUT_avec_authentification_basic() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val provider = WebDavSyncProvider(OkHttpClient(), store())

        val result = provider.upload("snapshot-a.json", byteArrayOf(1, 2, 3))

        assertTrue(result is SyncOperationResult.Success)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.getHeader("Authorization")?.startsWith("Basic ") == true)
    }

    @Test
    fun upload_distingue_un_echec_d_authentification() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = WebDavSyncProvider(OkHttpClient(), store())

        val result = provider.upload("snapshot-a.json", byteArrayOf(1)) as SyncOperationResult.Failed

        assertEquals(SyncFailureReason.INVALID_TOKEN, result.reason)
    }

    @Test
    fun download_rend_les_octets_ou_null_sur_404() = runTest {
        server.enqueue(MockResponse().setBody("contenu"))
        server.enqueue(MockResponse().setResponseCode(404))
        val provider = WebDavSyncProvider(OkHttpClient(), store())

        assertEquals("contenu", provider.download("snapshot-a.json")?.decodeToString())
        assertNull(provider.download("absent.json"))
    }

    @Test
    fun delete_et_testConnection_rendent_le_resultat_attendu() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = WebDavSyncProvider(OkHttpClient(), store())

        assertTrue(provider.delete("snapshot-a.json") is SyncOperationResult.Success)
        assertTrue(provider.testConnection("${server.url("/webdav/")}", "issa", "secret") is SyncOperationResult.Success)
        val failed = provider.testConnection("${server.url("/webdav/")}", "issa", "secret") as SyncOperationResult.Failed
        assertEquals(SyncFailureReason.INVALID_TOKEN, failed.reason)
    }

    @Test
    fun operations_sans_identifiants_rendent_un_echec_type_sans_IO() = runTest {
        val provider = WebDavSyncProvider(OkHttpClient(), InMemoryWebDavCredentialsStore())

        val result = provider.upload("snapshot-a.json", byteArrayOf(1)) as SyncOperationResult.Failed

        assertEquals(SyncFailureReason.INVALID_TOKEN, result.reason)
        assertNull(provider.download("snapshot-a.json"))
        assertEquals(emptyList<Any>(), provider.list())
    }
}
