package com.readflow.ui.theme

import androidx.compose.ui.graphics.Color

// ── Palette prototype ReadFlow Pro (inspirée RF.html) ──

// Fond sombre principal
val AppBackground = Color(0xFF0D0E15)
val SurfaceDark = Color(0xFF161722)
val SurfaceRaised = Color(0xFF202232)

// Texte
val TextMain = Color(0xFFE2E4ED)
val TextMuted = Color(0xFF7A7E9D)

// Accents
val AccentBlue = Color(0xFF0091EA)
val AccentTts = Color(0xFFFF79C6)

// Bordures
val BorderDark = Color(0xFF222538)
val BorderSoft = Color(0xFF2E324C)

// Shelf / étagère
val ShelfOverlay = Color(0x12FFFFFF)

// ── Couvertures (gradients prédéfinis) ──
val CoverGradients = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF373B44), Color(0xFF4286F4)),
    listOf(Color(0xFF870000), Color(0xFF190019)),
    listOf(Color(0xFF134E5E), Color(0xFF71B280)),
)

// ── Material 3 ColorScheme ──

// Dark theme (principal)
val DarkBackground = AppBackground
val DarkOnBackground = TextMain
val DarkSurface = SurfaceDark
val DarkOnSurface = TextMain
val DarkSurfaceVariant = SurfaceRaised
val DarkOnSurfaceVariant = TextMuted
val DarkPrimary = AccentBlue
val DarkOnPrimary = Color.White
val DarkPrimaryContainer = AccentBlue.copy(alpha = 0.15f)
val DarkOnPrimaryContainer = AccentBlue
val DarkSecondary = AccentTts
val DarkOnSecondary = Color(0xFF1A1A2E)
val DarkSecondaryContainer = AccentTts.copy(alpha = 0.15f)
val DarkOnSecondaryContainer = AccentTts
val DarkError = Color(0xFFFF6B6B)

// Light theme (nav drawer)
val LightBackground = Color.White
val LightOnBackground = Color(0xFF333333)
val LightSurface = Color(0xFFF5F5F5)
val LightOnSurface = Color(0xFF444444)
val LightPrimary = AccentBlue
val LightOnPrimary = Color.White
