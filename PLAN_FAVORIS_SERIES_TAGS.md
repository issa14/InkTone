# Plan d'implémentation — Favoris / Séries / Tags / Dossiers (fonctionnalités réelles)

Objectif : transformer le menu déroulant de la Bibliothèque, aujourd'hui décoratif, en fonctionnalités réellement câblées de bout en bout (DB → repository → ViewModel → UI). Basé sur l'audit du code réel (`LibraryScreen.kt`, `LibraryViewModel.kt`, `BookEntity.kt`, `BookMapper.kt`, `BookRepositoryImpl.kt`, `InkToneDatabase.kt`).

---

## 0. Diagnostic exact du problème actuel

- `applyFilters()` dans `LibraryViewModel.kt` ne lit **jamais** `filterMode` — seuls `filterType`, `sortOrder` et `searchQuery` influencent la liste affichée. Le menu `LibraryNavigationPopup` change un état qui n'est consulté nulle part.
- Aucun champ `isFavorite`, `series`, `seriesIndex` dans `BookEntity`/`Book`.
- `subjects` (tags potentiels) existe déjà en base — alimenté par `publication.metadata.subjects` via Readium à l'import — mais n'est branché à aucun filtre UI.
- **Point de vigilance découvert en cours d'audit, à traiter en même temps** : `InkToneDatabase.kt` est en `version = 13` mais seules les migrations `1→2` jusqu'à `5→6` sont définies, et `AppModule.kt` utilise `.fallbackToDestructiveMigration()`. Concrètement, chaque montée de version entre 6 et 13 a **effacé silencieusement toute la base** (livres, marque-pages, progression) chez les utilisateurs concernés. Ce n'est pas dans le périmètre demandé, mais toute nouvelle migration ajoutée ici doit être écrite en `Migration` réelle — pas relancer le fallback destructif sur une base qui contient déjà des vraies bibliothèques.

---

## 1. Périmètre retenu (complet, pas de placeholder)

| Fonctionnalité | Décision |
|---|---|
| **Favoris** | Implémentation complète : colonne DB, toggle UI (icône cœur sur la couverture + dans le lecteur), filtre fonctionnel |
| **Séries** | Auto-détection depuis les métadonnées EPUB (Readium `belongsTo`/collection EPUB3, fallback `calibre:series` EPUB2) + vue groupée par série, triée par tome. Champ éditable manuellement en secours si l'EPUB n'a pas cette métadonnée (beaucoup de livres FR n'ont pas de calibre:series) |
| **Tags** | Basé sur `subjects` existant (déjà peuplé, déjà en base) : filtre multi-sélection par chips. Ajout de tags manuels par l'utilisateur en V1.1 si utile, mais la V1 réelle = rendre `subjects` filtrable, ce qui est déjà 80% fait côté données |
| **Dossiers** | Ne pas dupliquer `FilesScreen` — l'entrée du menu doit naviguer vers l'écran Fichiers existant, pas simuler un filtre qui n'a pas de sens ici |

---

## 2. Migration Room (13 → 14)

`app/src/main/java/com/inktone/data/database/InkToneDatabase.kt` :

```kotlin
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN seriesName TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN seriesIndex REAL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_books_isFavorite ON books (isFavorite)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_books_seriesName ON books (seriesName)")
    }
}
```

- Incrémenter `version = 14` sur `@Database`.
- Ajouter `MIGRATION_13_14` à `.addMigrations(...)` dans `AppModule.kt`.
- **Ne pas** compter sur `fallbackToDestructiveMigration()` pour ce changement précis, même s'il reste présent ailleurs pour l'instant.

---

## 3. Couche données

### `BookEntity.kt`
```kotlin
val isFavorite: Boolean = false,
val seriesName: String? = null,
val seriesIndex: Float? = null
```

### `Book.kt` (domain)
Mêmes champs.

### `BookMapper.kt`
Ajouter le mapping `isFavorite`/`seriesName`/`seriesIndex` dans `toDomain()` et `toEntity()`.

### `BookDao.kt`
```kotlin
@Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
suspend fun setFavorite(bookId: String, isFavorite: Boolean)

@Query("SELECT DISTINCT subjects FROM books WHERE subjects != '[]'")
suspend fun getAllSubjectsRaw(): List<String>  // à désérialiser JSON puis dédupliquer en mémoire, cohérent avec le pattern JSON déjà utilisé pour subjects
```
(Alternative plus propre si le temps le permet : sortir `subjects`/tags dans une table normalisée `book_tags(bookId, tag)` plutôt que du JSON dans une colonne — meilleure requêtabilité, mais plus gros chantier. Pour rester réaliste vu le volume de la bibliothèque personnelle visée, le JSON existant + dédup en mémoire suffit largement et évite une migration lourde.)

### `BookRepository` / `BookRepositoryImpl`
- `suspend fun setFavorite(bookId: String, isFavorite: Boolean)`
- `suspend fun getAllTags(): List<String>` (dédup des `subjects`)
- Extraction série à l'import (`importEpub`, dans `BookRepositoryImpl.kt` juste après l'extraction de `subjects`) :
  ```kotlin
  val seriesInfo = try {
      publication.metadata.belongsTo?.get("series")?.firstOrNull()?.let {
          it.name to it.position?.toFloat()
      }
  } catch (e: Exception) {
      Log.w("BookRepo", "Lecture série échouée : ${e.message}")
      null
  }
  ```
  ⚠️ À vérifier précisément contre l'API exacte de Readium 3.0.0 (le nom du champ `belongsTo`/`Collection`/`position` peut différer légèrement) — Claude Code doit consulter la Javadoc/sources Readium embarquées avant d'écrire ce bloc, ne pas deviner l'API à l'aveugle. Si `belongsTo` n'existe pas dans cette version du toolkit, fallback sur un parsing manuel du `<meta name="calibre:series" content="...">` dans le OPF (déjà accessible puisque le repo parse le HTML/XML manuellement ailleurs pour le stripping).

