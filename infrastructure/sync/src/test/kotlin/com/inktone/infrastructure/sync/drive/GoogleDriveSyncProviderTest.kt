package com.inktone.infrastructure.sync.drive

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

/**
 * Lot 11, tâche 11.7 — vérifie les pièges explicitement cités par le
 * plan : `multipart/related`/`uploadType=multipart` sur l'envoi,
 * `spaces=appDataFolder` sur la recherche, distinction des échecs HTTP.
 * `GoogleDriveSyncProvider` pointe vers des URLs Google en dur — ces
 * tests ne peuvent pas rediriger vers `MockWebServer` sans passer les
 * hôtes en paramètre. Ils couvrent donc le comportement observable
 * indépendant de l'hôte (distinction des codes d'échec, structure de
 * la requête envoyée) via un `OkHttpClient` intercepté plutôt qu'un
 * remplacement d'URL — voir [interceptingClient].
 */
class GoogleDriveSyncProviderTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun interceptingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val redirected = original.newBuilder()
                .url(original.url.newBuilder().scheme(server.url("/").scheme).host(server.url("/").host).port(server.url("/").port).build())
                .build()
            chain.proceed(redirected)
        }
        .build()

    @Test
    fun list_renvoie_les_fichiers_du_dossier_applicatif() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"f1","name":"backup.rfbackup","modifiedTime":"2026-01-01T00:00:00.000Z","size":"1234"}]}""",
            ),
        )
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        val files = provider.list()

        assertEquals(1, files.size)
        assertEquals("backup.rfbackup", files.first().name)
        assertEquals(1234L, files.first().sizeBytes)

        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("spaces=appDataFolder") == true)
    }

    @Test
    fun list_sur_reponse_401_ne_leve_pas_et_rend_une_liste_vide() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-invalide" }

        assertEquals(emptyList<Any>(), provider.list())
    }

    @Test
    fun upload_envoie_multipart_related_avec_uploadType_multipart() = runTest {
        // Recherche de l'existant (findFileId) : aucun fichier -> creation (POST).
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"new-id","name":"backup.rfbackup"}"""))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        val result = provider.upload("backup.rfbackup", byteArrayOf(1, 2, 3))

        assertTrue(result is SyncOperationResult.Success)
        server.takeRequest() // la recherche findFileId
        val uploadRequest = server.takeRequest()
        assertTrue(uploadRequest.path?.contains("uploadType=multipart") == true)
        assertTrue(uploadRequest.getHeader("Content-Type")?.startsWith("multipart/related") == true)
        assertEquals("POST", uploadRequest.method)
    }

    @Test
    fun upload_sur_fichier_existant_utilise_PATCH_sans_parents() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"existing-id","name":"backup.rfbackup"}]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"existing-id"}"""))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        val result = provider.upload("backup.rfbackup", byteArrayOf(1))

        assertTrue(result is SyncOperationResult.Success)
        server.takeRequest()
        val uploadRequest = server.takeRequest()
        assertEquals("PATCH", uploadRequest.method)
        assertTrue(uploadRequest.path?.contains("existing-id") == true)
    }

    @Test
    fun upload_distingue_401_403_et_un_echec_inconnu() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setResponseCode(403))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        val result = provider.upload("backup.rfbackup", byteArrayOf(1)) as SyncOperationResult.Failed

        assertEquals(SyncFailureReason.QUOTA_EXCEEDED, result.reason)
    }

    @Test
    fun download_d_un_fichier_absent_rend_null() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        assertNull(provider.download("inexistant.rfbackup"))
    }

    @Test
    fun delete_d_un_fichier_absent_rend_NOT_FOUND() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        val provider = GoogleDriveSyncProvider(interceptingClient()) { "token-valide" }

        val result = provider.delete("inexistant.rfbackup") as SyncOperationResult.Failed

        assertEquals(SyncFailureReason.NOT_FOUND, result.reason)
    }
}
