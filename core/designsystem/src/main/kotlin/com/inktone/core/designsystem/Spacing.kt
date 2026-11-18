package com.inktone.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Espacements des écrans, sur une grille de 4 dp. Les valeurs de la grille
 * passent par ces jetons, jamais en littéral dans un `padding`, un
 * `spacedBy` ou un `Spacer` : `scripts/check-no-spacing-literal.sh` le
 * vérifie en CI (audit UI P2-1, DS-12/DS-13).
 */
object InkToneSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    // Marge d'écran M3 compacte, celle de la plupart des écrans (DS-13).
    val screenHorizontal = 16.dp
    val cardPadding = 16.dp
    val sectionSpacing = 16.dp
    val itemSpacing = 12.dp
}