---

## 4. ViewModel — corriger le bug de fond

`LibraryViewModel.kt` :

1. **`applyFilters()` doit enfin lire `filterMode`** :
```kotlin
.filter { book ->
    when (s.filterMode) {
        FilterMode.ALL -> true
        FilterMode.FAVORITES -> book.isFavorite
        FilterMode.BY_AUTHOR -> true // le tri gère déjà le regroupement par auteur
        FilterMode.SERIES -> book.seriesName != null
        FilterMode.TAGS -> s.selectedTags.isEmpty() || book.subjects.any { it in s.selectedTags }
        FilterMode.BY_TITLE, FilterMode.IN_PROGRESS, FilterMode.READ, FilterMode.UNREAD -> true
    }
}
```
2. Étendre `enum class FilterMode` avec `FAVORITES`, `SERIES`, `TAGS` (au lieu de tout aliaser sur `ALL`).
3. Ajouter à `LibraryUiState` : `selectedTags: Set<String> = emptySet()`, `availableTags: List<String> = emptyList()`.
4. `fun toggleFavorite(bookId: String)` — appelle le repository, recharge la liste.
5. `fun toggleTagFilter(tag: String)`.
6. Vue groupée série : ajouter une fonction `booksGroupedBySeries(): Map<String, List<Book>>` (triée par `seriesIndex`) utilisée uniquement quand `filterMode == SERIES`.

---

## 5. UI

### `LibraryNavigationPopup` (le menu déroulant lui-même)
Remplacer la liste hardcodée alias-sur-ALL par les vrais modes :
```kotlin
"Tous les livres" to FilterMode.ALL,
"Favoris"         to FilterMode.FAVORITES,
"Séries"          to FilterMode.SERIES,
"Auteur"          to FilterMode.BY_AUTHOR,
"Tags"            to FilterMode.TAGS,
```
Retirer **"Dossiers"** de ce menu — remplacer par une action de navigation directe vers `FilesScreen` (pas un `FilterMode`, ce n'est pas un filtre de la même liste, c'est un autre écran).

### `BookCover`
Ajouter une icône cœur (Material `Icons.Outlined.FavoriteBorder` / `Icons.Filled.Favorite` selon état) en overlay coin supérieur droit, `IconButton` avec `contentDescription` dynamique ("Ajouter aux favoris" / "Retirer des favoris"), tap indépendant du clic principal (ne doit pas ouvrir le livre).

### Vue "Séries"
Quand `filterMode == SERIES` : remplacer la grille plate par des groupes (en-tête = nom de série, sous-liste = tomes triés par `seriesIndex`). Les livres sans `seriesName` n'apparaissent pas dans cette vue (logique : vue "Séries" = uniquement ce qui est en série) — prévoir un état vide explicite ("Aucune série détectée pour l'instant") plutôt qu'un écran vide silencieux.

### Vue "Tags"
Barre de `FilterChip` en haut de la grille avec les tags distincts (`availableTags`), sélection multiple, filtre cumulatif (OR entre tags sélectionnés, cohérent avec les usages courants de ce type de filtre).

### "Dossiers"
`onClick` du menu → navigation vers `FilesScreen` existant (déjà fonctionnel), pas de nouveau code de filtrage.

---

## 6. Tests

Repo actuel : ~110 tests unitaires (`PROJECT_STATUS.md`), convention `./gradlew test` après chaque tâche. Ajouter :
- `LibraryViewModelTest` : `applyFilters()` avec chaque `FilterMode` (favoris, séries, tags — cas avec tags multiples sélectionnés, cas sans série).
- `BookMapperTest` : round-trip `isFavorite`/`seriesName`/`seriesIndex` entity ↔ domain.
- Test de migration Room `13→14` (`MigrationTestHelper`, pattern standard Android si pas déjà en place — vérifier si `androidx.room:room-testing` est présent dans le catalogue, sinon l'ajouter).

---

## 7. Ordre d'exécution (commits séparés, validés par `./gradlew assembleDebug` + tests après chacun)

1. Migration Room 13→14 + entités + mapper (aucun changement UI, juste la fondation)
2. Extraction série à l'import + `getAllTags()` côté repository
3. `FilterMode` étendu + `applyFilters()` corrigé + tests ViewModel
4. UI : toggle favori sur `BookCover`
5. UI : vue groupée Séries
6. UI : chips Tags
7. Menu déroulant final (vrais `FilterMode` + redirection Dossiers vers `FilesScreen`)
8. Tests de migration Room

---

## 8. Ce qui reste volontairement hors périmètre (à nommer explicitement, pas à cacher)

- Tags **créés manuellement** par l'utilisateur (au-delà des `subjects` extraits de l'EPUB) — faisable en V1.1, nécessiterait une table de jointure propre plutôt que du JSON.
- Édition manuelle du nom de série/tome pour les livres où l'EPUB ne fournit pas cette métadonnée — utile mais un formulaire d'édition mérite son propre plan (touche l'écran détail livre, qui n'existe pas encore en tant que tel dans ce repo).
