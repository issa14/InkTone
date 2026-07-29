package com.inktone.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Repris de l'audit UX legacy (Tache 8.4) — respecte le reglage SYSTEME
 * Android (echelle d'animation), pas seulement une preference
 * applicative. A utiliser partout ou une duree d'animation est fixee en
 * dur, pas seulement pour le preregalage d'accessibilite.
 */
@Composable
fun reducedMotionDuration(defaultMs: Int): Int {
    val context = LocalContext.current
    val isReduced = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f,
            ) == 0.0f
        } catch (_: SecurityException) { false }
    }
    return if (isReduced) 0 else defaultMs
}
