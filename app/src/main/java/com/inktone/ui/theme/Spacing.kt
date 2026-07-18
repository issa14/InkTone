package com.inktone.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Système d'espacement cohérent — grille 8dp (incréments de 4dp).
 *
 * Usage : InkToneSpacing.md au lieu de valeurs littérales.
 */
object InkToneSpacing {
    val xs  = 4.dp    // Micro (icônes, badges)
    val sm  = 8.dp    // Small (padding interne)
    val md  = 12.dp   // Medium (espacement standard)
    val lg  = 16.dp   // Large (padding conteneurs)
    val xl  = 24.dp   // Extra (sections)
    val xxl = 32.dp   // 2XL (grands séparateurs)

    // Pratiques pour les layouts horizontaux
    val screenHorizontal = 20.dp   // Padding latéral écrans
    val cardPadding      = 16.dp   // Padding intérieur cartes
    val sectionSpacing   = 16.dp   // Espacement entre sections
    val itemSpacing      = 12.dp   // Espacement entre items
}
