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
import androidx.compose.material3.Slider
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
    previewText: String,
    previewTextColor: Color,
    previewBackgroundColor: Color,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
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
                maxLines = 5,
            )

            Spacer(Modifier.height(24.dp))

            // ── Taille du texte — curseur continu (3d.2 : plus de paliers) ──
            Text("Taille du texte (${fontSizeDraft.roundToInt()}sp)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fontSizeDraft,
                onValueChange = { fontSizeDraft = it },
                onValueChangeFinished = { onFontSizeChange(fontSizeDraft.roundToInt()) },
                valueRange = 12f..32f,
                steps = 0,
            )

            Spacer(Modifier.height(20.dp))

            // ── Interligne — 3d.2 : seul vrai ajout de modèle du lot ──
            Text("Interligne (${"%.1f".format(lineHeightDraft)}×)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = lineHeightDraft,
                onValueChange = { lineHeightDraft = it },
                onValueChangeFinished = { onLineHeightChange(lineHeightDraft) },
                valueRange = 1.0f..2.0f,
                steps = 0,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
