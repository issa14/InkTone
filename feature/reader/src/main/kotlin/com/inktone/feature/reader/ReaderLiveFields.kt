package com.inktone.feature.reader

import androidx.compose.runtime.SnapshotMutationPolicy

/**
 * Champs VIVANTS de [ReaderUiState] : ceux qui changent au rythme de la
 * lecture — un paragraphe franchi, une page tournée, une phrase lue, une
 * seconde du minuteur — et non au rythme de la structure de l'écran.
 *
 * Tous neutralisés ici, et uniquement eux : ce sont ces champs (et les
 * propriétés calculées qui en dérivent, `bookProgression`,
 * `isCurrentPageBookmarked`) que `ReaderScreen` ne doit jamais lire via sa
 * vue structurelle. Un champ ajouté à [ReaderUiState] qui change à ce
 * rythme doit être ajouté ici ET lu via `uiState.value` dans l'écran.
 */
internal fun ReaderUiState.withoutLiveFields(): ReaderUiState = copy(
    currentSentenceIndex = 0,
    isAudioActive = false,
    sleepTimer = null,
    eyeRestReminderCountdownS = 0,
    pageOffsetY = 0f,
)

/** Vrai si [this] et [other] ne diffèrent que par leurs champs vivants. */
internal fun ReaderUiState.isStructurallyEquivalentTo(other: ReaderUiState): Boolean =
    this === other || withoutLiveFields() == other.withoutLiveFields()

/**
 * Politique de la vue structurelle de `ReaderScreen` : une nouvelle valeur
 * de l'état n'invalide ses lecteurs que si elle diffère hors champs vivants.
 * Sinon la valeur précédente est conservée — avec, par construction, des
 * champs vivants périmés (voir [withoutLiveFields]).
 */
internal object ReaderStructuralPolicy : SnapshotMutationPolicy<ReaderUiState> {
    override fun equivalent(a: ReaderUiState, b: ReaderUiState): Boolean = a.isStructurallyEquivalentTo(b)
}
