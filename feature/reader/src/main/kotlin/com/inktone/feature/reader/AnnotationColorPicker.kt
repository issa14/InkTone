package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind

/**
 * Rendu de la couleur pour l'UI (sélecteur ou surlignage d'annotation
 * existante). `AnnotationColor.argb` est déjà un ARGB packé (Lot 23,
 * décision 6) — même représentation binaire que le constructeur `Color(Int)`.
 */
fun AnnotationColor.toComposeColor(): Color = Color(argb)

/**
 * Lot 22, tâche 12 — place [color] en tête, sans doublon, plafonné à
 * [MAX_RECENT_ANNOTATION_COLORS] (la palette entière tiendrait de toute
 * façon dans le sélecteur, ce plafond ne fait que borner explicitement ce
 * qui compte comme « récent »).
 */
fun List<AnnotationColor>.withRecentColor(color: AnnotationColor): List<AnnotationColor> =
    (listOf(color) + filterNot { it == color }).take(MAX_RECENT_ANNOTATION_COLORS)

private const val MAX_RECENT_ANNOTATION_COLORS = 3

/**
 * Style de rendu d'une annotation selon son [AnnotationKind] (Lot 22,
 * tâche 10). Trois canaux visuels restent séparés — jamais mélangés :
 * - l'annotation : fond (surlignage) ou décoration de texte (souligné/barré) ;
 * - le surlignage TTS : [WordHighlightColor] ;
 * - la sélection : [SelectionHighlightColor].
 */
fun annotationSpanStyle(kind: AnnotationKind, color: AnnotationColor): SpanStyle = when (kind) {
    AnnotationKind.HIGHLIGHT -> SpanStyle(background = color.toComposeColor())
    AnnotationKind.UNDERLINE -> SpanStyle(color = color.toComposeColor(), textDecoration = TextDecoration.Underline)
    AnnotationKind.STRIKETHROUGH -> SpanStyle(color = color.toComposeColor(), textDecoration = TextDecoration.LineThrough)
}

/**
 * Défilement horizontal (Tâche 7.1, corrigé après vérification visuelle
 * sur device) : 5 `FilterChip` + 2 `Button` dépassent la largeur d'un
 * écran étroit — un `Row` sans défilement laissait « Surligner »/« Annuler »
 * inatteignables, découvert en testant réellement sur le device connecté,
 * pas supposé correct après compilation seule.
 */
@Composable
fun AnnotationColorPicker(
    selected: AnnotationColor,
    onSelect: (AnnotationColor) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    // Lot 22, tâche 12 — couleurs récemment utilisées (la plus récente en
    // tête, voir `UserPreferences.recentAnnotationColors`), proposées en
    // premier ; le reste de la palette suit dans son ordre habituel.
    recentColors: List<AnnotationColor> = emptyList(),
    // Lot 23, tâche 6 — comble le trou trouvé à la vérification device du
    // Lot 22 : `AnnotationKind` existait (rendu + migration) mais rien ne
    // permettait de le choisir. Pas de Squiggly (décision 2) : seulement
    // les 3 valeurs d'`AnnotationKind`.
    selectedKind: AnnotationKind = AnnotationKind.HIGHLIGHT,
    onSelectKind: (AnnotationKind) -> Unit = {},
) {
    val orderedColors = remember(recentColors) {
        recentColors + AnnotationColor.PRESETS.filterNot { it in recentColors }
    }
    // Lot 23, tâche 9 — état purement local à ce composable : la boîte de
    // dialogue n'a pas besoin de survivre au-delà d'une confirmation/
    // annulation, aucune raison de la faire remonter dans ReaderUiState.
    var showCustomColorDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AnnotationKindOption(AnnotationKind.HIGHLIGHT, AppSymbol.Highlight, "Surlignage", selectedKind, onSelectKind)
            AnnotationKindOption(AnnotationKind.UNDERLINE, AppSymbol.Underline, "Souligné", selectedKind, onSelectKind)
            AnnotationKindOption(AnnotationKind.STRIKETHROUGH, AppSymbol.Strikethrough, "Barré", selectedKind, onSelectKind)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            orderedColors.forEach { color ->
                ColorSwatch(color = color, isSelected = color == selected, onClick = { onSelect(color) })
            }
            CustomColorSwatch(onClick = { showCustomColorDialog = true })
            Button(onClick = onConfirm) { Text("Surligner") }
            Button(onClick = onCancel) { Text("Annuler") }
        }
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            onConfirm = { color ->
                onSelect(color)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false },
        )
    }
}

