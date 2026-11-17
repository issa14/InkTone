package com.inktone.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Couleurs des barres d'application, définies à un seul endroit (audit UI
 * DS-18 : la même recette était copiée dans chaque écran). Réservée aux
 * destinations du tiroir ; un écran ouvert par Retour garde la `TopAppBar`
 * neutre par défaut (P1-2).
 *
 * En clair, la barre est peinte en `primary`. En sombre, `primary` est un
 * lavande clair, lisible comme couleur de texte : la barre prend alors
 * `primaryContainer`, un violet profond, pour garder la marque.
 */
object InkToneTopBar {

    @Composable
    fun containerColor(): Color =
        if (isDark()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary

    /** Titre, icônes et boutons posés sur la barre. */
    @Composable
    fun contentColor(): Color =
        if (isDark()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors {
        val content = contentColor()
        return TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor(),
            titleContentColor = content,
            navigationIconContentColor = content,
            actionIconContentColor = content,
        )
    }

    // Le thème réel de l'app, pas celui du système (l'app peut être forcée).
    @Composable
    private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
}
