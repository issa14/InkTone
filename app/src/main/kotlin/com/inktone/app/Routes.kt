package com.inktone.app

import kotlinx.serialization.Serializable

/**
 * Routes typées (Tâche 9bis.0.1/9bis.2) — Compose Navigation 2.8+,
 * remplace l'état `AppScreen` à 3 cas (Phase 7) et les chaînes de
 * caractères du legacy (`"reader/{bookId}?jumpChapter=..."`). Le
 * compilateur garantit la cohérence des arguments, plus de
 * désérialisation manuelle de query params.
 *
 * `ReaderRoute` reprend les primitifs déjà définis par
 * `ReaderIntent.OpenPublication` (feature/reader) plutôt que le
 * `jumpChapter`/`jumpSentence` du plan initial — `MainActivity` n'a pas
 * le droit de dépendre de `domain` (Blueprint §12.4), ces champs sont
 * déjà la forme primitive utilisée pour franchir cette frontière.
 */
@Serializable
data class ReaderRoute(
    val publicationId: String,
    val targetResourceHref: String? = null,
    val targetChapterIndex: Int? = null,
    val targetCharOffset: Int? = null,
    /** Lot 4, tâche 4.7 — arrivée depuis « Marque-pages et notes » : flash différé du passage visé. */
    val flashOnArrival: Boolean = false,
)

@Serializable
object LibraryRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

@Serializable
object PronunciationRulesRoute

@Serializable
object StatisticsRoute

@Serializable
object BookmarksRoute

@Serializable
object AboutRoute

/**
 * Lot 2a.4 — écran de détail Séries/Tags, un seul écran réutilisable
 * pour les deux cas. `category` reste une chaîne brute ("series"/"tag")
 * plutôt que l'enum `LibraryDetailCategory` de `feature:library` : ce
 * module n'a pas le plugin kotlinx.serialization (seuls `app`/`data`
 * l'ont), même raison que les primitifs de [ReaderRoute].
 */
@Serializable
data class LibraryDetailRoute(val category: String, val value: String)

