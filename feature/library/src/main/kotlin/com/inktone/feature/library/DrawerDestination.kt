package com.inktone.feature.library

/**
 * Lot 18 — destination active de la liste principale du drawer.
 *
 * Remplace le `selected = true` figé sur l'item Bibliothèque de
 * [LibraryDrawerContent] : le drawer étant désormais partagé par les 6
 * destinations principales (hoisté dans `InkToneNavHost`), l'item
 * surligné doit refléter la destination réellement active, y compris en
 * navigation profonde (un écran de détail poussé depuis une destination —
 * ex. statistiques d'un livre — garde l'item de sa destination parente).
 *
 * Ne couvre que la liste principale : les 3 items du pied de drawer
 * (Paramètres, Thèmes, À propos) restent des poussées classiques à flèche
 * de retour, jamais surlignées (décision actée, plan du Lot 18).
 */
enum class DrawerDestination {
    RECENTS,
    LIBRARY,
    BOOKMARKS,
    OPDS,
    SYNC,
    STATISTICS,
}
