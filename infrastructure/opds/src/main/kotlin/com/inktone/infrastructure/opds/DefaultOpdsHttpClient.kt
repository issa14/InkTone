package com.inktone.infrastructure.opds

import com.inktone.domain.service.OpdsCredentialsStore
import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsFetchResult
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.infrastructure.opds.di.OpdsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client HTTP OPDS (Lot 13, tâche 13.2.3) — OkHttp brut, même sobriété
 * que le client sync du Lot 11 (pas de Retrofit/TikXml). L'en-tête
 * `Authorization: Basic` est posé **requête par requête**, résolu via
 * [OpdsCredentialsStore] par `catalogId` — jamais un intercepteur global
 * (écart délibéré §2 : OPDS multiplie les hôtes, un intercepteur non
 * borné par hôte risquerait de fuir des identifiants vers le mauvais
 * serveur).
 */
@Singleton
class DefaultOpdsHttpClient @Inject constructor(
    @OpdsClient private val client: OkHttpClient,
    private val credentialsStore: OpdsCredentialsStore,
) : OpdsHttpClient {

    override suspend fun fetch(url: String, catalogId: String?): OpdsFetchResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(url, catalogId)
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> OpdsFetchResult.Failure(
                        OpdsFailureReason.UNAUTHORIZED, "Authentification refusée pour ce catalogue",
                    )
                    response.code == 403 -> OpdsFetchResult.Failure(
                        OpdsFailureReason.NETWORK, "Accès refusé (403) — le catalogue bloque ce client",
                    )
                    response.code == 404 -> OpdsFetchResult.Failure(
                        OpdsFailureReason.NOT_FOUND, "Flux introuvable",
                    )
                    !response.isSuccessful -> OpdsFetchResult.Failure(
                        OpdsFailureReason.NETWORK, "Erreur HTTP ${response.code}",
                    )
                    else -> {
                        // Lot 13, tâche 13.4.1 — un flux annonçant
                        // `application/opds+json` sans variante Atom est rejeté
                        // avec un message clair plutôt qu'un crash de parsing
                        // XML (ADR-023 : OPDS 2.0/JSON hors périmètre).
                        val contentType = response.header("Content-Type").orEmpty()
                        if (contentType.contains("application/opds+json", ignoreCase = true) &&
                            !contentType.contains("atom", ignoreCase = true)
                        ) {
                            return@use OpdsFetchResult.Failure(
                                OpdsFailureReason.UNSUPPORTED_FORMAT,
                                "Flux OPDS 2.0 (JSON) non supporté — seul OPDS 1.2/Atom est accepté",
                            )
                        }
                        val body = response.body?.string()
                            ?: return@use OpdsFetchResult.Failure(OpdsFailureReason.NETWORK, "Réponse vide")
                        OpdsFetchResult.Success(body = body, finalUrl = response.request.url.toString())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Une URL invalide (ex. hôte avec un espace) lève
            // IllegalArgumentException — jamais un crash, un échec typé.
            OpdsFetchResult.Failure(OpdsFailureReason.NETWORK, e.message ?: "Erreur réseau")
        }
    }

    private fun buildRequest(url: String, catalogId: String?): Request {
        val builder = Request.Builder().url(url).get()
        if (catalogId != null) {
            val credentials = credentialsStore.getCredentials(catalogId)
            if (credentials != null) {
                // `Credentials.basic` (OkHttp, pur Java) — pas d'`android.util.Base64`
                // qui rendrait ce client intestable en JVM sans Robolectric.
                builder.header("Authorization", Credentials.basic(credentials.username, credentials.password))
            }
        }
        return builder.build()
    }

    override suspend fun download(url: String, catalogId: String?): OpdsDownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(url, catalogId)
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> OpdsDownloadResult.Failure(
                        OpdsFailureReason.UNAUTHORIZED, "Authentification refusée pour ce catalogue",
                    )
                    response.code == 404 -> OpdsDownloadResult.Failure(
                        OpdsFailureReason.NOT_FOUND, "Fichier introuvable",
                    )
                    !response.isSuccessful -> OpdsDownloadResult.Failure(
                        OpdsFailureReason.NETWORK, "Erreur HTTP ${response.code}",
                    )
                    else -> {
                        val bytes = response.body?.bytes()
                            ?: return@use OpdsDownloadResult.Failure(OpdsFailureReason.NETWORK, "Réponse vide")
                        OpdsDownloadResult.Success(bytes)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpdsDownloadResult.Failure(OpdsFailureReason.NETWORK, e.message ?: "Erreur réseau")
        }
    }
}
