package com.inktone.domain.usecase

import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PreAnalysisBuilder
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.service.PublicationParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Régénère la pré-analyse (Lot 22) de chaque EPUB dont la pré-analyse est
 * absente ou périmée — voir « Régénération » dans [PreAnalysisStore].
 *
 * Livre par livre, séquentiellement : c'est un travail de fond, qui ne
 * doit pas disputer le processeur à la lecture. Un livre en échec (source
 * déplacée, permission SAF perdue, EPUB malformé) est compté puis ignoré —
 * il ne bloque jamais les suivants.
 *
 * Mêmes chapitres que l'import : la liste vient du parsing d'import
 * ([PublicationParser.parse]), seule source qui inclut la page de
 * couverture ajoutée en tête quand l'OPF la sort de l'ordre de lecture.
 */
class RefreshStalePreAnalysesUseCase(
    private val publicationRepository: PublicationRepository,
    private val publicationParser: PublicationParser,
    private val preAnalysisBuilder: PreAnalysisBuilder,
    private val preAnalysisStore: PreAnalysisStore,
) {

    data class Outcome(val refreshed: Int, val alreadyFresh: Int, val failed: Int)

    suspend operator fun invoke(): Outcome {
        var refreshed = 0
        var alreadyFresh = 0
        var failed = 0
        for (publication in publicationRepository.observeAll().first()) {
            if (publication.format != PublicationFormat.EPUB) continue
            if (preAnalysisStore.isFresh(publication.id, publication.fileHash)) {
                alreadyFresh++
                continue
            }
            try {
                val shells = (publicationParser.parse(publication.fileUri) as? ParseResult.Success)
                    ?.documentModel?.chapters
                if (shells == null) {
                    failed++
                    continue
                }
                val chapters = preAnalysisBuilder.analyzeAll(publication.fileUri, shells.map { it.href })
                preAnalysisStore.save(publication.id, publication.fileHash, chapters)
                // Un parseur qui évolue peut déplacer les offsets des phrases :
                // les longueurs de chapitres (progression) suivent la même
                // analyse que celle que le Lecteur relira.
                if (chapters.size == publication.chapterCount) {
                    publicationRepository.setChapterLengths(publication.id, ChapterLengths.of(chapters))
                }
                preAnalysisBuilder.onPreAnalysisRewritten(publication.id)
                refreshed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        return Outcome(refreshed = refreshed, alreadyFresh = alreadyFresh, failed = failed)
    }
}
