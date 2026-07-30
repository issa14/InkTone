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
 * Validé sur device réel (V2206, 2026-07-30) :
 * - Sélection intra-phrase OK (mot → phrase entière)
 * - Interception `LocalTextToolbar` → `onSelected` OK
 * - Recyclage LazyColumn : la sélection survit au cycle
 *   sortie d'écran → recyclage → retour, l'index et le texte
 *   restent corrects, pas de décalage
 * - Limitation : pas de sélection inter-phrases (structurel,
 *   sera levée en Partie 3 via AnnotationSelectionHandler)
 *
 * Le `onSelected` reçoit le texte complet de la phrase pour l'instant —
 * la vraie extraction d'offsets sera greffée dans la Partie 3.
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
