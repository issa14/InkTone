package com.inktone.data.mapper

import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.infrastructure.database.entity.UserPreferencesEntity

fun UserPreferences.toEntity(): UserPreferencesEntity = UserPreferencesEntity(
    theme = theme, fontSize = fontSize, defaultTtsEngine = defaultTtsEngine.name,
    crashReportingEnabled = crashReportingEnabled, language = language,
    fontFamily = fontFamily.name, reduceMotion = reduceMotion,
    dynamicColorEnabled = dynamicColorEnabled, readingRulerEnabled = readingRulerEnabled,
    dailyGoalMinutes = dailyGoalMinutes, activeVoiceProfileId = activeVoiceProfileId,
    readingMode = readingMode, audioGain = audioGain, useSystemFontScale = useSystemFontScale,
    lineHeightMultiplier = lineHeightMultiplier, readerBrightness = readerBrightness,
    eyeRestReminderEnabled = eyeRestReminderEnabled,
    eyeRestReminderIntervalMinutes = eyeRestReminderIntervalMinutes,
    appTheme = appTheme.name,
    libraryLayoutMode = libraryLayoutMode,
    hasSeenOnboarding = hasSeenOnboarding,
    hasPromptedVoiceDownload = hasPromptedVoiceDownload,
    deviceId = deviceId,
    deviceDisplayName = deviceDisplayName,
    syncProvider = syncProvider,
    syncAccountLabel = syncAccountLabel,
    syncLinkedAt = syncLinkedAt,
    syncLastSyncAt = syncLastSyncAt,
    syncLastAutoSyncFailed = syncLastAutoSyncFailed,
    syncAutoEnabled = syncAutoEnabled,
    syncWifiOnly = syncWifiOnly,
    readerMarginStep = readerMarginStep,
    paragraphSpacingStep = paragraphSpacingStep,
    textJustified = textJustified,
    keepScreenOn = keepScreenOn,
    autoScrollSpeed = autoScrollSpeed,
    recentAnnotationColors = recentAnnotationColors.joinToString(",") { it.name },
)

fun UserPreferencesEntity.toDomain(): UserPreferences = UserPreferences(
    // Lot 9 — id de thème (String), plus un enum : la migration 18→19
    // garantit qu'une base réécrite ne contient plus les anciens noms
    // d'enum (LIGHT/DARK/SEPIA/SYSTEM), aucun lookup/valueOf ici.
    theme = theme, fontSize = fontSize,
    defaultTtsEngine = TtsEngineId.valueOf(defaultTtsEngine),
    crashReportingEnabled = crashReportingEnabled, language = language,
    fontFamily = FontFamily.valueOf(fontFamily), reduceMotion = reduceMotion,
    dynamicColorEnabled = dynamicColorEnabled, readingRulerEnabled = readingRulerEnabled,
    dailyGoalMinutes = dailyGoalMinutes, activeVoiceProfileId = activeVoiceProfileId,
    readingMode = readingMode, audioGain = audioGain, useSystemFontScale = useSystemFontScale,
    lineHeightMultiplier = lineHeightMultiplier, readerBrightness = readerBrightness,
    eyeRestReminderEnabled = eyeRestReminderEnabled,
    eyeRestReminderIntervalMinutes = eyeRestReminderIntervalMinutes,
    appTheme = AppTheme.valueOf(appTheme),
    libraryLayoutMode = libraryLayoutMode,
    hasSeenOnboarding = hasSeenOnboarding,
    hasPromptedVoiceDownload = hasPromptedVoiceDownload,
    deviceId = deviceId,
    deviceDisplayName = deviceDisplayName,
    syncProvider = syncProvider,
    syncAccountLabel = syncAccountLabel,
    syncLinkedAt = syncLinkedAt,
    syncLastSyncAt = syncLastSyncAt,
    syncLastAutoSyncFailed = syncLastAutoSyncFailed,
    syncAutoEnabled = syncAutoEnabled,
    syncWifiOnly = syncWifiOnly,
    readerMarginStep = readerMarginStep,
    paragraphSpacingStep = paragraphSpacingStep,
    textJustified = textJustified,
    keepScreenOn = keepScreenOn,
    autoScrollSpeed = autoScrollSpeed,
    // Lot 22, tâche 12 — repli défensif (constat 11 : un nom d'enum
    // inconnu, ex. base restaurée depuis une version future, est ignoré
    // plutôt que de faire planter la restauration des préférences).
    recentAnnotationColors = recentAnnotationColors.split(",")
        .mapNotNull { name -> AnnotationColor.entries.firstOrNull { it.name == name } },
)
