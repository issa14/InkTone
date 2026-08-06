package com.inktone.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcons
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
 */
@Composable
fun TtsPillBar(
    isPlaying: Boolean,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onPreviousSentence: () -> Unit,
    onPlayPause: () -> Unit,
    onNextSentence: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.padding(horizontal = 32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = CircleShape,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillAction(
                icon = AppIcons.ChapterPrevious,
                contentDescription = "Chapitre précédent",
                enabled = hasPreviousChapter,
                tint = accentColor,
                onClick = onPreviousChapter,
            )
            PillAction(
                icon = AppIcons.SentencePrevious,
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
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lire",
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
            PillAction(
                icon = AppIcons.SentenceNext,
                contentDescription = "Phrase suivante",
                enabled = true,
                tint = accentColor,
                onClick = onNextSentence,
            )
            PillAction(
                icon = AppIcons.ChapterNext,
                contentDescription = "Chapitre suivant",
                enabled = hasNextChapter,
                tint = accentColor,
                onClick = onNextChapter,
            )
        }
    }
}

/**
 * Tâche 3e.2 — état replié de [TtsPillBar] après 4 s d'inactivité (délai
 * porté par `ImmersiveReaderChrome`, voir `ReaderScreen.onAutoHide`, pas
 * un second minuteur). Même signature visuelle que le Play central de la
 * barre déployée — un tap la redéploie, la lecture et le surlignage
 * mot-à-mot ne sont jamais interrompus par le repli.
 */
@Composable
fun TtsPillBarCollapsed(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    FilledIconButton(
        onClick = onExpand,
        modifier = modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor),
        shape = InkToneShapes.large,
    ) {
        Icon(
            AppIcons.Speaking,
            contentDescription = "Afficher les contrôles de lecture",
            tint = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun PillAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color,
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
        Icon(icon, contentDescription = contentDescription)
    }
}
