package com.inktone.domain.model

/**
 * Catalogue des voix disponibles par moteur (Lot 14, Lot 20). Source de
 * vérité du DOMAINE pour le sélecteur de voix ; l'infrastructure valide à
 * la synthèse (ex. `EdgeTtsClient` retombe sur sa voix par défaut si une
 * valeur invalide est transmise ; `SherpaOnnxTtsEngine` résout le sid
 * depuis la voix). À ne pas confondre avec `VoiceProfile` : un profil =
 * un moteur + une voix + vitesse/intonation, alors que ce catalogue ne
 * décrit que la liste des voix d'un moteur.
 *
 * Lot 20 — Sherpa-ONNX bascule de Kokoro (`ff_siwis`) vers le modèle
 * `vits-piper-fr_FR-upmc-medium` qui porte **2 locuteurs** (vérifié dans
 * les métadonnées ONNX, pas supposé) : `jessica` (sid 0) et `pierre`
 * (sid 1) — mêmes noms que le legacy.
 */
fun availableVoicesFor(engine: TtsEngineId): List<String> = when (engine) {
    TtsEngineId.SHERPA_ONNX -> listOf("jessica", "pierre")
    TtsEngineId.ANDROID_NATIVE -> listOf("fr-fr-default")
    TtsEngineId.EDGE_TTS -> listOf("fr-FR-VivienneNeural", "fr-FR-HenriNeural")
    TtsEngineId.PIPER -> emptyList()
}

/** Libellé lisible d'une voix technique (jamais le nom brut — K12). */
fun voiceLabel(voice: String): String = when (voice) {
    "fr-FR-VivienneNeural" -> "Vivienne (FR)"
    "fr-FR-HenriNeural" -> "Henri (FR)"
    "jessica" -> "Jessica (FR)"
    "pierre" -> "Pierre (FR)"
    "fr-fr-default" -> "Voix système"
    else -> voice
}
