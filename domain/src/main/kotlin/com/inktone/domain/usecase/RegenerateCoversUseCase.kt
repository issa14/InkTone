package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.CoverExtractionResult
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
 * ([PublicationParser.extractCover]) et la réécrit en base.
 *
 * - [CoverExtractionResult.Success] → `coverUri` réécrit (chemin extrait,
 *   ou `null` = couverture par défaut pour un format sans couverture).
 * - [CoverExtractionResult.Failure] → la couverture existante est
 *   **préservée** (aucune écriture), l'échec est compté — jamais un
 *   fichier illisible ne dégrade une couverture déjà extraite.
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
            val extraction = runCatching { publicationParser.extractCover(publication.fileUri) }
            if (extraction.isSuccess) {
                when (val result = extraction.getOrThrow()) {
                    is CoverExtractionResult.Success -> publicationRepository.setCoverUri(publication.id, result.coverUri)
                    is CoverExtractionResult.Failure -> failed++
                }
            } else {
                // Un parser qui lève malgré le contrat — isolé, jamais
                // fatal, et surtout jamais une écriture de `null`.
                failed++
            }
            onProgress(index + 1, total)
        }

        return CoverRegenerationResult(processed = total, failed = failed)
    }
}
