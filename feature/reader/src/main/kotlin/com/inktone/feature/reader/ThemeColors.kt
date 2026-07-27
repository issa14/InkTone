package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import com.inktone.domain.model.ReadingTheme

/**
 * Extrait de ReaderScreen (Tâche 4.7) pour rester testable en JVM pur —
 * un Composable privé ne peut pas être appelé depuis un test JUnit
 * classique sans moteur de rendu Compose.
 */
object ThemeColors {
    fun background(theme: ReadingTheme): Color = when (theme) {
        ReadingTheme.LIGHT, ReadingTheme.SYSTEM -> Color.White
        ReadingTheme.DARK -> Color.Black
        ReadingTheme.SEPIA -> Color(0xFFF4ECD8)
    }

    fun text(theme: ReadingTheme): Color = when (theme) {
        ReadingTheme.LIGHT, ReadingTheme.SYSTEM, ReadingTheme.SEPIA -> Color.Black
        ReadingTheme.DARK -> Color.White
    }
}
