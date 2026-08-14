package com.inktone.feature.reader.transition

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Le duo « texte statique + cercle dynamique » décrit dans l'UX : le
 * périmètre du cercle se remplit proportionnellement au tirage, puis se
 * transforme en spinner (rotation infinie) pendant le chargement du
 * nouveau chapitre.
 */
@Composable
fun ChapterTransitionIndicator(
    direction: ChapterTransitionDirection?,
    fraction: Float,
    isLoading: Boolean,
    reduceMotion: Boolean,
    contentColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val text = when (direction) {
        ChapterTransitionDirection.PREVIOUS -> "Chapitre précédent"
        ChapterTransitionDirection.NEXT -> "Chapitre suivant"
        null -> return
    }

    val alpha = if (isLoading) 1f else fraction.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .alpha(alpha)
            .background(surfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleProgress(
            fraction = fraction,
            isLoading = isLoading,
            reduceMotion = reduceMotion,
            color = contentColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun CircleProgress(
    fraction: Float,
    isLoading: Boolean,
    reduceMotion: Boolean,
    color: Color,
) {
    if (isLoading && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "chapterLoad")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "chapterLoadRotation",
        )
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color.copy(alpha = 0.3f), 0f, 360f, false, style = stroke)
            drawArc(color, rotation - 90f, 270f, false, style = stroke)
        }
    } else {
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color.copy(alpha = 0.3f), 0f, 360f, false, style = stroke)
            // `isLoading && reduceMotion` : cercle plein statique (pas de rotation).
            val sweep = if (isLoading) 360f else 360f * fraction.coerceIn(0f, 1f)
            drawArc(color, -90f, sweep, false, style = stroke)
        }
    }
}
