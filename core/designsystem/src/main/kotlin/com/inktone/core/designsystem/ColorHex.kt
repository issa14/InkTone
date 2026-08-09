package com.inktone.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Lot 9 — les couleurs de [com.inktone.domain.model.ReadingTheme] sont
 * stockées en hex `#RRGGBB` côté domaine (qui ne dépend jamais de
 * Compose, Blueprint §12.4). Conversion côté UI uniquement, ici plutôt
 * que dupliquée dans chaque module consommateur (`feature/reader`,
 * `feature/settings`).
 */
fun String.toColor(): Color {
    val hex = removePrefix("#")
    require(hex.length == 6) { "couleur invalide : $this (attendu #RRGGBB)" }
    return Color(0xFF000000.toInt() or hex.toInt(16))
}

fun Color.toHex(): String {
    val argb = toArgb()
    return "#%06X".format(argb and 0x00FFFFFF)
}
