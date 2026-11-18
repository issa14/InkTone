package com.inktone.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Teinte d'une annotation hors du texte (pastille, icône d'une liste) :
 * variante saturée des préréglages pastel, lisible sur une carte claire.
 * Le surlignage dans le texte garde la teinte pastel elle-même. Clé : ARGB
 * de la couleur d'annotation (ce module ne dépend pas du domaine) ; une
 * couleur personnalisée est rendue telle quelle (audit UI P2-3, DS-5).
 */
fun annotationMarkerColor(argb: Int): Color = MarkerVariants[argb] ?: Color(argb)

private val MarkerVariants = mapOf(
    0xFFFFF59D.toInt() to Color(0xFFFBC02D),
    0xFFA5D6A7.toInt() to Color(0xFF66BB6A),
    0xFF90CAF9.toInt() to Color(0xFF4FC3F7),
    0xFFF48FB1.toInt() to Color(0xFFF06292),
    0xFFFFCC80.toInt() to Color(0xFFFFA726),
)
