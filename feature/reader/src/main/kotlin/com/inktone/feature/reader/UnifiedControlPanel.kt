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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Tache 9bis.3.3 — panneau de controle unifie, porte la structure du
 * legacy (`ReaderBottomControls.UnifiedControlPanel`) : le legacy avait
 * deja resolu ici un vrai probleme d'UX (icones separees qui tronquaient
 * le titre — note trouvee dans le code), pas de retour en arriere sur
 * cette lecon. Sous-ensemble des actions legacy : Voix/Police/Theme
 * (`onTtsSettingsClick`/`onFontToggle`/`onThemeCycle`) n'ont pas
 * d'equivalent `ReaderIntent` aujourd'hui, pas de bouton qui ne ferait
 * rien - a ajouter quand ces reglages existeront (Tache 9bis.5).
 */
@Composable
fun UnifiedControlPanel(
    isPlaying: Boolean,
    sleepTimerActive: Boolean,
    onPlayPause: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onTocClick: () -> Unit,
    onAaClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
    onReadingModeClick: () -> Unit = {},
    hasPreviousChapter: Boolean = true,
    hasNextChapter: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onPreviousChapter, modifier = Modifier.size(44.dp), enabled = hasPreviousChapter) {
                    Icon(Icons.Outlined.SkipPrevious,
                        contentDescription = if (hasPreviousChapter) "Chapitre precedent" else "Pas de chapitre precedent",
                        tint = accentColor.copy(alpha = if (hasPreviousChapter) 0.4f else 0.15f))
                }
                Spacer(Modifier.width(24.dp))
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
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = onNextChapter, modifier = Modifier.size(44.dp), enabled = hasNextChapter) {
                    Icon(Icons.Outlined.SkipNext,
                        contentDescription = if (hasNextChapter) "Chapitre suivant" else "Pas de chapitre suivant",
                        tint = accentColor.copy(alpha = if (hasNextChapter) 0.4f else 0.15f))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SecondaryAction(icon = AppIcons.Appearance, label = "Aa", tint = accentColor.copy(alpha = 0.5f), onClick = onAaClick)
                SecondaryAction(icon = AppIcons.Speaking, label = "Voix", tint = accentColor.copy(alpha = 0.5f), onClick = onTtsClick)
                SecondaryAction(icon = AppIcons.ReadingModePaged, label = "Mode", tint = accentColor.copy(alpha = 0.5f), onClick = onReadingModeClick)
                SecondaryAction(icon = Icons.Filled.Timer, label = "Veille", tint = if (sleepTimerActive) accentColor else accentColor.copy(alpha = 0.5f), onClick = onSleepTimerClick)
                SecondaryAction(icon = AppIcons.Search, label = "Recherche", tint = accentColor.copy(alpha = 0.5f), onClick = onSearchClick)
                SecondaryAction(icon = AppIcons.Bookmark, label = "Signets", tint = accentColor.copy(alpha = 0.5f), onClick = onBookmarksClick)
                SecondaryAction(icon = AppIcons.Toc, label = "Sommaire", tint = accentColor.copy(alpha = 0.5f), onClick = onTocClick)
            }
        }
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}
