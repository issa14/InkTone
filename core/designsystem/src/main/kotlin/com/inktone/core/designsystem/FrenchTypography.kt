package com.inktone.core.designsystem

import java.util.Locale

/**
 * Espace fine insécable (U+202F) : règle typographique française avant `%`
 * et les unités. Insécable, elle empêche aussi « 42 » et « % » de se
 * retrouver sur deux lignes.
 */
const val NARROW_NBSP: Char = '\u202F'

/** Pourcentage entier à la française : « 42 % ». */
fun formatPercent(value: Int): String = "$value$NARROW_NBSP%"

/** Durée à la française : « 45 min », « 1 h 05 ». */
fun formatDurationMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours${NARROW_NBSP}h${NARROW_NBSP}${minutes.toString().padStart(2, '0')}"
    } else {
        "$minutes${NARROW_NBSP}min"
    }
}

/**
 * Nombre décimal à la française (« 1,59 »), quelle que soit la langue du
 * système : les libellés de l'app sont en français, un séparateur « . »
 * y détonne sur un appareil configuré en anglais.
 */
fun formatDecimal(value: Double, fractionDigits: Int): String =
    String.format(Locale.FRANCE, "%.${fractionDigits}f", value)

/** Taille de fichier à la française (« 1,59 Go »), en unités binaires. */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes.coerceAtLeast(0)}${NARROW_NBSP}o"
    bytes < 1024 * 1024 -> "${formatDecimal(bytes / 1024.0, 0)}${NARROW_NBSP}Ko"
    bytes < 1024 * 1024 * 1024 -> "${formatDecimal(bytes / (1024.0 * 1024.0), 1)}${NARROW_NBSP}Mo"
    else -> "${formatDecimal(bytes / (1024.0 * 1024.0 * 1024.0), 2)}${NARROW_NBSP}Go"
}
