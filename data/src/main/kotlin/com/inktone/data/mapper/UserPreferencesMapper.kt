package com.inktone.data.mapper

import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.infrastructure.database.entity.UserPreferencesEntity

fun UserPreferences.toEntity(): UserPreferencesEntity = UserPreferencesEntity(
    theme = theme.name, fontSize = fontSize, defaultTtsEngine = defaultTtsEngine.name,
    crashReportingEnabled = crashReportingEnabled, language = language,
)

fun UserPreferencesEntity.toDomain(): UserPreferences = UserPreferences(
    theme = ReadingTheme.valueOf(theme), fontSize = fontSize,
    defaultTtsEngine = TtsEngineId.valueOf(defaultTtsEngine),
    crashReportingEnabled = crashReportingEnabled, language = language,
)
