package com.inktone.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneShapes

/**
 * Tâche 3b.6 — panneau de contrôle unifié, trois rangées :
 * 1. Barre de progression du livre (déplacée depuis le haut de l'écran —
 *    elle cesse d'être persistante, c'est `StatusLineBar` (3b.4) qui
 *    porte désormais l'information permanente).
 * 2. 5 icônes, Play central proéminent : Sommaire · Marque-pages · Play ·
 *    Thème · TT.
 * 3. 5 icônes : Minuteur · Haut-parleur · Mode · Recherche · Luminosité
 *    (3d.3 — dernière icône ajoutée avec son action, jamais avant :
 *    laissée vide depuis le lot 3b faute d'action existante).
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
    onBrightnessClick: () -> Unit = {},
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

            // Bug réel trouvé sur appareil (lot 3b) : Arrangement.SpaceEvenly
            // répartit l'espace ENTRE les bords des enfants, pas par largeur
            // égale — "Marque-pages" (libellé plus long que les autres)
            // élargit sa colonne et décale visuellement tout le reste, Play
            // compris, hors du centre réel de la rangée. Modifier.weight(1f)
            // sur chaque slot force des largeurs égales, jamais un décalage
            // dépendant de la longueur du texte.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Toc, label = "Sommaire", tint = iconTint, onClick = onTocClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Bookmark, label = "Marque-pages", tint = iconTint, onClick = onBookmarksClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                            if (isPlaying) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (isPlaying) "Pause" else "Lire",
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Theme, label = "Thème", tint = iconTint, onClick = onThemeCycle)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Appearance, label = "TT", tint = iconTint, onClick = onAaClick)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(
                        icon = AppIcons.SleepTimer,
                        label = "Minuteur",
                        tint = if (sleepTimerActive) accentColor else iconTint,
                        onClick = onSleepTimerClick,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Speaking, label = "Haut-parleur", tint = iconTint, onClick = onTtsClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.ReadingModePaged, label = "Mode", tint = iconTint, onClick = onReadingModeClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Search, label = "Recherche", tint = iconTint, onClick = onSearchClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(icon = AppIcons.Brightness, label = "Luminosité", tint = iconTint, onClick = onBrightnessClick)
                }
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
