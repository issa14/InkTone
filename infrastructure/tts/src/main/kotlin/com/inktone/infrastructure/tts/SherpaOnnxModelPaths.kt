package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résout les chemins du modèle vocal Kokoro (Palier 2, voix `ff_siwis`
 * française, speaker id 30 — `kokoro-int8-multi-lang-v1_0`) sur le
 * stockage privé de l'app. `context.filesDir/voices/<nom>/` — même
 * convention que `VoiceModelDownloader` (Tâche 5.6), qui remplira
 * réellement ce répertoire par téléchargement vérifié. Pour l'instant
 * (avant 5.6), le contenu y est placé manuellement pour la validation de
 * l'adaptateur (5.1.2).
 *
 * Remplace la voix VITS `fr_FR-siwis-medium` de la Tâche 5.1.1 : la
 * prémisse de cette tâche (« aucun modèle Kokoro français n'existe, le
 * catalogue multi-lang ne couvre que zh/en ») était factuellement fausse
 * — vérifié en pratique le 2026-07-28 en faisant tourner l'app d'exemple
 * officielle `SherpaOnnxTts` avec `kokoro-int8-multi-lang-v1_0` sur un
 * device Snapdragon 680 réel : le modèle contient bien une voix
 * française (`ff_siwis`, speaker id 30, confirmé via les métadonnées
 * ONNX `speaker2id` du modèle, pas supposé) et produit un français
 * correct (liaisons/élisions vérifiées,
 * `docs/execution/PROTOTYPE_SYNTHESE_KOKORO_ONNX.md`).
 */
@Singleton
class SherpaOnnxModelPaths @Inject constructor(@ApplicationContext context: Context) {

    private val voiceDir = File(context.filesDir, "voices/kokoro-int8-multi-lang-v1_0")

    val modelFile: File get() = File(voiceDir, "model.int8.onnx")
    val voicesFile: File get() = File(voiceDir, "voices.bin")
    val tokensFile: File get() = File(voiceDir, "tokens.txt")
    val espeakDataDir: File get() = File(voiceDir, "espeak-ng-data")
    val lexiconUsEnFile: File get() = File(voiceDir, "lexicon-us-en.txt")
    val lexiconZhFile: File get() = File(voiceDir, "lexicon-zh.txt")
    val phoneZhFst: File get() = File(voiceDir, "phone-zh.fst")
    val dateZhFst: File get() = File(voiceDir, "date-zh.fst")
    val numberZhFst: File get() = File(voiceDir, "number-zh.fst")

    val isReady: Boolean
        get() = modelFile.exists() && voicesFile.exists() && tokensFile.exists() &&
            espeakDataDir.exists() && lexiconUsEnFile.exists()
}
