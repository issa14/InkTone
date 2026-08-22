package com.inktone.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Lot 10, Tâche 10.6 — dette du lot 2a.6 : « étagère avec emplacements de
 * livres en pointillés », jamais produite (`AppSymbol.Reading` servait de
 * repli, signalé comme non conforme). Même procédé que
 * `feature/onboarding/OnboardingIllustrations.kt` (couleur paramétrée,
 * tout en `dp.toPx()`/proportionnel à `size`, décorative explicitement) —
 * dupliqué plutôt que partagé : un module feature ne dépend jamais d'un
 * autre module feature (Blueprint §12.3).
 */
@Composable
fun EmptyLibraryShelfIllustration(
    modifier: Modifier = Modifier,
    neutralColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val strokeWidth = 3.dp.toPx()
        val shelfY = size.height * 0.75f
        val slotCount = 3
        val slotWidth = size.width * 0.22f
        val slotHeight = size.height * 0.55f
        val spacing = (size.width - slotWidth * slotCount) / (slotCount + 1)

        // Étagère — trait plein.
        drawLine(
            color = neutralColor,
            start = Offset(size.width * 0.05f, shelfY),
            end = Offset(size.width * 0.95f, shelfY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // Emplacements de livres — pointillés, suggèrent l'absence de contenu plutôt qu'un vide complet.
        for (i in 0 until slotCount) {
            val left = spacing + i * (slotWidth + spacing)
            drawRoundRect(
                color = neutralColor,
                topLeft = Offset(left, shelfY - slotHeight),
                size = Size(slotWidth, slotHeight),
                cornerRadius = CornerRadius(slotWidth * 0.08f, slotWidth * 0.08f),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.02f, size.width * 0.02f), 0f),
                ),
                alpha = 0.6f,
            )
        }
    }
}
