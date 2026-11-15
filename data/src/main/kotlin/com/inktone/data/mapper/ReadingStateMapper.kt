package com.inktone.data.mapper

import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.infrastructure.database.entity.ReadingStateEntity

fun ReadingState.toEntity(): ReadingStateEntity {
    val cols = locator.toColumns()
    return ReadingStateEntity(
        publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        pageOffsetY = cols.pageOffsetY,
        lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
        // Lot 9 — id de thème (String), plus un enum.
        overrideTheme = overrides?.theme, overrideFontSize = overrides?.fontSize,
    )
}

fun ReadingStateEntity.toDomain(): ReadingState = ReadingState(
    publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset, pageOffsetY).toLocator(),
    lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
    overrides = if (overrideTheme != null || overrideFontSize != null) {
        ReadingOverrides(
            theme = overrideTheme,
            fontSize = overrideFontSize,
        )
    } else null,
)
