package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress
    data class VerificationFailed(val expectedHash: String, val actualHash: String) : DownloadProgress
    data class Complete(val modelFile: File) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

/**
 * Téléchargement de modèle vocal à la demande (ADR-018) : l'APK
 * n'embarque aucun modèle, le premier lancement (ou une action explicite
 * de l'utilisateur) télécharge la voix choisie, vérifiée par empreinte
 * SHA-256 avant utilisation — jamais un modèle dont l'empreinte est
 * fausse n'est conservé sur disque.
 */
@Singleton
class VoiceModelDownloader @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * URL et hash SHA-256 attendus fournis par l'appelant — ce
     * téléchargeur ne connaît aucune URL/empreinte en dur, pour rester
     * réutilisable pour n'importe quelle voix.
     *
     * @param subDir Lot 20 — sous-répertoire cible dans `filesDir`
     *   (« voices » pour la voix, « models » pour le modèle CTC
     *   d'alignement), chaque modèle restant dans son arborescence.
     */
    fun downloadVoiceModel(
        url: String,
        expectedSha256: String,
        fileName: String,
        subDir: String = "voices",
    ): Flow<DownloadProgress> = flow {
        val targetFile = File(context.filesDir, "$subDir/$fileName")
        if (targetFile.exists() && verifyHash(targetFile, expectedSha256)) {
            emit(DownloadProgress.Complete(targetFile))
            return@flow
        }
        targetFile.parentFile?.mkdirs()

        try {
            val connection = java.net.URL(url).openConnection()
            val totalBytes = connection.contentLengthLong
            var downloaded = 0L
            connection.getInputStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        emit(DownloadProgress.InProgress(downloaded, totalBytes))
                        read = input.read(buffer)
                    }
                }
            }
        } catch (e: java.io.IOException) {
            targetFile.delete()
            emit(DownloadProgress.Failed(e.message ?: "Echec de telechargement"))
            return@flow
        }

        if (!verifyHash(targetFile, expectedSha256)) {
            val actual = computeHash(targetFile)
            targetFile.delete() // ne jamais garder un modele dont l'empreinte est fausse
            emit(DownloadProgress.VerificationFailed(expectedSha256, actual))
            return@flow
        }

        emit(DownloadProgress.Complete(targetFile))
    }.flowOn(Dispatchers.IO)

    private fun verifyHash(file: File, expectedSha256: String) = computeHash(file) == expectedSha256

    private fun computeHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read >= 0) { digest.update(buffer, 0, read); read = input.read(buffer) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
