package com.inktone.feature.reader

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Spike Tâche 1.1.1 — Reproduit le pattern exact du legacy
 * (`SelectableSentence`) : un [SelectionContainer] par phrase
 * individuelle, chacun avec son propre [LocalTextToolbar] intercepté.
 *
 * Cette granularité fine évite le piège « comportement non défini » de
 * `SelectionContainer` + `LazyColumn` documenté par Compose Foundation.
 *
 * Le `onSelected` reçoit le texte complet de la phrase pour l'instant —
 * la vraie extraction d'offsets sera greffée dans la Partie 3, une fois
 * le spike validé sur device réel avec une `LazyColumn` longue
 * (plusieurs dizaines de phrases).
 *
 * TEST À FAIRE SUR DEVICE RÉEL :
 * 1. Rendre 50+ SelectableSentenceSpike dans une LazyColumn.
 * 2. Sélectionner du texte dans une phrase visible.
 * 3. Faire défiler pendant qu'une sélection est active.
 * 4. Confirmer qu'aucun crash ni comportement erratique n'apparaît.
 * 5. Si ça casse uniquement en dehors de l'écran visible, c'est le
 *    signal exact que la documentation Compose annonçait — pas un faux
 *    positif.
 */
@Composable
fun SelectableSentenceSpike(
    sentenceText: String,
    onSelected: (String) -> Unit,
) {
    val defaultToolbar = LocalTextToolbar.current
    val toolbar = remember(sentenceText) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = defaultToolbar.status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                // Tâche 1.1.1 : pour l'instant, onSelected reçoit le texte
                // complet de la phrase. La vraie extraction d'offsets de
                // sélection sera branchée dans la Partie 3
                // (AnnotationSelectionHandler).
                onSelected(sentenceText)
                defaultToolbar.hide()
            }

            override fun hide() {
                defaultToolbar.hide()
            }
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer {
            Text(sentenceText)
        }
    }
}
