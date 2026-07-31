package com.inktone.feature.settings

import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    // A.5 — profils vocaux disponibles pour le picker
    val voiceProfiles: List<VoiceProfile> = emptyList(),
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
    // A.5 — selection du profil vocal actif
    data class SetActiveVoiceProfile(val profileId: String?) : SettingsIntent
    // D.3 — gain audio (1.0× à 4.0×)
    data class SetAudioGain(val gain: Float) : SettingsIntent
    // D.3 — respecter le fontScale système
    data class SetUseSystemFontScale(val enabled: Boolean) : SettingsIntent
}
