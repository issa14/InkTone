package com.inktone.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol

/**
 * Barre du haut du lecteur (Tâche 3b.5) — appartient au **HUD** : soumise
 * à l'auto-masquage d'`ImmersiveReaderChrome` exactement comme
 * `UnifiedControlPanel`, jamais affichée/masquée indépendamment de lui
 * (`ReaderScreen` les gate tous les deux sur `isHudVisible`). Ne pas
 * confondre avec `StatusLineBar` (3b.4), persistante celle-là.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderTopBar(
    title: String?,
    author: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Contraste garanti avec la page de lecture par défaut (voir
    // ThemeColors.barSurface/barContent) — les défauts au thème chrome
    // ne servent qu'aux previews/tests qui n'ont pas de ReadingTheme.
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surfaceColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Edge-to-edge : l'inset est posé sur le CONTENU, jamais sur la
                // `Surface` ci-dessus. Sur la Surface, il aurait décalé la barre
                // entière vers le bas et laissé voir le texte de la page
                // au-dessus d'elle ; ici, le fond monte jusqu'au bord haut de
                // l'écran et seuls le titre et la flèche descendent sous la
                // découpe de caméra. Même motif que `StatusLineBar` en bas.
                //
                // `statusBarsPadding()` ne conviendrait pas : le mode immersif
                // MASQUE la barre, son inset tombe donc à zéro et le titre
                // remonterait sous la caméra, pour être recouvert par l'horloge
                // dès qu'un balayage rappelle les barres transitoires. La
                // variante `IgnoringVisibility` réserve la place que la barre
                // OCCUPERAIT, masquée ou non.
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                AppIcon(AppSymbol.Back, contentDescription = "Retour", tint = contentColor)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    text = title ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
