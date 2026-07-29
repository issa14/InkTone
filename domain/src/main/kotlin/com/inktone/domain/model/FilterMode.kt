package com.inktone.domain.model

/**
 * Modes de filtrage de la bibliothèque (Tâche 6.5) — construit de zéro,
 * rien de posé en Phase 1 : `PublicationRepository` n'exposait que
 * `observeAll()` avant cette tâche.
 *
 * `SERIES`/`TAG`/`BY_AUTHOR` attendent une valeur (`value` dans
 * `PublicationRepository.observeFiltered`) ; les autres l'ignorent.
 *
 * `READ` : aucun concept explicite de « terminé » n'existe dans le
 * domaine (pas de champ `Publication.isFinished`, pas de `progression`
 * stockée). Défini ici comme « dernier chapitre atteint »
 * (`ReadingState.locator.chapterIndex >= Publication.chapterCount - 1`)
 * plutôt qu'un seuil de progression en pourcentage — ce dernier
 * exigerait la longueur totale du texte par chapitre, absente du schéma
 * actuel (`ReadingStateEntity` ne stocke qu'un offset de caractère dans
 * la ressource courante, pas un total). Connu limité pour les livres à
 * chapitre unique (voir Blueprint §16.4, PHASE_6_LIBRARY_IMPORT.md
 * §6.5.2) — point produit non tranché, pas une vérité du domaine.
 */
enum class FilterMode {
    ALL, FAVORITES, SERIES, TAG, BY_AUTHOR, IN_PROGRESS, READ, UNREAD
}
