package com.inktone.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bouton Lecture/Pause de la narration, partout où il apparaît (audit UI
 * DS-19) : mêmes libellés TalkBack, même retour haptique. La zone tactile
 * reste de 48 dp même quand le disque est plus petit.
 */
@Composable
fun NarrationPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = InkToneShapes.large,
) {
    val haptics = rememberAppHaptics()
    FilledIconButton(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = contentColor),
        shape = shape,
    ) {
        AppIcon(
            if (isPlaying) AppSymbol.Pause else AppSymbol.Play,
            contentDescription = narrationPlayLabel(isPlaying),
            tint = contentColor,
        )
    }
}

/** Libellé d'action du bouton : ce que fait l'appui, pas l'état courant. */
fun narrationPlayLabel(isPlaying: Boolean): String = if (isPlaying) "Mettre en pause" else "Écouter"
