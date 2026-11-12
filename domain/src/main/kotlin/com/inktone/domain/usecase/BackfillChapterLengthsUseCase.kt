package com.inktone.domain.usecase

import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.service.PublicationParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Calcule les longueurs de chapitres ([ChapterLengths]) des livres importés
 * avant qu'elles n'existent (colonne ajoutée à NULL par `MIGRATION_32_33`) :
 * tant qu'elles manquent, leur progression reste « inconnue » et n'est pas
 * affichée.
 *
 * - EPUB : lues dans la pré-analyse, qui contient déjà le texte analysé de
 *   chaque chapitre dans l'ordre de lecture — aucune ouverture de l'archive.
 *   Une pré-analyse absente ou périmée est laissée à
 *   [RefreshStalePreAnalysesUseCase], qui tourne juste avant dans le même
 *   travail de fond : le livre sera rattrapé au passage suivant.
 * - TXT : son parseur peuple les phrases d'emblée, sans effet de bord.
 * - PDF : jamais — sa progression se compte en pages.
 *
 * Idempotent : un livre déjà pourvu est ignoré. Un échec (source déplacée,
 * permission SAF perdue) est compté puis ignoré, jamais bloquant pour les
 * suivants.
 */
class BackfillChapterLengthsUseCase(
    private val publicationRepository: PublicationRepository,
    private val publicationParser: PublicationParser,
    private val preAnalysisStore: PreAnalysisStore,
) {

    data class Outcome(val filled: Int, val pending: Int, val failed: Int)

    suspend operator fun invoke(): Outcome {
        var filled = 0
        var pending = 0
        var failed = 0
        for (publication in publicationRepository.observeAll().first()) {
            if (publication.chapterLengths != null || publication.format == PublicationFormat.PDF) continue
            try {
                val lengths = when (publication.format) {
                    PublicationFormat.EPUB -> preAnalysisStore.chapterLengths(publication.id, publication.fileHash)
                    PublicationFormat.TXT -> (publicationParser.openForReading(publication) as? ParseResult.Success)
                        ?.documentModel?.chapters?.let(ChapterLengths::of)
                    PublicationFormat.PDF -> null
                }
                when {
                    lengths == null -> pending++
                    // Structure divergente de celle enregistrée à l'import :
                    // des longueurs décalées donneraient une progression fausse.
                    lengths.values.size != publication.chapterCount -> failed++
                    else -> {
                        publicationRepository.setChapterLengths(publication.id, lengths)
                        filled++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        return Outcome(filled = filled, pending = pending, failed = failed)
    }
}
