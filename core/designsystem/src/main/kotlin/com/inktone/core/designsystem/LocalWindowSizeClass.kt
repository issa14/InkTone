package com.inktone.core.designsystem

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * Fondation posee Tache 9.0.2 (reprise du pattern legacy) — calculee une
 * seule fois dans `MainActivity` (`calculateWindowSizeClass(this)`) et
 * fournie via `CompositionLocalProvider` a toute l'arborescence. Objectif
 * minimal de cette tache : la fondation, pas la fonctionnalite tablette
 * complete (double page, hors perimetre v1, Blueprint §16.4) — aucun
 * layout existant ne consomme cette valeur pour l'instant.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass non fourni — doit être défini au niveau de MainActivity")
}
