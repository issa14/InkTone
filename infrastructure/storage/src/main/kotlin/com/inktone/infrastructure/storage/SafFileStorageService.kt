package com.inktone.infrastructure.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.inktone.domain.service.FileStorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class SafFileStorageService @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileStorageService {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun openInputStream(uri: String): InputStream? = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(Uri.parse(uri)) }.getOrNull()
    }

    override suspend fun computeSha256(uri: String): String? = withContext(Dispatchers.IO) {
        openInputStream(uri)?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read = stream.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = stream.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun getFileSize(uri: String): Long? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            // Fallback actif UNIQUEMENT en test (URI file:// vers un fichier
            // temporaire, Tâche 2.8 test ci-dessous) — la production ne
            // reçoit jamais que des URI SAF content://, pour lesquelles
            // ContentResolver.query()/OpenableColumns est la seule voie
            // correcte (une URI file:// n'a pas de "provider" à interroger).
            return@withContext parsed.path?.let { File(it).length() }
        }
        runCatching {
            resolver.query(parsed, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else null
            }
        }.getOrNull()
    }

    override suspend fun getFileName(uri: String): String? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            // Meme repli qu'ailleurs dans ce fichier : file:// n'a pas de
            // provider a interroger, le dernier segment du chemin est deja
            // le nom reel (utilise en test, jamais en production SAF).
            return@withContext parsed.lastPathSegment
        }
        runCatching {
            resolver.query(parsed, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    override suspend fun persistReadPermission(uri: String) = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        // No-op gracieux quand l'URI est déjà possédée par l'app
        // (FileProvider app-scopé, Lot 13 tâche 13.3.2) :
        // `takePersistableUriPermission` n'est valide que pour une URI de
        // picker (`ACTION_OPEN_DOCUMENT`), pas pour un fichier que l'app
        // vient d'écrire elle-même.
        if (isAppOwned(uri)) return@withContext
        runCatching {
            resolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Unit
    }

    /** Vrai si [path] désigne un fichier du répertoire des couvertures de l'application. */
    private fun isOwnedCover(path: String): Boolean {
        val covers = File(context.filesDir, COVERS_DIR).canonicalFile
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return file.parentFile == covers
    }

    /** Vrai si l'URI est servie par un provider de l'app (FileProvider app-scopé). */
    override fun isAppOwned(uri: String): Boolean {
        val authority = Uri.parse(uri).authority ?: return false
        return authority == context.packageName || authority == "${context.packageName}.fileprovider"
    }

    override suspend fun copyToLibrary(uri: String, fileName: String, fileHash: String): String? =
        withContext(Dispatchers.IO) {
            if (isAppOwned(uri)) return@withContext uri
            val directory = File(context.filesDir, LIBRARY_DIR).apply { mkdirs() }
            val extension = fileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
            val target = File(directory, if (extension != null) "$fileHash.$extension" else fileHash)
            // Écrite à côté puis renommée : une copie interrompue (espace
            // plein, application tuée) ne laisse jamais un livre tronqué.
            val partial = File(directory, "${target.name}.${UUID.randomUUID()}.part")
            val copied = runCatching {
                resolver.openInputStream(Uri.parse(uri))?.use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied || !partial.renameTo(target)) {
                partial.delete()
                return@withContext null
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString()
        }

    override suspend fun deleteOwnedFile(uri: String) = withContext(Dispatchers.IO) {
        // Une couverture est un chemin de fichier du répertoire `covers` de
        // l'application, jamais une URI de FileProvider (audit UX D68).
        if (isOwnedCover(uri)) {
            runCatching { File(uri).delete() }
            return@withContext
        }
        if (!isAppOwned(uri)) return@withContext
        // FileProvider.delete supprime le fichier qu'il sert.
        runCatching { resolver.delete(Uri.parse(uri), null, null) }
        Unit
    }

    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openOutputStream(Uri.parse(uri))?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
    }

    private companion object {
        /** Doit correspondre au `files-path` « library » de `file_paths.xml`. */
        const val LIBRARY_DIR = "library"

        /** Doit correspondre à `CoverStorage` (infrastructure/parser). */
        const val COVERS_DIR = "covers"
    }
}
