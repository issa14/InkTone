package com.inktone.infrastructure.tts

import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation reelle de [VoiceModelDownloadService] (Tache 8.7) —
 * delegue a [VoiceModelDownloader] (Tache 5.6, deja fait et teste).
 *
 * TODO(URL/SHA-256 du modele Kokoro reel a finaliser avant release —
 * ADR-018 ne fixe pas encore l'origine de distribution du binaire ;
 * suivi hors Phase 8, cf. checklist de cloture Phase 5 point 7) : les
 * constantes ci-dessous sont des espaces reserves, pas des secrets, et
 * doivent etre remplacees par la vraie URL de distribution avant
 * publication.
 */
@Singleton
class SherpaOnnxVoiceModelDownloadService @Inject constructor(
    private val modelPaths: SherpaOnnxModelPaths,
    private val voiceModelDownloader: VoiceModelDownloader,
) : VoiceModelDownloadService {

    override fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress> =
        voiceModelDownloader.downloadVoiceModel(
            url = KOKORO_MODEL_URL,
            expectedSha256 = KOKORO_MODEL_SHA256,
            fileName = modelPaths.modelFile.name,
        ).map { progress ->
            when (progress) {
                is DownloadProgress.InProgress -> VoiceDownloadProgress.InProgress(progress.bytesDownloaded, progress.totalBytes)
                is DownloadProgress.Complete -> VoiceDownloadProgress.Complete
                is DownloadProgress.Failed -> VoiceDownloadProgress.Failed(progress.message)
                is DownloadProgress.VerificationFailed ->
                    VoiceDownloadProgress.Failed("Empreinte invalide (attendu ${progress.expectedHash}, obtenu ${progress.actualHash})")
            }
        }

    private companion object {
        const val KOKORO_MODEL_URL = "https://TODO-cdn-inktone/kokoro-int8-multi-lang-v1_0/model.int8.onnx"
        const val KOKORO_MODEL_SHA256 = "TODO-sha256-a-completer-avant-release"
    }
}
