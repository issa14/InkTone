package com.inktone.core.designsystem

/**
 * Espace fine insécable (U+202F) : règle typographique française avant `%`
 * et les unités. Insécable, elle empêche aussi « 42 » et « % » de se
 * retrouver sur deux lignes.
 */
const val NARROW_NBSP: Char = ' '

/** Pourcentage entier à la française : « 42 % ». */
fun formatPercent(value: Int): String = "$value$NARROW_NBSP%"
