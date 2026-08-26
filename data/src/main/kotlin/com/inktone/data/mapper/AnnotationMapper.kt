package com.inktone.data.mapper

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind
import com.inktone.domain.model.toHex
import com.inktone.infrastructure.database.entity.AnnotationEntity

fun Annotation.toEntity(): AnnotationEntity {
    val start = startLocator.toColumns()
    val end = endLocator.toColumns()
    return AnnotationEntity(
        id = id, publicationId = publicationId,
        startResourceHref = start.resourceHref, startChapterIndex = start.chapterIndex,
        startParagraphIndex = start.paragraphIndex, startCharOffset = start.charOffset,
        endResourceHref = end.resourceHref, endChapterIndex = end.chapterIndex,
        endParagraphIndex = end.paragraphIndex, endCharOffset = end.charOffset,
        color = color.toHex(), kind = kind.name, content = content, excerpt = excerpt, isPinned = isPinned,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}

fun AnnotationEntity.toDomain(): Annotation = Annotation(
    id = id, publicationId = publicationId,
    startLocator = LocatorColumns(startResourceHref, startChapterIndex, startParagraphIndex, startCharOffset).toLocator(),
    endLocator = LocatorColumns(endResourceHref, endChapterIndex, endParagraphIndex, endCharOffset).toLocator(),
    color = AnnotationColor.parse(color), kind = AnnotationKind.valueOf(kind), content = content, excerpt = excerpt, isPinned = isPinned,
    createdAt = createdAt, updatedAt = updatedAt,
)
