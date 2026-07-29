package com.inktone.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Sélecteur SAF multi-fichiers (Tâche 6.2bis) — seul geste utilisateur qui
 * déclenche réellement `ImportWorker` (via [ImportViewModel]). EPUB et TXT
 * uniquement, cohérent avec `CompositePublicationParser`
 * (infrastructure/parser) — PDF hors périmètre v1 (ADR-017).
 */
@Composable
fun ImportPickerButton(viewModel: ImportViewModel = hiltViewModel()) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.enqueueImport(uris.map { it.toString() })
        }
    }

    Button(onClick = {
        launcher.launch(arrayOf("application/epub+zip", "text/plain"))
    }) {
        Text("Importer des livres")
    }
}
