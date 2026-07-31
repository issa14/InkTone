package com.inktone.data.mapper

import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.infrastructure.database.entity.UserPreferencesEntity

fun UserPreferences.toEntity(): UserPreferencesEntity = UserPreferencesEntity(
    theme = theme.name, fontSize = fontSize, defaultTtsEngine = defaultTtsEngine.name,
    crashReportingEnabled = crashReportingEnabled, language = language,
    fontFamily = fontFamily.name, reduceMotion = reduceMotion,
    dynamicColorEnabled = dynamicColorEnabled, readingRulerEnabled = readingRulerEnabled,
    dailyGoalMinutes = dailyGoalMinutes, activeVoiceProfileId = activeVoiceProfileId,
    readingMode = readingMode,
)

fun UserPreferencesEntity.toDomain(): UserPreferences = UserPreferences(
    theme = ReadingTheme.valueOf(theme), fontSize = fontSize,
    defaultTtsEngine = TtsEngineId.valueOf(defaultTtsEngine),
    crashReportingEnabled = crashReportingEnabled, language = language,
    fontFamily = FontFamily.valueOf(fontFamily), reduceMotion = reduceMotion,
    dynamicColorEnabled = dynamicColorEnabled, readingRulerEnabled = readingRulerEnabled,
    dailyGoalMinutes = dailyGoalMinutes, activeVoiceProfileId = activeVoiceProfileId,
    readingMode = readingMode,
)
