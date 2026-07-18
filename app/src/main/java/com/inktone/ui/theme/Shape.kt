package com.inktone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formes Material 3 du design system InkTone.
 *
 * Cohérentes avec Material 3 (medium = 12dp par défaut).
 * Les formes nommées sont prévues pour des composants spécifiques.
 */
val InkToneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Formes des cartes (livres, sections). */
val CardShape = RoundedCornerShape(12.dp)

/** Formes des Bottom Sheets (panneau TTS, réglages lecture). */
val BottomSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** Formes des FAB (bouton d'action flottant). */
val FabShape = RoundedCornerShape(16.dp)

/** Formes des chips (filtres, tags, voix). */
val ChipShape = RoundedCornerShape(8.dp)

/** Formes des dialogues (alertes, sélecteurs). */
val DialogShape = RoundedCornerShape(28.dp)
