package com.inktone.infrastructure.sync.webdav

import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.SyncRemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

private val PROPFIND_BODY = """
    <?xml version="1.0" encoding="utf-8"?>
    <d:propfind xmlns:d="DAV:">
      <d:prop>
        <d:getcontentlength/>
        <d:getlastmodified/>
        <d:displayname/>
      </d:prop>
    </d:propfind>
""".trimIndent()

/**
 * Client WebDAV REST léger sur OkHttp (Lot 19) — mêmes quatre opérations
 * que [GoogleDriveSyncProvider], plus un test de connexion (PROPFIND
 * depth 0). Aucune bibliothèque WebDAV dédiée : le protocole se réduit
 * à PROPFIND/GET/PUT/DELETE sur un dossier applicatif unique.
 *
 * Authentification : Basic (identifiant/mot de passe) sur chaque requête.
 * Identifiants lus depuis [WebDavCredentialsStoreContract] (chiffrés au
 * repos), jamais exposés au-delà de ce module.
 */
@Singleton
class WebDavSyncProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialsStore: WebDavCredentialsStoreContract,
) : SyncProvider {
    override val id = SyncProviderId.WEBDAV

    /** Teste la connexion avec des identifiants explicites (avant persistance). */
    suspend fun testConnection(url: String, username: String, password: String): SyncOperationResult =
        withContext(Dispatchers.IO) {
            try {
                val request = propfindRequest(baseUrl(url), depth = 0, username, password)
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) SyncOperationResult.Success else response.toWebDavFailure()
                }
            } catch (e: IOException) {
                SyncOperationResult.Failed(SyncFailureReason.NETWORK, e.message ?: "Erreur réseau")
            } catch (e: IllegalArgumentException) {
                // URL mal formée (hôte absent, schéma invalide) — ne jamais
                // laisser fuir une exception non typée jusqu'à l'UI.
                SyncOperationResult.Failed(SyncFailureReason.UNKNOWN, e.message ?: "URL WebDAV invalide")
            }
        }

    override suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult =
        withCredentialsOr(SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, "WebDAV non configuré")) { credentials ->
            try {
                val request = Request.Builder()
                    .url(fileUrl(credentials.url, fileName))
                    .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .put(bytes.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) SyncOperationResult.Success else response.toWebDavFailure()
                }
            } catch (e: IOException) {
                SyncOperationResult.Failed(SyncFailureReason.NETWORK, e.message ?: "Erreur réseau")
            }
        }

    override suspend fun download(fileName: String): ByteArray? =
        withCredentialsOr<ByteArray?>(null) { credentials ->
            try {
                val request = Request.Builder()
                    .url(fileUrl(credentials.url, fileName))
                    .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            } catch (e: IOException) {
                null
            }
        }

    override suspend fun list(): List<SyncRemoteFile> =
        withCredentialsOr(emptyList()) { credentials ->
            try {
                val request = propfindRequest(baseUrl(credentials.url), depth = 1, credentials.username, credentials.password)
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withCredentialsOr emptyList()
                    val body = response.body?.string() ?: return@withCredentialsOr emptyList()
                    parseMultistatus(body)
                }
            } catch (e: IOException) {
                emptyList()
            }
        }

    override suspend fun delete(fileName: String): SyncOperationResult =
        withCredentialsOr(SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, "WebDAV non configuré")) { credentials ->
            try {
                val request = Request.Builder()
                    .url(fileUrl(credentials.url, fileName))
                    .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .delete()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) SyncOperationResult.Success else response.toWebDavFailure()
                }
            } catch (e: IOException) {
                SyncOperationResult.Failed(SyncFailureReason.NETWORK, e.message ?: "Erreur réseau")
            }
        }

    private suspend fun <T> withCredentialsOr(default: T, block: suspend (WebDavCredentials) -> T): T {
        val credentials = credentialsStore.read() ?: return default
        return withContext(Dispatchers.IO) { block(credentials) }
    }

    private fun propfindRequest(baseUrl: String, depth: Int, username: String, password: String): Request =
        Request.Builder()
            .url(baseUrl)
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", depth.toString())
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .build()

    private fun baseUrl(url: String): String = url.trimEnd('/')

    private fun fileUrl(baseUrl: String, fileName: String): String = "$baseUrl/$fileName"

    /**
     * Parse la réponse multistatus WebDAV en [SyncRemoteFile]. Les href
     * incluent le dossier racine lui-même (profondeur 1) — filtré : un
     * href terminant par `/` et égal à la racine n'est pas un fichier.
     */
    private fun parseMultistatus(xml: String): List<SyncRemoteFile> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = document.getElementsByTagNameNS("DAV:", "response")
        (0 until responses.length).mapNotNull { index ->
            val element = responses.item(index) as? Element ?: return@mapNotNull null
            val href = element.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent?.trim()
                ?: return@mapNotNull null
            if (href.endsWith("/")) return@mapNotNull null
            val name = href.substringAfterLast('/').ifBlank { null } ?: return@mapNotNull null
            val size = element.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent?.toLongOrNull() ?: 0L
            val modified = element.getElementsByTagNameNS("DAV:", "getlastmodified").item(0)?.textContent
            SyncRemoteFile(
                name = name,
                modifiedAt = modified?.let { parseHttpDate(it) } ?: 0L,
                sizeBytes = size,
            )
        }
    }.getOrElse { emptyList() }

    private fun parseHttpDate(value: String): Long =
        runCatching { Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)).toEpochMilli() }.getOrElse { 0L }
}

/** Distingue les causes d'échec HTTP plutôt qu'un `else` unique — même leçon que [GoogleDriveSyncProvider] (lot 5). */
private fun Response.toWebDavFailure(): SyncOperationResult.Failed = when (code) {
    401, 403 -> SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, "Identifiants WebDAV invalides")
    404 -> SyncOperationResult.Failed(SyncFailureReason.NOT_FOUND, "Dossier WebDAV introuvable")
    else -> SyncOperationResult.Failed(SyncFailureReason.UNKNOWN, "Échec HTTP $code")
}
