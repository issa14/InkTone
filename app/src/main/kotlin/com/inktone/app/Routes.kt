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

/** Lot 10 — premier lancement uniquement (UserPreferences.hasSeenOnboarding). */
@Serializable
object OnboardingRoute

/** Lot 8 — item de drawer Récents, en première position des destinations. */
@Serializable
object RecentsRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

/** Lot 11, tâche 11.6 — dernière des 4 destinations masquées au lot 1 (drawer). */
@Serializable
object SyncRoute

/** Lot 13, tâche 13.6 — destination « Catalogues OPDS » (drawer b4, ADR-023). */
@Serializable
object OpdsRoute

@Serializable
object PronunciationRulesRoute

@Serializable
object StatisticsRoute

@Serializable
data class BookStatisticsRoute(val bookId: String)

@Serializable
object BookmarksRoute

@Serializable
object AboutRoute

/** Lot 9 — pied de drawer, "Thèmes" réactivé. */
@Serializable
object ThemeGalleryRoute

/** Lot 9 — themeId null = création (carte pointillée), non-null = édition (icône crayon). */
@Serializable
data class ThemeStudioRoute(val themeId: String? = null)

/**
 * Lot 2a.4 — écran de détail Séries/Tags, un seul écran réutilisable
 * pour les deux cas. `category` reste une chaîne brute ("series"/"tag")
 * plutôt que l'enum `LibraryDetailCategory` de `feature:library` : ce
 * module n'a pas le plugin kotlinx.serialization (seuls `app`/`data`
 * l'ont), même raison que les primitifs de [ReaderRoute].
 */
@Serializable
data class LibraryDetailRoute(val category: String, val value: String)

