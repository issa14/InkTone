package com.inktone.data.epub

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/**
 * Résolution du nom de fichier et du dossier source d'un [Uri] SAF — partagé entre l'import
 * simple ([com.inktone.ui.screen.library.LibraryViewModel.importEpub]) et l'import par lot
 * ([com.inktone.domain.usecase.ImportBooksUseCase], exécuté par le worker d'arrière-plan —
 * voir PLAN import EPUB §4).
 */
fun resolveEpubFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) it.getString(idx) else null
        } else null
    }
}

/**
 * Meilleur effort pour nommer le dossier d'origine d'un document SAF : l'ID de document
 * (`primary:Download/MesLivres/x.epub`) encode souvent le chemin relatif pour les fournisseurs
 * de stockage local, mais pas pour tous (ex. Google Drive) — retourne `null` dans ce cas,
 * regroupé ensuite sous `UNKNOWN_SOURCE_FOLDER_LABEL` côté UI.
 */
fun resolveEpubSourceFolder(uri: Uri): String? = try {
    val docId = DocumentsContract.getDocumentId(uri)
    val path = docId.substringAfter(':')
    val parent = path.substringBeforeLast('/', "")
    parent.substringAfterLast('/').takeIf { it.isNotBlank() }
} catch (e: Exception) {
    null
}
