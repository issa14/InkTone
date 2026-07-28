package com.inktone.infrastructure.tts

/**
 * Binding JNI vers `kaldi-native-fbank` (compilé pour arm64-v8a via
 * CMake/NDK, `src/main/cpp/`) — extraction des features fbank +
 * normalisation NeMo pour le modèle d'alignement forcé CTC (Tâche 5.2).
 * Déjà prouvé sur device réel avant ce portage :
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §6.
 */
internal object CtcFbankNative {
    init {
        System.loadLibrary("inktone_ctc_fbank")
    }

    /** `audio` doit être à `sampleRate` Hz — resampler avant l'appel si besoin (voir [AudioResampler]). */
    external fun computeNemoFbank(audio: FloatArray, sampleRate: Int): FloatArray
}
