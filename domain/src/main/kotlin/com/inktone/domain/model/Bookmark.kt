package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

data class Bookmark(
    val id: String,
    val publicationId: String,
    val locator: Locator,
    val title: String? = null,
    val note: String? = null,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(excerpt == null || excerpt.length <= MAX_EXCERPT_LENGTH) {
            "excerpt ne doit pas dépasser $MAX_EXCERPT_LENGTH caractères"
        }
    }

    companion object {
        /** Même borne que Annotation (Lot 4, tâche 4.2). */
        const val MAX_EXCERPT_LENGTH = 280
    }
}
