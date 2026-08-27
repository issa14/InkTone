package com.inktone.feature.reader

import android.content.Context
import android.content.Intent
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
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind

/**
 * Tâche 3c.4 — remplace `AnnotationColorPicker` en position fixe basse
 * d'écran par un bloc positionné **près de la sélection**. Quatre options
 * de premier niveau (Copier · Surligner · Note · un overflow « Plus
 * d'actions » menant à Partager, Lot 21 tâche 7), la couleur reste un
 * second temps (`Surligner` seulement) — cible confirmée
 * (`UX_FLOW_DESIGN.md` § popup de sélection de texte, Signet
 * volontairement absent : un signet marque une position, pas une plage).
 *
 * S'applique à la sélection libre au mot (voir `ReaderUiState.freeSelectionRange`,
 * `PagedChapterContent.PageBlock`/`ReaderScreen.ParagraphText`) — seul
 * modèle de sélection de texte du lecteur.
 *
 * **Cycle de vie (Phase 2 de la refonte de la sélection)** : ce popup
 * n'est monté que lorsque l'unité adressable a remonté des bornes fenêtre
 * depuis `TextToolbar.showMenu()`, c'est-à-dire quand le geste de
 * sélection est TERMINÉ (doigt levé). `selectionBoundsInWindow == null`
 * signifie « glissement de poignée en cours » : rien ne doit s'afficher,
 * la loupe native doit rester dégagée — d'où le retour anticipé
 * ci-dessous, qui rend aussi le popup apatride entre deux gestes (mode
 * ACTIONS, texte de note vide à chaque réapparition).
 *
 * **Disposition (Lot 23, tâche 7, ancrage en bas essayé puis abandonné
 * après vérification device)** : un ancrage en bas d'écran pleine largeur,
 * inspiré de Moon+ Reader, a été tenté puis écarté — retour direct d'Issa
 * après usage réel : la distance main-œil pour un texte en haut d'écran et
 * la rupture du lien spatial avec la sélection l'emportaient sur l'intérêt
 * du panneau plus large. Ancrage près de la sélection restauré, disposition
 * d'origine de la Tâche 3c.4 ; les ajouts de ce Lot
 * (type d'annotation, pastilles, éditeur personnalisé) vivent dans
 * `AnnotationColorPicker`, indépendant du conteneur qui le positionne —
 * aucun autre changement nécessaire.
 */
private enum class SelectionPopupMode { ACTIONS, COLOR_PICKER, NOTE_INPUT, MORE }

