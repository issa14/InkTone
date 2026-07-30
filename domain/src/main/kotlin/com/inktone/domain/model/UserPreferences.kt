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
    // Tache 9bis.1.2 — s'applique uniquement au chrome de l'app
    // (InkToneTheme), jamais aux ReadingTheme de lecture.
    val dynamicColorEnabled: Boolean = true,
    // Tache 9bis.3.6 — reglage seul pour l'instant, ReaderScreen ne
    // consomme pas encore ce champ (voir TODO sur ReadingRuler.kt).
    val readingRulerEnabled: Boolean = false,
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
    }
}

enum class FontFamily { DEFAULT, OPEN_DYSLEXIC, SERIF, SANS_SERIF }
