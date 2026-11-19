package com.inktone.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Couleurs des graphiques de statistiques : lecture visuelle, écoute, et
 * variation positive. Hors de l'accent Material You, qui ne distinguerait
 * pas les deux modes (audit UI P2-3, DS-4).
 */
@Immutable
data class InkToneChartColors(val visual: Color, val tts: Color, val positive: Color)

// Séries de la palette violette : chacune ≥ 3:1 sur les surfaces (élément non
// textuel) et ≥ 2:1 entre elles, pour rester distinctes une fois empilées.
// `positive` reste vert : c'est un sens (hausse), pas une série.
internal val LightChartColors = InkToneChartColors(
    visual = Color(0xFF3F2C9E),
    tts = Color(0xFFB8669A),
    positive = Color(0xFF2E7D32),
)

internal val DarkChartColors = InkToneChartColors(
    visual = Color(0xFF8C7BD9),
    tts = Color(0xFFF2C6DE),
    positive = Color(0xFF81C784),
)

/** Variante choisie sur le thème réellement appliqué, que l'app peut forcer contre celui du système. */
@Composable
fun chartColors(): InkToneChartColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkChartColors else LightChartColors
