package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résout les chemins du modèle vocal VITS (Palier 2, voix fr_FR-siwis-
 * medium, CC-BY 4.0 — Tâche 5.1.1) sur le stockage privé de l'app.
 * `context.filesDir/voices/<nom>/` — même convention que
 * `VoiceModelDownloader` (Tâche 5.6), qui remplira réellement ce
 * répertoire par téléchargement vérifié. Pour l'instant (avant 5.6),
 * le contenu y est placé manuellement pour la validation de
 * l'adaptateur (5.1.2).
 */
@Singleton
class SherpaOnnxModelPaths @Inject constructor(@ApplicationContext context: Context) {

    private val voiceDir = File(context.filesDir, "voices/vits-piper-fr_FR-siwis-medium")

    val modelFile: File get() = File(voiceDir, "fr_FR-siwis-medium.onnx")
    val tokensFile: File get() = File(voiceDir, "tokens.txt")
    val espeakDataDir: File get() = File(voiceDir, "espeak-ng-data")

    val isReady: Boolean get() = modelFile.exists() && tokensFile.exists() && espeakDataDir.exists()
}
