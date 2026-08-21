package com.inktone.feature.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Demande de `POST_NOTIFICATIONS` au premier démarrage d'une narration
 * (P1, plan polissage Pareto — écart déclaré au palier notification).
 *
 * Depuis Android 13, une permission refusée ne fait pas échouer le service
 * foreground : elle empêche seulement sa notification d'apparaître dans le
 * volet. Or c'est précisément cette notification qui est la voie de contrôle
 * en arrière-plan et sur écran verrouillé — sans elle, tout le palier P1
 * devient invisible pour l'utilisateur.
 *
 * Demandée **en contexte** (au moment où l'on lance la lecture à voix haute),
 * jamais au lancement de l'application : une demande sans rapport avec ce que
 * l'utilisateur vient de faire est la manière la plus fiable de récolter un
 * refus définitif.
 *
 * Ne bloque jamais la lecture : la narration démarre que la permission soit
 * accordée ou non — refuser ne doit pas coûter la fonctionnalité, seulement
 * son contrôle depuis le volet.
 */
@Composable
internal fun rememberTtsNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    // `rememberSaveable` : une rotation d'écran ne doit pas redemander une
    // permission déjà refusée dans la même session de lecture.
    val alreadyRequested = rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* accordée ou non, la lecture continue — voir KDoc. */ }

    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !alreadyRequested.value) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    alreadyRequested.value = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
