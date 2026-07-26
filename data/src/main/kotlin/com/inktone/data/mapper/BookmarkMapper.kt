package com.inktone.data.mapper

import com.inktone.domain.model.Bookmark
import com.inktone.infrastructure.database.entity.BookmarkEntity

fun Bookmark.toEntity(): BookmarkEntity {
    val cols = locator.toColumns()
    return BookmarkEntity(
        id = id, publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        title = title, note = note, createdAt = createdAt,
    )
}

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id, publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset).toLocator(),
    title = title, note = note, createdAt = createdAt,
)
