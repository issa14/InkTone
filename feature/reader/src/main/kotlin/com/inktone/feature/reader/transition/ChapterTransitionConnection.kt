package com.inktone.feature.reader.transition

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Connexion de scroll imbriqué unique pour les deux orientations : capte
 * l'overscroll au bord du chapitre, amortit le tirage (via
 * [ChapterTransitionState]) et décide au relâchement (distance, vélocité
 * ou hystérésis) s'il faut valider ou annuler.
 *
 * **Bug réel trouvé à la vérification device (résistance perceptible sur
 * un swipe normal, PAGED comme SCROLL)** : la première implémentation
 * interceptait via `onPreScroll`, appelé AVANT que l'enfant scrollable
 * (`HorizontalPager`/`LazyColumn`) n'ait eu la moindre chance de consommer
 * le delta. Un simple bruit de signe sur les toutes premières frames d'un
 * geste (tolérance tactile, arrondi) suffisait alors à voler le début
 * d'un swipe parfaitement normal en page 0 ou dernière page — capturé à
 * tort comme un tirage de transition, jamais restitué au pager, d'où la
 * sensation de résistance/à-coups. Cette connexion capte désormais via
 * `onPostScroll` : elle ne voit que le delta RESTANT une fois l'enfant
 * scrollable revenu bredouille — c'est-à-dire un authentique dépassement
 * de bord, jamais une portion d'un scroll que l'enfant pouvait honorer
 * lui-même. Un swipe normal (l'enfant consomme tout) ne traverse donc
 * plus jamais cette connexion.
 *
 * Bugs réels trouvés à l'audit de la branche :
 * - le geste restait actif pendant un chargement de chapitre déjà en cours
 *   ([ChapterTransitionState.isLoading]), permettant un second `onCommit`
 *   concurrent (chapitre sauté) ;
 * - un tirage abandonné par l'utilisateur (retour au scroll normal AVANT
 *   que [canPullPrevious]/[canPullNext] ne redevienne faux naturellement)
 *   ne réinitialisait jamais [state] — le relâchement final pouvait alors
 *   valider une transition sur un état périmé ;
 * - la sélection de texte libre partage la même arène de gestes que ce
 *   geste de tirage : sans [isSelectionActive], un glissement de sélection
 *   au bord du chapitre pouvait être capté par ce connecteur plutôt que
 *   par le champ de texte.
 */
class ChapterTransitionConnection(
    private val state: ChapterTransitionState,
    private val orientation: Orientation,
    private val canPullPrevious: () -> Boolean,
    private val canPullNext: () -> Boolean,
    private val isSelectionActive: () -> Boolean = { false },
    private val onCommit: (ChapterTransitionDirection) -> Unit,
    private val onCancel: () -> Unit,
) : NestedScrollConnection {

    /** Un chargement en cours ou une sélection active neutralisent tout le geste. */
    private fun isGestureAllowed(): Boolean = !state.isLoading && !isSelectionActive()

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // UserInput = drag utilisateur (Drag est déprécié en 1.7 au profit de UserInput).
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (!isGestureAllowed()) {
            abandon()
            return Offset.Zero
        }
        // `available` ICI est ce qu'il RESTE après que l'enfant scrollable
        // a consommé tout ce qu'il pouvait — jamais le delta brut du doigt.
        val delta = if (orientation == Orientation.Vertical) available.y else available.x
        // Signe vérifié sur appareil : delta > 0 = tirer vers le bas/droite
        // (doigt vers le bas/droite = chapitre précédent), delta < 0 =
        // pousser vers le haut/gauche (chapitre suivant).
        return when {
            delta > 0f && canPullPrevious() -> {
                state.onDrag(delta, ChapterTransitionDirection.PREVIOUS)
                consume(delta)
            }
            delta < 0f && canPullNext() -> {
                state.onDrag(delta, ChapterTransitionDirection.NEXT)
                consume(delta)
            }
            // Rien à tirer ici (l'enfant a tout consommé, ou la condition
            // de bord n'est plus remplie) : tout tirage entamé
            // précédemment devient obsolète, il ne doit pas survivre
            // jusqu'au relâchement final.
            else -> {
                abandon()
                Offset.Zero
            }
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val direction = state.direction ?: return available
        if (!isGestureAllowed()) {
            abandon()
            return available
        }
        val velocity = if (orientation == Orientation.Vertical) available.y else available.x
        return if (state.resolveRelease(velocity)) {
            onCommit(direction)
            Velocity.Zero
        } else {
            onCancel()
            Velocity.Zero
        }
    }

    /** Efface un tirage entamé mais devenu invalide, sans effet si rien n'était en cours. */
    private fun abandon() {
        if (state.direction == null) return
        state.cancel()
        onCancel()
    }

    private fun consume(delta: Float): Offset =
        if (orientation == Orientation.Vertical) Offset(0f, delta) else Offset(delta, 0f)
}
