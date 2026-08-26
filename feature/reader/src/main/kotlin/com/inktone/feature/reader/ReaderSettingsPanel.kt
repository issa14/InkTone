package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.core.designsystem.InkToneSlider
import com.inktone.domain.model.FontFamily as DomainFontFamily
import com.inktone.domain.model.UserPreferences
import kotlin.math.roundToInt

/**
 * 3d.2 — Panneau de typographie seule (taille, interligne) : le thème n'y
 * vit plus, la bascule cyclique du lot 3b (icône Thème du panneau unifié)
 * l'a rendu redondant. Curseurs continus (`steps = 0`, avant : paliers de
 * taille discrets) avec un aperçu en direct du texte RÉELLEMENT en cours
 * de lecture — jamais un exemple statique, c'est l'intérêt de la cible
 * (voir doc du lot 3d, tâche 3d.2, et `UX_FLOW_DESIGN.md` §TT).
 *
 * L'aperçu suit la valeur *en cours de drag* du slider (état local), pas
 * seulement la valeur persistée : `onValueChangeFinished` déclenche
 * l'intent réel (`SetOverrides`/`SetLineHeight`) au relâchement, pour ne
 * pas spammer la persistance/la repagination à chaque pixel glissé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsPanel(
    currentFontSize: Int,
    currentLineHeightMultiplier: Float,
    // P4 — confort de lecture visuelle : le panneau n'exposait que la taille
    // et l'interligne.
    currentMarginStep: Int,
    isTextJustified: Boolean,
    keepScreenOn: Boolean,
    // Correctif Lot 21 — jusqu'ici aucun écran ne dispatchait
    // `SettingsIntent.SetFontFamily` : OpenDyslexic (hors préréglage
    // d'accessibilité) et Source Serif 4 étaient rendues mais
    // inatteignables. Valeurs par défaut pour ne pas casser les appelants
    // existants (tests inclus).
    currentFontFamily: DomainFontFamily = DomainFontFamily.DEFAULT,
    // Lot 21, tâche 9 — auto-scroll visuel (vitesse réglable, 0 = off).
    autoScrollSpeed: Int,
    reduceMotion: Boolean,
    isScrollMode: Boolean,
    previewText: String,
    previewTextColor: Color,
    previewBackgroundColor: Color,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onMarginStepChange: (Int) -> Unit,
    onTextJustifiedChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoScrollSpeedChange: (Int) -> Unit,
    onFontFamilyChange: (DomainFontFamily) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fontSizeDraft by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }
    var lineHeightDraft by remember(currentLineHeightMultiplier) { mutableFloatStateOf(currentLineHeightMultiplier) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Correctif — le panneau TT débordait de l'écran depuis
                // l'ajout du sélecteur de police (Lot 21, tâche 10) et de
                // l'auto-scroll (tâche 9) : justification, écran allumé et
                // auto-scroll étaient coupés en bas, inaccessibles. Le
                // scroll vertical ne gêne ni les Sliders (drag horizontal)
                // ni les segments de choix.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Typographie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // ── Aperçu en direct — le vrai texte du chapitre en cours ──
            Text(
                previewText,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(previewBackgroundColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                color = previewTextColor,
                fontSize = fontSizeDraft.sp,
                lineHeight = (fontSizeDraft * lineHeightDraft).sp,
                // L'aperçu montre AUSSI la justification : un aperçu qui
                // ignorerait un réglage du même panneau mentirait sur son
                // effet, ce qui est pire que pas d'aperçu du tout.
                textAlign = if (isTextJustified) TextAlign.Justify else TextAlign.Unspecified,
                maxLines = 5,
            )

            Spacer(Modifier.height(24.dp))

            // ── Taille du texte — curseur continu (3d.2 : plus de paliers) ──
            // `onValueChangeFinished` conservé : appliquer chaque valeur
            // intermédiaire repaginerait le chapitre à chaque pixel du geste.
            InkToneSlider(
                label = "Taille du texte",
                value = fontSizeDraft,
                range = 12f..32f,
                onValueChange = { fontSizeDraft = it },
                onValueChangeFinished = { onFontSizeChange(fontSizeDraft.roundToInt()) },
                displayFormatter = { "${it.roundToInt()} sp" },
            )

            Spacer(Modifier.height(20.dp))

            // ── Interligne — 3d.2 : seul vrai ajout de modèle du lot ──
            InkToneSlider(
                label = "Interligne",
                value = lineHeightDraft,
                range = 1.0f..2.0f,
                onValueChange = { lineHeightDraft = it },
                onValueChangeFinished = { onLineHeightChange(lineHeightDraft) },
                displayFormatter = { "%.1f×".format(it) },
            )

            Spacer(Modifier.height(20.dp))

            // ── Police — menu déroulant (5 segments à libellés longs
            // débordaient du panneau, rendu grotesque). Seul point
            // d'accès réel à OpenDyslexic (hors préréglage d'accessibilité)
            // et Source Serif 4, mappées mais jusqu'ici inatteignables ──
            SettingsDropdown(
                label = "Police",
                current = currentFontFamily,
                optionLabel = { fontFamilyLabel(it) },
                options = FONT_FAMILY_CHOICES,
                onSelect = onFontFamilyChange,
            )

            Spacer(Modifier.height(20.dp))

            // ── Marges — P4 : trois crans, pas un curseur continu ──
            Text(
                "Marges (${readerMarginLabel(currentMarginStep).lowercase()})",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                UserPreferences.MARGIN_STEP_RANGE.forEachIndexed { index, step ->
                    SegmentedButton(
                        selected = currentMarginStep == step,
                        onClick = { onMarginStepChange(step) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = UserPreferences.MARGIN_STEP_RANGE.count(),
                        ),
                    ) {
                        Text(readerMarginLabel(step))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Justification — la césure vient avec, jamais séparément ──
            SettingSwitchRow(
                label = "Texte justifié",
                supporting = "Aligne les deux bords, avec césure",
                checked = isTextJustified,
                onCheckedChange = onTextJustifiedChange,
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                label = "Garder l'écran allumé",
                supporting = "Pendant la lecture visuelle uniquement",
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
            )

            Spacer(Modifier.height(20.dp))

            // ── Auto-scroll — Lot 21, tâche 9 : vitesse réglable, mode
            // SCROLL uniquement, jamais quand reduceMotion est actif. Menu
            // déroulant (les 4 segments débordaient aussi du panneau) ──
            Text(
                "Auto-scroll",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !isScrollMode -> "Disponible en mode défilement uniquement"
                    reduceMotion -> "Désactivé : le mouvement réduit est actif"
                    else -> "Défilement continu, arrêté au premier toucher"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SettingsDropdown(
                label = "Vitesse",
                current = autoScrollSpeed,
                optionLabel = { autoScrollSpeedLabel(it) },
                options = UserPreferences.AUTO_SCROLL_SPEED_RANGE.toList(),
                onSelect = onAutoScrollSpeedChange,
                enabled = isScrollMode && !reduceMotion,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Correctif Lot 21 — options du sélecteur de police. `DomainFontFamily`
 * ne comporte que 5 valeurs au total (Blueprint : une valeur ajoutée ne
 * se retire jamais) — toutes exposées ici, aucun `when` à maintenir en
 * double.
 */
