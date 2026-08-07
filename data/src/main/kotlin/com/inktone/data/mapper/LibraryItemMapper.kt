package com.inktone.data.mapper

import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemType
import com.inktone.infrastructure.database.entity.LibraryItemView

fun LibraryItemView.toDomain(): LibraryItem {
    val type = when {
        this.type == "bookmark" -> LibraryItemType.BOOKMARK
        note == null -> LibraryItemType.HIGHLIGHT
        else -> LibraryItemType.NOTE
    }
    val startLocator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset).toLocator()
    val endLocator = if (this.type == "annotation") {
        LocatorColumns(endResourceHref!!, endChapterIndex!!, endParagraphIndex, endCharOffset!!).toLocator()
    } else {
        null
    }
    return LibraryItem(
        id = id, type = type, publicationId = publicationId, publicationTitle = publicationTitle,
        startLocator = startLocator, endLocator = endLocator,
        color = color?.let { AnnotationColor.valueOf(it) },
        excerpt = excerpt, note = note, isPinned = isPinned, createdAt = createdAt,
    )
}

/** Traduit le filtre domaine en argument SQL de [com.inktone.infrastructure.database.dao.LibraryItemDao]. */
fun LibraryItemFilter.toSqlTypeFilter(): String? = when (this) {
    LibraryItemFilter.ALL -> null
    LibraryItemFilter.BOOKMARK -> "BOOKMARK"
    LibraryItemFilter.HIGHLIGHT -> "HIGHLIGHT"
    LibraryItemFilter.NOTE -> "NOTE"
}
