package com.inktone.core.designsystem

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
