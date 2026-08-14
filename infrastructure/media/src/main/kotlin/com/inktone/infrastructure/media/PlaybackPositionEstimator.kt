package com.inktone.infrastructure.media

/**
 * Calcul pur de la frame jouée courante à partir d'un échantillon
 * `AudioTrack.getTimestamp()` (Lot 16, spike positif). Extraite pour être
 * testable en JVM — sans Android.
 *
 * `getTimestamp()` rapporte que la frame [timestampFramePosition] a été
 * présentée à la sortie audio à l'instant [timestampNanoTime]. Depuis, la
 * lecture avance à [sampleRate] frames/seconde : on extrapole jusqu'à
 * [nowNanoTime].
 */
fun estimatePlayedFrame(
    timestampFramePosition: Long,
    timestampNanoTime: Long,
    nowNanoTime: Long,
    sampleRate: Int,
): Long {
    require(sampleRate > 0) { "sampleRate doit être strictement positif" }
    val elapsedFrames = (nowNanoTime - timestampNanoTime) * sampleRate / NANOS_PER_SECOND
    return timestampFramePosition + elapsedFrames
}

private const val NANOS_PER_SECOND = 1_000_000_000L
