package com.inktone.infrastructure.opds

import com.inktone.core.testing.fake.FakeOpdsCredentialsStore
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsFetchResult
import kotlinx.coroutines.test.runTest
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lot 13, tâche 13.2.8 — injection Basic Auth conditionnelle, requête sans credentials sans en-tête. */
class OpdsHttpClientTest {

    private val server = MockWebServer()

    @Before fun setUp() { server.start() }

    @After fun tearDown() { server.shutdown() }

    private val client = OkHttpClient.Builder().build()

    @Test
    fun fetch_avec_credentials_stockes_pose_l_en_tete_authorization_basic() = runTest {
        val store = FakeOpdsCredentialsStore().apply { setCredentials("cat-1", "alice", "s3cret") }
        val http = DefaultOpdsHttpClient(client, store)
        server.enqueue(MockResponse().setBody("<feed/>"))

        http.fetch(server.url("/catalog.opds").toString(), "cat-1")

        val recorded = server.takeRequest()
        assertEquals(Credentials.basic("alice", "s3cret"), recorded.getHeader("Authorization"))
    }

    @Test
    fun fetch_sans_catalogId_ne_pose_aucun_en_tete() = runTest {
        val store = FakeOpdsCredentialsStore().apply { setCredentials("cat-1", "alice", "s3cret") }
        val http = DefaultOpdsHttpClient(client, store)
        server.enqueue(MockResponse().setBody("<feed/>"))

        http.fetch(server.url("/catalog.opds").toString(), null)

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun fetch_avec_catalogId_sans_credentials_stockes_ne_pose_aucun_en_tete() = runTest {
        val http = DefaultOpdsHttpClient(client, FakeOpdsCredentialsStore())
        server.enqueue(MockResponse().setBody("<feed/>"))

        http.fetch(server.url("/catalog.opds").toString(), "cat-inconnu")

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun fetch_sur_401_renvoie_unauthorized() = runTest {
        val http = DefaultOpdsHttpClient(client, FakeOpdsCredentialsStore())
        server.enqueue(MockResponse().setResponseCode(401))

        val result = http.fetch(server.url("/catalog.opds").toString(), "cat-1")

        assertTrue(result is OpdsFetchResult.Failure)
        assertEquals(OpdsFailureReason.UNAUTHORIZED, (result as OpdsFetchResult.Failure).reason)
    }
}
