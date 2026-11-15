package com.inktone.domain.model

/**
 * Réglages d'avant le préréglage « Mode sombre », restaurés à sa désactivation
 * au lieu des valeurs par défaut (audit UX A11).
 */
data class DarkModePresetBackup(val theme: String, val appTheme: AppTheme) {

    fun encode(): String = listOf(theme, appTheme.name).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        /** `null` si [encoded] est absent ou illisible : l'appelant retombe sur les valeurs par défaut. */
        fun decode(encoded: String?): DarkModePresetBackup? {
            val parts = encoded?.split(SEPARATOR) ?: return null
            if (parts.size != 2) return null
            val appTheme = runCatching { AppTheme.valueOf(parts[1]) }.getOrNull() ?: return null
            return DarkModePresetBackup(theme = parts[0], appTheme = appTheme)
        }
    }
}

/**
 * Réglages d'avant le préréglage « Accessibilité » (audit UX A11) : un lecteur
 * en thème Sépia et police Serif qui l'essayait puis le coupait se retrouvait
 * en Papier Clair, police système, 18 sp, grille détaillée.
 */
data class AccessibilityPresetBackup(
    val fontSize: Int,
    val theme: String,
    val fontFamily: FontFamily,
    val reduceMotion: Boolean,
    val readingRulerEnabled: Boolean,
    val libraryLayoutMode: String,
) {

    fun encode(): String = listOf(
        fontSize.toString(), theme, fontFamily.name, reduceMotion.toString(),
        readingRulerEnabled.toString(), libraryLayoutMode,
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        /** `null` si [encoded] est absent ou illisible : l'appelant retombe sur les valeurs par défaut. */
        fun decode(encoded: String?): AccessibilityPresetBackup? {
            val parts = encoded?.split(SEPARATOR) ?: return null
            if (parts.size != 6) return null
            return runCatching {
                AccessibilityPresetBackup(
                    fontSize = parts[0].toInt(),
                    theme = parts[1],
                    fontFamily = FontFamily.valueOf(parts[2]),
                    reduceMotion = parts[3].toBooleanStrict(),
                    readingRulerEnabled = parts[4].toBooleanStrict(),
                    libraryLayoutMode = parts[5],
                )
            }.getOrNull()
        }
    }
}
