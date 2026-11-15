package com.inktone.feature.reader

/**
 * Vrai si [backgroundColorHex] (`#RRGGBB` ou `#AARRGGBB`) est un fond sombre :
 * luminance perçue sous 0,3. Un test d'égalité à `#000000` ne reconnaissait que
 * le noir absolu, pas un thème sombre personnalisé ni Obsidienne (audit UX B6).
 * Une couleur illisible n'est pas tenue pour sombre.
 */
internal fun isDarkBackground(backgroundColorHex: String): Boolean {
    val digits = backgroundColorHex.removePrefix("#")
    val rgb = when (digits.length) {
        6 -> digits
        8 -> digits.substring(2)
        else -> return false
    }
    val value = rgb.toIntOrNull(16) ?: return false
    val red = (value shr 16 and 0xFF) / 255.0
    val green = (value shr 8 and 0xFF) / 255.0
    val blue = (value and 0xFF) / 255.0
    return 0.299 * red + 0.587 * green + 0.114 * blue < 0.3
}
