package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résout les chemins du modèle d'alignement forcé CTC (NeMo FastConformer
 * CTC multilingue, variante int8 — Tâche 5.2, déjà validée sur device réel
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §7) sur le stockage privé
 * de l'app. Même convention que `SherpaOnnxModelPaths` (Tâche 5.6 pour le
 * téléchargement vérifié, placement manuel pour l'instant).
 *
 * Modèle distinct du modèle de synthèse Kokoro (`SherpaOnnxModelPaths`) :
 * deux modèles ONNX différents, chargés dans deux sessions séparées.
 */
@Singleton
class CtcModelPaths @Inject constructor(@ApplicationContext context: Context) {

    private val modelDir = File(context.filesDir, "models/nemo-ctc-fr-multilang-int8")

    val modelFile: File get() = File(modelDir, "model.int8.onnx")
    val tokensFile: File get() = File(modelDir, "tokens.txt")

    /** Répertoire cible de l'extraction — exposé pour le service de téléchargement. */
    internal val dir: File get() = modelDir

    val isReady: Boolean get() = modelFile.exists() && tokensFile.exists()
}
