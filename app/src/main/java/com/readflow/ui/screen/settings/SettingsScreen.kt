package com.readflow.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.data.settings.AppTheme

private val CardBg = Color(0xFF1A1A1A)
private val AccentBlue = Color(0xFF4FC3F7)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showVoicePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showPathEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── SECTION AUDIO ──
        SectionHeader("🎙️ Configuration Audio")
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Voix
                SettingRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voix active",
                    subtitle = state.voice,
                    onClick = { showVoicePicker = true }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

                // Vitesse
                SliderSetting(
                    icon = Icons.Default.Speed,
                    title = "Vitesse d'élocution",
                    value = state.speed,
                    valueRange = 0.5f..2.0f,
                    format = { "${"%.1f".format(it)}x" },
                    onValueChange = { viewModel.setSpeed(it) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

                // Gain
                SliderSetting(
                    icon = Icons.Default.VolumeUp,
                    title = "Gain audio",
                    value = state.gain,
                    valueRange = 1.0f..4.0f,
                    format = { "${"%.1f".format(it)}x" },
                    onValueChange = { viewModel.setGain(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── SECTION APPARENCE ──
        SectionHeader("🎨 Apparence")
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Thème
                SettingRow(
                    icon = Icons.Default.Palette,
                    title = "Thème",
                    subtitle = state.theme.label,
                    onClick = { showThemePicker = true }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

                // Couleurs dynamiques (Material You)
                SwitchSetting(
                    icon = Icons.Default.ColorLens,
                    title = "Couleurs dynamiques",
                    subtitle = "Palette générée depuis le fond d'écran (Android 12+)",
                    checked = state.dynamicColors,
                    onCheckedChange = { viewModel.setDynamicColors(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── SECTION STOCKAGE ──
        SectionHeader("📁 Stockage")
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingRow(
                    icon = Icons.Default.Folder,
                    title = "Dossier des modèles",
                    subtitle = state.modelPath.ifBlank { "Chemin par défaut" },
                    onClick = { showPathEditor = true }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Dialogues ──
        if (showVoicePicker) {
            VoicePickerDialog(state.availableVoices, state.voice) { viewModel.setVoice(it); showVoicePicker = false }
        }
        if (showThemePicker) {
            ThemePickerDialog(state.theme) { viewModel.setTheme(it); showThemePicker = false }
        }
        if (showPathEditor) {
            PathEditDialog(state.modelPath, { showPathEditor = false }) { viewModel.setModelPath(it); showPathEditor = false }
        }
    }
}

// ─────────────────────────────────────────────────────
//  COMPOSANTS RÉUTILISABLES
// ─────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SliderSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(format(value), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SwitchSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue, checkedTrackColor = AccentBlue.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun VoicePickerDialog(voices: List<String>, selected: String, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        containerColor = Color(0xFF252525),
        title = { Text("Voix TTS", color = Color.White) },
        text = {
            Column { voices.forEach { voice ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(voice) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = voice == selected, onClick = { onSelect(voice) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentBlue))
                    Spacer(Modifier.width(8.dp))
                    Text(voice, color = Color.White)
                }
            }}
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Fermer", color = AccentBlue) } }
    )
}

@Composable
private fun ThemePickerDialog(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options = AppTheme.entries.map { it to it.label }
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        containerColor = Color(0xFF252525),
        title = { Text("Thème", color = Color.White) },
        text = {
            Column { options.forEach { (theme, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(theme) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = theme == selected, onClick = { onSelect(theme) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentBlue))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White)
                }
            }}
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Fermer", color = AccentBlue) } }
    )
}

@Composable
private fun PathEditDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252525),
        title = { Text("Dossier des modèles", color = Color.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Chemin absolu") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White.copy(alpha = 0.7f),
                    cursorColor = AccentBlue, focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Enregistrer", color = AccentBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = Color.White.copy(alpha = 0.5f)) } }
    )
}

// Extension pour clickable sans ripple
