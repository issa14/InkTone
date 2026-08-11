package com.inktone.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol

/**
 * Barre du haut du lecteur (Tâche 3b.5) — appartient au **HUD** : soumise
 * à l'auto-masquage d'`ImmersiveReaderChrome` exactement comme
 * `UnifiedControlPanel`, jamais affichée/masquée indépendamment de lui
 * (`ReaderScreen` les gate tous les deux sur `isHudVisible`). Ne pas
 * confondre avec `StatusLineBar` (3b.4), persistante celle-là.
 */
@Composable
fun ReaderTopBar(
    title: String?,
    author: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                AppIcon(AppSymbol.Back,  contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    text = title ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
