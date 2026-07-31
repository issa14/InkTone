package com.inktone.data.mapper

import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import com.inktone.infrastructure.database.entity.ReadingSessionEntity

fun ReadingSession.toEntity(): ReadingSessionEntity = ReadingSessionEntity(
    id = id, publicationId = publicationId, startedAt = startedAt, endedAt = endedAt,
    mode = mode.name, sentencesRead = sentencesRead, durationMs = durationMs, wordsRead = wordsRead,
)

fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id, publicationId = publicationId, startedAt = startedAt, endedAt = endedAt,
    mode = ReadingMode.valueOf(mode), sentencesRead = sentencesRead, durationMs = durationMs, wordsRead = wordsRead,
)
