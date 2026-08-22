package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.inktone.core.designsystem.InkToneSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.inktone.domain.model.UserPreferences
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    previewText: String,
    previewTextColor: Color,
    previewBackgroundColor: Color,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onMarginStepChange: (Int) -> Unit,
    onTextJustifiedChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fontSizeDraft by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }
    var lineHeightDraft by remember(currentLineHeightMultiplier) { mutableFloatStateOf(currentLineHeightMultiplier) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            Spacer(Modifier.height(32.dp))
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
