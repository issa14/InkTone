package com.inktone.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Fondation des reglages (Tache 8.1). Critere de validation : modifier
 * un reglage, tuer l'app, la rouvrir - le reglage persiste
 * (PreferencesRepository Room-backed depuis la Phase 2, deja teste en
 * isolation ; cette tache prouve le chemin UI complet).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenPronunciationRules: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val preferences = state.preferences

    Column(modifier = Modifier.padding(16.dp)) {
        SectionGroup("Lecture") {
            SettingRow("Theme", preferences.theme.name) {
                viewModel.onIntent(SettingsIntent.SetTheme(nextEnumValue(preferences.theme)))
            }
            SliderSetting("Taille du texte", preferences.fontSize.toFloat(), 12f..32f) {
                viewModel.onIntent(SettingsIntent.SetFontSize(it.toInt()))
            }
            SettingRow("Police", preferences.fontFamily.name) {
                viewModel.onIntent(SettingsIntent.SetFontFamily(nextEnumValue(preferences.fontFamily)))
            }
        }
        SectionGroup("Voix") {
            SettingRow("Moteur par defaut", preferences.defaultTtsEngine.name) {
                viewModel.onIntent(SettingsIntent.SetDefaultTtsEngine(nextEnumValue(preferences.defaultTtsEngine)))
            }
            SettingRow("Regles de prononciation", "Gerer") { onOpenPronunciationRules() }
        }
        SectionGroup("Langue") {
            SettingRow("Langue de l'interface", preferences.language) {
                viewModel.onIntent(SettingsIntent.SetLanguage(if (preferences.language == "fr") "en" else "fr"))
            }
        }
        SectionGroup("Confidentialite") {
            ToggleSetting(
                "Rapports de crash",
                preferences.crashReportingEnabled,
            ) { viewModel.onIntent(SettingsIntent.SetCrashReportingEnabled(it)) }
        }
        SectionGroup("Accessibilite") {
            ToggleSetting(
                "Reduire les animations",
                preferences.reduceMotion,
            ) { viewModel.onIntent(SettingsIntent.SetReduceMotion(it)) }
        }
    }
}

@Composable
private fun SectionGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title)
        content()
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Button(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label (${value.toInt()})")
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

private inline fun <reified T : Enum<T>> nextEnumValue(current: T): T {
    val values = enumValues<T>()
    return values[(current.ordinal + 1) % values.size]
}
