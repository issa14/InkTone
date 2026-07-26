package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

data class Bookmark(
    val id: String,
    val publicationId: String,
    val locator: Locator,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
    }
}
