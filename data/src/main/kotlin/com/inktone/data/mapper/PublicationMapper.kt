package com.inktone.data.mapper

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.infrastructure.database.entity.PublicationEntity

fun Publication.toEntity(): PublicationEntity = PublicationEntity(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = format.name, fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    pageCount = pageCount,
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    isPinned = isPinned, subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)

fun PublicationEntity.toDomain(): Publication = Publication(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = PublicationFormat.valueOf(format), fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    pageCount = pageCount,
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    isPinned = isPinned, subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)