@Composable
fun SelectionActionPopup(
    selectedText: String,
    selectionBoundsInWindow: Rect?,
    onHighlight: (AnnotationColor, AnnotationKind) -> Unit,
    onSaveNote: (String, AnnotationColor, AnnotationKind) -> Unit,
    onDismiss: () -> Unit,
    // Lot 21, tâche 7 — contexte du partage (« Titre — Auteur — Chapitre
    // X », construit par l'appelant). `null`/vide → on partage le texte
    // seul. Paramètre à défaut : aucun changement pour les appelants
    // existants ni pour les tests.
    shareContext: String? = null,
    // Lot 22, tâche 12 — couleurs récemment utilisées, transmises telles
    // quelles à AnnotationColorPicker.
    recentColors: List<AnnotationColor> = emptyList(),
) {
    if (selectionBoundsInWindow == null) return

    var mode by remember { mutableStateOf(SelectionPopupMode.ACTIONS) }
    var pendingColor by remember { mutableStateOf(AnnotationColor.YELLOW) }
    // Lot 23, tâche 6 — type par défaut HIGHLIGHT (comportement identique
    // à avant ce Lot tant que l'utilisateur ne choisit pas autre chose).
    var pendingKind by remember { mutableStateOf(AnnotationKind.HIGHLIGHT) }
    var noteText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
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

    val density = LocalDensity.current
    val positionProvider = remember(selectionBoundsInWindow, density) {
        SelectionPopupPositionProvider(selectionBoundsInWindow, with(density) { 8.dp.roundToPx() })
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        // Bug réel trouvé sur appareil, pile d'appels à l'appui
        // (`PopupLayout.onTouchEvent` → `onDismissRequest` →
        // `clearSelectionAndPopup`) : tout toucher HORS de la surface du
        // popup déclenchait `onDismissRequest`, donc la purge complète de la
        // sélection. Or les poignées de sélection natives sont, par
        // construction, hors de cette surface — saisir une poignée pour
        // ajuster la sélection la détruisait avant même le moindre
        // mouvement. Ce popup ne doit PAS se fermer sur un toucher
        // extérieur : l'annulation passe par le tap sur le texte
        // (`onValueChange`, seule source de vérité du tap) ou par une action
        // du popup lui-même.
        properties = PopupProperties(
            focusable = mode == SelectionPopupMode.NOTE_INPUT,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            when (mode) {
                SelectionPopupMode.ACTIONS -> Row(modifier = Modifier.padding(4.dp)) {
                    PopupActionButton(icon = AppSymbol.Copy, label = "Copier") {
                        clipboardManager.setText(AnnotatedString(selectedText))
                        Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    PopupActionButton(icon = AppSymbol.Highlight, label = "Surligner") {
                        mode = SelectionPopupMode.COLOR_PICKER
                    }
                    PopupActionButton(icon = AppSymbol.Note, label = "Note") {
                        mode = SelectionPopupMode.NOTE_INPUT
                    }
                    // Lot 21, tâche 7 — « Partager » derrière un overflow
                    // « ⋮ » : quatre actions en premier niveau élargiraient
                    // la barre au-delà du raisonnable. L'action secondaire
                    // reste à un geste (un tap), jamais « Tout sélectionner ».
                    TextButton(onClick = { mode = SelectionPopupMode.MORE }) {
                        AppIcon(AppSymbol.MoreActions, contentDescription = "Plus d'actions")
                    }
                }

                SelectionPopupMode.COLOR_PICKER -> AnnotationColorPicker(
                    selected = pendingColor,
                    onSelect = { pendingColor = it },
                    onConfirm = { onHighlight(pendingColor, pendingKind) },
                    onCancel = onDismiss,
                    recentColors = recentColors,
                    selectedKind = pendingKind,
                    onSelectKind = { pendingKind = it },
                )

                // Lot 21, tâche 7 — « Partager » (ACTION_SEND) : texte
                // sélectionné + contexte titre/auteur/chapitre. Le popup
                // reste non-focusable ici (mode sans clavier) — la gestion
                // conditionnelle de `focusable` est INCHANGÉE (bug device
                // documenté : ne pas voler le focus à la zone de lecture).
                SelectionPopupMode.MORE -> Column(modifier = Modifier.padding(4.dp)) {
                    PopupActionButton(icon = AppSymbol.Share, label = "Partager") {
                        shareSelection(context, selectedText, shareContext)
                        onDismiss()
                    }
                    TextButton(onClick = { mode = SelectionPopupMode.ACTIONS }) {
                        AppIcon(AppSymbol.Back, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Retour")
                    }
                }

                SelectionPopupMode.NOTE_INPUT -> Column(modifier = Modifier.padding(12.dp).width(260.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note") },
                        modifier = Modifier.focusRequester(noteFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (noteText.isNotBlank()) onSaveNote(noteText, pendingColor, pendingKind)
                        }),
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Annuler") }
                        Button(
                            onClick = { if (noteText.isNotBlank()) onSaveNote(noteText, pendingColor, pendingKind) },
                            enabled = noteText.isNotBlank(),
                        ) { Text("Enregistrer") }
                    }
                }
            }
        }
    }
}


@Composable
private fun PopupActionButton(icon: AppSymbol, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
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
 *
 * Restauré au Lot 23 (tâche 7 abandonnée après vérification device, voir
 * KDoc de tête) après un essai d'ancrage en bas d'écran.
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

/**
 * Lot 21, tâche 7 — message partagé (ACTION_SEND) : texte sélectionné
 * entre guillemets français, suivi du contexte (titre — auteur — chapitre)
 * sur une ligne dédiée s'il est fourni. Pure, testable en JVM.
 */
internal fun buildShareMessage(selectedText: String, shareContext: String?): String {
    val contextLine = shareContext?.takeIf { it.isNotBlank() }
    return buildString {
        // Correctif Lot 21 — espace insécable entre le guillemet et le
        // texte (typographie française), même convention que
        // XmlOpdsFeedParser (entité `&nbsp;` → `\u00A0`).
        append('«').append('\u00A0').append(selectedText).append('\u00A0').append('»')
        if (contextLine != null) append("\n\n— ").append(contextLine)
    }
}

/** Lance le partage Android (chooser) avec le message construit. */
private fun shareSelection(context: Context, selectedText: String, shareContext: String?) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildShareMessage(selectedText, shareContext))
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
