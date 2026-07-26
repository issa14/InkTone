package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

enum class AnnotationColor { YELLOW, GREEN, BLUE, PINK, ORANGE }

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
    val content: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(endLocator >= startLocator) { "endLocator doit être postérieur ou égal à startLocator" }
    }
}
