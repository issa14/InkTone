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
 * Branchee depuis `ReaderScreen` : `enabled` reflete
 * `UserPreferences.readingRulerEnabled` (observe en continu par
 * `ReaderViewModel`, reglable depuis `SettingsScreen`) ; `currentLineY`
 * vient du `Modifier.onGloballyPositioned` de la seule `SentenceText`
 * dont `isCurrentlyPlaying` est vrai, position relative au `Box` qui
 * superpose cette reglette et le texte du chapitre.
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
