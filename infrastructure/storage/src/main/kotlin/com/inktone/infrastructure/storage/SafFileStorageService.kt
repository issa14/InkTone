package com.inktone.infrastructure.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.inktone.domain.service.FileStorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
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

    override suspend fun persistReadPermission(uri: String) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Unit
    }
}
