package com.inktone.domain.model

/**
 * Préférences globales — une seule instance par application. Toute
 * surcharge par publication vit dans [ReadingOverrides] et prime sur ces
 * valeurs (Blueprint §3.3).
 */
data class UserPreferences(
    val theme: ReadingTheme = ReadingTheme.SYSTEM,
    val fontSize: Int = 18,
    val defaultTtsEngine: TtsEngineId = TtsEngineId.SHERPA_ONNX,
    val crashReportingEnabled: Boolean = false,
    val language: String = "fr",
    val fontFamily: FontFamily = FontFamily.DEFAULT,
    val reduceMotion: Boolean = false,
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
    }
}

enum class FontFamily { DEFAULT, OPEN_DYSLEXIC, SERIF, SANS_SERIF }
