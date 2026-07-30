package com.inktone.feature.settings

import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
)

sealed interface SettingsIntent {
    data class SetTheme(val theme: ReadingTheme) : SettingsIntent
    data class SetFontSize(val fontSize: Int) : SettingsIntent
    data class SetFontFamily(val fontFamily: FontFamily) : SettingsIntent
    data class SetDefaultTtsEngine(val engine: TtsEngineId) : SettingsIntent
    data class SetLanguage(val language: String) : SettingsIntent
    data class SetCrashReportingEnabled(val enabled: Boolean) : SettingsIntent
    data class SetReduceMotion(val enabled: Boolean) : SettingsIntent
    data class SetDynamicColorEnabled(val enabled: Boolean) : SettingsIntent
    data class SetReadingRulerEnabled(val enabled: Boolean) : SettingsIntent
    object ApplyAccessibilityPreset : SettingsIntent
}
