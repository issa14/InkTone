package com.inktone.feature.settings
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.model.availableVoicesFor
import com.inktone.domain.model.voiceLabel

/**
 * Écran Réglages (Tâche 8.1).
 *
 * Lot 6 — restructuré en 6 cartes :
 *   1. Présets rapides (Mode sombre, Accessibilité) — toggles réversibles
 *   2. Lecture (moteur, voix, vitesse, gain, intonation, extrait)
 *   3. Appareil (thème système, couleurs dynamiques, accessibilité, langue, confidentialité)
 *   4. Données (Palier B — export/import de sauvegarde, cache, réinitialisation)
 *   5. Performance & Bien-être (objectif, repos oculaire)
 *   6. Prononciation (Palier B — carte inline, plus un écran séparé)
 *   7. À propos
 *
 * Refonte UI/UX (standards Material 3) : titres de section en
 * `titleSmall`/sentence case (fini le ALL_CAPS), `ElevatedCard`, plus de
 * sous-titres majuscules internes (séparation par `HorizontalDivider`),
 * padding standardisé 20dp horizontal / 12dp vertical sur chaque ligne
 * interactive.
 *
 * Palier B — carte Données : `BackupManager` vit dans `:data`, invisible
 * depuis `feature/settings` (Blueprint §12.4 — feature ne dépend que de
 * domain/core). L'export/import lui-même (choix SAF + appel à
 * `BackupManager`) est donc piloté par l'appelant (module `app`) via
 * [onExportData]/[onImportData]/[dataOperationResult] ; tout le reste de
 * la carte (dossier des modèles, cache, réinitialisation des préférences)
 * ne touche que `Context`/`domain` et reste géré par [SettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenAbout: () -> Unit = {},
    onBack: () -> Unit = {},
    modelsFolderInfo: ModelsFolderInfo = ModelsFolderInfo(path = "Emplacement inconnu"),
    dataOperationResult: DataOperationResult? = null,
    onDismissDataOperationResult: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppSymbol.Back, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            SettingsContent(
                preferences = state.preferences,
                voiceProfiles = state.voiceProfiles,
                pronunciationRules = state.pronunciationRules,
                cacheSizeBytes = state.cacheSizeBytes,
                voiceDownloadProgress = state.voiceDownloadProgress,
                isPreviewing = state.isPreviewing,
                previewError = state.previewError,
                onIntent = viewModel::onIntent,
                onOpenAbout = onOpenAbout,
                modelsFolderInfo = modelsFolderInfo,
                dataOperationResult = dataOperationResult,
                onDismissDataOperationResult = onDismissDataOperationResult,
                onExportData = onExportData,
                onImportData = onImportData,
            )
        }
    }
}

/**
 * Composable sans état (Tâche 9.1) — séparée de [SettingsScreen] pour
 * rester testable via `createAndroidComposeRule` sans dépendre de Hilt
 * ni de `hiltViewModel()` (audit d'accessibilité, Tâche 9.1.1/9.1.2/9.1.4).
 */