private val FONT_FAMILY_CHOICES = DomainFontFamily.entries.toList()

/** Correctif Lot 21 — libellé court d'une police pour le menu déroulant. */
private fun fontFamilyLabel(family: DomainFontFamily): String = when (family) {
    DomainFontFamily.DEFAULT -> "Système"
    DomainFontFamily.SERIF -> "Serif"
    DomainFontFamily.SANS_SERIF -> "Sans-serif"
    DomainFontFamily.OPEN_DYSLEXIC -> "Dyslexie"
    DomainFontFamily.SOURCE_SERIF -> "Empattée FR"
}

/**
 * Lot 21, tâche 9 — libellé d'un cran de vitesse d'auto-scroll.
 * `0` = désactivé, puis trois crans croissants.
 */
private fun autoScrollSpeedLabel(speed: Int): String = when (speed) {
    0 -> "Désactivé"
    1 -> "Lente"
    2 -> "Moyenne"
    3 -> "Rapide"
    else -> speed.toString()
}

/**
 * Correctif Lot 21 — ligne de réglage « libellé + valeur ▾ » ouvrant un
 * menu déroulant. Remplace les rangées de segments dont les libellés
 * longs débordaient du panneau (Police à 5 options, Vitesse d'auto-scroll
 * à 4). `enabled = false` affiche la valeur mais n'ouvre pas le menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    label: String,
    current: T,
    optionLabel: (T) -> String,
    options: List<T>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                optionLabel(current),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(4.dp))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Ligne libellé + interrupteur, toute la ligne étant cliquable (cible tactile). */
@Composable
private fun SettingSwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // `onCheckedChange = null` : la ligne entière porte déjà la
        // sémantique d'accessibilité (toggleable ci-dessus) — un second
        // gestionnaire ici annoncerait deux fois le même contrôle.
        Switch(checked = checked, onCheckedChange = null)
    }
}
