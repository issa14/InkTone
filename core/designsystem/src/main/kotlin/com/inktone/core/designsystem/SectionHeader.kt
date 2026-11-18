package com.inktone.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Titre de section, le même sur tous les écrans (audit UI P1-18) : quatre
 * styles différents coexistaient. Annoncé comme titre par TalkBack, qui
 * permet d'y sauter directement.
 */
@Composable
fun InkToneSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    // 16 dp : aligné sur le contenu d'une carte. 0 quand la section porte des
    // éléments posés à même la marge d'écran (grille de vignettes).
    startPadding: Dp = 16.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = startPadding, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}
