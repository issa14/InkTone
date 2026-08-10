package com.inktone.infrastructure.sync.drive

import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.SyncRemoteFile
import com.inktone.domain.service.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val METADATA_BASE = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/files"
private val permissiveJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class DriveFileListResponse(val files: List<DriveFileDto> = emptyList())

@Serializable
private data class DriveFileDto(val id: String, val name: String, val modifiedTime: String? = null, val size: String? = null)

@Serializable
private data class DriveFileMetadata(val name: String, val parents: List<String>? = null)

/**
 * Client REST léger sur OkHttp (tâche 11.5) — la bibliothèque Google
 * Drive officielle n'est volontairement pas embarquée, poids
 * disproportionné pour quatre opérations sur le seul dossier applicatif.
 *
 * **Aucun verrou distant** : deux appareils qui téléversent en même
 * temps peuvent s'écraser mutuellement au niveau fichier — la cohérence
 * fine (registre de flotte, palier C) applique son propre
 * relire-avant-écrire, ce client ne fait qu'exposer les quatre
 * opérations brutes.
 */
@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: TokenProvider,
) : SyncProvider {
    override val id = SyncProviderId.GOOGLE_DRIVE

    override suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider.getValidToken()
            val existingId = findFileId(fileName, token)
            val metadataJson = Json.encodeToString(
                DriveFileMetadata.serializer(),
                DriveFileMetadata(name = fileName, parents = if (existingId == null) listOf("appDataFolder") else null),
            )
            // multipart/related, pas multipart/form-data (le défaut de
            // MultipartBody.Builder) : Drive rejette le second. uploadType=multipart
            // doit accompagner le premier en paramètre de requête.
            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()

            val url = if (existingId != null) {
                "$UPLOAD_BASE/$existingId?uploadType=multipart"
            } else {
                "$UPLOAD_BASE?uploadType=multipart"
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .method(if (existingId != null) "PATCH" else "POST", multipartBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) SyncOperationResult.Success else response.toFailure()
            }
        } catch (e: AuthorizationException) {
            SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, e.errorDescription ?: "Jeton invalide")
        } catch (e: IOException) {
            SyncOperationResult.Failed(SyncFailureReason.NETWORK, e.message ?: "Erreur réseau")
        }
    }

    override suspend fun download(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider.getValidToken()
            val fileId = findFileId(fileName, token) ?: return@withContext null
            val request = Request.Builder()
                .url("$METADATA_BASE/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: AuthorizationException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun list(): List<SyncRemoteFile> = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider.getValidToken()
            val request = Request.Builder()
                .url("$METADATA_BASE?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&pageSize=1000")
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                permissiveJson.decodeFromString(DriveFileListResponse.serializer(), body).files.map { it.toDomain() }
            }
        } catch (e: AuthorizationException) {
            emptyList()
        } catch (e: IOException) {
            emptyList()
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    override suspend fun delete(fileName: String): SyncOperationResult = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider.getValidToken()
            val fileId = findFileId(fileName, token)
                ?: return@withContext SyncOperationResult.Failed(SyncFailureReason.NOT_FOUND, "Fichier introuvable : $fileName")
            val request = Request.Builder()
                .url("$METADATA_BASE/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) SyncOperationResult.Success else response.toFailure()
            }
        } catch (e: AuthorizationException) {
            SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, e.errorDescription ?: "Jeton invalide")
        } catch (e: IOException) {
            SyncOperationResult.Failed(SyncFailureReason.NETWORK, e.message ?: "Erreur réseau")
        }
    }

    /**
     * `spaces=appDataFolder` est indispensable ici : son absence renvoie
     * une liste vide alors que le fichier existe (panne silencieuse,
     * Drive cherche par défaut dans « Mon Drive », jamais dans le
     * dossier applicatif caché) — tâche 11.5.
     */
    private fun findFileId(fileName: String, token: String): String? {
        val query = "name = '${fileName.replace("'", "\\'")}'"
        val url = "$METADATA_BASE?spaces=appDataFolder&q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return permissiveJson.decodeFromString(DriveFileListResponse.serializer(), body).files.firstOrNull()?.id
        }
    }
}

private fun DriveFileDto.toDomain(): SyncRemoteFile = SyncRemoteFile(
    name = name,
    modifiedAt = modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
    sizeBytes = size?.toLongOrNull() ?: 0L,
)

/** Distingue les causes d'échec HTTP plutôt qu'un `else` unique — leçon du lot 5 (tâche 11.5). */
private fun Response.toFailure(): SyncOperationResult.Failed = when (code) {
    401 -> SyncOperationResult.Failed(SyncFailureReason.INVALID_TOKEN, "Jeton invalide ou expiré")
    403 -> SyncOperationResult.Failed(SyncFailureReason.QUOTA_EXCEEDED, "Quota Drive dépassé")
    404 -> SyncOperationResult.Failed(SyncFailureReason.NOT_FOUND, "Fichier introuvable")
    else -> SyncOperationResult.Failed(SyncFailureReason.UNKNOWN, "Échec HTTP $code")
}
