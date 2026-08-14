package com.inktone.feature.reader

import com.inktone.domain.service.WordTimestamp

/**
 * Intervalle de caractères du mot courant, déduit de la position jouée (Lot 16,
 * Tâche 2.1). Fonction pure JVM — aucune coroutine, aucune dépendance Android —
 * pour être testée sans appareil.
 *
 * @param playedMs position jouée estimée depuis le début de la lecture
 *  (`AudioPlayer.playbackPosition.playedMs`).
 * @param sentenceStartMs position jouée au début de la phrase courante (cumul
 *  des durées des phrases précédentes, silences compris).
 * @param wordTimestamps horodatages mot-à-mot de la phrase courante
 *  (`startMs`/`endMs` relatifs au début de l'audio de la phrase).
 * @return l'intervalle `charOffset until charOffset + word.length` du mot en
 *  cours de lecture, ou `null` hors des bornes de tout mot (silence inter-mots,
 *  silence ponctué, ou phrase sans timestamps).
 */
internal fun wordRangeAt(
    playedMs: Long,
    sentenceStartMs: Long,
    wordTimestamps: List<WordTimestamp>,
): IntRange? {
    val relative = playedMs - sentenceStartMs
    for (timestamp in wordTimestamps) {
        if (relative >= timestamp.startMs && relative < timestamp.endMs) {
            return timestamp.charOffset until (timestamp.charOffset + timestamp.word.length)
        }
    }
    return null
}
