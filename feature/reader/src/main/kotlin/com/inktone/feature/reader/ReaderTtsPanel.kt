package com.inktone.feature.reader

import com.inktone.core.designsystem.formatMultiplier
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import com.inktone.core.designsystem.InkToneSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.model.voiceLabel

/**
 * 3d.1 — Panneau Voix et vitesse du Lecteur : voix active, vitesse (branchée
 * sur le profil vocal actif), lien vers l'ajout d'une règle de prononciation.
 * Lecture/Pause et phrase précédente/suivante n'y figurent pas : la pilule et
 * le panneau unifié les portent déjà (audit UI P1-6).
 *
 * Les puces de minuteur de sommeil, présentes ici avant ce lot, sont
 * retirées : elles vivent désormais dans le panneau Minuteur dédié
 * (`SleepTimerPanel`, tâche 3d.4).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderTtsPanel(
    activeVoiceProfile: VoiceProfile?,
    onSpeedChange: (Float) -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenPronunciationRules: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speed = activeVoiceProfile?.speed ?: 1.0f
    // Valeur suivie pendant le geste, transmise à la fin seulement : la
    // narration en cours est relancée à chaque changement (audit UX A6).
    var speedDraft by remember(speed) { mutableFloatStateOf(speed) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Grande police sur petit écran : le lien des règles sortait de la feuille.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Narration",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            // ── Voix — choisie dans les Réglages (décision D74) : ce bouton
            // listait des enregistrements de profil, pas les voix des moteurs.
            Text("Voix", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(activeVoiceProfile?.let(::voiceDisplayName) ?: "Voix par défaut", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onOpenVoiceSettings) {
                Text("Changer de voix dans les Réglages")
            }

            Spacer(Modifier.height(20.dp))

            // ── Vitesse — 3d.1 : branchée sur VoiceProfile.speed, déjà
            // consommée par les deux moteurs TTS (SherpaOnnxTtsEngine,
            // AndroidNativeTtsEngine) ; ce curseur ne fait plus que
            // refléter/écrire cette valeur, il n'y avait pas de chantier
            // domaine à faire ici.
            InkToneSlider(
                label = "Vitesse",
                value = speedDraft.coerceIn(VoiceProfile.SPEED_RANGE),
                range = VoiceProfile.SPEED_RANGE,
                onValueChange = { speedDraft = it },
                onValueChangeFinished = { onSpeedChange(speedDraft) },
                minIcon = AppSymbol.SlowMotionVideo,
                maxIcon = AppSymbol.FastForward,
                steps = 5, // crans de 0,25
                displayFormatter = ::formatMultiplier,
            )

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onOpenPronunciationRules, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter une règle de prononciation")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

}


/**
 * 3d.1 — format cible `voix · moteur · langue` (UX_FLOW_DESIGN.md
 * §Haut-parleur), jamais un nom inventé : la voix passe par le libellé
 * lisible du catalogue domaine (`voiceLabel`), le moteur est un mapping
 * statique.
 */
internal fun voiceDisplayName(profile: VoiceProfile): String =
    "${voiceLabel(profile.voice)} · ${engineDisplayName(profile.engine)} · ${languageDisplayName(profile.language)}"

private fun engineDisplayName(engine: TtsEngineId): String = when (engine) {
    // Lot 20 — Sherpa = modèle upmc-medium (remplace Kokoro).
    TtsEngineId.SHERPA_ONNX -> "Voix naturelle"
    TtsEngineId.ANDROID_NATIVE -> "Voix du téléphone"
    TtsEngineId.PIPER -> "Piper"
    TtsEngineId.EDGE_TTS -> "Voix en ligne"
}

private fun languageDisplayName(languageCode: String): String = when {
    languageCode.startsWith("fr", ignoreCase = true) -> "Français"
    languageCode.startsWith("en", ignoreCase = true) -> "English"
    languageCode.startsWith("es", ignoreCase = true) -> "Español"
    languageCode.startsWith("de", ignoreCase = true) -> "Deutsch"
    else -> languageCode
}