/**
 * Lot 23, tâche 9 — pastille « Personnaliser » : ouvre [CustomColorDialog].
 * Toute couleur validée y devient une entrée comme une autre (décision 3),
 * jamais un remplacement de la palette rapide.
 */
@Composable
private fun CustomColorSwatch(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Personnaliser la couleur" },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppSymbol.Add, contentDescription = null)
    }
}

/**
 * Lot 23, tâche 9 — éditeur de couleur personnalisée : champ hex `#RRGGBB`,
 * même convention que `ThemeStudioScreen.ColorPickerDialog` (cohérence
 * avec l'éditeur de thème existant plutôt que des curseurs R/V/B inédits
 * dans le reste de l'app). Opacité toujours pleine (`FF`) : une annotation
 * translucide n'a pas de sens pour un surlignage/soulignement/barré.
 */
@Composable
private fun CustomColorDialog(onConfirm: (AnnotationColor) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf("") }
    val isValid = remember(hex) { Regex("^#[0-9A-Fa-f]{6}$").matches(hex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur personnalisée") },
        text = {
            TextField(
                value = hex,
                onValueChange = { hex = it },
                label = { Text("Valeur hexadécimale") },
                placeholder = { Text("#RRGGBB") },
                isError = hex.isNotEmpty() && !isValid,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(hexRgbToAnnotationColor(hex)) },
            ) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Lot 23, tâche 9 — `#RRGGBB` (opacité toujours pleine, voir
 * [CustomColorDialog]) vers [AnnotationColor]. `internal` pour rester
 * testable sans passer par la boîte de dialogue Compose.
 */
internal fun hexRgbToAnnotationColor(hex: String): AnnotationColor =
    AnnotationColor((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())

/**
 * Lot 23, tâche 8 — pastille pleine plutôt qu'un `FilterChip` texte
 * (« Jaune », « Vert »…) : rendu direct de la couleur, cible confirmée
 * (`RAPPORT_POPUP_SELECTION_MOONREADER_v2.md` §4.1/4.2 — Moon+ utilise des
 * pastilles rondes pour sa palette rapide). Anneau de la couleur primaire
 * autour de la pastille sélectionnée, même convention que
 * `ThemeStudioScreen.ColorPickerDialog`.
 */
@Composable
private fun ColorSwatch(color: AnnotationColor, isSelected: Boolean, onClick: () -> Unit) {
    val label = color.label()
    Box(
        modifier = Modifier
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
            .background(color.toComposeColor())
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Couleur $label" },
    )
}

@Composable
private fun AnnotationKindOption(
    kind: AnnotationKind,
    icon: AppSymbol,
    label: String,
    selectedKind: AnnotationKind,
    onSelectKind: (AnnotationKind) -> Unit,
) {
    FilterChip(
        selected = kind == selectedKind,
        onClick = { onSelectKind(kind) },
        label = { Text(label) },
        leadingIcon = { AppIcon(icon, contentDescription = null) },
        modifier = Modifier.semantics { contentDescription = "Type $label" },
    )
}

/**
 * E.2 — Noms français pour les couleurs d'annotation (TalkBack). Repli
 * générique pour une couleur hors des 5 préréglages (Lot 23, Palier D —
 * couleur personnalisée) : aucun nom dédié n'a de sens pour une teinte
 * arbitraire.
 */
private fun AnnotationColor.label(): String = when (this) {
    AnnotationColor.YELLOW -> "Jaune"
    AnnotationColor.GREEN -> "Vert"
    AnnotationColor.BLUE -> "Bleu"
    AnnotationColor.PINK -> "Rose"
    AnnotationColor.ORANGE -> "Orange"
    else -> "Personnalisée"
}
