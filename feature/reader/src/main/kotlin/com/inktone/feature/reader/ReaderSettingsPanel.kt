package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme

/**
 * B.2 — Panneau de réglages de lecture accessible depuis le Reader.
 * Thème, police, taille, interligne, marges — sans quitter la lecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsPanel(
    currentTheme: ReadingTheme,
    currentFontSize: Int,
    onThemeChange: (ReadingTheme) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Réglages de lecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            // ── Thèmes (cartes visuelles) ──
            Text("Thème", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeCard("Clair", ThemeColors.background(ReadingTheme.LIGHT), ThemeColors.text(ReadingTheme.LIGHT), currentTheme == ReadingTheme.LIGHT) {
                    onThemeChange(ReadingTheme.LIGHT)
                }
                ThemeCard("Sombre", ThemeColors.background(ReadingTheme.DARK), ThemeColors.text(ReadingTheme.DARK), currentTheme == ReadingTheme.DARK) {
                    onThemeChange(ReadingTheme.DARK)
                }
                ThemeCard("Sépia", ThemeColors.background(ReadingTheme.SEPIA), ThemeColors.text(ReadingTheme.SEPIA), currentTheme == ReadingTheme.SEPIA) {
                    onThemeChange(ReadingTheme.SEPIA)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Taille du texte ──
            Text("Taille du texte (${currentFontSize}sp)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = currentFontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 12f..32f,
                steps = 19,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeCard(label: String, bg: Color, textColor: Color, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(bg, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            label,
            modifier = Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
