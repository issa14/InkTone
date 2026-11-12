package com.inktone.feature.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol

/**
 * Lot 23, tâche 12 — menu contextuel d'une annotation déjà posée dans le
 * texte (tap détecté par `BookBlockItem.onAnnotationTapped`, mode SCROLL
 * uniquement — écart déclaré pour le mode PAGED, voir plan). Deux actions,
 * réutilisant les cas d'usage déjà écrits au Lot 22, tâche 11
 * (`UpdateAnnotationNote`/`DeleteAnnotation`) : aucun nouveau cas d'usage
 * ici, seulement le point d'entrée in-situ qui manquait.
 *
 * Ancré près du tap plutôt qu'en bas d'écran (contrairement à
 * `SelectionActionPopup`, Lot 23, tâche 7) : une action ponctuelle sur un
 * élément déjà posé n'a pas besoin de l'espace d'un panneau pleine largeur.
 */
@Composable
fun AnnotationTapPopup(
    boundsInWindow: Rect,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val positionProvider = remember(boundsInWindow) { NearBoundsPositionProvider(boundsInWindow, marginPx = 24) }
    // Suppression en deux temps, dans le menu lui-même : un tap sur
    // « Supprimer » effaçait l'annotation et sa note sans retour possible,
    // là où la vue globale « Marque-pages et notes » demande confirmation.
    var confirmingDeletion by remember(boundsInWindow) { mutableStateOf(false) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (confirmingDeletion) {
                    Text(
                        "Supprimer ?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    TextButton(onClick = { confirmingDeletion = false }) { Text("Annuler") }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Supprimer") }
                } else {
                    TextButton(onClick = onEdit) {
                        AppIcon(AppSymbol.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Modifier")
                    }
                    TextButton(onClick = { confirmingDeletion = true }) {
                        AppIcon(AppSymbol.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Supprimer")
                    }
                }
            }
        }
    }
}

private class NearBoundsPositionProvider(
    private val boundsInWindow: Rect,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centerX = (boundsInWindow.left + boundsInWindow.right) / 2f
        val x = (centerX - popupContentSize.width / 2f).toInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val spaceAbove = boundsInWindow.top
        val y = if (spaceAbove >= popupContentSize.height + marginPx) {
            (boundsInWindow.top - popupContentSize.height - marginPx).toInt()
        } else {
            (boundsInWindow.bottom + marginPx).toInt()
        }
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