@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    voiceProfiles: List<VoiceProfile> = emptyList(),
    pronunciationRules: List<PronunciationRule> = emptyList(),
    cacheSizeBytes: Long = 0L,
    voiceDownloadProgress: com.inktone.domain.service.VoiceDownloadProgress? = null,
    isPreviewing: Boolean = false,
    previewError: String? = null,
    onIntent: (SettingsIntent) -> Unit,
    onOpenAbout: () -> Unit = {},
    modelsFolderInfo: ModelsFolderInfo = ModelsFolderInfo(path = "Emplacement inconnu"),
    dataOperationResult: DataOperationResult? = null,
    onDismissDataOperationResult: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
) {
    // Bug réel trouvé en testant sur device (vérification manuelle,
    // Tâche 9bis suite) : sans verticalScroll ici, le contenu qui déborde
    // de l'écran était inatteignable — ET le LargeTopAppBar (Tâche 9bis.5)
    // n'avait rien à quoi accrocher son effet de collapse.
    Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

        // ── Carte 1 : Présets rapides ──────────────────────────────────────────
        // Le toggle reflète l'état réel : si un réglage du préset est modifié
        // manuellement, le toggle se désactive automatiquement.
        val isDarkPresetActive = preferences.appTheme == AppTheme.DARK &&
            preferences.theme == ReadingTheme.OBSIDIENNE.id
        val isAccessibilityPresetActive = preferences.fontFamily == FontFamily.OPEN_DYSLEXIC &&
            preferences.fontSize == 24 &&
            preferences.theme == ReadingTheme.PAPIER_CLAIR.id &&
            preferences.reduceMotion &&
            preferences.libraryLayoutMode == "LIST"

        SectionGroup("Présets rapides") {
            PresetRow(
                icon = { AppIcon(AppSymbol.DarkMode, contentDescription = null) },
                title = "Mode sombre",
                description = "Thème sombre · Interface sombre",
                checked = isDarkPresetActive,
                onCheckedChange = { onIntent(SettingsIntent.SetDarkModePreset(it)) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            PresetRow(
                icon = { AppIcon(AppSymbol.Accessibility, contentDescription = null) },
                title = "Accessibilité",
                description = "OpenDyslexic · 24 sp · Animations réduites · Mode Liste",
                checked = isAccessibilityPresetActive,
                onCheckedChange = { onIntent(SettingsIntent.SetAccessibilityPreset(it)) },
            )
        }

        // ── Carte 2 : Lecture ──────────────────────────────────────────────────
        var showEnginePicker by remember { mutableStateOf(false) }
        var showVoicePicker by remember { mutableStateOf(false) }

        val activeVoiceProfile = voiceProfiles.find { it.id == preferences.activeVoiceProfileId }
        val activeVoiceName = activeVoiceProfile?.voice?.let(::voiceLabel) ?: "Voix par défaut"
        val activeSpeed = activeVoiceProfile?.speed ?: 1.0f
        val activePitch = activeVoiceProfile?.pitch ?: 1.0f

        SectionGroup("Lecture") {
            SettingRow(
                label = "Moteur de synthèse",
                value = ttsEngineLabel(preferences.defaultTtsEngine),
                onClick = { showEnginePicker = true },
            )
            // Lot 14 — signaler les capacités du moteur sélectionné (§8.10) :
            // jamais un changement silencieux. Edge (cloud) porte
            // l'avertissement réseau ; les moteurs locaux sont explicites.
            Text(
                text = ttsEngineDescription(preferences.defaultTtsEngine),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            // Passage du cycle au dialogue : avec plusieurs profils, cycler devient impraticable.
            SettingRow(
                label = "Voix active",
                value = activeVoiceName,
                onClick = { showVoicePicker = true },
            )
            // Lot 20 — voix neuronale RESTAURÉE : le téléchargement est
            // désormais réellement exploitable (extraction tar.bz2 +
            // modèle CTC câblés, AUDIT_CONSOLIDATION_V1.md B2 corrigé).
            // La voix du système reste le repli tant que le modèle n'est
            // pas installé (FallbackTtsEngine) — jamais bloquant.
            VoiceDownloadRow(
                progress = voiceDownloadProgress,
                onStart = { onIntent(SettingsIntent.StartVoiceDownload) },
                onCancel = { onIntent(SettingsIntent.CancelVoiceDownload) },
            )
            // Lot 6 — vitesse : même cible VoiceProfile.speed que le panneau Voix du lecteur (Tâche 3d).
            SliderSetting(
                label = "Vitesse d'élocution",
                value = activeSpeed,
                range = 0.5f..2.0f,
                minIcon = AppSymbol.SlowMotionVideo,
                maxIcon = AppSymbol.FastForward,
                displayFormatter = { "%.1f×".format(it) },
                onValueChange = { onIntent(SettingsIntent.SetVoiceSpeed(it)) },
            )
            SliderSetting(
                label = "Gain audio",
                value = preferences.audioGain,
                range = 1.0f..4.0f,
                minIcon = AppSymbol.VolumeDown,
                maxIcon = AppSymbol.Speaking,
                displayFormatter = { "%.1f×".format(it) },
                onValueChange = { onIntent(SettingsIntent.SetAudioGain(it)) },
            )
            // Lot 6 — Intonation : VoiceProfile.pitch.
            // Signalé : non vérifié que pitch atteint le moteur Sherpa-ONNX — à confirmer sur device (A4).
            SliderSetting(
                label = "Intonation (pitch)",
                value = activePitch,
                range = 0.5f..1.5f,
                minIcon = AppSymbol.KeyboardArrowDown,
                maxIcon = AppSymbol.KeyboardArrowUp,
                displayFormatter = { "%.1f×".format(it) },
                onValueChange = { onIntent(SettingsIntent.SetVoicePitch(it)) },
            )
            // Lot 6 — Écouter un extrait. Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md,
            // B1) : RÉ-IMPLÉMENTÉ — le bouton avait été retiré (l'intent était
            // un no-op) puis recâblé sur une vraie synthèse + lecture
            // (SettingsViewModel.togglePreview). Spec UX (l.507) : bouton
            // plein largeur en bas de carte, icône + libellé.
            OutlinedButton(
                onClick = { onIntent(SettingsIntent.PlayPreview) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                AppIcon(
                    if (isPreviewing) AppSymbol.Pause else AppSymbol.Play,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPreviewing) "Arrêter l'extrait" else "Écouter un extrait")
            }
            if (previewError != null) {
                Text(
                    text = previewError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        // ── Carte 3 : Appareil ─────────────────────────────────────────────────
        var showAppThemePicker by remember { mutableStateOf(false) }

        SectionGroup("Appareil") {
            // ─ Apparence ─
            // Lot 6 — thème système de l'app (SYSTEM/LIGHT/DARK).
            // Ne pas confondre avec preferences.theme (ReadingTheme de lecture).
            SettingRow(
                label = "Thème",
                value = when (preferences.appTheme) {
                    AppTheme.SYSTEM -> "Système"
                    AppTheme.LIGHT -> "Clair"
                    AppTheme.DARK -> "Sombre"
                },
                onClick = { showAppThemePicker = true },
            )
            // Tâche 9bis.1.2/9bis.5 — consommé par AppThemeViewModel (module app, MainActivity),
            // pas par SettingsViewModel lui-même : InkToneTheme s'applique avant tout hiltViewModel()
            // scope à une destination de navigation.
            ToggleSetting(
                label = "Couleurs dynamiques (Material You)",
                checked = preferences.dynamicColorEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetDynamicColorEnabled(it)) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // ─ Accessibilité ─
            ToggleSetting(
                label = "Réduire les animations",
                checked = preferences.reduceMotion,
                onCheckedChange = { onIntent(SettingsIntent.SetReduceMotion(it)) },
            )
            // D.3 — respecter le fontScale système Android au lieu du fontSize interne
            ToggleSetting(
                label = "Police adaptée au système",
                checked = preferences.useSystemFontScale,
                onCheckedChange = { onIntent(SettingsIntent.SetUseSystemFontScale(it)) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // ─ Langue et confidentialité ─
            // Rattachées ici comme ajouts assumés (pas de place dans la cible stricte des 6 cartes).
            // Signalé dans UX_FLOW_DESIGN.md § Réglages.
            SettingRow(
                label = "Langue de l'interface",
                value = if (preferences.language == "fr") "Français" else "English",
                onClick = {
                    onIntent(SettingsIntent.SetLanguage(if (preferences.language == "fr") "en" else "fr"))
                },
            )
            // Lot 10, Tâche 10.5 — formulation honnête exigée par ADR-014
            // (« avec une explication honnête de son contenu »). Seul
            // point de consentement restant depuis le retrait de l'étape
            // d'onboarding CrashConsent (Tâche 10.3) : cette description
            // doit porter l'explication à elle seule.
            ToggleSetting(
                label = "Rapports de crash",
                description = "Envoie uniquement la trace d'erreur, la version de l'app et le modèle d'appareil. " +
                    "Jamais le contenu de vos livres ni vos annotations.",
                checked = preferences.crashReportingEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetCrashReportingEnabled(it)) },
            )
        }

        // ── Carte 4 : Données (Palier B) ──────────────────────────────────────
        var showImportWarning by remember { mutableStateOf(false) }
        var showClearCacheConfirm by remember { mutableStateOf(false) }
        var showResetConfirm by remember { mutableStateOf(false) }

        SectionGroup("Données") {
            // Dossier des modèles — chemin fixe (infrastructure/tts), aucune
            // capacité de déplacement : signalé en lecture seule plutôt que
            // de suggérer un contrôle qui n'a pas d'effet.
            // Le chemin (potentiellement long : chemin absolu filesDir) va
            // sur sa propre ligne, ellipsé à une ligne — le mettre à côté du
            // libellé sur la même Row écrase ce dernier caractère par
            // caractère quand le chemin dépasse l'espace restant (même bug
            // que le stepper Intervalle de pause, corrigé plus tôt : un
            // Modifier.weight(1f) seul ne protège pas contre un voisin non
            // borné qui grandit librement).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dossier des modèles")
                    Text(
                        text = modelsFolderInfo.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!modelsFolderInfo.isEditable) {
                    AppIcon(
                        AppSymbol.Lock,
                        contentDescription = "Lecture seule",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // Action directe, sans confirmation — n'écrase rien de local.
            ActionRow(
                icon = AppSymbol.Upload,
                title = "Exporter les données",
                subtitle = "Signets, règles de prononciation, progression et statistiques",
                onClick = onExportData,
            )
            // Avertissement préalable — l'import écrase la configuration courante.
            ActionRow(
                icon = AppSymbol.Download,
                title = "Importer une sauvegarde",
                subtitle = "Remplace les données actuelles par celles du fichier sélectionné",
                onClick = { showImportWarning = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // Taille réelle calculée (Context.cacheDir), pas estimée.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vider le cache")
                    Text(
                        text = "${formatBytes(cacheSizeBytes)} occupés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showClearCacheConfirm = true }, enabled = cacheSizeBytes > 0) {
                    AppIcon(AppSymbol.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Vider")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // Couleur d'alerte + icône triangle : signale le côté destructif.
            ActionRow(
                icon = AppSymbol.WarningAmber,
                title = "Réinitialiser les paramètres",
                subtitle = "Revient aux réglages par défaut — les livres et les données ne sont pas touchés",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showResetConfirm = true },
            )
        }

        // ── Carte 5 : Performance & Bien-être ─────────────────────────────────
        var showGoalPicker by remember { mutableStateOf(false) }

        SectionGroup("Performance & bien-être") {
            SettingRow(
                label = "Objectif quotidien de lecture",
                value = "${preferences.dailyGoalMinutes} min",
                onClick = { showGoalPicker = true },
            )
            ToggleSetting(
                label = "Rappel de repos oculaire",
                checked = preferences.eyeRestReminderEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetEyeRestReminderEnabled(it)) },
            )
            // Stepper désactivé visuellement quand le rappel est éteint.
            StepperSetting(
                label = "Intervalle de pause",
                value = preferences.eyeRestReminderIntervalMinutes,
                step = 15,
                range = 15..120,
                enabled = preferences.eyeRestReminderEnabled,
                displayFormatter = { "$it min" },
                onValueChange = { onIntent(SettingsIntent.SetEyeRestReminderIntervalMinutes(it)) },
            )
        }

        // ── Carte 6 : Prononciation (Palier B — inline) ───────────────────────
        // Écran séparé (PronunciationRulesRoute) conservé pour le lien du
        // panneau Voix du lecteur (ReaderTtsPanel) — cette carte ne le
        // remplace que dans les Réglages.
        var showRuleDialog by remember { mutableStateOf(false) }
        var editingRule by remember { mutableStateOf<PronunciationRule?>(null) }

        PronunciationCard(
            rules = pronunciationRules,
            onAddClick = { editingRule = null; showRuleDialog = true },
        ) {
            if (pronunciationRules.isEmpty()) {
                Text(
                    "Aucune règle pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                pronunciationRules.forEachIndexed { index, rule ->
                    PronunciationRuleRow(
                        rule = rule,
                        onClick = { editingRule = rule; showRuleDialog = true },
                        onToggle = { onIntent(SettingsIntent.TogglePronunciationRule(rule)) },
                        onDelete = { onIntent(SettingsIntent.DeletePronunciationRule(rule.id)) },
                    )
                    if (index != pronunciationRules.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }

        // ── Carte 7 : À propos ─────────────────────────────────────────────────
        SectionGroup("À propos") {
            SettingRow(label = "InkTone", value = "Voir", onClick = onOpenAbout)
        }

        // ── Dialogues ──────────────────────────────────────────────────────────
        if (showEnginePicker) {
            PickerDialog(
                title = "Moteur TTS",
                options = TtsEngineId.entries.filter { it != TtsEngineId.PIPER },
                selected = preferences.defaultTtsEngine,
                label = { ttsEngineLabel(it) },
                onSelect = { onIntent(SettingsIntent.SetDefaultTtsEngine(it)); showEnginePicker = false },
                onDismiss = { showEnginePicker = false },
            )
        }
        if (showVoicePicker) {
            // Lot 14 — liste les VOIX du moteur sélectionné (pas les profils) :
            // Edge expose Vivienne et Henri, le legacy les avait toutes deux.
            val engineVoices = availableVoicesFor(preferences.defaultTtsEngine)
            PickerDialog(
                title = "Voix",
                options = engineVoices,
                selected = activeVoiceProfile?.voice,
                label = { voiceLabel(it) },
                onSelect = { onIntent(SettingsIntent.SetActiveVoiceProfileVoice(it)); showVoicePicker = false },
                onDismiss = { showVoicePicker = false },
            )
        }
        if (showAppThemePicker) {
            PickerDialog(
                title = "Thème de l'interface",
                options = AppTheme.entries.toList(),
                selected = preferences.appTheme,
                label = {
                    when (it) {
                        AppTheme.SYSTEM -> "Système"
                        AppTheme.LIGHT -> "Clair"
                        AppTheme.DARK -> "Sombre"
                    }
                },
                onSelect = { onIntent(SettingsIntent.SetAppTheme(it)); showAppThemePicker = false },
                onDismiss = { showAppThemePicker = false },
            )
        }
        if (showGoalPicker) {
            GoalPickerDialog(
                current = preferences.dailyGoalMinutes,
                onConfirm = { onIntent(SettingsIntent.SetDailyGoalMinutes(it)); showGoalPicker = false },
                onDismiss = { showGoalPicker = false },
            )
        }
        if (showImportWarning) {
            ConfirmDialog(
                title = "Importer une sauvegarde",
                message = "L'import remplace les signets, règles de prononciation, positions de " +
                    "lecture et statistiques actuels par le contenu du fichier sélectionné. " +
                    "Cette action est irréversible.",
                confirmLabel = "Importer",
                onConfirm = { showImportWarning = false; onImportData() },
                onDismiss = { showImportWarning = false },
            )
        }
        if (showClearCacheConfirm) {
            ConfirmDialog(
                title = "Vider le cache",
                message = "${formatBytes(cacheSizeBytes)} de fichiers temporaires seront supprimés. " +
                    "Vos livres, signets et réglages ne sont pas concernés.",
                confirmLabel = "Vider",
                destructive = true,
                onConfirm = { showClearCacheConfirm = false; onIntent(SettingsIntent.ClearCache) },
                onDismiss = { showClearCacheConfirm = false },
            )
        }
        if (showResetConfirm) {
            ConfirmDialog(
                title = "Réinitialiser les paramètres",
                message = "Tous les réglages (thème, lecture, accessibilité, bien-être) reviennent à " +
                    "leurs valeurs par défaut. Vos livres, signets et notes ne sont pas affectés.",
                confirmLabel = "Réinitialiser",
                destructive = true,
                onConfirm = { showResetConfirm = false; onIntent(SettingsIntent.ResetPreferences) },
                onDismiss = { showResetConfirm = false },
            )
        }
        if (showRuleDialog) {
            PronunciationRuleDialog(
                editing = editingRule,
                onSave = { original, replacement, isRegex ->
                    onIntent(SettingsIntent.SavePronunciationRule(editingRule?.id, original, replacement, isRegex))
                    showRuleDialog = false
                },
                onDismiss = { showRuleDialog = false },
            )
        }
        dataOperationResult?.let { result ->
            val (title, message) = dataOperationResultText(result)
            AlertDialog(
                onDismissRequest = onDismissDataOperationResult,
                title = { Text(title) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = onDismissDataOperationResult) { Text("OK") } },
            )
        }
    }
}

/** Lot 6, Palier B — les résultats doivent remonter, jamais être jetés (même défaut corrigé au lot 5). */
private fun dataOperationResultText(result: DataOperationResult): Pair<String, String> = when (result) {
    is DataOperationResult.ExportSuccess -> "Export réussi" to "La sauvegarde a été créée."
    is DataOperationResult.ExportFailed -> "Échec de l'export" to result.message
    is DataOperationResult.ImportSuccess -> "Import terminé" to buildString {
        append("${result.restored} élément(s) restauré(s).")
        if (result.skippedOrphans > 0) {
            append(" ${result.skippedOrphans} ignoré(s) (référencent un livre absent de la bibliothèque).")
        }
    }
    is DataOperationResult.ImportFailed -> "Échec de l'import" to result.message
}

/** Lot 6, Palier B — taille lisible, calculée réellement, jamais affichée en octets bruts. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes o"
    bytes < 1024 * 1024 -> "%.0f Ko".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f Mo".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f Go".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

// ─── Composants partagés ────────────────────────────────────────────────────

/**
 * Refonte Material 3 — titre de section externe en `titleSmall`/couleur
 * primaire, sentence case (plus d'ALL_CAPS) ; `ElevatedCard` pour la
 * hiérarchie de surface standard M3. Le padding horizontal vit désormais
 * sur chaque ligne interactive (20dp), pas sur ce conteneur — ce qui
 * permet aux `HorizontalDivider` internes de courir bord à bord sous le
 * padding des lignes.
 */
@Composable
private fun SectionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Lot 6, Palier B — ligne d'action avec icône, titre et sous-texte
 * optionnel (ex. Exporter/Importer/Réinitialiser dans la carte Données).
 * `tint` par défaut neutre ; passer `colorScheme.error` pour une action
 * destructive (Réinitialiser).
 */
@Composable
private fun ActionRow(
    icon: AppSymbol,
    title: String,
    subtitle: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Lot 6 — Ligne de préset avec icône, titre, description et toggle réversible.
 * Tâche 9.1.1 — Modifier.toggleable pour que TalkBack annonce correctement.
 * Refonte — l'icône s'aligne en haut avec la première ligne de texte
 * (`Alignment.Top` sur la Row) plutôt que d'être centrée sur tout le bloc
 * quand la description passe sur plusieurs lignes ; le Switch, lui, reste
 * centré verticalement (`align` explicite, puisque le défaut de la Row a changé).
 */
@Composable
private fun PresetRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

/**
 * Lot 10 / Lot 20 — téléchargement de la voix neuronale (voix upmc-medium
 * + modèle CTC d'alignement). RESTAURÉ au Lot 20 : le téléchargement est
 * désormais réellement exploitable (extraction tar.bz2 + modèle CTC
 * câblés — AUDIT_CONSOLIDATION_V1.md B2 corrigé), l'état « installée »
 * n'est affiché que si les modèles sont réellement prêts.
 *
 * Retour Issa (vérification device) : confirmation avant de lancer (nom
 * du moteur/voix + taille annoncés, pas de démarrage automatique au
 * clic), annulation possible en cours de route, progression affichée en
 * Mo plutôt qu'en octets bruts.
 */
@Composable
private fun VoiceDownloadRow(
    progress: com.inktone.domain.service.VoiceDownloadProgress?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            when (progress) {
                is com.inktone.domain.service.VoiceDownloadProgress.InProgress ->
                    "Téléchargement : ${formatMegabytes(progress.bytesDownloaded)} / ${formatMegabytes(progress.totalBytes)}"
                is com.inktone.domain.service.VoiceDownloadProgress.Failed -> "Échec : ${progress.message}"
                com.inktone.domain.service.VoiceDownloadProgress.Complete -> "Voix neuronale installée"
                null -> "Voix neuronale locale (Jessica & Pierre). La lecture reste disponible sans cela."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Lot 20 — une fois installée, plus de bouton : re-télécharger une
        // voix déjà prête n'a pas de sens (le flux émettrait Complete
        // immédiatement de toute façon).
        when (progress) {
            is com.inktone.domain.service.VoiceDownloadProgress.InProgress ->
                OutlinedButton(onClick = onCancel) {
                    Text("Annuler le téléchargement")
                }
            com.inktone.domain.service.VoiceDownloadProgress.Complete -> Unit
            // null (jamais lancé) ou Failed (réessayer) : bouton de téléchargement.
            null, is com.inktone.domain.service.VoiceDownloadProgress.Failed ->
                OutlinedButton(onClick = { showConfirmDialog = true }) {
                    AppIcon(AppSymbol.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Télécharger une voix neuronale")
                }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Télécharger la voix neuronale ?") },
            text = {
                Text(
                    "Moteur Sherpa-ONNX (voix UPMC upmc-medium — Jessica & Pierre) : " +
                        "environ 183 Mo au total (voix 80 Mo + alignement mot à mot 102 Mo). " +
                        "Téléchargé une seule fois, réutilisable hors ligne ensuite.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; onStart() }) { Text("Télécharger") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Annuler") }
            },
        )
    }
}

/** Retour Issa : Mo lisibles plutôt que des octets bruts ("345566/132111222"). */
private fun formatMegabytes(bytes: Long): String = "%.1f Mo".format(bytes / 1_000_000.0)

/**
 * Tâche 9.1.1 — Row entière rendue accessible via Modifier.toggleable
 * (role Switch, label fusionné) plutôt que le seul Switch isolé :
 * TalkBack annonce "Rapports de crash, interrupteur, désactivé".
 */
@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Tâche 9.1.1 — contentDescription explicite : un Slider seul n'annonce ni
 * son label ni sa valeur à TalkBack.
 * Refonte — la valeur quitte les parenthèses du titre pour rejoindre
 * l'extrémité droite de la ligne (`Arrangement.SpaceBetween`) ; le slider
 * lui-même est encadré d'icônes de repère (mini/maxi, `onSurfaceVariant`),
 * piste allégée à 4dp et partie inactive discrète (`surfaceVariant`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    minIcon: AppSymbol,
    maxIcon: AppSymbol,
    displayFormatter: (Float) -> String = { it.toInt().toString() },
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(
                text = displayFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            AppIcon(
                minIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
                    )
                },
                // Le curseur M3 par défaut (1.3+) est une barre pilule 4×44dp —
                // remplacé par un petit cercle plein, plus discret sur une piste
                // fine. Le cercle visuel (20dp) reste centré dans une cible
                // tactile de 48dp, la zone de toucher ne rétrécit pas avec lui.
                thumb = {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "$label, valeur ${displayFormatter(value)}" },
            )
            AppIcon(
                maxIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lot 6 — Stepper −/+ par pas, désactivé visuellement quand enabled=false.
 * Refonte — de vrais `IconButton` (48x48dp, `AppSymbol.Remove`/`Add`)
 * remplacent les boutons texte "−"/"+" ; la valeur est encadrée par les deux.
 * Correctif — le libellé porte `Modifier.weight(1f)` pour prendre tout
 * l'espace disponible et repousser le stepper à droite ; le stepper ne
 * porte lui aucune largeur minimale forcée (`wrapContentWidth`, sa taille
 * suit son propre contenu) — l'ancien `widthIn(min = 152.dp)` sur le
 * stepper entrait en conflit avec le `weight(1f)` du libellé sur les
 * écrans étroits et écrasait le texte caractère par caractère.
 */
@Composable
private fun StepperSetting(
    label: String,
    value: Int,
    step: Int,
    range: IntRange,
    enabled: Boolean,
    displayFormatter: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Le titre prend l'espace disponible et pousse le stepper à droite.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        // Le stepper ne prend que la largeur dont il a besoin.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(),
        ) {
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = enabled && value > range.first,
            ) { AppIcon(AppSymbol.Remove, contentDescription = "Diminuer $label") }
            Text(
                text = displayFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = enabled && value < range.last,
            ) { AppIcon(AppSymbol.Add, contentDescription = "Augmenter $label") }
        }
    }
}

/**
 * Lot 6, Palier B — en-tête de la carte Prononciation avec compteur et
 * bouton `+` carré, distincte de [SectionGroup] (qui n'a qu'un titre simple).
 */
@Composable
private fun PronunciationCard(
    rules: List<PronunciationRule>,
    onAddClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dictionnaire phonétique (${rules.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onAddClick) {
                AppIcon(AppSymbol.Add, contentDescription = "Ajouter une règle de prononciation")
            }
        }
        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** Lot 6, Palier B — une règle : `original → remplacement`, badge `regex`, toggle, suppression. */
@Composable
private fun PronunciationRuleRow(
    rule: PronunciationRule,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(rule.originalText, style = MaterialTheme.typography.bodyLarge)
            AppIcon(
                AppSymbol.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(rule.replacementText, style = MaterialTheme.typography.bodyLarge)
            if (rule.isRegex) {
                Text(
                    "regex",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Switch(checked = rule.isEnabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) {
            AppIcon(AppSymbol.Delete, contentDescription = "Supprimer la règle ${rule.originalText}")
        }
    }
}

/**
 * Lot 6, Palier B — dialogue modal ajout/édition, titre seul change entre
 * les deux cas ([editing] null = ajout).
 */
@Composable
private fun PronunciationRuleDialog(
    editing: PronunciationRule?,
    onSave: (originalText: String, replacementText: String, isRegex: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var originalText by remember { mutableStateOf(editing?.originalText ?: "") }
    var replacementText by remember { mutableStateOf(editing?.replacementText ?: "") }
    var isRegex by remember { mutableStateOf(editing?.isRegex ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Ajouter une règle" else "Modifier la règle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text("Texte d'origine") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Prononciation de remplacement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = isRegex, onValueChange = { isRegex = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isRegex, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Expression régulière (Regex)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(originalText, replacementText, isRegex) },
                enabled = originalText.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Lot 6, Palier B — dialogue de confirmation générique pour les actions
 * destructives (import, vidage du cache, réinitialisation). `destructive`
 * colore le bouton de confirmation en `colorScheme.error`.
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Lot 6 — Dialog de sélection générique.
 * Correction (audit initial) : "Annuler" est dans dismissButton, pas confirmButton.
 * La sélection au clic est défendable ; le libellé dans le mauvais slot ne l'était pas.
 */

/** Lot 14 — libellé lisible du moteur TTS (jamais `enum.name` brut, K12 : pas d'emoji). */
private fun ttsEngineLabel(engine: TtsEngineId): String = when (engine) {
    // Lot 20 — upmc-medium remplace Kokoro.
    TtsEngineId.SHERPA_ONNX -> "Sherpa-ONNX (UPMC)"
    TtsEngineId.ANDROID_NATIVE -> "Voix système"
    TtsEngineId.EDGE_TTS -> "Edge (cloud)"
    TtsEngineId.PIPER -> "Piper (indisponible)"
}

/** Lot 14 — description des capacités par moteur (§8.10 : signaler pertes/gains, jamais silencieux). */
private fun ttsEngineDescription(engine: TtsEngineId): String = when (engine) {
    TtsEngineId.SHERPA_ONNX -> "Voix neuronale locale · surlignage mot à mot"
    TtsEngineId.ANDROID_NATIVE -> "Voix du système · hors ligne · surlignage mot à mot"
    TtsEngineId.EDGE_TTS -> "En ligne — nécessite une connexion. Repli automatique vers une voix locale hors connexion."
    TtsEngineId.PIPER -> ""
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(label(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Lot 6 — Dialog curseur pour l'objectif quotidien (10–120 min). */
@Composable
private fun GoalPickerDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objectif quotidien de lecture") },
        text = {
            Column {
                Text("${value.toInt()} minutes par jour")
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 10f..120f,
                    modifier = Modifier.semantics {
                        contentDescription = "Objectif, valeur ${value.toInt()} minutes"
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10 min", style = MaterialTheme.typography.labelSmall)
                    Text("120 min", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toInt()) }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
