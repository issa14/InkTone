package com.inktone.feature.settings

import com.inktone.core.designsystem.InkToneSectionHeader
import android.os.Build
import com.inktone.core.designsystem.formatMultiplier
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneSlider

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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Cinq sections (audit UI P1-12) : Narration (règles de prononciation
 * comprises, en une ligne vers leur écran) · Apparence (profil
 * Accessibilité compris) · Habitudes de lecture · Données · Confidentialité.
 * Lignes en `ListItem` M3 : hauteurs et marges de 16 dp fixées par le
 * composant, plus par chaque ligne maison.
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
    focusVoiceDownload: Boolean = false,
    onBack: () -> Unit = {},
    dataOperationResult: DataOperationResult? = null,
    onDismissDataOperationResult: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onOpenPronunciationRules: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppSymbol.Back, contentDescription = "Retour")
                    }
                },
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
                pitchEngines = state.pitchEngines,
                coverRegeneration = state.coverRegeneration,
                coverStatus = state.coverStatus,
                focusVoiceDownload = focusVoiceDownload,
                onIntent = viewModel::onIntent,
                dataOperationResult = dataOperationResult,
                onDismissDataOperationResult = onDismissDataOperationResult,
                onExportData = onExportData,
                onImportData = onImportData,
                onOpenPronunciationRules = onOpenPronunciationRules,
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
    pitchEngines: Set<TtsEngineId> = emptySet(),
    coverRegeneration: CoverProgress? = null,
    coverStatus: String? = null,
    focusVoiceDownload: Boolean = false,
    onIntent: (SettingsIntent) -> Unit,
    dataOperationResult: DataOperationResult? = null,
    onDismissDataOperationResult: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onOpenPronunciationRules: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    // Arrivée depuis la proposition du Lecteur : la page défile jusqu'à la voix
    // neuronale au lieu de s'ouvrir en haut (audit UX A7).
    val scrolledContent = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var voiceDownloadRow by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(focusVoiceDownload, voiceDownloadRow != null) {
        if (!focusVoiceDownload) return@LaunchedEffect
        val row = voiceDownloadRow ?: return@LaunchedEffect
        val content = scrolledContent[0] ?: return@LaunchedEffect
        scrollState.animateScrollTo(content.localPositionOf(row, Offset.Zero).y.toInt())
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .onGloballyPositioned { scrolledContent[0] = it },
    ) {

        // Reflète l'état réel : il se désactive dès qu'un des réglages du profil change.
        val isAccessibilityPresetActive = preferences.fontFamily == FontFamily.OPEN_DYSLEXIC &&
            preferences.fontSize == 24 &&
            preferences.theme == ReadingTheme.PAPIER_CLAIR.id &&
            preferences.reduceMotion &&
            preferences.libraryLayoutMode == "LIST"

        // ── Narration ──
        var showEnginePicker by remember { mutableStateOf(false) }
        var showVoicePicker by remember { mutableStateOf(false) }

        val activeVoiceProfile = voiceProfiles.find { it.id == preferences.activeVoiceProfileId }
        val activeVoiceName = activeVoiceProfile?.voice?.let(::voiceLabel) ?: "Voix par défaut"
        val activeSpeed = activeVoiceProfile?.speed ?: 1.0f
        val activePitch = activeVoiceProfile?.pitch ?: 1.0f
        // Le moteur qui parlera : celui du profil actif (SettingsViewModel
        // .setDefaultTtsEngine les garde alignés), sinon celui des préférences.
        val selectedEngine = activeVoiceProfile?.engine ?: preferences.defaultTtsEngine

        SectionGroup("Narration") {
            SettingRow(
                label = "Type de voix",
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
                modifier = Modifier.padding(horizontal = 16.dp),
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
            Box(Modifier.onGloballyPositioned { if (voiceDownloadRow == null) voiceDownloadRow = it }) {
                VoiceDownloadRow(
                    progress = voiceDownloadProgress,
                    onStart = { onIntent(SettingsIntent.StartVoiceDownload) },
                    onCancel = { onIntent(SettingsIntent.CancelVoiceDownload) },
                )
            }
            // Lot 6 — vitesse : même cible VoiceProfile.speed que le panneau Voix du lecteur (Tâche 3d).
            InkToneSlider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                label = "Vitesse d'élocution",
                value = activeSpeed.coerceIn(VoiceProfile.SPEED_RANGE),
                range = VoiceProfile.SPEED_RANGE,
                minIcon = AppSymbol.SlowMotionVideo,
                maxIcon = AppSymbol.FastForward,
                displayFormatter = ::formatMultiplier,
                onValueChange = { onIntent(SettingsIntent.SetVoiceSpeed(it)) },
            )
            // Audit UX A15 — curseur « Gain audio » retiré : aucun moteur ni
            // lecteur audio ne lisait `audioGain`. Un gain au-delà de 1,0×
            // demande une amplification PCM avec limiteur, à concevoir à part.
            // Audit UX A16 — intonation proposée seulement pour un moteur qui
            // l'applique réellement (TtsCapabilities.pitchControl : voix
            // système et Edge). Sherpa-ONNX (VITS) ne règle que la durée.
            if (selectedEngine in pitchEngines) {
                InkToneSlider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    label = "Intonation",
                    value = activePitch.coerceIn(VoiceProfile.PITCH_RANGE),
                    range = VoiceProfile.PITCH_RANGE,
                    minIcon = AppSymbol.KeyboardArrowDown,
                    maxIcon = AppSymbol.KeyboardArrowUp,
                    displayFormatter = ::formatMultiplier,
                    onValueChange = { onIntent(SettingsIntent.SetVoicePitch(it)) },
                )
            }
            // Une ligne vers l'écran dédié plutôt qu'une carte de plus (audit UI P1-12).
            SettingRow(
                label = "Règles de prononciation",
                value = pronunciationRuleCountText(pronunciationRules.size),
                onClick = onOpenPronunciationRules,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Apparence ──
        var showAppThemePicker by remember { mutableStateOf(false) }

        SectionGroup("Apparence") {
            // ─ Apparence ─
            // Lot 6 — thème système de l'app (SYSTEM/LIGHT/DARK).
            // Ne pas confondre avec preferences.theme (ReadingTheme de lecture).
            SettingRow(
                label = "Thème de l'interface",
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
            // Les couleurs dynamiques n'existent qu'à partir d'Android 12
            // (InkToneTheme les ignore en dessous) : l'interrupteur n'y
            // aurait aucun effet.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleSetting(
                    label = "Couleurs dynamiques",
                    description = "Reprend les couleurs de votre fond d'écran.",
                    checked = preferences.dynamicColorEnabled,
                    onCheckedChange = { onIntent(SettingsIntent.SetDynamicColorEnabled(it)) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ─ Accessibilité ─
            ToggleSetting(
                label = "Profil Accessibilité",
                description = "Police pour la dyslexie, grand texte, animations réduites, vue en liste",
                checked = isAccessibilityPresetActive,
                onCheckedChange = { onIntent(SettingsIntent.SetAccessibilityPreset(it)) },
            )
            ToggleSetting(
                label = "Réduire les animations",
                checked = preferences.reduceMotion,
                onCheckedChange = { onIntent(SettingsIntent.SetReduceMotion(it)) },
            )
            // Audit UX A2 — interrupteur « Police adaptée au système » retiré :
            // la valeur n'était lue nulle part. Tous les textes (interface et
            // lecture, en sp) suivent déjà l'échelle de police du système.

        }

        // ── Habitudes de lecture ──
        var showGoalPicker by remember { mutableStateOf(false) }

        SectionGroup("Habitudes de lecture") {
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
                label = "Intervalle du rappel",
                value = preferences.eyeRestReminderIntervalMinutes,
                step = 15,
                range = UserPreferences.EYE_REST_INTERVAL_RANGE,
                enabled = preferences.eyeRestReminderEnabled,
                displayFormatter = { "$it min" },
                onValueChange = { onIntent(SettingsIntent.SetEyeRestReminderIntervalMinutes(it)) },
            )
        }

        // ── Données ──
        var showImportWarning by remember { mutableStateOf(false) }
        var showClearCacheConfirm by remember { mutableStateOf(false) }
        var showResetConfirm by remember { mutableStateOf(false) }
        var showRestoreCoversConfirm by remember { mutableStateOf(false) }

        SectionGroup("Données") {
            // Action directe, sans confirmation — n'écrase rien de local.
            ActionRow(
                icon = AppSymbol.Upload,
                title = "Exporter les données",
                subtitle = "Signets, notes, positions, statistiques et thèmes, sans les livres.",
                onClick = onExportData,
            )
            // Avertissement préalable — l'import écrase la configuration courante.
            ActionRow(
                icon = AppSymbol.Download,
                title = "Importer une sauvegarde",
                subtitle = "Ajoute les données d'un fichier de sauvegarde.",
                onClick = { showImportWarning = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Taille réelle calculée (Context.cacheDir), pas estimée.
            ListItem(
                headlineContent = { Text("Vider le cache") },
                supportingContent = { Text("${formatBytes(cacheSizeBytes)} occupés") },
                trailingContent = {
                    TextButton(onClick = { showClearCacheConfirm = true }, enabled = cacheSizeBytes > 0) {
                        AppIcon(AppSymbol.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Vider")
                    }
                },
                colors = settingsItemColors(),
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Maintenance des couvertures : opérations rares, rangées ici plutôt
            // qu'au même rang qu'« Importer » dans la Bibliothèque (P1-17).
            ActionRow(
                icon = AppSymbol.Refresh,
                title = "Régénérer les couvertures",
                subtitle = coverRegeneration?.let { "En cours… ${it.processed} sur ${it.total}" }
                    ?: coverStatus
                    ?: "Extrait de nouveau la couverture de chaque livre.",
                onClick = { if (coverRegeneration == null) onIntent(SettingsIntent.RegenerateCovers) },
            )
            ActionRow(
                icon = AppSymbol.CoverOnly,
                title = "Rétablir les couvertures d'origine",
                subtitle = "Remplace les couvertures par leur version par défaut.",
                onClick = { showRestoreCoversConfirm = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Couleur d'alerte + icône triangle : signale le côté destructif.
            ActionRow(
                icon = AppSymbol.WarningAmber,
                title = "Réinitialiser les réglages",
                subtitle = "Vos livres et vos données ne sont pas touchés.",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showResetConfirm = true },
            )
        }

        SectionGroup("Confidentialité") {
            // Audit UX A1 — ligne « Langue de l'interface » retirée : aucune
            // traduction n'existe (libellés français en dur), et la valeur
            // choisie devenait la langue des profils vocaux créés ensuite.
            // Elle reviendra avec l'internationalisation.
            // Lot 10, Tâche 10.5 — formulation honnête exigée par ADR-014
            // (« avec une explication honnête de son contenu »). Seul
            // point de consentement restant depuis le retrait de l'étape
            // d'onboarding CrashConsent (Tâche 10.3) : cette description
            // doit porter l'explication à elle seule.
            ToggleSetting(
                label = "Rapports de plantage",
                description = "Envoie uniquement la trace d'erreur, la version de l'app et le modèle d'appareil. " +
                    "Jamais le contenu de vos livres ni vos annotations.",
                checked = preferences.crashReportingEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetCrashReportingEnabled(it)) },
            )
        }


        // ── Dialogues ──────────────────────────────────────────────────────────
        if (showEnginePicker) {
            PickerDialog(
                title = "Type de voix",
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
                // Décrit ce que fait réellement BackupManager.importFrom : il
                // n'efface rien. Signets, notes, surlignages et statistiques
                // absents sont ajoutés, ceux déjà présents conservés ; les
                // positions, règles et thèmes du fichier écrasent les leurs.
                message = "Les signets, notes et statistiques du fichier s'ajoutent aux vôtres. " +
                    "Les positions de lecture, règles et thèmes du fichier remplacent ceux de l'appareil.",
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
        if (showRestoreCoversConfirm) {
            ConfirmDialog(
                title = "Rétablir les couvertures d'origine ?",
                message = "Chaque livre reprend sa couverture par défaut. Vous pourrez les régénérer à tout moment.",
                confirmLabel = "Rétablir",
                destructive = true,
                onConfirm = { showRestoreCoversConfirm = false; onIntent(SettingsIntent.RestoreDefaultCovers) },
                onDismiss = { showRestoreCoversConfirm = false },
            )
        }
        if (showResetConfirm) {
            ConfirmDialog(
                title = "Réinitialiser les réglages",
                message = "Tous les réglages reviennent à leurs valeurs par défaut. " +
                    "Vos livres, signets et notes ne sont pas touchés.",
                confirmLabel = "Réinitialiser",
                destructive = true,
                onConfirm = { showResetConfirm = false; onIntent(SettingsIntent.ResetPreferences) },
                onDismiss = { showResetConfirm = false },
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
        val restored = result.restored
        append(if (restored > 1) "$restored éléments restaurés." else "$restored élément restauré.")
        val skipped = result.skippedOrphans
        if (skipped > 0) {
            append(
                if (skipped > 1) " $skipped éléments concernaient des livres absents de la bibliothèque."
                else " 1 élément concernait un livre absent de la bibliothèque.",
            )
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
        InkToneSectionHeader(title)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** Fond de la carte sous chaque ligne : `ListItem` peindrait sinon sa propre surface. */
@Composable
private fun settingsItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Réglage à choix : la valeur courante en sous-titre, le choix dans un dialogue. */
@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        colors = settingsItemColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Ligne d'action avec icône, titre et sous-texte optionnel (carte Données).
 * `tint` : `colorScheme.error` pour une action destructive.
 */
@Composable
private fun ActionRow(
    icon: AppSymbol,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { AppIcon(icon, contentDescription = null, tint = tint) },
        colors = settingsItemColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
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

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            when (progress) {
                is com.inktone.domain.service.VoiceDownloadProgress.InProgress ->
                    "Téléchargement : ${formatMegabytes(progress.bytesDownloaded)} / ${formatMegabytes(progress.totalBytes)}"
                is com.inktone.domain.service.VoiceDownloadProgress.Failed -> "Le téléchargement a échoué. Vérifiez la connexion et réessayez."
                com.inktone.domain.service.VoiceDownloadProgress.Complete -> "Voix naturelle installée"
                null -> "Voix naturelle (Jessica et Pierre) · 183 Mo"
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
                    Text("Télécharger la voix naturelle")
                }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Télécharger la voix naturelle ?") },
            text = {
                Text(
                    "183 Mo, une seule fois. Elle fonctionne ensuite sans connexion " +
                        "et surligne chaque mot prononcé.",
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

/** Nombre de règles de prononciation, sous la ligne qui ouvre leur écran. */
internal fun pronunciationRuleCountText(count: Int): String = when (count) {
    0 -> "Aucune règle"
    1 -> "1 règle"
    else -> "$count règles"
}

/** Retour Issa : Mo lisibles plutôt que des octets bruts ("345566/132111222"). */
private fun formatMegabytes(bytes: Long): String = "%.1f Mo".format(bytes / 1_000_000.0)

/**
 * Ligne entière basculable (rôle Switch, libellé fusionné) plutôt que le
 * seul Switch : TalkBack annonce « Rapports de plantage, interrupteur ».
 */
@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = settingsItemColors(),
        modifier = Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    )
}

/** Stepper −/+ par pas, grisé quand [enabled] est faux. */
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

    ListItem(
        headlineContent = { Text(label, color = contentColor) },
        // Le stepper ne prend que sa largeur : le libellé garde le reste.
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((value - step).coerceIn(range)) },
                    enabled = enabled && value > range.first,
                ) { AppIcon(AppSymbol.Remove, contentDescription = "Diminuer $label") }
                Text(
                    text = displayFormatter(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                IconButton(
                    onClick = { onValueChange((value + step).coerceIn(range)) },
                    enabled = enabled && value < range.last,
                ) { AppIcon(AppSymbol.Add, contentDescription = "Augmenter $label") }
            }
        },
        colors = settingsItemColors(),
    )
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
                text = "Règles de prononciation (${rules.size})",
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
    val problem = PronunciationRule.problemOf(originalText, replacementText, isRegex)
    val problemMessage = problem?.message()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Ajouter une règle" else "Modifier la règle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text("Mot ou expression") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Prononcer comme") },
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
                    Text("Expression régulière")
                }
                if (problemMessage != null) {
                    Text(problemMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(originalText, replacementText, isRegex) },
                enabled = problem == null,
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
    TtsEngineId.SHERPA_ONNX -> "Voix naturelle"
    TtsEngineId.ANDROID_NATIVE -> "Voix du téléphone"
    TtsEngineId.EDGE_TTS -> "Voix en ligne"
    TtsEngineId.PIPER -> "Piper (indisponible)"
}

/** Lot 14 — description des capacités par moteur (§8.10 : signaler pertes/gains, jamais silencieux). */
private fun ttsEngineDescription(engine: TtsEngineId): String = when (engine) {
    TtsEngineId.SHERPA_ONNX -> "Hors ligne. Chaque mot prononcé est surligné."
    TtsEngineId.ANDROID_NATIVE -> "Hors ligne. Mot à mot si le téléphone le permet, sinon phrase par phrase."
    // Confidentialité : dire où part le texte, pas seulement qu'il faut du réseau.
    TtsEngineId.EDGE_TTS -> "Le texte est envoyé à Microsoft. Sans connexion, une voix hors ligne prend le relais."
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
                InkToneSlider(
                    label = "Minutes par jour",
                    value = value,
                    range = 10f..120f,
                    onValueChange = { value = it },
                    displayFormatter = { "${it.toInt()} min" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10 min", style = MaterialTheme.typography.labelSmall)
                    Text("120 min", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toInt()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Carte des règles de prononciation — liste, ajout, modification,
 * suppression confirmée. Partagée par les Réglages et l'écran ouvert depuis
 * le panneau Voix du Lecteur (audit UX B8 : ce dernier était une ébauche,
 * titre en double, sans modification, suppression directe).
 */
@Composable
internal fun PronunciationRulesSection(rules: List<PronunciationRule>, onIntent: (SettingsIntent) -> Unit) {
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<PronunciationRule?>(null) }
    // Audit UX C7 : la corbeille, voisine de l'interrupteur, supprimait
    // la règle en un tap ; elle demande désormais confirmation.
    var ruleToDelete by remember { mutableStateOf<PronunciationRule?>(null) }
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Supprimer cette règle ?") },
            text = { Text("« ${rule.originalText} » → « ${rule.replacementText} »") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ruleToDelete = null
                        onIntent(SettingsIntent.DeletePronunciationRule(rule.id))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text("Annuler") } },
        )
    }

    PronunciationCard(
        rules = rules,
        onAddClick = { editingRule = null; showRuleDialog = true },
    ) {
        if (rules.isEmpty()) {
            Text(
                "Aucune règle. Exemple : « M. » prononcé « Monsieur ».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            rules.forEachIndexed { index, rule ->
                PronunciationRuleRow(
                    rule = rule,
                    onClick = { editingRule = rule; showRuleDialog = true },
                    onToggle = { onIntent(SettingsIntent.TogglePronunciationRule(rule)) },
                    onDelete = { ruleToDelete = rule },
                )
                if (index != rules.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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
}
