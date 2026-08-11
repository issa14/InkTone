package com.inktone.feature.reader

import com.inktone.core.designsystem.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.InkToneShapes
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile

/**
 * 3d.1 — Panneau de contrôle vocal accessible depuis le Reader. Navigation
 * phrase à phrase, play/pause, vitesse (branchée sur le profil vocal actif,
 * plus de curseur décoratif), sélecteur de voix, lien vers l'ajout d'une
 * règle de prononciation.
 *
 * Le bouton Stop du B.3 d'origine est retiré : `ReaderViewModel.pausePlayback()`
 * coupe déjà entièrement l'`AudioTrack` et annule la coroutine de lecture —
 * il n'existe aucun pause/resume réel dans l'architecture actuelle
 * (`AudioSegmentPlayer` en `MODE_STATIC`, pas de reprise à mi-phrase), donc
 * aucun comportement distinct à donner à un second bouton. Un vrai Stop
 * nécessiterait de migrer vers `AudioPlaybackService`/Media3 (déjà utilisé
 * par `feature/player`), hors périmètre du lot 3d (voir doc, tâche 3d.1 et
 * consignation 3d.7).
 *
 * Les puces de minuteur de sommeil, présentes ici avant ce lot, sont
 * retirées : elles vivent désormais dans le panneau Minuteur dédié
 * (`SleepTimerPanel`, tâche 3d.4).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderTtsPanel(
    isPlaying: Boolean,
    currentSentenceIndex: Int,
    totalSentences: Int,
    activeVoiceProfile: VoiceProfile?,
    availableVoiceProfiles: List<VoiceProfile>,
    onPlayPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSelectVoiceProfile: (String) -> Unit,
    onOpenPronunciationRules: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showVoicePicker by remember { mutableStateOf(false) }
    val speed = activeVoiceProfile?.speed ?: 1.0f

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Contrôle vocal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // ── Navigation phrase ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousSentence) {
                    Icon(AppIcons.SentencePrevious, contentDescription = "Phrase précédente")
                }
                Text(
                    "Phrase ${currentSentenceIndex + 1} / $totalSentences",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onNextSentence) {
                    Icon(AppIcons.SentenceNext, contentDescription = "Phrase suivante")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Play/Pause ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = InkToneShapes.large,
                ) {
                    Icon(
                        if (isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Lire",
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Voix ──
            Text("Voix", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { showVoicePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(activeVoiceProfile?.let(::voiceDisplayName) ?: "Voix par défaut")
            }

            Spacer(Modifier.height(20.dp))

            // ── Vitesse — 3d.1 : branchée sur VoiceProfile.speed, déjà
            // consommée par les deux moteurs TTS (SherpaOnnxTtsEngine,
            // AndroidNativeTtsEngine) ; ce curseur ne fait plus que
            // refléter/écrire cette valeur, il n'y avait pas de chantier
            // domaine à faire ici.
            Text("Vitesse (${"%.1f".format(speed)}×)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..3.0f,
                steps = 9,
            )

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onOpenPronunciationRules, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter une règle de prononciation")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showVoicePicker) {
        VoiceProfilePickerDialog(
            profiles = availableVoiceProfiles,
            activeProfileId = activeVoiceProfile?.id,
            onSelect = { profileId ->
                onSelectVoiceProfile(profileId)
                showVoicePicker = false
            },
            onDismiss = { showVoicePicker = false },
        )
    }
}

@Composable
private fun VoiceProfilePickerDialog(
    profiles: List<VoiceProfile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une voix") },
        text = {
            if (profiles.isEmpty()) {
                Text("Aucune autre voix disponible pour le moteur sélectionné dans les Réglages.")
            } else {
                Column {
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = profile.id == activeProfileId,
                                onClick = { onSelect(profile.id) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(voiceDisplayName(profile))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

/**
 * 3d.1 — format cible `ff_siwis · Kokoro · Français` (UX_FLOW_DESIGN.md
 * §Haut-parleur), jamais un nom inventé : la voix et la langue viennent
 * directement du `VoiceProfile`, seul le libellé de moteur est un mapping
 * statique (aucun nom lisible n'existe ailleurs dans le domaine).
 */
internal fun voiceDisplayName(profile: VoiceProfile): String =
    "${profile.voice} · ${engineDisplayName(profile.engine)} · ${languageDisplayName(profile.language)}"

private fun engineDisplayName(engine: TtsEngineId): String = when (engine) {
    TtsEngineId.SHERPA_ONNX -> "Kokoro"
    TtsEngineId.ANDROID_NATIVE -> "Voix système"
    TtsEngineId.PIPER -> "Piper"
    TtsEngineId.EDGE_TTS -> "Edge"
}

private fun languageDisplayName(languageCode: String): String = when {
    languageCode.startsWith("fr", ignoreCase = true) -> "Français"
    languageCode.startsWith("en", ignoreCase = true) -> "English"
    languageCode.startsWith("es", ignoreCase = true) -> "Español"
    languageCode.startsWith("de", ignoreCase = true) -> "Deutsch"
    else -> languageCode
}
