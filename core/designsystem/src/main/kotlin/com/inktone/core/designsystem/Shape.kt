package com.inktone.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formes Material 3 du design system InkTone (Tâche 9bis.1.1, porté du
 * legacy tel quel — cohérent avec Material 3, medium = 12dp par défaut).
 */
val InkToneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Barres du Lecteur, arrondies du seul côté de la page : la barre du haut
 * en bas, le panneau de commandes en haut. Les autres formes passent par
 * `MaterialTheme.shapes` (audit UI P2-2).
 */
val HudTopShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
val HudBottomShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
