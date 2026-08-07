package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

/**
 * Ligne unifiée de la vue globale « Marque-pages et notes » (Lot 4).
 *
 * Fusionne [Bookmark] et [Annotation] pour permettre un tri et une
 * recherche uniques au niveau de la requête (Blueprint — voir tâche
 * 4.4). [HIGHLIGHT] et [NOTE] désignent la même entité [Annotation],
 * distinguées par la présence de [note] (le `content` de l'annotation) —
 * jamais un second type d'entité.
 */
enum class LibraryItemType { BOOKMARK, HIGHLIGHT, NOTE }

data class LibraryItem(
    val id: String,
    val type: LibraryItemType,
    val publicationId: String,
    val publicationTitle: String?,
    val startLocator: Locator,
    /** Non nul uniquement pour [LibraryItemType.HIGHLIGHT] et [LibraryItemType.NOTE]. */
    val endLocator: Locator?,
    val color: AnnotationColor?,
    val excerpt: String?,
    val note: String?,
    val isPinned: Boolean,
    val createdAt: Long,
)

enum class LibraryItemSortOrder { CHRONOLOGICAL, ALPHABETICAL }

enum class LibraryItemFilter { ALL, BOOKMARK, HIGHLIGHT, NOTE }
