package com.inktone.domain.service

import java.io.File
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

    /**
     * Nom de fichier affichable (avec extension) résolu depuis une URI
     * SAF `content://` — seule façon correcte de détecter un format par
     * extension : l'URI elle-même est opaque, elle ne contient jamais le
     * nom de fichier (bug réel trouvé lot 2a — le format TXT n'était
     * jamais détecté sur un vrai fichier importé via SAF, seulement sur
     * les URI de test qui se terminaient par hasard en `.txt`).
     */
    suspend fun getFileName(uri: String): String?

    /**
     * Écrit [sourceFile] vers la destination SAF [uri] (Tâche 6.0 —
     * absent depuis la Phase 2 car rien n'écrivait de fichier utilisateur
     * jusqu'à l'export de la Phase 6). Retourne `false` sur tout échec
     * (permission refusée, URI invalide) plutôt que de lever une
     * exception (Blueprint §7.11).
     */
    suspend fun writeToUri(uri: String, sourceFile: File): Boolean
}
