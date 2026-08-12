package com.inktone.infrastructure.parser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.TableOfContentsEntry
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import org.readium.r2.shared.publication.Metadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.FileOutputStream
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
@OptIn(ExperimentalReadiumApi::class)
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
            // Prerequis verifie contre les sources 3.0.0 (Tache 3.4, absent
            // du plan d'origine) : publication.content() renvoie null tant
            // que ce ContentService n'est pas enregistre explicitement —
            // DefaultPublicationParser ne le fait pas de lui-meme.
            onCreatePublication = {
                servicesBuilder.contentServiceFactory = DefaultContentService.createFactory(
                    resourceContentIteratorFactories = listOf(HtmlResourceContentIterator.Factory()),
                )
            },
        )
    }

    override suspend fun parse(fileUri: String): ParseResult = parseLazy(fileUri)

    /**
     * Ouvre un EPUB et extrait UNIQUEMENT les métadonnées, la TOC et les
     * coquilles de chapitres (sans contenu). Les chapitres retournés ont
     * un [ChapterContent.Rich] avec `blocks` vide — le contenu réel sera
     * chargé à la demande via [EpubChapterParser.parseChapter].
     *
     * L'ancien [parse] continue de fonctionner (backward compat) — il
     * utilise encore [DocumentModelExtractor] pour un parsing complet.
     */
    suspend fun parseLazy(fileUri: String): ParseResult = withContext(Dispatchers.IO) {
        val url = if (fileUri.contains("://")) {
            Uri.parse(fileUri).toAbsoluteUrl()
                ?: return@withContext ParseResult.Corrupted("URI non absolue: $fileUri")
        } else {
            File(fileUri).toUrl()
        }

        val asset = assetRetriever.retrieve(url).getOrElse {
            return@withContext ParseResult.Corrupted("Echec de lecture de l'asset: $it")
        }

        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            return@withContext ParseResult.Corrupted("Echec d'ouverture de la publication: $it")
        }

        val metadata = publication.metadata.toDomain()
        val coverUri = extractAndSaveCover(publication, fileUri)

        // Coquilles de chapitres : index, href, title, contenu vide
        val chapters = publication.readingOrder.mapIndexed { index, link ->
            Chapter(
                index = index,
                href = link.href.toString(),
                title = link.title?.takeIf { it.isNotBlank() },
                content = ChapterContent.Rich(blocks = emptyList()),
                sentences = emptyList(),
            )
        }

        // TOC (même logique que DocumentModelExtractor)
        val readingOrderUrls = publication.readingOrder.map { it.href.resolve().removeFragment() }
        val toc = publication.tableOfContents.map { link -> toTocEntry(link, readingOrderUrls) }

        ParseResult.Success(
            documentModel = DocumentModel(
                chapters = chapters,
                tableOfContents = toc,
                resources = emptyList(),
            ),
            isDrmProtected = publication.isProtected,
            metadata = metadata.copy(coverUri = coverUri),
        )
    }

    /**
     * Extrait la couverture depuis Readium et la sauvegarde dans le cache
     * interne de l'app. Retourne le chemin local du fichier ou null.
     *
     * Readium 3.0.0 fournit [Publication.cover] qui retourne un [Bitmap]
     * déjà décodé — [Publication.coverLink] est déprécié.
     */
    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun extractAndSaveCover(
        publication: org.readium.r2.shared.publication.Publication,
        fileUri: String,
    ): String? = withContext(Dispatchers.IO) {
        val bitmap = publication.cover() ?: return@withContext null

        val coverDir = File(context.cacheDir, "covers")
        coverDir.mkdirs()
        val coverFile = File(coverDir, "${fileUri.hashCode().toUInt()}.jpg")

        try {
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            coverFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.w("ReadiumParser", "Échec sauvegarde couverture pour $fileUri", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Tâche 6.1.1 : forme de `Metadata` vérifiée contre les classes réelles
     * du jar `readium-shared-3.0.0` (title/authors/subjects/belongsToSeries
     * déjà aplatis en `String`/`List<Contributor>` par Readium — pas de
     * `LocalizedString` à résoudre ici). `belongsToSeries` fournit à la
     * fois le nom de série (`Contributor.name`) et l'index
     * (`Contributor.position`, `Double?` — converti en `Float?`).
     */
    private fun Metadata.toDomain(): PublicationMetadata {
        val series = belongsToSeries.firstOrNull()
        return PublicationMetadata(
            title = title,
            subtitle = null,
            authors = authors.mapNotNull { it.name },
            publisher = publishers.firstOrNull()?.name,
            language = language?.code,
            description = description,
            seriesName = series?.name,
            seriesIndex = series?.position?.toFloat(),
            subjects = subjects.mapNotNull { it.name },
        )
    }

    /**
     * Convertit un [Link] Readium en [TableOfContentsEntry] avec résolution
     * du chapterIndex par correspondance de href (sans fragment).
     * Même logique que [DocumentModelExtractor.toTocEntry].
     */
    private fun toTocEntry(link: Link, readingOrderUrls: List<org.readium.r2.shared.util.Url>): TableOfContentsEntry {
        val chapterIndex = readingOrderUrls.indexOf(link.href.resolve().removeFragment())
        return TableOfContentsEntry(
            title = link.title ?: "",
            chapterIndex = chapterIndex.coerceAtLeast(0),
            children = link.children.map { child -> toTocEntry(child, readingOrderUrls) },
        )
    }
}
