package com.inktone.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Formes des champs de saisie. Passer par ces valeurs plutôt que changer
 * `shapes.extraSmall`, que Material 3 applique aussi aux menus, infobulles
 * et snackbars.
 */
object InkToneTextField {
    /** Champs à contour (formulaires, dialogues). */
    val shape: Shape
        @Composable get() = MaterialTheme.shapes.medium

    /** Champ de recherche rempli, en pilule. */
    val searchShape: Shape
        @Composable get() = MaterialTheme.shapes.extraLarge

    /** Sans soulignement : il serait coupé par les coins arrondis de la pilule. */
    @Composable
    fun searchColors(): TextFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}
