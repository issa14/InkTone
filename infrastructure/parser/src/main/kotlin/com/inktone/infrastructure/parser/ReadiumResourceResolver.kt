package com.inktone.infrastructure.parser

import com.inktone.domain.service.EpubResourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * Implémente [EpubResourceResolver] via Readium.
 *
 * Délègue l'ouverture de la [org.readium.r2.shared.publication.Publication]
 * à [ReadiumPublicationRegistry] — partagée avec [EpubChapterParser] (K2 :
 * une seule ouverture ZIP par `publicationId`, jamais une par consommateur).
 *
 * ## Cycle de vie
 *
 * - [open] : enregistre et ouvre (ou réutilise) la `Publication` partagée
 *   pour `publicationId` (appelé par le ViewModel après l'import).
 * - [openStream] : retourne un flux vers une ressource.
 * - [close] : libère la `Publication` partagée pour la publication liée
 *   au dernier [open] (appelé par `DisposableEffect` dans `ReaderScreen`).
 *
 * Cette classe est instanciée par Hilt avec un scope ViewModel
 * (`@ViewModelScoped`, voir `di/ParserModule.kt`) — chaque `ReaderViewModel`
 * reçoit sa propre instance, jamais partagée entre deux lecteurs ouverts
 * en parallèle (une instance `@Singleton` fermerait ici la publication
 * d'un autre lecteur au moindre chevauchement d'écran).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadiumResourceResolver @Inject constructor(
    private val registry: ReadiumPublicationRegistry,
) : EpubResourceResolver {

    private var boundPublicationId: String? = null

    /**
     * Ouvre l'EPUB à [fileUri] pour résoudre les ressources.
     *
     * Doit être appelé avant [openStream]. Idempotent : si la même
     * publication est déjà ouverte, ne fait rien.
     */
    override suspend fun open(publicationId: String, fileUri: String) {
        registry.register(publicationId, fileUri)
        registry.getOrOpen(publicationId) // ouverture (ou réutilisation) immédiate — échoue tôt si l'EPUB est illisible
        boundPublicationId = publicationId
    }

    override suspend fun openStream(
        publicationId: String,
        resourceHref: String,
    ): InputStream? {
        val pub = registry.getOrOpen(publicationId)

        val viaManifest = withContext(Dispatchers.IO) {
            // K6, CLAUDE.md : Url + linkWithHref normalisent le
            // percent-encoding au lieu d'une comparaison de chaîne brute
            // contre readingOrder/resources (resourceWithHref(String)
            // équivalent mais déprécié en 3.0.0).
            val url = Url(resourceHref) ?: return@withContext null
            val link = pub.linkWithHref(url) ?: return@withContext null
            val resource: Resource = pub.get(link) ?: return@withContext null
            val length = resource.length().getOrElse { return@withContext null }.toLong()
            resource.read(0L until length).getOrElse { null }
        }
        if (viaManifest != null) return ByteArrayInputStream(viaManifest)

        // Bug réel : accès aux entrées ZIP sensible à la casse sur Android
        // (contrairement à Windows/macOS, où l'EPUB a pu être généré ou
        // édité) — le HTML référence p.ex. "Images/Cover.JPG" alors que
        // l'entrée réelle est "images/cover.jpg". `linkWithHref` échoue
        // silencieusement dans ce cas ; repli sur une lecture ZIP directe
        // insensible à la casse.
        val viaZipFallback = registry.readAssetIgnoreCase(publicationId, resourceHref)
        return viaZipFallback?.let { ByteArrayInputStream(it) }
    }

    override fun close() {
        boundPublicationId?.let { registry.release(it) }
        boundPublicationId = null
    }
}
