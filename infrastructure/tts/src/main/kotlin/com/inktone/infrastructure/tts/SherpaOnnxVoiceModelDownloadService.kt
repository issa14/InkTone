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
 * **Decision d'hebergement (Tache 9.0.1)** : option 1 retenue (URL
 * publique deja stable), pas de CDN propre. Verifie le 2026-07-29,
 * PAS suppose : `k2-fsa/sherpa-onnx` publie le paquet
 * `kokoro-int8-multi-lang-v1_0` (celui deja vendore et valide en Phase 5)
 * comme asset de la release GitHub taguee `tts-models`, url stable
 * (`releases/download/<tag>/<asset>`, ne change pas meme si l'asset est
 * remplace) :
 * `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2`.
 * SHA-256 lu directement depuis le champ `digest` de l'API GitHub
 * Releases pour cet asset (`gh api .../releases/tags/tts-models`),
 * jamais calcule a la main sur un fichier telecharge sans verification
 * independante. Taille annoncee par GitHub : 131 839 838 octets
 * (~125,7 Mio).
 *
 * Option CDN propre explicitement rejetee : cout d'infrastructure
 * recurrent pour une app gratuite a donation volontaire (philosophie
 * actee Phase 5), sans benefice pour une release qui n'a change ni de
 * contenu ni d'URL depuis sa publication initiale par k2-fsa.
 *
 * **Limite non resolue par cette tache, a ne pas confondre avec un
 * hebergement non tranche** : l'asset telecharge est une ARCHIVE
 * `.tar.bz2` contenant plusieurs fichiers (`model.int8.onnx`,
 * `voices.bin`, `tokens.txt`, `espeak-ng-data/`, lexiques, `.fst`) —
 * pas le seul `model.int8.onnx` attendu tel quel par
 * [SherpaOnnxModelPaths]. [VoiceModelDownloader] (Tache 5.6) telecharge
 * et verifie l'empreinte d'un fichier unique, il n'extrait aucune
 * archive. Cette classe telecharge donc l'archive verifiee dans le
 * repertoire de voix (a cote de l'arborescence attendue), mais
 * l'extraction tar+bzip2 vers les fichiers individuels reste TODO
 * (necessite une dependance de decompression, ex. Apache Commons
 * Compress, absente du projet) — a traiter dans une tache dediee, pas
 * suppose fonctionner silencieusement ici.
 */
@Singleton
class SherpaOnnxVoiceModelDownloadService @Inject constructor(
    private val modelPaths: SherpaOnnxModelPaths,
    private val voiceModelDownloader: VoiceModelDownloader,
) : VoiceModelDownloadService {

    override fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress> =
        voiceModelDownloader.downloadVoiceModel(
            url = KOKORO_MODEL_ARCHIVE_URL,
            expectedSha256 = KOKORO_MODEL_ARCHIVE_SHA256,
            fileName = KOKORO_MODEL_ARCHIVE_FILE_NAME,
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
        const val KOKORO_MODEL_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
        const val KOKORO_MODEL_ARCHIVE_SHA256 =
            "75654a84864be26f345f020f4070c2c019e96dd1b7f9bf6e2ffd59efac6aa5a3"
        const val KOKORO_MODEL_ARCHIVE_FILE_NAME = "kokoro-int8-multi-lang-v1_0.tar.bz2"
    }
}
