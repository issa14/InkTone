package com.inktone.data.mapper

import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.ReadingTheme
import com.inktone.infrastructure.database.entity.ReadingStateEntity

fun ReadingState.toEntity(): ReadingStateEntity {
    val cols = locator.toColumns()
    return ReadingStateEntity(
        publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
        overrideTheme = overrides?.theme?.name, overrideFontSize = overrides?.fontSize,
    )
}

fun ReadingStateEntity.toDomain(): ReadingState = ReadingState(
    publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset).toLocator(),
    lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
    overrides = if (overrideTheme != null || overrideFontSize != null) {
        ReadingOverrides(
            theme = overrideTheme?.let { ReadingTheme.valueOf(it) },
            fontSize = overrideFontSize,
        )
    } else null,
)
