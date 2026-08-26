package com.inktone.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
) {
    val orderedColors = remember(recentColors) {
        recentColors + AnnotationColor.PRESETS.filterNot { it in recentColors }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        orderedColors.forEach { color ->
            val label = color.label()
            FilterChip(
                selected = color == selected,
                onClick = { onSelect(color) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = "Couleur $label" },
            )
        }
        Button(onClick = onConfirm) { Text("Surligner") }
        Button(onClick = onCancel) { Text("Annuler") }
    }
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
