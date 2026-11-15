package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Tache 9bis.3.6 — reglette de lecture : bande semi-transparente qui suit
 * la ligne en cours, aide documentee pour plusieurs conditions visuelles
 * et la dyslexie, absente du legacy.
 *
 * [currentLineY] donne la position de la ligne dans la zone de lecture, ou
 * `null` quand elle n'est pas à l'écran (la bande est alors masquée). C'est une
 * fonction, lue pendant la mise en page seulement : un défilement ou un mot
 * prononcé ne recompose pas l'écran (audit UX A17 : la valeur était la hauteur
 * de la ligne dans son paragraphe, pas dans l'écran).
 */
@Composable
fun ReadingRuler(currentLineY: () -> Dp?, enabled: Boolean) {
    if (!enabled) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (currentLineY() == null) 0f else 1f }
            .offset { IntOffset(0, (currentLineY() ?: 0.dp).roundToPx()) }
            .height(32.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    )
}
