package com.inktone.domain.model

/**
 * Tache 9bis.3.3 — minuteur de sommeil, aucune brique domaine n'existait
 * avant cette tache (nouveau, pas dans les 10 phases). `fadeOutEnabled`
 * decrit l'intention (fondu sonore en fin de minuteur plutot qu'un arret
 * brutal) mais n'est pas encore applique par `AudioSegmentPlayer` — celui-ci
 * n'expose aucun controle de volume progressif aujourd'hui, seulement
 * play/stop (Tache 3.8). Le minuteur declenche donc pour l'instant un
 * `ReaderIntent.Pause` net a expiration, pas un fondu.
 */
data class SleepTimerState(
    val remainingMs: Long,
    /**
     * Durée initialement armée. Retenue à part du restant : sans elle, l'écran
     * ne peut pas savoir QUELLE durée a été choisie une fois le décompte
     * entamé — la puce « 30 min » se désélectionnerait au bout d'une seconde.
     */
    val totalMs: Long = remainingMs,
    val fadeOutEnabled: Boolean = true,
) {
    init {
        require(remainingMs >= 0) { "remainingMs doit être positif ou nul" }
        require(totalMs >= remainingMs) { "totalMs ne peut pas être inférieur au temps restant" }
    }
}
