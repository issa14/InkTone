package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Livre de REPRISE : le dernier ouvert (`lastOpened` le plus récent).
 *
 * C'est celui que porte la carte « Reprendre la lecture » de la
 * Bibliothèque. À ne pas confondre avec les deux autres notions de « livre
 * courant » déjà présentes dans le dépôt, qui répondent à d'autres
 * questions et n'ont pas de raison de coïncider :
 *
 * - le livre **narré** (`PlaybackMetadata.publicationId`) — celui que la
 *   session TTS joue, qui survit à l'ouverture d'un autre livre ;
 * - le livre de [GetCurrentBookUseCase]
 *   (`ReadingSessionRepository.getLastReadPublicationId`) — le dernier à
 *   avoir une session de lecture enregistrée, pour les Statistiques.
 *
 * La règle elle-même vit dans [resumePublication] pour rester unique :
 * `LibraryUiState` la dérive de la liste qu'il observe déjà (aucune
 * requête supplémentaire), ce use case l'observe depuis le repository pour
 * les appelants qui n'ont pas cette liste — le mini-lecteur.
 */
class ObserveResumePublicationUseCase(
    private val publicationRepository: PublicationRepository,
) {
    operator fun invoke(): Flow<Publication?> =
        publicationRepository.observeAll()
            .map { it.resumePublication() }
            .distinctUntilChanged()
}

/**
 * Le livre de reprise d'une liste : le plus récemment ouvert, ou `null` si
 * aucun ne l'a jamais été. Fonction pure — SEULE définition de cette règle.
 */
fun List<Publication>.resumePublication(): Publication? =
    filter { it.lastOpened != null }.maxByOrNull { it.lastOpened!! }
