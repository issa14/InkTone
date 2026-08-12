package com.inktone.infrastructure.parser

import com.inktone.domain.service.FileStorageService
import java.io.File
import java.io.InputStream

/**
 * Fake local backee par de vrais `java.io.File`, partagee par les tests
 * PDF de ce module (Lot 12, tache 12.6) - `uri` est ici `file.absolutePath`,
 * jamais une URI SAF reelle (androidTest de `infrastructure/storage`
 * couvre deja `content://` via un vrai `ContentResolver`). Meme patron que
 * `LocalFileStorageService` de `TxtPublicationParserTest` (src/test) -
 * source set distinct, pas partageable telle quelle.
 */
internal class LocalFileStorageService : FileStorageService {
    override suspend fun openInputStream(uri: String): InputStream? =
        File(uri).takeIf { it.exists() }?.inputStream()

    override suspend fun computeSha256(uri: String): String? = uri
    override suspend fun getFileSize(uri: String): Long? = File(uri).takeIf { it.exists() }?.length()
    override suspend fun getFileName(uri: String): String? = File(uri).name
    override suspend fun persistReadPermission(uri: String) = Unit
    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean = false
}
