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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
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
    // Indicateur du mode d'affichage courant (lot HUD) : l'icône « Mode »
    // reflète le mode ACTIF (pages vs défilement), contrairement à
    // l'ancienne version qui affichait toujours « pages ».
    readingMode: ReadingMode = ReadingMode.SCROLL,
    // Emplacements vides plutot que retires de la Row (les slots en
    // Modifier.weight(1f) evitent le decalage deja corrige au lot 3b,
    // voir commentaire plus bas), jamais un bouton visible sans effet.
    //
    // ADR-017 volet 2 : le TTS n'est plus lie au format. Seul un PDF
    // entierement scanne (aucune page ne porte de texte) masque encore ces
    // commandes - voir ReaderUiState.supportsTts.
    showTtsControls: Boolean = true,
    // Distinct de showTtsControls depuis l'activation du TTS sur PDF : la
    // bascule SCROLL/PAGED, elle, reste sans objet pour un PDF, nativement
    // pagine (decision actee 16 du plan, Lot 12 tache 12.10).
    showReadingModeToggle: Boolean = true,
    // Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, M6) : PDF et TXT n'ont
    // aucune table des matières (parsers -> tableOfContents vide) ; avant
    // le fix, le bouton Sommaire ouvrait une feuille vide sans message.
    showToc: Boolean = true,
    // Contraste garanti avec la page de lecture par défaut (voir
    // ThemeColors.barSurface/barContent) — les défauts au thème chrome ne
    // servent qu'aux previews/tests qui n'ont pas de ReadingTheme.
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val haptic = LocalHapticFeedback.current
    val iconTint = accentColor.copy(alpha = 0.7f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
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
                    if (showToc) {
                        SecondaryAction(symbol = AppSymbol.Toc, contentDescription = "Sommaire", tint = iconTint, onClick = onTocClick)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(symbol = AppSymbol.Bookmark, contentDescription = "Marque-pages", tint = iconTint, onClick = onBookmarksClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (showTtsControls) {
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
                                tint = surfaceColor,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(symbol = AppSymbol.Theme, contentDescription = "Thème", tint = iconTint, onClick = onThemeCycle)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(symbol = AppSymbol.FormatSize, contentDescription = "Réglages du texte", tint = iconTint, onClick = onAaClick)
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
                    // Toujours visible, meme pour un PDF (decision actee
                    // 16) : ce bouton ouvre aussi le repos oculaire,
                    // independant du TTS - voir SleepTimerPanel.showSleepTimer.
                    // Icône chronomètre (Timer) : l'ancien glyphe « bedtime »
                    // (demi-lune) n'évoquait pas un minuteur.
                    SecondaryAction(
                        symbol = AppSymbol.Timer,
                        contentDescription = "Minuteur",
                        tint = if (sleepTimerActive) accentColor else iconTint,
                        onClick = onSleepTimerClick,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (showTtsControls) {
                        SecondaryAction(symbol = AppSymbol.Speaking, contentDescription = "Haut-parleur", tint = iconTint, onClick = onTtsClick)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (showReadingModeToggle) {
                        SecondaryAction(
                            symbol = if (readingMode == ReadingMode.PAGED) AppSymbol.ReadingModePaged else AppSymbol.ReadingModeScroll,
                            contentDescription = if (readingMode == ReadingMode.PAGED) "Mode pages" else "Mode défilement",
                            tint = iconTint,
                            onClick = onReadingModeClick,
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(symbol = AppSymbol.Search, contentDescription = "Recherche", tint = iconTint, onClick = onSearchClick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SecondaryAction(symbol = AppSymbol.Brightness, contentDescription = "Luminosité", tint = iconTint, onClick = onBrightnessClick)
                }
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    symbol: AppSymbol,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    // Libellés retirés (lot HUD) : l'icône seule porte l'action, le
    // `contentDescription` reste complet pour TalkBack. Padding vertical
    // légèrement augmenté pour conserver une cible tactile ~44dp.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        AppIcon(
            symbol = symbol,
            contentDescription = contentDescription,
            selected = selected,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
