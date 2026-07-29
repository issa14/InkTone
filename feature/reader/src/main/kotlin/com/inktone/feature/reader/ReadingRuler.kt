package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tache 9bis.3.6 — reglette de lecture : bande semi-transparente qui suit
 * la ligne en cours, aide documentee pour plusieurs conditions visuelles
 * et la dyslexie, absente du legacy.
 *
 * TODO(pas encore appele depuis ReaderScreen, Tache 9bis.5) : le reglage
 * on/off doit vivre dans `UserPreferences` (Blueprint, migration Room +
 * test dans le meme commit, K4) et etre expose par `SettingsScreen` -
 * hors perimetre de cette seule tache Reader. `currentLineY` devra venir
 * du `Modifier.onGloballyPositioned` de la phrase en cours de lecture TTS
 * dans `ReaderScreen`, non plus branche pour l'instant.
 */
@Composable
fun ReadingRuler(currentLineY: Dp, enabled: Boolean) {
    if (!enabled) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = currentLineY)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    )
}
