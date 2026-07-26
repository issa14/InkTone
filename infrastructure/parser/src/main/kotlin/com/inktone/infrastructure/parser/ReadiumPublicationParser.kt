package com.inktone.infrastructure.parser

import android.content.Context
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente PublicationParser (Tâche 1.7) via Readium 3.0.0 — encapsulé
 * ici, jamais exposé au-delà de ce module (ADR-011). Portée de la Tâche
 * 3.2 : ouverture + métadonnées uniquement. L'extraction en DocumentModel
 * (Chapter/Sentence avec offsets) est la Tâche 3.4, pas celle-ci — ne pas
 * anticiper dessus tant que le guide d'extraction Readium n'est pas
 * vérifié séparément.
 *
 * Packages vérifiés contre les sources réelles du tag 3.0.0 du dépôt
 * `readium/kotlin-toolkit` avant écriture (voir avertissement du plan de
 * Tâche 3.2) : la racine est toujours `org.readium.r2.*` à cette version
 * — le renommage en `org.readium.*` n'a pas encore eu lieu en 3.0.0,
 * contrairement à ce que la branche `develop` pourrait laisser supposer.
 */
@Singleton
class ReadiumPublicationParser @Inject constructor(
    @ApplicationContext private val context: Context,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.EPUB)

    // Instanciation paresseuse — coûteuse, un seul jeu de composants
    // Readium réutilisé pour tous les parses de la durée de vie du singleton.
    private val httpClient by lazy { DefaultHttpClient() }
    private val assetRetriever by lazy {
        AssetRetriever(contentResolver = context.contentResolver, httpClient = httpClient)
    }
    private val publicationOpener by lazy {
        PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                // Pas de pdfFactory : PDF hors périmètre v1 (ADR-017).
                pdfFactory = null,
            ),
        )
    }

    override suspend fun parse(fileUri: String): ParseResult = withContext(Dispatchers.IO) {
        // Tâche 3.2 : URI de fichier local de test uniquement. Le SAF réel
        // (content://) sera branché en reliant infrastructure/storage à
        // ce parser en Phase 4/6 — pas cette tâche.
        val url = File(fileUri).toUrl()

        val asset = assetRetriever.retrieve(url).getOrElse {
            return@withContext ParseResult.Corrupted("Echec de lecture de l'asset: $it")
        }

        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            return@withContext ParseResult.Corrupted("Echec d'ouverture de la publication: $it")
        }

        // DRM : verifie contre les sources reelles (Tache 3.2) — Publication.isProtected
        // (org.readium.r2.shared.publication.services) reflete la presence d'un
        // ContentProtectionService, enregistre par FallbackContentProtection meme
        // sans module readium-lcp/adept integre des qu'un format LCP ou Adobe ADEPT
        // est detecte a la racine du conteneur. Suffisant pour K7 : detection, pas
        // dechiffrement (hors perimetre v1).
        ParseResult.Success(
            documentModel = DocumentModel(chapters = emptyList(), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = publication.isProtected,
        )
    }
}
