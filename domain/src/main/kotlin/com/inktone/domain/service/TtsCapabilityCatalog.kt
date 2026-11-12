package com.inktone.domain.service

import com.inktone.domain.model.TtsEngineId

/**
 * Capacités déclarées de chaque moteur TTS, indépendamment de celui qui
 * est actif à un instant donné.
 *
 * Le [TtsEngine] injecté dans l'application est un routeur : ses
 * `capabilities` reflètent le moteur de la DERNIÈRE synthèse. Un écran de
 * réglage a besoin de savoir ce que fait le moteur CHOISI, avant toute
 * synthèse — par exemple pour n'afficher le curseur d'intonation que si
 * ce moteur l'applique réellement (« un moteur ne fait jamais semblant »).
 */
interface TtsCapabilityCatalog {

    /** Capacités du moteur [engine], ou `null` s'il n'est pas disponible dans cette application. */
    fun capabilitiesOf(engine: TtsEngineId): TtsCapabilities?
}
