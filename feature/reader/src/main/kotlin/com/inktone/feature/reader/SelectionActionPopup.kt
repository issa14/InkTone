package com.inktone.feature.reader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.AnnotationColor

/**
 * Tâche 3c.4 — remplace `AnnotationColorPicker` en position fixe basse
 * d'écran par un bloc positionné **près de la sélection**. Trois options
 * de premier niveau (Copier · Surligner · Note), la couleur reste un
 * second temps (`Surligner` seulement) — cible confirmée
 * (`UX_FLOW_DESIGN.md` § popup de sélection de texte, Signet
 * volontairement absent : un signet marque une position, pas une plage).
 *
 * S'applique à la sélection libre au mot (voir `ReaderUiState.freeSelectionRange`,
 * `PagedChapterContent.PageBlock`/`ReaderScreen.ParagraphText`) — seul
 * modèle de sélection de texte du lecteur.
 */
private enum class SelectionPopupMode { ACTIONS, COLOR_PICKER, NOTE_INPUT }

@Composable
fun SelectionActionPopup(
    selectedText: String,
    selectionBoundsInWindow: Rect?,
    onHighlight: (AnnotationColor) -> Unit,
    onSaveNote: (String, AnnotationColor) -> Unit,
    onDismiss: () -> Unit,
) {
    if (selectionBoundsInWindow == null) return

    var mode by remember { mutableStateOf(SelectionPopupMode.ACTIONS) }
    var pendingColor by remember { mutableStateOf(AnnotationColor.YELLOW) }
    var noteText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val noteFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Bug réel trouvé sur appareil pendant la vérification du lot 3c :
    // cliquer sur le champ « Note » ne faisait pas apparaître le clavier.
    // `Popup` est NON focusable par défaut (PopupProperties(focusable =
    // false), pensé pour les tooltips/menus qui ne doivent pas voler le
    // focus) — un OutlinedTextField à l'intérieur ne peut alors jamais
    // recevoir le focus, donc jamais déclencher l'IME. `focusable = true`
    // uniquement en mode NOTE_INPUT (voir plus bas), plus une demande de
    // focus explicite au passage dans ce mode (un TextField ne se focus
    // jamais tout seul à sa composition).
    //
    // Bug réel trouvé sur appareil (palier 3f.2, diagnostic dédié) :
    // `focusable = true` posé sans condition sur TOUT le Popup (donc dès
    // le mode ACTIONS, avant même que l'utilisateur touche « Note »)
    // volait le focus de FENÊTRE Android à la zone de lecture dès qu'une
    // sélection existait — `BasicTextField` masque alors ses poignées et
    // sa loupe natives dès que sa fenêtre hôte n'est plus focus (même
    // logique que le natif `EditText` : ce n'est pas un bug de Compose,
    // c'est notre propre popup qui volait le focus trop tôt). Restreint
    // désormais au SEUL mode qui a réellement besoin du clavier.
    LaunchedEffect(mode) {
        if (mode == SelectionPopupMode.NOTE_INPUT) {
            noteFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val positionProvider = remember(selectionBoundsInWindow, density) {
        SelectionPopupPositionProvider(selectionBoundsInWindow, with(density) { 8.dp.roundToPx() })
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = mode == SelectionPopupMode.NOTE_INPUT),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            when (mode) {
                SelectionPopupMode.ACTIONS -> Row(modifier = Modifier.padding(4.dp)) {
                    PopupActionButton(icon = AppIcons.Copy, label = "Copier") {
                        clipboardManager.setText(AnnotatedString(selectedText))
                        Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    PopupActionButton(icon = AppIcons.Highlight, label = "Surligner") {
                        mode = SelectionPopupMode.COLOR_PICKER
                    }
                    PopupActionButton(icon = AppIcons.Note, label = "Note") {
                        mode = SelectionPopupMode.NOTE_INPUT
                    }
                }

                SelectionPopupMode.COLOR_PICKER -> AnnotationColorPicker(
                    selected = pendingColor,
                    onSelect = { pendingColor = it },
                    onConfirm = { onHighlight(pendingColor) },
                    onCancel = onDismiss,
                )

                SelectionPopupMode.NOTE_INPUT -> Column(modifier = Modifier.padding(12.dp).width(260.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note") },
                        modifier = Modifier.focusRequester(noteFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (noteText.isNotBlank()) onSaveNote(noteText, pendingColor)
                        }),
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Annuler") }
                        Button(
                            onClick = { if (noteText.isNotBlank()) onSaveNote(noteText, pendingColor) },
                            enabled = noteText.isNotBlank(),
                        ) { Text("Enregistrer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text(label)
    }
}

/**
 * Tâche 3c.4 — contrainte d'implémentation : positionnement alimenté par
 * les `LayoutCoordinates` réelles de la zone sélectionnée
 * (`selectionBoundsInWindow`, calculées par l'appelant à partir de
 * `onGloballyPositioned`/`TextLayoutResult.getPathForRange`), jamais des
 * coordonnées d'écran calculées à la main. Ignore délibérément
 * `anchorBounds` fourni par `Popup` (bounds du site d'appel du composable,
 * pas de la sélection) — c'est `selectionBoundsInWindow`, recalculé à
 * chaque recomposition par l'appelant pendant un défilement ou une
 * rotation, qui pilote la position, pas l'ancre par défaut de `Popup`.
 */
private class SelectionPopupPositionProvider(
    private val selectionBoundsInWindow: Rect,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centerX = (selectionBoundsInWindow.left + selectionBoundsInWindow.right) / 2f
        val x = (centerX - popupContentSize.width / 2f).toInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val spaceAbove = selectionBoundsInWindow.top
        val y = if (spaceAbove >= popupContentSize.height + marginPx) {
            (selectionBoundsInWindow.top - popupContentSize.height - marginPx).toInt()
        } else {
            (selectionBoundsInWindow.bottom + marginPx).toInt()
        }
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
