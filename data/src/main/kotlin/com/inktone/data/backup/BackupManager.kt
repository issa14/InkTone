package com.inktone.data.backup

import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.service.FileStorageService
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

sealed interface ImportBackupResult {
    data class Success(val restored: Int, val skippedOrphans: Int) : ImportBackupResult
    data class Failed(val message: String) : ImportBackupResult
}

private val backupJson = Json { ignoreUnknownKeys = true }

/**
 * Export/import des metadonnees utilisateur (Tache 8.5, recupere
 * de l'audit UX legacy) — jamais les livres eux-memes.
 *
 * `appVersion` est passe par l'appelant (module `app`, seul endroit qui
 * connait `BuildConfig.VERSION_NAME` d'une application, pas d'une
 * bibliotheque) plutot que lu directement ici.
 *
 * Lot 11, tâche 11.1 — le fichier `.rfbackup` est chiffré E2EE
 * (AES/GCM, clé dérivée du mot de passe par [BackupCrypto], jamais le
 * mot de passe lui-même persisté). **Compatibilité ascendante** : un
 * fichier exporté avant ce lot est un JSON en clair sans annotations —
 * [importFrom] le détecte (absence du marqueur binaire de
 * [BackupCrypto]) et l'importe directement, mot de passe ignoré. Même
 * [BackupPayload]/[backupJson] pour le fichier local et la future
 * synchronisation distante (tâche 11.2) — une seule définition de
 * charge utile.
 */
class BackupManager @Inject constructor(
    private val fileStorageService: FileStorageService,
    private val bookmarkRepository: BookmarkRepository,
    private val pronunciationRuleRepository: PronunciationRuleRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
    private val themeRepository: ThemeRepository,
    private val annotationRepository: AnnotationRepository,
) {
    /**
     * @param password mot de passe E2EE — un mot de passe perdu rend la
     * sauvegarde irrécupérable, il n'existe aucun mécanisme de
     * recouvrement (à dire explicitement côté UI, pas seulement ici).
     * @return `true` si l'écriture a réussi — le résultat doit remonter à l'appelant, pas être avalé.
     */
    suspend fun exportTo(destinationUri: String, appVersion: String, password: String): Boolean {
        val payload = buildPayload(appVersion)
        val json = backupJson.encodeToString(payload)
        val envelope = BackupCrypto.encrypt(json.encodeToByteArray(), password)
        val tempFile = File.createTempFile("inktone-backup", ".rfbackup")
        return try {
            tempFile.writeBytes(envelope)
            fileStorageService.writeToUri(destinationUri, tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun buildPayload(appVersion: String) = BackupPayload(
        appVersion = appVersion,
        createdAt = System.currentTimeMillis(),
        bookmarks = bookmarkRepository.observeAll().first().map { it.toBackup() },
        pronunciationRules = pronunciationRuleRepository.observeAll().first().map { it.toBackup() },
        readingStates = readingStateRepository.getAll().map { it.toBackup() },
        readingSessions = readingSessionRepository.getAll().map { it.toBackup() },
        customThemes = themeRepository.observeAll().first().filterNot { it.isBuiltIn }.map { it.toBackup() },
        annotations = annotationRepository.getAll().map { it.toBackup() },
    )

    /** @param password ignoré si le fichier est un export antérieur en clair (compatibilité ascendante). */
    suspend fun importFrom(sourceUri: String, password: String?): ImportBackupResult {
        val bytes = fileStorageService.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: return ImportBackupResult.Failed("Impossible de lire le fichier")

        val jsonBytes = if (BackupCrypto.isEncryptedEnvelope(bytes)) {
            if (password.isNullOrBlank()) {
                return ImportBackupResult.Failed("Cette sauvegarde est chiffrée : un mot de passe est requis")
            }
            try {
                BackupCrypto.decrypt(bytes, password)
            } catch (e: BackupCrypto.WrongPasswordException) {
                return ImportBackupResult.Failed("Mot de passe incorrect")
            }
        } else {
            bytes
        }

        val payload = runCatching { backupJson.decodeFromString<BackupPayload>(jsonBytes.decodeToString()) }
            .getOrElse { return ImportBackupResult.Failed("Fichier de sauvegarde invalide ou corrompu") }

        var restored = 0
        var skippedOrphans = 0

        // CORRECTIF par rapport au legacy : le legacy inserait sans
        // verifier que la Publication referencee existe encore - une
        // contrainte FK aurait fait planter tout l'import sur un seul
        // signet orphelin. Ici : verification explicite, comptage
        // separe, jamais un crash sur une donnee partiellement obsolete.
        payload.bookmarks.forEach { backup ->
            if (publicationRepository.getById(backup.publicationId) != null) {
                bookmarkRepository.insert(backup.toDomain())
                restored++
            } else {
                skippedOrphans++
            }
        }
        payload.pronunciationRules.forEach { backup ->
            pronunciationRuleRepository.save(backup.toDomain())
            restored++
        }
        payload.customThemes.forEach { backup ->
            themeRepository.saveCustom(backup.toDomain())
            restored++
        }
        payload.readingStates.forEach { backup ->
            if (publicationRepository.getById(backup.publicationId) != null) {
                readingStateRepository.save(backup.toDomain())
                restored++
            } else {
                skippedOrphans++
            }
        }
        payload.readingSessions.forEach { backup ->
            if (publicationRepository.getById(backup.publicationId) != null) {
                readingSessionRepository.insert(backup.toDomain())
                restored++
            } else {
                skippedOrphans++
            }
        }
        payload.annotations.forEach { backup ->
            if (publicationRepository.getById(backup.publicationId) != null) {
                annotationRepository.insert(backup.toDomain())
                restored++
            } else {
                skippedOrphans++
            }
        }

        return ImportBackupResult.Success(restored = restored, skippedOrphans = skippedOrphans)
    }
}
