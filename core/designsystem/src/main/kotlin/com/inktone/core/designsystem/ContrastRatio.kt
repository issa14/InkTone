package com.inktone.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Ratio de contraste WCAG 2.1 (Tache 9.1.3) entre deux couleurs —
 * `(L1 + 0.05) / (L2 + 0.05)`, L1 la luminance relative la plus claire.
 * `Color.luminance()` (androidx.compose.ui.graphics) implemente deja la
 * formule de luminance relative sRGB, pas reimplementee ici.
 */
fun calculateContrastRatio(background: Color, foreground: Color): Double {
    val l1 = max(background.luminance(), foreground.luminance()) + 0.05
    val l2 = min(background.luminance(), foreground.luminance()) + 0.05
    return (l1 / l2).toDouble()
}
