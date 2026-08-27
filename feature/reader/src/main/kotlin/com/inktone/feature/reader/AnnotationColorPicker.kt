package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.inktone.core.designsystem.InkToneSlider
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
 * sur device) : les pastilles peuvent dépasser la largeur d'un écran
 * étroit — un `Row` sans défilement les rendrait inatteignables,
 * découvert en testant réellement sur le device connecté, pas supposé
 * correct après compilation seule.
 *
 * **Application immédiate, sans confirmation (Lot 24, décision 1)** :
 * chaque tap sur une pastille ou un type applique tout de suite
 * l'annotation ([onApply]/[onSelectKind]) — il n'y a plus de bouton
 * `Surligner`/`Annuler` à ce niveau. Fermer le popup (tap en dehors de la
 * sélection, géré par l'appelant) est la seule façon de « terminer »,
 * avant ou après avoir appliqué une couleur.
 */
@Composable
fun AnnotationColorPicker(
    selected: AnnotationColor,
    // Lot 24, tâche 3 — remplace onSelect+onConfirm : un seul appel
    // applique directement la couleur à l'annotation en cours (créée ou
    // mise à jour côté ViewModel, voir ReaderViewModel.confirmAnnotation).
    onApply: (AnnotationColor) -> Unit,
    // Lot 22, tâche 12 — couleurs récemment utilisées (la plus récente en
    // tête, voir `UserPreferences.recentAnnotationColors`), proposées en
    // premier ; le reste de la palette suit dans son ordre habituel.
    recentColors: List<AnnotationColor> = emptyList(),
    // Lot 23, tâche 6 — comble le trou trouvé à la vérification device du
    // Lot 22 : `AnnotationKind` existait (rendu + migration) mais rien ne
    // permettait de le choisir. Pas de Squiggly (décision 2) : seulement
    // les 3 valeurs d'`AnnotationKind`. Lot 24 : appliqué immédiatement
    // comme une couleur, même discipline.
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
        // Centrée pour rester cohérente avec la rangée de couleurs
        // juste en dessous (voir son commentaire, retour device Lot 24).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            AnnotationKindOption(AnnotationKind.HIGHLIGHT, AppSymbol.Highlight, "Surlignage", selectedKind, onSelectKind)
            AnnotationKindOption(AnnotationKind.UNDERLINE, AppSymbol.Underline, "Souligné", selectedKind, onSelectKind)
            AnnotationKindOption(AnnotationKind.STRIKETHROUGH, AppSymbol.Strikethrough, "Barré", selectedKind, onSelectKind)
        }
        // Lot 24 (retour device) — le retrait des boutons Surligner/Annuler
        // (décision 1) laissait un vide à droite : `fillMaxWidth` + un
        // `Arrangement` par défaut alignait les pastilles à gauche du
        // `Row`, plus vide à combler par ces boutons. Centré à la place —
        // `horizontalScroll` continue de s'appliquer si la palette déborde
        // sur un écran étroit (couleurs récentes + préréglages + pastille
        // « Personnaliser »).
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            orderedColors.forEach { color ->
                ColorSwatch(color = color, isSelected = color == selected, onClick = { onApply(color) })
            }
            CustomColorSwatch(onClick = { showCustomColorDialog = true })
        }
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            // Lot 24, tâche 4 — la validation du dialogue applique
            // directement (décision 3), même chemin que les pastilles.
            onConfirm = { color ->
                onApply(color)
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
 * Lot 23, tâche 9 (corrigé après retour Issa) — éditeur de couleur
 * personnalisée : **des curseurs R/V/B en interaction principale**, avec
 * aperçu en direct. Un champ hex avait été retenu d'abord pour rester
 * cohérent avec `ThemeStudioScreen.ColorPickerDialog` — mais un champ
 * hexadécimal exige de connaître ce système de notation, contre-productif
 * pour une app grand public (retour direct d'Issa). Les curseurs
 * réutilisent [InkToneSlider] (forme unique de tout réglage numérique
 * d'InkTone). Le champ hex reste, mais en second plan : saisie optionnelle
 * qui met à jour les curseurs (sens unique hex → curseurs), jamais le seul
 * chemin pour choisir une couleur. Opacité toujours pleine (`FF`) : une
 * annotation translucide n'a pas de sens pour un surlignage/soulignement/
 * barré.
 */
@Composable
private fun CustomColorDialog(onConfirm: (AnnotationColor) -> Unit, onDismiss: () -> Unit) {
    var red by remember { mutableFloatStateOf(255f) }
    var green by remember { mutableFloatStateOf(235f) }
    var blue by remember { mutableFloatStateOf(59f) }
    var hex by remember { mutableStateOf("") }

    val previewColor = remember(red, green, blue) {
        Color(red = red / 255f, green = green / 255f, blue = blue / 255f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur personnalisée") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewColor)
                        .semantics { contentDescription = "Aperçu de la couleur" },
                )
                InkToneSlider(
                    label = "Rouge",
                    value = red,
                    range = 0f..255f,
                    onValueChange = { red = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
                InkToneSlider(label = "Vert", value = green, range = 0f..255f, onValueChange = { green = it })
                InkToneSlider(label = "Bleu", value = blue, range = 0f..255f, onValueChange = { blue = it })
                // Lot 23 (corrigé) — chemin avancé optionnel : une saisie
                // hex valide met à jour les curseurs, jamais l'inverse (les
                // curseurs restent la seule source de vérité affichée).
                TextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed
                        if (Regex("^#[0-9A-Fa-f]{6}$").matches(typed)) {
                            val argb = hexRgbToAnnotationColor(typed).argb
                            red = ((argb shr 16) and 0xFF).toFloat()
                            green = ((argb shr 8) and 0xFF).toFloat()
                            blue = (argb and 0xFF).toFloat()
                        }
                    },
                    label = { Text("Ou saisir un code hexadécimal") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = 0xFF000000.toInt() or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
                    onConfirm(AnnotationColor(argb))
                },
            ) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Lot 23, tâche 9 — `#RRGGBB` (opacité toujours pleine) vers
 * [AnnotationColor]. `internal` pour rester testable indépendamment de la
 * boîte de dialogue Compose ; utilisé par la saisie hex optionnelle de
 * [CustomColorDialog].
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
