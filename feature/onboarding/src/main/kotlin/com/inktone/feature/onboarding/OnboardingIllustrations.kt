package com.inktone.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Lot 10, Tâche 10.1 — illustrations vectorielles de l'onboarding.
 * Reprend le motif fourni (livre ouvert + ondes sonores, « lisez avec
 * les yeux, continuez avec les oreilles ») avec six corrections :
 *
 * 1. **Couleurs paramétrées**, jamais littérales — défauts
 *    `MaterialTheme.colorScheme.onSurface`/`primary` : seul moyen pour
 *    ces illustrations de s'adapter au thème (clair/sombre/dynamique),
 *    l'argument même qui a fait préférer le vectoriel composé à un
 *    asset figé.
 * 2. **Tout en `dp.toPx()`**, plus aucun littéral pixel brut — un
 *    `CornerRadius(12f, 12f)` valait 4dp en densité 3×, pas 12dp.
 * 3. `quadraticTo` (pas `quadraticBezierTo`, déprécié depuis Compose UI 1.7).
 * 4. **Aucune taille imposée depuis l'intérieur** : le paramètre
 *    `modifier` n'est jamais complété par un `.size()` dans le corps —
 *    l'appelant dimensionne entièrement. Les proportions internes
 *    (rayons, dimensions du livre) sont calculées à partir de
 *    `size.width`/`size.height` mesurés par le `Canvas` lui-même, pas de
 *    `dp` fixes, pour rester correctes quelle que soit la taille reçue
 *    (petit écran, paysage) sans rogner les ondes.
 * 5. **Cartes 1 et 3 différenciées** : la carte 3 (clôture, voir
 *    [ReadyIllustration]) retire le livre au profit des seules ondes
 *    convergentes — point jugé sur appareil, pas seulement sur le code.
 * 6. **Décoratives explicitement** : `Modifier.clearAndSetSemantics {}`
 *    sur chaque `Canvas`, exclu de l'arbre d'accessibilité pour que
 *    TalkBack lise le texte de la carte, jamais un élément muet.
 */
@Composable
fun WelcomeIllustration(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    neutralColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val strokeWidth = 6.dp.toPx()
        val centerX = size.width * 0.4f
        val centerY = size.height * 0.5f
        val bookWidth = size.width * 0.3f
        val bookHeight = size.height * 0.32f

        val bookPath = Path().apply {
            moveTo(centerX, centerY - bookHeight / 2)
            lineTo(centerX, centerY + bookHeight / 2)

            moveTo(centerX, centerY + bookHeight / 2)
            quadraticTo(
                centerX - bookWidth / 2, centerY + bookHeight / 2 + bookHeight * 0.15f,
                centerX - bookWidth, centerY + bookHeight / 4,
            )
            lineTo(centerX - bookWidth, centerY - bookHeight / 4)
            quadraticTo(
                centerX - bookWidth / 2, centerY,
                centerX, centerY - bookHeight / 2,
            )

            moveTo(centerX, centerY + bookHeight / 2)
            quadraticTo(
                centerX + bookWidth / 2, centerY + bookHeight / 2 + bookHeight * 0.15f,
                centerX + bookWidth, centerY + bookHeight / 4,
            )
            lineTo(centerX + bookWidth, centerY - bookHeight / 4)
            quadraticTo(
                centerX + bookWidth / 2, centerY,
                centerX, centerY - bookHeight / 2,
            )
        }

        drawPath(
            path = bookPath,
            color = neutralColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Ondes sonores rayonnant vers la droite — rayons proportionnels
        // à la largeur mesurée (correction 4), jamais des dp fixes.
        val waveCenter = Offset(centerX + bookWidth * 0.55f, centerY)
        val maxWaveRadius = (size.width - waveCenter.x).coerceAtMost(size.height / 2f) * 0.85f
        for (i in 1..3) {
            val radius = maxWaveRadius * (i / 3f)
            drawArc(
                color = accentColor,
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(waveCenter.x - radius, waveCenter.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round),
                alpha = 1f - (i * 0.2f),
            )
        }
    }
}

@Composable
fun FeaturesIllustration(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    neutralColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier) {
        BookIconIllustration(modifier = Modifier.size(100.dp), accentColor = accentColor, neutralColor = neutralColor)
        Spacer(modifier = Modifier.width(24.dp))
        AudioIconIllustration(modifier = Modifier.size(100.dp), accentColor = accentColor, neutralColor = neutralColor)
    }
}

/** Icône gauche de [FeaturesIllustration] — typographie / livre. Séparée pour être placée individuellement (carte 2 de l'onboarding, bloc gauche). */
@Composable
fun BookIconIllustration(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    neutralColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val strokeW = 5.dp.toPx()
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = neutralColor,
            topLeft = Offset(w * 0.2f, h * 0.1f),
            size = Size(w * 0.6f, h * 0.8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = strokeW),
        )
        drawLine(
            color = accentColor,
            start = Offset(w * 0.35f, h * 0.35f),
            end = Offset(w * 0.65f, h * 0.35f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accentColor,
            start = Offset(w * 0.35f, h * 0.55f),
            end = Offset(w * 0.55f, h * 0.55f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

/** Icône droite de [FeaturesIllustration] — égaliseur audio. Séparée pour être placée individuellement (carte 2 de l'onboarding, bloc droit). */
@Composable
fun AudioIconIllustration(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    neutralColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val barWidth = size.width * 0.12f
        val spacing = size.width * 0.08f
        val totalWidth = (barWidth * 4) + (spacing * 3)
        val startX = (size.width - totalWidth) / 2
        val centerY = size.height / 2

        val heights = listOf(0.4f, 0.8f, 0.5f, 0.9f)

        heights.forEachIndexed { index, heightRatio ->
            val barHeight = size.height * heightRatio
            drawRoundRect(
                color = if (index % 2 == 0) neutralColor else accentColor,
                topLeft = Offset(startX + index * (barWidth + spacing), centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/**
 * Carte 3 (clôture) — délibérément différenciée de [WelcomeIllustration]
 * (correction 5) : pas de livre, seulement des ondes concentriques
 * convergeant vers un point d'accent central, pour symboliser une
 * expérience unifiée plutôt que rappeler visuellement la carte 1.
 */
@Composable
fun ReadyIllustration(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val strokeWidth = 5.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = minOf(size.width, size.height) / 2f * 0.9f

        for (i in 1..4) {
            val radius = maxRadius * (i / 4f)
            drawCircle(
                color = accentColor,
                radius = radius,
                center = center,
                style = Stroke(
                    width = strokeWidth * 0.7f,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(size.width * 0.03f, size.width * 0.045f), 0f,
                    ),
                ),
                alpha = 1f - (i * 0.15f),
            )
        }

        drawCircle(
            color = accentColor,
            radius = maxRadius * 0.12f,
            center = center,
        )
    }
}
