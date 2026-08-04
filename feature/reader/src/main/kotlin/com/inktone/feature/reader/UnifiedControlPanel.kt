package com.inktone.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.InkToneShapes

/**
 * Tâche 3b.6 — panneau de contrôle unifié, trois rangées :
 * 1. Barre de progression du livre (déplacée depuis le haut de l'écran —
 *    elle cesse d'être persistante, c'est `StatusLineBar` (3b.4) qui
 *    porte désormais l'information permanente).
 * 2. 5 icônes, Play central proéminent : Sommaire · Marque-pages · Play ·
 *    Thème · TT.
 * 3. 4 icônes : Minuteur · Haut-parleur · Mode · Recherche.
 *
 * Navigation par chapitre retirée de ce panneau (chevrons précédent/
 * suivant) — la cible les place dans la barre de contrôle TTS (lot 3d) ;
 * en attendant, elle reste accessible via le Sommaire (`onTocClick`,
 * inchangé). `horizontalScroll` retiré avec elles : 7 actions sur une
 * rangée ne tenaient pas sur un téléphone standard, mais 5 puis 4
 * n'en ont plus besoin.
 */
@Composable
fun UnifiedControlPanel(
    isPlaying: Boolean,
    sleepTimerActive: Boolean,
    bookProgression: Float,
    onPlayPause: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onTocClick: () -> Unit,
    onThemeCycle: () -> Unit,
    onAaClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
    onReadingModeClick: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary
    val iconTint = accentColor.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookProgressBar(progression = bookProgression)

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryAction(icon = AppIcons.Toc, label = "Sommaire", tint = iconTint, onClick = onTocClick)
                SecondaryAction(icon = AppIcons.Bookmark, label = "Marque-pages", tint = iconTint, onClick = onBookmarksClick)
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
                SecondaryAction(icon = AppIcons.Theme, label = "Thème", tint = iconTint, onClick = onThemeCycle)
                SecondaryAction(icon = AppIcons.Appearance, label = "TT", tint = iconTint, onClick = onAaClick)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryAction(
                    icon = Icons.Filled.Timer,
                    label = "Minuteur",
                    tint = if (sleepTimerActive) accentColor else iconTint,
                    onClick = onSleepTimerClick,
                )
                SecondaryAction(icon = AppIcons.Speaking, label = "Haut-parleur", tint = iconTint, onClick = onTtsClick)
                SecondaryAction(icon = AppIcons.ReadingModePaged, label = "Mode", tint = iconTint, onClick = onReadingModeClick)
                SecondaryAction(icon = AppIcons.Search, label = "Recherche", tint = iconTint, onClick = onSearchClick)
            }
        }
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}
