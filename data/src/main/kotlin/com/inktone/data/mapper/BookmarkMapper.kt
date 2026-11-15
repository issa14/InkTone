package com.inktone.data.mapper

import com.inktone.domain.model.Bookmark
import com.inktone.infrastructure.database.entity.BookmarkEntity

fun Bookmark.toEntity(): BookmarkEntity {
    val cols = locator.toColumns()
    return BookmarkEntity(
        id = id, publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        title = title, note = note, excerpt = excerpt, isPinned = isPinned, createdAt = createdAt,
        textBefore = cols.textBefore, textAfter = cols.textAfter,
        updatedAt = updatedAt,
    )
}

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id, publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset, textBefore = textBefore, textAfter = textAfter).toLocator(),
    title = title, note = note, excerpt = excerpt, isPinned = isPinned, createdAt = createdAt,
    // Un signet d'avant la migration n'a pas de date de modification : sa création en tient lieu.
    updatedAt = if (updatedAt == 0L) createdAt else updatedAt,
)
