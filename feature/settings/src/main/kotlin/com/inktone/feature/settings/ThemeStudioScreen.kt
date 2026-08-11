package com.inktone.feature.settings
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.toColor

/**
 * Lot 9, Tâche 9.5 — Studio de thème personnalisé (UX §Studio de thème
 * personnalisé). `themeId` null = création, non-null = édition (ouvert
 * depuis l'icône crayon de la Galerie).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    themeId: String?,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ThemeStudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var colorFieldBeingEdited by remember { mutableStateOf<ColorField?>(null) }

    LaunchedEffect(themeId) { viewModel.onIntent(ThemeStudioIntent.Load(themeId)) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ThemeStudioEffect.SavedAndClose -> onSaved()
                is ThemeStudioEffect.DeletedAndClose -> onDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Studio de Thème") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Retour")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.onIntent(ThemeStudioIntent.Save) }) {
                        Text("Sauvegarder")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            StudioLivePreview(state)

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.onIntent(ThemeStudioIntent.SetName(it)) },
                        label = { Text("Nom du thème") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    ColorSelectorRow("Fond de page", state.backgroundColorHex) { colorFieldBeingEdited = ColorField.BACKGROUND }
                }
                item {
                    ColorSelectorRow("Texte principal", state.textColorHex) { colorFieldBeingEdited = ColorField.TEXT }
                }
                item {
                    ColorSelectorRow("Accent & Progression", state.accentColorHex) { colorFieldBeingEdited = ColorField.ACCENT }
                }
                item {
                    ColorSelectorRow("Surlignage d'annotation", state.highlightColorHex) { colorFieldBeingEdited = ColorField.HIGHLIGHT }
                }
                item {
                    Column {
                        Text("Palette de départ", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StarterPalette.entries) { palette ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.onIntent(ThemeStudioIntent.ApplyStarterPalette(palette)) },
                                    label = { Text(palette.label()) },
                                )
                            }
                        }
                    }
                }
                if (state.editingThemeId != null) {
                    item {
                        Column {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                AppIcon(AppSymbol.Delete,  contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("Supprimer ce thème", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    colorFieldBeingEdited?.let { field ->
        val currentHex = when (field) {
            ColorField.BACKGROUND -> state.backgroundColorHex
            ColorField.TEXT -> state.textColorHex
            ColorField.ACCENT -> state.accentColorHex
            ColorField.HIGHLIGHT -> state.highlightColorHex
        }
        ColorPickerDialog(
            initialHex = currentHex,
            onDismiss = { colorFieldBeingEdited = null },
            onConfirm = { hex ->
                val intent = when (field) {
                    ColorField.BACKGROUND -> ThemeStudioIntent.SetBackgroundColor(hex)
                    ColorField.TEXT -> ThemeStudioIntent.SetTextColor(hex)
                    ColorField.ACCENT -> ThemeStudioIntent.SetAccentColor(hex)
                    ColorField.HIGHLIGHT -> ThemeStudioIntent.SetHighlightColor(hex)
                }
                viewModel.onIntent(intent)
                colorFieldBeingEdited = null
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer ce thème ?") },
            text = { Text("Si ce thème est actif, la lecture repasse automatiquement sur Papier Clair. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onIntent(ThemeStudioIntent.ConfirmDelete)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } },
        )
    }
}

private enum class ColorField { BACKGROUND, TEXT, ACCENT, HIGHLIGHT }

private fun StarterPalette.label(): String = when (this) {
    StarterPalette.SOMBRE -> "Sombre"
    StarterPalette.CLAIR -> "Clair"
    StarterPalette.CHAUD -> "Chaud"
    StarterPalette.NEON -> "Néon"
}

/**
 * Aperçu dynamique en direct (Tâche 9.5) — mis à jour à chaque changement
 * de couleur puisqu'il lit directement `state`, pas une copie figée. Le
 * mot surligné inline démontre la couleur de surlignage en contexte,
 * pas une pastille séparée.
 */
