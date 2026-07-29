package com.inktone.core.testing.fake

import com.inktone.domain.service.FileStorageService
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Simule l'accès SAF sans Android : le hash est dérivé de l'URI elle-même
 * (déterministe — la même URI produit toujours le même hash, suffisant
 * pour tester la détection de doublons sans fichier réel).
 */
class FakeFileStorageService(
    private val fileSize: Long = 1024L,
) : FileStorageService {
    val writtenUris = mutableListOf<String>()

    override suspend fun openInputStream(uri: String): InputStream? = uri.byteInputStream()

    override suspend fun computeSha256(uri: String): String? =
        MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }

    override suspend fun getFileSize(uri: String): Long = fileSize

    override suspend fun persistReadPermission(uri: String) = Unit

    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean {
        writtenUris += uri
        return true
    }
}
