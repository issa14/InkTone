package com.inktone.feature.reader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneShapes

/**
 * Tâche 3e.1 — barre pilule flottante affichée à la place du panneau
 * unifié pendant la lecture TTS. Cinq contrôles dans l'ordre de la cible
 * (UX_FLOW_DESIGN.md §Lecture — couche TTS) : chapitre précédent · phrase
 * précédente · lecture/pause · phrase suivante · chapitre suivant.
 *
 * Les contrôles de chapitre sont désactivés (`enabled = false`), jamais
 * masqués, aux extrémités du livre — une barre dont le nombre d'éléments
 * change sous le doigt est déroutante.
 *
 * Tâche 3e.3 — [isAudioActive] pilote l'onde sonore (distincte de l'icône
 * Play/Pause, voir [TtsSoundWave]) ; un balayage vers le bas déclenche
 * [onSwipeDown], redéfini en « mettre en pause » (pas un arrêt réel —
 * décision consignée dans UX_FLOW_DESIGN.md, voir le KDoc de
 * [TtsPillBarCollapsed] pour le contexte).
 */
@Composable
fun TtsPillBar(
    isPlaying: Boolean,
    isAudioActive: Boolean,
    reduceMotion: Boolean,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onPreviousSentence: () -> Unit,
    onPlayPause: () -> Unit,
    onNextSentence: () -> Unit,
    onNextChapter: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }

    Surface(
        modifier = modifier
            .testTag("TtsPillBar")
            .padding(horizontal = 32.dp)
            .pointerInput(onSwipeDown) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        accumulatedDrag += dragAmount
                        if (accumulatedDrag > swipeThresholdPx) {
                            change.consume()
                            onSwipeDown()
                        }
                    },
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = CircleShape,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillAction(
                icon = AppSymbol.ChapterPrevious,
                contentDescription = "Chapitre précédent",
                enabled = hasPreviousChapter,
                tint = accentColor,
                onClick = onPreviousChapter,
            )
            PillAction(
                icon = AppSymbol.SentencePrevious,
                contentDescription = "Phrase précédente",
                enabled = true,
                tint = accentColor,
                onClick = onPreviousSentence,
            )
            Box(Modifier.padding(horizontal = 4.dp)) {
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor),
                    shape = InkToneShapes.large,
                ) {
                    AppIcon(
                        if (isPlaying) AppSymbol.Pause else AppSymbol.Play,
                        contentDescription = if (isPlaying) "Pause" else "Lire",
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
            PillAction(
                icon = AppSymbol.SentenceNext,
                contentDescription = "Phrase suivante",
                enabled = true,
                tint = accentColor,
                onClick = onNextSentence,
            )
            PillAction(
                icon = AppSymbol.ChapterNext,
                contentDescription = "Chapitre suivant",
                enabled = hasNextChapter,
                tint = accentColor,
                onClick = onNextChapter,
            )
            Box(Modifier.padding(start = 4.dp), contentAlignment = Alignment.Center) {
                TtsSoundWave(isActive = isAudioActive, reduceMotion = reduceMotion, tint = accentColor)
            }
        }
    }
}

/**
 * Tâche 3e.2 — état replié de [TtsPillBar] après 4 s d'inactivité (délai
 * porté par `ImmersiveReaderChrome`, voir `ReaderScreen.onAutoHide`, pas
 * un second minuteur). Même signature visuelle que le Play central de la
 * barre déployée — un tap la redéploie, la lecture et le surlignage
 * mot-à-mot ne sont jamais interrompus par le repli.
 *
 * Tâche 3e.3 — **balayage redéfini en pause, pas un arrêt réel.** La
 * cible d'origine (UX_FLOW_DESIGN.md) prévoyait un « stop immédiat » au
 * balayage sur ce FAB ; `ReaderViewModel.pausePlayback()` est le seul
 * comportement disponible aujourd'hui (pas de reprise ni de libération
 * distincte d'une pause, voir le KDoc historique de `ReaderTtsPanel` sur
 * le retrait du bouton Stop en 3d). Décision actée pour ce lot plutôt que
 * de construire un vrai Stop (migration vers `AudioPlaybackService`/
 * Media3, hors périmètre présentation) : le balayage déclenche
 * [onSwipeDown] → `ReaderIntent.Pause`, la cible est corrigée en
 * conséquence (tâche 3e.5).
 */
@Composable
fun TtsPillBarCollapsed(
    isAudioActive: Boolean,
    reduceMotion: Boolean,
    onExpand: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }

    Box(modifier = modifier.testTag("TtsPillBarCollapsed")) {
        FilledIconButton(
            onClick = onExpand,
            modifier = Modifier
                .size(56.dp)
                .pointerInput(onSwipeDown) {
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            accumulatedDrag += dragAmount
                            if (accumulatedDrag > swipeThresholdPx) {
                                change.consume()
                                onSwipeDown()
                            }
                        },
                    )
                },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor),
            shape = InkToneShapes.large,
        ) {
            AppIcon(
                AppSymbol.Speaking,
                contentDescription = "Afficher les contrôles de lecture",
                tint = MaterialTheme.colorScheme.surface,
            )
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            TtsSoundWave(
                isActive = isAudioActive,
                reduceMotion = reduceMotion,
                tint = MaterialTheme.colorScheme.surface,
                barCount = 2,
            )
        }
    }
}

/**
 * Tâche 3e.3 — indicateur d'onde sonore. Reflète [isActive]
 * (`ReaderUiState.isAudioActive`, distinct de l'état Play/Pause du
 * bouton), jamais une animation permanente : à l'arrêt ou pendant un
 * blanc de synthèse, aucune transition infinie n'est même composée, les
 * barres restent figées à leur hauteur basse. Purement décoratif — pas
 * de `contentDescription`, l'état Lire/Pause du bouton central porte
 * déjà l'information pour TalkBack (même principe que le retrait des
 * annonces redondantes en B.7).
 */
@Composable
private fun TtsSoundWave(
    isActive: Boolean,
    reduceMotion: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
) {
    val fractions: List<Float> = if (isActive && !reduceMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "TtsSoundWave")
        List(barCount) { index ->
            val animated by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, delayMillis = index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "TtsSoundWaveBar$index",
            )
            animated
        }
    } else {
        // Figé : actif + reduceMotion → hauteur haute lisible sans
        // mouvement ; inactif (pause ou blanc de synthèse) → hauteur basse.
        List(barCount) { if (isActive) 1f else 0.25f }
    }

    Row(
        modifier = modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        fractions.forEach { fraction ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((4 + fraction * 8).dp)
                    .background(
                        tint.copy(alpha = if (isActive) 0.9f else 0.35f),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PillAction(
    icon: AppSymbol,
    contentDescription: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint.copy(alpha = 0.7f),
            disabledContentColor = tint.copy(alpha = 0.25f),
        ),
    ) {
        AppIcon(icon, contentDescription = contentDescription)
    }
}