@Composable
private fun StudioLivePreview(state: ThemeStudioUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(state.backgroundColorHex.toColor())
            .padding(16.dp),
    ) {
        WcagBadge(state)
        Spacer(Modifier.height(12.dp))
        Text(
            "Chapitre 3 — L'Arrivée",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = state.textColorHex.toColor(),
        )
        Spacer(Modifier.height(8.dp))
        Column {
            Text(
                "La lumière filtrait doucement à travers les persiennes, dessinant des",
                style = MaterialTheme.typography.bodyMedium,
                color = state.textColorHex.toColor(),
            )
            Row {
                Text("rayures d'or sur le ", style = MaterialTheme.typography.bodyMedium, color = state.textColorHex.toColor())
                Text(
                    "parquet ancien",
                    style = MaterialTheme.typography.bodyMedium,
                    color = state.textColorHex.toColor(),
                    modifier = Modifier
                        .background(state.highlightColorHex.toColor().copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp),
                )
                Text(", immobile.", style = MaterialTheme.typography.bodyMedium, color = state.textColorHex.toColor())
            }
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { 0.35f },
            modifier = Modifier.fillMaxWidth(),
            color = state.accentColorHex.toColor(),
            trackColor = state.accentColorHex.toColor().copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "35% · Page 42",
            style = MaterialTheme.typography.labelSmall,
            color = state.textColorHex.toColor().copy(alpha = 0.7f),
        )
    }
}

/**
 * Badge WCAG calculé en direct — informatif, JAMAIS bloquant (Tâche 9.5,
 * décision actée à la lettre) : le bouton Sauvegarder reste actif quel
 * que soit le ratio, ce badge ne fait qu'avertir sous le seuil.
 */
@Composable
private fun WcagBadge(state: ThemeStudioUiState) {
    val (label, color, icon) = when (state.wcagLevel) {
        WcagLevel.AAA -> Triple("WCAG AAA (%.1f:1)".format(state.contrastRatio), Color4Ok, AppIcons.Success)
        WcagLevel.AA -> Triple("WCAG AA (%.1f:1)".format(state.contrastRatio), Color4Ok, AppIcons.Success)
        WcagLevel.BELOW_THRESHOLD -> Triple("Contraste faible (%.1f:1)".format(state.contrastRatio), MaterialTheme.colorScheme.error, AppIcons.Warning)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
    if (state.wcagLevel == WcagLevel.BELOW_THRESHOLD) {
        Text(
            "Ce thème peut être difficile à lire pour certaines personnes — vous pouvez tout de même l'enregistrer.",
            style = MaterialTheme.typography.labelSmall,
            color = state.textColorHex.toColor().copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val Color4Ok = androidx.compose.ui.graphics.Color(0xFF2E7D32)

@Composable
private fun ColorSelectorRow(label: String, hex: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hex.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(hex.toColor()),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClick) {
            AppIcon(AppSymbol.Edit,  contentDescription = "Modifier $label")
        }
    }
}

private val PRESET_SWATCHES = listOf(
    "#FFFFFF", "#000000", "#F4ECD8", "#1976D2", "#E64A19", "#2E7D32", "#FFEB3B", "#FF00E5",
)

@Composable
private fun ColorPickerDialog(initialHex: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var draft by remember { mutableStateOf(initialHex) }
    val isValid = remember(draft) { Regex("^#[0-9A-Fa-f]{6}$").matches(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une couleur") },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PRESET_SWATCHES) { swatch ->
                        val isSelected = swatch.equals(draft, ignoreCase = true)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) {
                                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clip(CircleShape)
                                .background(swatch.toColor())
                                .clickable { draft = swatch },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Valeur hexadécimale") },
                    isError = !isValid,
                    singleLine = true,
                    supportingText = { if (!isValid) Text("Format attendu : #RRGGBB") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onConfirm(draft) }) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
