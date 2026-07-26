package com.inktone.domain.service

import java.io.InputStream

/**
 * Accès aux fichiers de l'utilisateur via Storage Access Framework
 * (Blueprint §10.3, ADR-015 — SAF exclusivement, jamais la permission
 * large d'accès au stockage externe qu'ADR-015 interdit). Implémentée
 * directement par infrastructure/storage — pas via data/, réservé aux
 * repositories.
 */
interface FileStorageService {
    suspend fun openInputStream(uri: String): InputStream?
    suspend fun computeSha256(uri: String): String?
    suspend fun getFileSize(uri: String): Long?
    suspend fun persistReadPermission(uri: String)
}
