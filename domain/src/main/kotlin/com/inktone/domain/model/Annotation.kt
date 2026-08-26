package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

enum class AnnotationColor { YELLOW, GREEN, BLUE, PINK, ORANGE }

/**
 * Type d'annotation (Lot 22, tâche 10) — trois canaux visuels distincts :
 * surlignage, souligné, barré. Le [HIGHLIGHT] couvre l'existant (toute
 * annotation créée avant ce lot est un surlignage) ; les deux autres
 * étendent la parité avec les lecteurs top-tier sans jamais mélanger les
 * canaux visuels : annotation, surlignage TTS (`WordHighlightColor`) et
 * sélection (`SelectionHighlightColor`) restent trois canaux séparés.
 */
enum class AnnotationKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH }

/**
 * Surlignage, note ou citation liée à une plage de [Locator]s. Un seul
 * modèle d'adressage pour toute la plage — jamais chapter+startOffset
 * d'un côté et Locator de l'autre (revue B2/D7).
 */
data class Annotation(
    val id: String,
    val publicationId: String,
    val startLocator: Locator,
    val endLocator: Locator,
    val color: AnnotationColor,
    val kind: AnnotationKind = AnnotationKind.HIGHLIGHT,
    val content: String? = null,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(endLocator >= startLocator) { "endLocator doit être postérieur ou égal à startLocator" }
        require(excerpt == null || excerpt.length <= MAX_EXCERPT_LENGTH) {
            "excerpt ne doit pas dépasser $MAX_EXCERPT_LENGTH caractères"
        }
    }

    companion object {
        /** Borne fixée à la création (Lot 4, tâche 4.2) — la carte cible affiche un extrait, pas un passage entier. */
        const val MAX_EXCERPT_LENGTH = 280
    }
}
