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

private val LightChartColors = InkToneChartColors(
    visual = Color(0xFF2E7D32),
    tts = Color(0xFF8E24AA),
    positive = Color(0xFF2E7D32),
)

private val DarkChartColors = InkToneChartColors(
    visual = Color(0xFF81C784),
    tts = Color(0xFFCE93D8),
    positive = Color(0xFF81C784),
)

/** Variante choisie sur le thème réellement appliqué, que l'app peut forcer contre celui du système. */
@Composable
fun chartColors(): InkToneChartColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkChartColors else LightChartColors
