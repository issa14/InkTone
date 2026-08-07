package com.inktone.data.backup

import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
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

/**
 * Export/import JSON des metadonnees utilisateur (Tache 8.5, recupere
 * de l'audit UX legacy) — jamais les livres eux-memes.
 *
 * `appVersion` est passe par l'appelant (module `app`, seul endroit qui
 * connait `BuildConfig.VERSION_NAME` d'une application, pas d'une
 * bibliotheque) plutot que lu directement ici.
 */
class BackupManager @Inject constructor(
    private val fileStorageService: FileStorageService,
    private val bookmarkRepository: BookmarkRepository,
    private val pronunciationRuleRepository: PronunciationRuleRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) {
    /** @return `true` si l'écriture a réussi — le résultat doit remonter à l'appelant, pas être avalé. */
    suspend fun exportTo(destinationUri: String, appVersion: String): Boolean {
        val payload = BackupPayload(
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            bookmarks = bookmarkRepository.observeAll().first().map { it.toBackup() },
            pronunciationRules = pronunciationRuleRepository.observeAll().first().map { it.toBackup() },
            readingStates = readingStateRepository.getAll().map { it.toBackup() },
            readingSessions = readingSessionRepository.getAll().map { it.toBackup() },
        )
        val json = Json.encodeToString(payload)
        val tempFile = File.createTempFile("inktone-backup", ".json")
        return try {
            tempFile.writeText(json)
            fileStorageService.writeToUri(destinationUri, tempFile)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importFrom(sourceUri: String): ImportBackupResult {
        val json = fileStorageService.openInputStream(sourceUri)?.bufferedReader()?.readText()
            ?: return ImportBackupResult.Failed("Impossible de lire le fichier")

        val payload = runCatching { Json.decodeFromString<BackupPayload>(json) }
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

        return ImportBackupResult.Success(restored = restored, skippedOrphans = skippedOrphans)
    }
}
