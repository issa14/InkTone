package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PublicationParser
import kotlinx.coroutines.flow.first

/**
 * Résultat de la reconstruction des couvertures (Lot 19) — comptages
 * bruts, jamais une exception pour un cas métier attendu (Blueprint
 * §7.11) : un fichier devenu illisible compte comme un échec, pas comme
 * un arrêt de l'opération.
 */
data class CoverRegenerationResult(
    val processed: Int,
    val failed: Int,
)

/**
 * Reconstruit les couvertures de toute la bibliothèque (Lot 19) : pour
 * chaque publication, ré-extrait la couverture depuis le fichier source
 * ([PublicationParser.extractCover]) et la réécrit en base. Un format
 * sans couverture (TXT) ou un EPUB sans image de couverture produit
 * `null` → couverture par défaut (dégradé procédural), ce qui n'est pas
 * compté comme un échec.
 *
 * Progression live via [onProgress] (compteur `processed/total`), cohérent
 * avec le menu legacy (« progression live X/Y ») — l'appelant (ViewModel)
 * pilote l'affichage, ce Use Case ne connaît ni Compose ni l'UI.
 */
class RegenerateCoversUseCase(
    private val publicationRepository: PublicationRepository,
    private val publicationParser: PublicationParser,
) {
    suspend operator fun invoke(onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): CoverRegenerationResult {
        val publications = publicationRepository.observeAll().first()
        val total = publications.size
        var failed = 0

        publications.forEachIndexed { index, publication ->
            val extracted = runCatching { publicationParser.extractCover(publication.fileUri) }
            if (extracted.isSuccess) {
                publicationRepository.setCoverUri(publication.id, extracted.getOrNull())
            } else {
                failed++
            }
            onProgress(index + 1, total)
        }

        return CoverRegenerationResult(processed = total, failed = failed)
    }
}
