package com.inktone.feature.reader

/** Rythme de lecture au-delà duquel on ne croit plus que le texte a été lu (mots par minute). */
internal const val MAX_PLAUSIBLE_READING_WPM = 900

/** Marge de mots, pour qu'un paragraphe franchi d'un geste ne soit jamais écarté. */
internal const val CREDIT_SLACK_WORDS = 60

/**
 * Mots qu'on peut raisonnablement créditer comme lus en [elapsedMs] : faire
 * défiler vite un chapitre ne crédite pas tous les mots franchis (audit UX D29).
 */
internal fun plausibleWordLimit(elapsedMs: Long): Int =
    (elapsedMs.coerceAtLeast(0L) * MAX_PLAUSIBLE_READING_WPM / 60_000L).toInt() + CREDIT_SLACK_WORDS
