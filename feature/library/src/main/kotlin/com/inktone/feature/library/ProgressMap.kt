package com.inktone.feature.library

import com.inktone.domain.model.Publication
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.progressionAt
import kotlin.math.roundToInt

/**
 * Pourcentage de lecture (0..100) de chaque livre ENTAMÉ dont la progression
 * est connue — même calcul que la ligne de statut du Lecteur
 * ([progressionAt] : position dans le texte du livre entier), jamais un
 * second calcul propre à la Bibliothèque. Partagé entre [LibraryViewModel],
 * `LibraryDetailViewModel` et `RecentsViewModel` : ne pas dupliquer.
 *
 * Remplace l'ancien `chapterIndex / (chapterCount - 1)`, qui ignorait la
 * position dans le chapitre et la taille des chapitres (tout le premier
 * chapitre à 0 %, le début du dernier à 100 %, pages liminaires comptées
 * comme des chapitres) et ne concordait jamais avec le Lecteur.
 *
 * Absents de la carte : livres jamais ouverts, et livres dont la progression
 * est inconnue (longueurs de chapitres en attente de rattrapage) — aucun
 * badge plutôt qu'un chiffre faux. Un livre entamé n'affiche jamais 0 % : le
 * badge commence à 1 %.
 */
internal fun computeProgressMap(publications: List<Publication>, readingStates: List<ReadingState>): Map<String, Int> {
    val stateMap = readingStates.associateBy { it.publicationId }
    return buildMap {
        for (publication in publications) {
            val state = stateMap[publication.id] ?: continue
            val progression = publication.progressionAt(state.locator) ?: continue
            put(publication.id, (progression * 100).roundToInt().coerceIn(1, 100))
        }
    }
}

/** Livres entamés (un état de reprise existe), que leur progression soit connue ou non. */
internal fun startedPublicationIds(readingStates: List<ReadingState>): Set<String> =
    readingStates.mapTo(HashSet()) { it.publicationId }
