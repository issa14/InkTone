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

/**
 * AUDIT_REACTIVITE_UX §5.3 — millisecondes avant la prochaine échéance
 * connue (début du prochain mot si on est dans un silence inter-mots, fin
 * du mot courant sinon), à partir des [WordTimestamp] déjà en main. Permet
 * à [com.inktone.feature.reader.PlaybackOrchestrator] de cadencer son
 * sondage sur la durée réelle du mot plutôt que sur un intervalle fixe —
 * `0` si [wordTimestamps] est vide ou si `playedMs` a déjà dépassé la fin
 * du dernier mot (l'appelant sort de sa boucle sur ce cas avant d'appeler
 * cette fonction).
 */
internal fun msUntilNextWordBoundary(
    playedMs: Long,
    sentenceStartMs: Long,
    wordTimestamps: List<WordTimestamp>,
): Long {
    val relative = playedMs - sentenceStartMs
    for (timestamp in wordTimestamps) {
        if (relative < timestamp.startMs) return timestamp.startMs - relative
        if (relative < timestamp.endMs) return timestamp.endMs - relative
    }
    return 0L
}
