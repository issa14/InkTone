package com.inktone.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile

/**
 * Fondation des reglages (Tache 8.1). Critere de validation : modifier
 * un reglage, tuer l'app, la rouvrir - le reglage persiste
 * (PreferencesRepository Room-backed depuis la Phase 2, deja teste en
 * isolation ; cette tache prouve le chemin UI complet).
 *
 * Tache 9bis.5 — `LargeTopAppBar` avec effet de collapse au defilement
 * (`TopAppBarDefaults.exitUntilCollapsedScrollBehavior`) plutot qu'un
 * en-tete statique, absent du legacy. Portee par cet ecran lui-meme
 * (pas par `BackScaffold`/`InkToneNavHost`, Tache 9bis.2) pour avoir acces
 * au `scrollBehavior` partage avec le contenu defilant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenPronunciationRules: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Reglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            SettingsContent(state.preferences, state.voiceProfiles, viewModel::onIntent, onOpenPronunciationRules, onOpenAbout)
        }
    }
}

/**
 * Composable sans etat (Tache 9.1) — separee de [SettingsScreen] pour
 * rester testable via `createAndroidComposeRule` sans dependre de Hilt
 * ni de `hiltViewModel()` (audit d'accessibilite, Tache 9.1.1/9.1.2/9.1.4).
 */
@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    voiceProfiles: List<VoiceProfile> = emptyList(),
    onIntent: (SettingsIntent) -> Unit,
    onOpenPronunciationRules: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    // Bug reel trouve en testant sur device (verification manuelle,
    // Tache 9bis suite) : sans verticalScroll ici, le contenu qui deborde
    // de l'ecran (reglette de lecture, preregalage, a propos) etait
    // inatteignable - ET le LargeTopAppBar (Tache 9bis.5) n'avait rien a
    // quoi accrocher son effet de collapse (aucun enfant scrollable ne
    // produit de delta de defilement).
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        // D.1 — pickers dialog pour thème/police/moteur
        var showThemePicker by remember { mutableStateOf(false) }
        var showFontPicker by remember { mutableStateOf(false) }
        var showEnginePicker by remember { mutableStateOf(false) }

        SectionGroup("Lecture") {
            SettingRow("Theme", preferences.theme.name) { showThemePicker = true }
            SliderSetting("Taille du texte", preferences.fontSize.toFloat(), 12f..32f) {
                onIntent(SettingsIntent.SetFontSize(it.toInt()))
            }
            SettingRow("Police", preferences.fontFamily.name) { showFontPicker = true }
        }
        SectionGroup("Voix") {
            SettingRow("Moteur par defaut", preferences.defaultTtsEngine.name) { showEnginePicker = true }
            val activeVoiceName = voiceProfiles
                .find { it.id == preferences.activeVoiceProfileId }
                ?.voice ?: "Voix par defaut"
            SettingRow("Voix", activeVoiceName) {
                onIntent(SettingsIntent.SetActiveVoiceProfile(
                    nextVoiceProfileId(preferences.activeVoiceProfileId, voiceProfiles)))
            }
            // D.3 — gain audio slider
            SliderSetting("Gain audio", preferences.audioGain, 1.0f..4.0f) {
                onIntent(SettingsIntent.SetAudioGain(it))
            }
            SettingRow("Regles de prononciation", "Gerer") { onOpenPronunciationRules() }
        }
        SectionGroup("Langue") {
            SettingRow("Langue de l'interface", preferences.language) {
                onIntent(SettingsIntent.SetLanguage(if (preferences.language == "fr") "en" else "fr"))
            }
        }
        SectionGroup("Confidentialite") {
            ToggleSetting(
                "Rapports de crash",
                preferences.crashReportingEnabled,
            ) { onIntent(SettingsIntent.SetCrashReportingEnabled(it)) }
        }
        SectionGroup("Apparence") {
            // Tache 9bis.1.2/9bis.5 - consomme par AppThemeViewModel
            // (module app, MainActivity), pas par SettingsViewModel lui-meme :
            // InkToneTheme s'applique avant tout hiltViewModel() scope a une
            // destination de navigation.
            ToggleSetting(
                "Couleur dynamique (Material You)",
                preferences.dynamicColorEnabled,
            ) { onIntent(SettingsIntent.SetDynamicColorEnabled(it)) }
        }
        SectionGroup("Accessibilite") {
            ToggleSetting(
                "Reduire les animations",
                preferences.reduceMotion,
            ) { onIntent(SettingsIntent.SetReduceMotion(it)) }
            ToggleSetting(
                "Reglette de lecture",
                preferences.readingRulerEnabled,
            ) { onIntent(SettingsIntent.SetReadingRulerEnabled(it)) }
            // D.3 — respecter le fontScale système
            ToggleSetting(
                "Police adaptee au systeme",
                preferences.useSystemFontScale,
            ) { onIntent(SettingsIntent.SetUseSystemFontScale(it)) }
            Button(
                onClick = { onIntent(SettingsIntent.ApplyAccessibilityPreset) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Appliquer le preregalage d'accessibilite")
            }
        }
        SectionGroup("A propos") {
            SettingRow("InkTone", "Voir") { onOpenAbout() }
        }
        // D.1 — Pickers dialog (remplacent les cycles d'enum)
        if (showThemePicker) {
            PickerDialog("Thème", ReadingTheme.entries.toList(), preferences.theme, { it.name },
                onSelect = { onIntent(SettingsIntent.SetTheme(it)); showThemePicker = false },
                onDismiss = { showThemePicker = false })
        }
        if (showFontPicker) {
            PickerDialog("Police", FontFamily.entries.toList(), preferences.fontFamily, { it.name },
                onSelect = { onIntent(SettingsIntent.SetFontFamily(it)); showFontPicker = false },
                onDismiss = { showFontPicker = false })
        }
        if (showEnginePicker) {
            PickerDialog("Moteur TTS", TtsEngineId.entries.toList(), preferences.defaultTtsEngine, { it.name },
                onSelect = { onIntent(SettingsIntent.SetDefaultTtsEngine(it)); showEnginePicker = false },
                onDismiss = { showEnginePicker = false })
        }    }
}

