package com.inktone.feature.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tache 9bis.3.2 — progression du LIVRE ENTIER (`ReaderUiState.bookProgression`),
 * pas la barre par chapitre du legacy. Persistante (pas masquee par
 * `ImmersiveReaderChrome`/HUD) : c'est l'information qui compte pour
 * l'utilisateur (« je suis a 80% »), la position dans le chapitre reste
 * disponible via la TOC, jamais melangee a cette barre.
 */
@Composable
fun BookProgressBar(progression: Float) {
    LinearProgressIndicator(
        progress = { progression },
        modifier = Modifier.fillMaxWidth().height(2.dp),
    )
}
