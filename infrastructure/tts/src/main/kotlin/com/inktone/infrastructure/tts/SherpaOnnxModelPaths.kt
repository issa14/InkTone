package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résout les chemins du modèle vocal VITS Piper `fr_FR-upmc-medium`
 * (Lot 20 — remplace Kokoro) sur le stockage privé de l'app.
 *
 * Le modèle (2 locuteurs : `jessica` sid 0, `pierre` sid 1 — vérifié dans
 * les métadonnées `fr_FR-upmc-medium.onnx.json`, pas supposé) est
 * téléchargé puis **extrait** par [SherpaOnnxVoiceModelDownloadService]
 * (extraction tar.bz2, Lot 20) dans ce répertoire — même convention que
 * `VoiceModelDownloader` (Tâche 5.6). Les fichiers attendus sont ceux de
 * l'archive sherpa-onnx `vits-piper-fr_FR-upmc-medium` (listing vérifié) :
 * `fr_FR-upmc-medium.onnx`, `tokens.txt`, `espeak-ng-data/` — **pas de
 * `lexicon.txt`** : le phonémiseur espeak-ng fait le G2P (lexicon vide,
 * comme dans le legacy, `OnnxInferenceService.kt`).
 */
@Singleton
class SherpaOnnxModelPaths @Inject constructor(@ApplicationContext context: Context) {

    private val voiceDir = File(context.filesDir, "voices/vits-piper-fr_FR-upmc-medium")

    /** Répertoire cible de l'extraction — exposé pour le service de téléchargement. */
    internal val dir: File get() = voiceDir

    val modelFile: File get() = File(voiceDir, "fr_FR-upmc-medium.onnx")
    val tokensFile: File get() = File(voiceDir, "tokens.txt")
    val espeakDataDir: File get() = File(voiceDir, "espeak-ng-data")

    /** Vrai seulement si la VOIX est réellement exploitable — source de
     * vérité pour l'UI (« installée ») et pour le moteur. */
    val isReady: Boolean
        get() = modelFile.exists() && tokensFile.exists() && espeakDataDir.exists()
}