/**
 * D.2 — Section groupée avec titre coloré et carte visuelle.
 */
@Composable
private fun SectionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.em,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(value) }
    }
}

/**
 * Tache 9.1.1 — `Row` entiere rendue accessible via `Modifier.toggleable`
 * (role `Switch`, label fusionne) plutot que le seul `Switch` isole :
 * TalkBack annonce alors "Rapports de crash, interrupteur, desactive",
 * pas un interrupteur muet a cote d'un texte non associe. `onCheckedChange`
 * du `Switch` lui-meme passe a `null` — le clic est gere par la Row
 * parente (recommandation Material3 pour les lignes de reglage completes).
 */
@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Tache 9.1.1 — `contentDescription` explicite : un `Slider` seul n'annonce ni son label ni sa valeur courante a TalkBack. */
@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label (${value.toInt()})")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "$label, valeur ${value.toInt()}" },
        )
    }
}

private inline fun <reified T : Enum<T>> nextEnumValue(current: T): T {
    val values = enumValues<T>()
    return values[(current.ordinal + 1) % values.size]
}

/**
 * A.5 — cycle entre les profils vocaux disponibles.
 * Si aucun profil n'est sélectionné, passe au premier. Si le dernier
 * est atteint, revient à null (voix par défaut).
 */
private fun nextVoiceProfileId(currentId: String?, profiles: List<VoiceProfile>): String? {
    if (profiles.isEmpty()) return null
    val currentIndex = profiles.indexOfFirst { it.id == currentId }
    return if (currentIndex < 0 || currentIndex >= profiles.lastIndex) {
        null // retour à la voix par défaut
    } else {
        profiles[currentIndex + 1].id
    }
}

/**
 * D.1 — Dialog de sélection générique remplaçant le cycle d'enum.
 */
@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(label(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
