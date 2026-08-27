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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind

/** Rendu de la couleur pour l'UI (sélecteur ou surlignage d'annotation existante). */
fun AnnotationColor.toComposeColor(): Color = when (this) {
    AnnotationColor.YELLOW -> Color(0xFFFFF59D)
    AnnotationColor.GREEN -> Color(0xFFA5D6A7)
    AnnotationColor.BLUE -> Color(0xFF90CAF9)
    AnnotationColor.PINK -> Color(0xFFF48FB1)
    AnnotationColor.ORANGE -> Color(0xFFFFCC80)
}

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
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnnotationColor.entries.forEach { color ->
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

/** E.2 — Noms français pour les couleurs d'annotation (TalkBack). */
private fun AnnotationColor.label(): String = when (this) {
    AnnotationColor.YELLOW -> "Jaune"
    AnnotationColor.GREEN -> "Vert"
    AnnotationColor.BLUE -> "Bleu"
    AnnotationColor.PINK -> "Rose"
    AnnotationColor.ORANGE -> "Orange"
}
