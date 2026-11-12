package com.inktone.data.mapper

import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.infrastructure.database.entity.PublicationEntity

fun Publication.toEntity(): PublicationEntity = PublicationEntity(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = format.name, fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    pageCount = pageCount, chapterLengths = chapterLengths?.toColumn(),
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    isPinned = isPinned, subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)

fun PublicationEntity.toDomain(): Publication = Publication(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = PublicationFormat.valueOf(format), fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    pageCount = pageCount, chapterLengths = chapterLengthsFromColumn(chapterLengths, chapterCount),
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    isPinned = isPinned, subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)

/** CSV d'entiers, dans l'ordre de lecture (`"0,1520,8840"`). */
fun ChapterLengths.toColumn(): String = values.joinToString(",")

/**
 * Relit la colonne. Une valeur illisible ou dont le nombre de chapitres ne
 * correspond plus à [chapterCount] est traitée comme ABSENTE (progression
 * inconnue, recalculée par le rattrapage) — jamais un crash à la lecture de
 * la bibliothèque, jamais une progression calculée sur des longueurs
 * décalées.
 */
internal fun chapterLengthsFromColumn(column: String?, chapterCount: Int): ChapterLengths? {
    if (column == null) return null
    val values = if (column.isEmpty()) emptyList() else column.split(",").map { it.toIntOrNull() ?: return null }
    if (values.size != chapterCount || values.any { it < 0 }) return null
    return ChapterLengths(values)
}
