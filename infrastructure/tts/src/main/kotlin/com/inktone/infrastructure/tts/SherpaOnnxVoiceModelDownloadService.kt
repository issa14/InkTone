package com.inktone.infrastructure.tts

import android.content.Context
import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation reelle de [VoiceModelDownloadService] (Tache 8.7) —
 * Lot 20 : installe **la voix et le modèle CTC** en une seule opération,
 * puis rend la chaîne réellement utilisable.
 *
 * Avant le Lot 20, cette classe téléchargeait l'archive `.tar.bz2` de la
 * voix sans jamais l'extraire (`SherpaOnnxModelPaths.isReady` toujours
 * faux) et n'affichait pas moins « Voix neuronale installée » —
 * AUDIT_CONSOLIDATION_V1.md B2. Désormais :
 *
 * 1. **Voix** `vits-piper-fr_FR-upmc-medium` (fp32, ~80 Mo, 2 locuteurs
 *    jessica/pierre) — téléchargée, SHA-256 vérifié, **extraite** vers
 *    `SherpaOnnxModelPaths.dir`.
 * 2. **Modèle CTC** d'alignement forcé (`nemo-fast-conformer-ctc-...-20k`
 *    int8, ~102 Mo — même modèle que le prototype, doc l.703) —
 *    téléchargé, vérifié, extrait vers `CtcModelPaths.dir` (surlignage
 *    mot à mot réel).
 *
 * Progression **globale** (somme des octets des deux archives) ; chaque
 * modèle déjà prêt est sauté (reprise après interruption). L'état
 * « installée » n'est vrai que si les deux `isReady` le sont — jamais
 * avant.
 *
 * URL/hash lus depuis l'API GitHub Releases (digest), jamais calculés à
 * la main sur un fichier téléchargé sans vérification indépendante.
 */
@Singleton
class SherpaOnnxVoiceModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelPaths: SherpaOnnxModelPaths,
    private val ctcModelPaths: CtcModelPaths,
    private val voiceModelDownloader: VoiceModelDownloader,
    // Lot 20 — préchauffage après installation : le premier usage TTS ne
    // paie pas le chargement froid des deux modèles ONNX (budget §11.2).
    private val ttsEngine: SherpaOnnxTtsEngine,
) : VoiceModelDownloadService {

    override fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress> = flow {
        val voiceRemaining = if (modelPaths.isReady) 0L else VOICE_ARCHIVE_SIZE
        val ctcRemaining = if (ctcModelPaths.isReady) 0L else CTC_ARCHIVE_SIZE
        val total = voiceRemaining + ctcRemaining
        if (total == 0L) {
            emit(VoiceDownloadProgress.Complete)
            return@flow
        }
        var done = 0L

        if (voiceRemaining > 0L) {
            val ok = downloadAndExtract(
                url = VOICE_ARCHIVE_URL,
                sha256 = VOICE_ARCHIVE_SHA256,
                fileName = VOICE_ARCHIVE_FILE_NAME,
                subDir = "voices",
                archiveSize = VOICE_ARCHIVE_SIZE,
                targetDir = modelPaths.dir,
                total = total,
                done = done,
            ) { newDone -> done = newDone }
            if (!ok) return@flow
        }

        if (ctcRemaining > 0L) {
            val ok = downloadAndExtract(
                url = CTC_ARCHIVE_URL,
                sha256 = CTC_ARCHIVE_SHA256,
                fileName = CTC_ARCHIVE_FILE_NAME,
                subDir = "models",
                archiveSize = CTC_ARCHIVE_SIZE,
                targetDir = ctcModelPaths.dir,
                total = total,
                done = done,
            ) { newDone -> done = newDone }
            if (!ok) return@flow
        }

        // Lot 20 — préchauffage : charge les deux modèles ONNX (TTS +
        // aligneur) hors du premier usage, pour tenir le budget §11.2.
        withContext(Dispatchers.IO) { ttsEngine.warmUp() }

        emit(VoiceDownloadProgress.Complete)
    }

    override fun isDefaultVoiceInstalled(): Boolean =
        modelPaths.isReady && ctcModelPaths.isReady

    /**
     * Télécharge, vérifie puis extrait une archive dans [targetDir].
     * Émet la progression (octets globaux) au fil du téléchargement.
     * @return faux (et une erreur `Failed` émise) en cas d'échec —
     *   jamais d'échec silencieux ni d'archive partielle conservée.
     */
    private suspend fun FlowCollector<VoiceDownloadProgress>.downloadAndExtract(
        url: String,
        sha256: String,
        fileName: String,
        subDir: String,
        archiveSize: Long,
        targetDir: File,
        total: Long,
        done: Long,
        onDoneUpdate: (Long) -> Unit,
    ): Boolean {
        var step = false
        voiceModelDownloader.downloadVoiceModel(url, sha256, fileName, subDir).collect { progress ->
            when (progress) {
                is DownloadProgress.InProgress ->
                    emit(VoiceDownloadProgress.InProgress(done + progress.bytesDownloaded, total))
                is DownloadProgress.Complete -> {
                    // L'archive vérifiée est déjà sur disque (chemin fourni
                    // par le téléchargeur) — extraction + suppression.
                    val extracted = try {
                        withContext(Dispatchers.IO) {
                            TarBz2Extractor.extract(progress.modelFile, targetDir, stripRoot = true)
                            progress.modelFile.delete()
                            true
                        }
                    } catch (e: Exception) {
                        emit(VoiceDownloadProgress.Failed("Extraction du modèle impossible : ${e.message}"))
                        false
                    }
                    if (!extracted) {
                        step = true
                        return@collect
                    }
                    val newDone = done + archiveSize
                    onDoneUpdate(newDone)
                    emit(VoiceDownloadProgress.InProgress(newDone, total))
                }
                is DownloadProgress.Failed -> {
                    emit(VoiceDownloadProgress.Failed(progress.message))
                    step = true
                }
                is DownloadProgress.VerificationFailed -> {
                    emit(VoiceDownloadProgress.Failed("Empreinte invalide (attendu ${progress.expectedHash})"))
                    step = true
                }
            }
        }
        return !step
    }

    private companion object {
        const val VOICE_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-upmc-medium.tar.bz2"
        const val VOICE_ARCHIVE_SHA256 =
            "e9830a331a16f6cc5ef3116a287065e015d3495c3f56b974889a266da7f89a7f"
        const val VOICE_ARCHIVE_FILE_NAME = "vits-piper-fr_FR-upmc-medium.tar.bz2"
        const val VOICE_ARCHIVE_SIZE = 80_422_639L

        const val CTC_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8.tar.bz2"
        const val CTC_ARCHIVE_SHA256 =
            "2116eebbfc923ee3332a244e8c933ccc1b7e6783070f7bf842d0b5fc64f6ae33"
        const val CTC_ARCHIVE_FILE_NAME = "sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8.tar.bz2"
        const val CTC_ARCHIVE_SIZE = 102_261_698L
    }
}
