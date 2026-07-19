# Plan d'action — Audit fonctionnel InkTone
# À soumettre à Claude Code
# Branche : feature/feature-polish depuis main
# Convention : commits français à l'impératif

---

## Contexte pour Claude Code

```
Projet : InkTone, lecteur EPUB Android, Kotlin + Compose + Hilt + Room.
Package : com.inktone

Résultat de l'audit fonctionnel :
  CASSÉ (inutilisable) : Recherche FTS, Notes, Navigation depuis Bookmarks
  CODE MORT : Onboarding, RecentBooks, BookProgressDao
  PARTIEL : TOC, Highlights, Progression triplée, Overlays
  OK : Lecture, Thèmes, Typographie

Règle générale : ne pas casser ce qui fonctionne.
Chaque tâche est autonome — les traiter dans l'ordre donné.
```

---

## TÂCHE 1 — Réparer la recherche FTS (Cassé → Fonctionnel)

**Durée estimée : 1.5 heure**

**Problème** : La table `sentence_fts` n'est jamais peuplée lors de l'import.
`SearchDao.insertAll()` existe mais n'est jamais appelé.

### 1.1 — Peupler sentence_fts à l'import

Dans `data/repository/BookRepositoryImpl.kt`, injecter `SearchDao` et appeler `insertAll` :

```kotlin
// Ajouter dans le constructor de BookRepositoryImpl :
private val searchDao: SearchDao

// Dans importEpub(), après chunkText(bookId, i, combinedHtml) :
val ftsEntries = sentences.map { sentence ->
    SentenceFts(
        bookId = bookId,
        chapterIndex = i,
        sentenceIndex = sentence.index,
        text = sentence.text
    )
}
if (ftsEntries.isNotEmpty()) {
    searchDao.deleteForBook(bookId)  // Nettoyer l'ancien index si re-import
    searchDao.insertAll(ftsEntries)
}
```

Ajouter `SearchDao` dans le constructor via `@Inject` et dans `AppModule.kt` si pas encore fourni.

### 1.2 — Réparer la navigation depuis SearchScreen

Dans `InkToneNavGraph.kt`, le callback `onNavigate` fait juste `navController.popBackStack()`.
Il faut passer la position au Reader via `SavedStateHandle` :

```kotlin
// Dans NavGraph — route SEARCH :
onNavigate = { chapterIndex, sentenceIndex ->
    // Naviguer vers le Reader à la bonne position
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("jumpChapter", chapterIndex)
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("jumpSentence", sentenceIndex)
    navController.popBackStack()
}
```

Dans `ReaderViewModel.kt`, ajouter la lecture du SavedStateHandle à l'init :

```kotlin
// Dans loadBook() ou init, après avoir chargé le livre :
val jumpChapter = savedState.get<Int>("jumpChapter")
val jumpSentence = savedState.get<Int>("jumpSentence")
if (jumpChapter != null && jumpSentence != null) {
    savedState.remove<Int>("jumpChapter")
    savedState.remove<Int>("jumpSentence")
    goToChapter(jumpChapter, jumpSentence)
}
```

Ajouter `fun goToChapter(chapterIdx: Int, sentenceIdx: Int = 0)` dans ReaderViewModel si elle n'existe pas encore (elle existe probablement sous le nom `goToChapter(idx)`).

**Vérification** :
1. Importer un EPUB
2. Ouvrir Recherche → taper un mot qui existe dans le livre
3. Résultats doivent apparaître
4. Taper sur un résultat → le reader doit s'ouvrir au bon chapitre

---

## TÂCHE 2 — Réparer la navigation depuis BookmarkScreen (Cassé → Fonctionnel)

**Durée estimée : 45 minutes**

**Problème** : `onNavigate(chapter, sentence)` dans `BookmarkScreen` n'ouvre pas la bonne position.
Même pattern que Tâche 1.

### 2.1 — Même mécanique SavedStateHandle

Dans `InkToneNavGraph.kt`, route BOOKMARKS :

```kotlin
onNavigate = { chapterIndex, sentenceIndex ->
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("jumpChapter", chapterIndex)
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("jumpSentence", sentenceIndex)
    navController.popBackStack()
}
```

La Tâche 1 a déjà ajouté la lecture dans `ReaderViewModel` — réutiliser le même code.

### 2.2 — AllBookmarksPanel : naviguer à la position (pas juste au livre)

Dans `AllBookmarksPanel.kt`, le callback `onTap` appelle `onNavigateToBook(bookmark.bookId)` — qui ne transmet pas la position.

Option A : Changer la signature pour passer `(bookId, chapterIndex, sentenceIndex)` :

```kotlin
// AllBookmarksPanel.kt
@Composable
fun AllBookmarksPanel(
    onNavigateToBook: (bookId: String, chapterIndex: Int, sentenceIndex: Int) -> Unit,
    // ...
) {
    // Dans BookmarkDrawerItem.onTap :
    onTap = { onNavigateToBook(bookmark.bookId, bookmark.chapterIndex, bookmark.sentenceIndex) }
}
```

Option B (plus simple) : garder `onNavigateToBook(bookId)` et laisser le Reader ouvrir depuis la progression sauvegardée.

**Recommander Option A** si les bookmarks doivent naviguer précisément.

---

## TÂCHE 3 — Réparer les Notes (Cassé → Fonctionnel minimal)

**Durée estimée : 2 heures**

**Problèmes** :
1. Aucun champ texte n'est demandé à l'utilisateur avant d'enregistrer
2. Les annotations ne sont pas affichées dans le Reader
3. Aucun écran de consultation

### 3.1 — Dialog de saisie de note

Dans `ReaderScreen.kt`, remplacer l'appel direct à `viewModel.addAnnotation()` par l'affichage d'un Dialog :

```kotlin
// Ajouter dans ReaderScreen state :
var showNoteDialog by remember { mutableStateOf(false) }
var noteDialogSentenceIdx by remember { mutableStateOf(-1) }
var noteDialogSelectedText by remember { mutableStateOf("") }

// Dans SelectionActionBar.onNote :
onNote = {
    val s = selectionState ?: return@SelectionActionBar
    noteDialogSentenceIdx = s.sentenceIndex
    noteDialogSelectedText = s.selectedText
    selectionState = null
    showNoteDialog = true
}

// Le Dialog :
if (showNoteDialog) {
    var noteText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { showNoteDialog = false },
        title = { Text("Ajouter une note") },
        text = {
            Column {
                Text(
                    noteDialogSelectedText.take(80) + if (noteDialogSelectedText.length > 80) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Votre note...") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (noteText.isNotBlank()) {
                        viewModel.addAnnotation(noteDialogSentenceIdx, noteDialogSelectedText, noteText)
                    }
                    showNoteDialog = false
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = { showNoteDialog = false }) { Text("Annuler") }
        }
    )
}
```

### 3.2 — Mettre à jour addAnnotation dans ViewModel et UseCase

```kotlin
// ManageReaderAnnotationsUseCase.kt — MODIFIER :
suspend fun addAnnotation(
    bookId: String,
    chapterIndex: Int,
    sentenceIndex: Int,
    selectedText: String,
    noteText: String  // NOUVEAU paramètre
): AnnotationResult {
    annotationDao.insertAnnotation(
        AnnotationEntity(
            bookId = bookId,
            chapterIndex = chapterIndex,
            sentenceIndex = sentenceIndex,
            selectedText = selectedText,
            notes = noteText,       // STOCKER dans le champ notes
            colorHex = "#FFF9C4"
        )
    )
    return AnnotationResult.Success("Note ajoutée")
}

// ReaderViewModel.kt — MODIFIER la signature :
fun addAnnotation(sentenceIndex: Int, selectedText: String, noteText: String) { ... }
```

### 3.3 — Afficher un indicateur visuel dans ReaderContent

Ajouter `annotations` dans `ReaderUiState` :

```kotlin
// ReaderUiState
val annotations: List<AnnotationEntity> = emptyList()  // NOUVEAU

// Dans reloadAnnotations() :
val annotations = annotationDao.getAnnotationsForChapter(bookId, chapterIdx)
_uiState.update { it.copy(
    highlights = reloaded.highlights,
    bookmarks = reloaded.bookmarks,
    annotations = annotations  // NOUVEAU
)}
```

Dans `SentenceRenderer`, ajouter un indicateur note (icône discrète) :

```kotlin
val hasNote = annotations.any { it.sentenceIndex == index }

// Dans Row() de SentenceRenderer :
if (hasNote) {
    Text(
        "📝",
        fontSize = 10.sp,
        modifier = Modifier.padding(end = 4.dp)
    )
}
```

---

## TÂCHE 4 — Réparer les Surlignages (Partiel → Fonctionnel)

**Durée estimée : 1.5 heure**

**Problèmes** :
1. Couleur unique `#FFEB3D` — pas de color picker
2. Aucune UI pour voir/supprimer les surlignages
3. Le rendu ignore `startOffset`/`endOffset` — colorie la phrase entière

### 4.1 — Color picker minimal dans SelectionActionBar

Remplacer le bouton "🖍️ Surligner" par un bouton qui ouvre un mini-picker de 5 couleurs :

```kotlin
// Dans ReaderScreen, ajouter un state :
var showColorPicker by remember { mutableStateOf(false) }
var colorPickerSentenceIdx by remember { mutableStateOf(-1) }
// ...

// Mini-picker inline (pas de Dialog, juste une Row de cercles colorés)
if (showColorPicker) {
    val colors = listOf("#FFEB3D", "#90EE90", "#ADD8E6", "#FFB6C1", "#FFA500")
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .clickable {
                            viewModel.addHighlight(
                                colorPickerSentenceIdx,
                                colorPickerSelectedText,
                                colorPickerStartOffset,
                                colorPickerEndOffset,
                                hex  // Passer la couleur choisie
                            )
                            showColorPicker = false
                        }
                )
            }
        }
    }
}
```

Mettre à jour `addHighlight()` pour accepter le paramètre `colorHex: String`.

### 4.2 — Écran des surlignages (intégré dans BookmarkScreen ou onglet séparé)

Option simple : ajouter un onglet "Surlignages" dans `BookmarkScreen` :

```kotlin
// BookmarkScreen.kt — ajouter un TabRow avec "Signets" / "Surlignages"
// Dans l'onglet Surlignages : LazyColumn de HighlightEntity
// Chaque item : couleur ● + texte + bouton supprimer
```

Ajouter une route `highlights/{bookId}/{bookTitle}` dans NavGraph, ou intégrer directement dans BookmarkScreen avec un `TabRow`.

---

## TÂCHE 5 — TOC : afficher les vrais titres de chapitres (Partiel → OK)

**Durée estimée : 1 heure**

**Problème** : `ChapterPicker` affiche "Chapitre 1, 2, 3" au lieu des vrais titres.
Les titres sont dans `sentence_cache.chapterTitle` mais ne sont pas remontés.

### 5.1 — Requête Room pour les titres

Ajouter dans `SentenceCacheDao.kt` :

```kotlin
@Query("""
    SELECT DISTINCT chapterIndex, chapterTitle
    FROM sentence_cache
    WHERE bookId = :bookId AND chapterTitle != ''
    ORDER BY chapterIndex ASC
""")
suspend fun getChapterTitles(bookId: String): List<ChapterTitleRow>
```

Avec :
```kotlin
data class ChapterTitleRow(val chapterIndex: Int, val chapterTitle: String)
```

### 5.2 — Exposer dans ReaderUiState et ViewModel

```kotlin
// ReaderUiState
val chapterTitles: List<String> = emptyList()  // NOUVEAU

// Dans loadBook() ou après l'ouverture :
val titles = sentenceCacheDao.getChapterTitles(bookId)
    .associate { it.chapterIndex to it.chapterTitle }
val titleList = (0 until totalChapters).map { i ->
    titles[i] ?: "Chapitre ${i + 1}"
}
_uiState.update { it.copy(chapterTitles = titleList) }
```

### 5.3 — Mettre à jour ChapterPicker

```kotlin
// ReaderTopBar.kt
@Composable
fun ChapterPicker(
    totalChapters: Int,
    currentChapter: Int,
    chapterTitles: List<String> = emptyList(),  // NOUVEAU
    onSelect: (Int) -> Unit
) {
    // ...
    for (i in 0 until totalChapters) {
        val title = chapterTitles.getOrNull(i) ?: "Chapitre ${i + 1}"
        // ... afficher title au lieu de "Chapitre ${i+1}"
    }
}
```

Passer `state.chapterTitles` dans l'appel à `ChapterPicker` dans `ReaderScreen.kt`.

---

## TÂCHE 6 — Unifier les overlays bas de l'écran (Partiel → OK)

**Durée estimée : 1.5 heure**

**Problème** : 6-7 éléments peuvent apparaître simultanément à `Alignment.BottomCenter` avec des collisions visuelles.

### Inventaire des éléments bas de l'écran (du plus prioritaire au moins prioritaire)

```
1. SelectionActionBar    (visible quand selectionState != null)
2. UnifiedControlPanel   (visible quand isHudVisible)
3. Tooltip 1             (visible quand showReaderTooltip && isHudVisible)
4. Tooltip 2             (visible quand showPlayTooltip)
5. Captions TTS          (visible quand isPlaying && text.isNotEmpty)
6. Micro-indicateur      (visible quand !isHudVisible)
7. Snackbar              (toujours présent, auto-géré)
8. FAB Play              (visible quand !isHudVisible && !isPlaying)
```

### Règles de priorité à implémenter

```kotlin
// États mutuellement exclusifs :
// SelectionActionBar → masque captions, tooltip 2, micro-indicateur
// UnifiedControlPanel → masque tooltip 2 (le tooltip doit être dans le panel, pas en dehors)
// isHudVisible → masque FAB, micro-indicateur

// Corrections :
// 1. showReaderTooltip : ne l'afficher que si !isHudVisible (ou le rendre DANS le panel)
// 2. showPlayTooltip : ne l'afficher que si !isPlaying && !selectionState
// 3. Captions TTS : ne pas afficher si selectionState != null
// 4. Tooltip 1 DANS UnifiedControlPanel (pas par-dessus)
```

### Correction concrète

```kotlin
// Dans ReaderScreen.kt :

// SUPPRIMER le Card tooltip1 existant (lignes 165-191)
// INTÉGRER le hint dans UnifiedControlPanel quand showReaderTooltip est true

// Pour le tooltip 2, ajouter une condition :
if (state.showPlayTooltip && !state.isPlaying && selectionState == null) {
    // Card tooltip...
}

// Pour les captions TTS, ajouter une condition :
visible = state.isPlaying && playbackState.activeSentenceText.isNotEmpty()
       && selectionState == null  // NE PAS afficher si SelectionActionBar visible
```

---

## TÂCHE 7 — Purge du code mort (Nettoyage)

**Durée estimée : 45 minutes**

### Ce qui peut être supprimé sans risque

```kotlin
// 1. BookProgressDao + BookProgressEntity + table book_progress
//    Vérifier qu'aucun code ne l'utilise (audit : aucune référence productive trouvée)
//    Supprimer : BookProgressDao.kt, BookProgressEntity.kt
//    Retirer de InkToneDatabase : entities, abstract fun bookProgressDao()
//    Ajouter une migration Room pour supprimer la table

// 2. RecentBooksRepository + RecentBookDao + RecentBookEntity
//    Vérifier que LibraryScreen.NavigationDestination.RECENTS trie par addedAt (pas par recent_books)
//    Supprimer : RecentBooksRepository.kt, RecentBookDao.kt, RecentBookEntity.kt
//    Retirer de InkToneDatabase
//    Ajouter une migration Room pour supprimer la table
//    ATTENTION : supprimer aussi la référence dans AppModule.kt

// 3. OnboardingScreen — 2 options :
//    Option A : Le relier au NavGraph (voir Tâche 8 ci-dessous) → NE PAS supprimer
//    Option B : Supprimer OnboardingScreen.kt si on décide de ne pas l'utiliser

// 4. TtsTestScreen — conditionner à BuildConfig.DEBUG
//    Modifier InkToneNavGraph.kt :
//    composable(Routes.DEBUG) {
//        if (BuildConfig.DEBUG) {
//            com.inktone.ui.screen.TtsTestScreen()
//        }
//    }
//    Retirer le bouton debug de LibraryScreen en release
```

**Migration Room à créer pour supprimer les tables obsolètes** :
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS book_progress")
        db.execSQL("DROP TABLE IF EXISTS recent_books")
    }
}
```

---

## TÂCHE 8 (Optionnel) — Connecter l'Onboarding

**Durée estimée : 30 minutes**

L'`OnboardingScreen` est complet mais inaccessible.

```kotlin
// InkToneNavGraph.kt — ajouter :
object Routes {
    const val ONBOARDING = "onboarding"
    // ...
}

// Dans NavHost :
composable(Routes.ONBOARDING) {
    OnboardingScreen(onComplete = {
        // Marquer l'onboarding comme complété dans DataStore
        navController.navigate(Routes.LIBRARY) {
            popUpTo(Routes.ONBOARDING) { inclusive = true }
        }
    })
}

// Dans MainActivity ou InkToneNavGraph, lire depuis DataStore si onboarding complété :
val startDestination = if (onboardingCompleted) Routes.LIBRARY else Routes.ONBOARDING
```

Ajouter `onboardingCompleted: Boolean` dans `SettingsRepository.kt` (DataStore preferences).

---

## Récapitulatif et ordre d'exécution

| # | Tâche | Impact | Durée | Priorité |
|---|-------|--------|-------|----------|
| 1 | Réparer FTS + navigation search | Recherche enfin utilisable | 1.5h | 🔴 Critique |
| 2 | Navigation depuis Bookmarks | Marque-pages enfin utiles | 45min | 🔴 Critique |
| 3 | Réparer les Notes | Feature existante mais nulle | 2h | 🔴 Critique |
| 4 | Highlights : color picker + gestion | UX complète des surlignages | 1.5h | 🟠 Haute |
| 5 | TOC avec vrais titres | Navigation plus humaine | 1h | 🟠 Haute |
| 6 | Unifier les overlays | Plus de collision visuelle | 1.5h | 🟠 Haute |
| 7 | Purge code mort | DB plus légère, code plus clair | 45min | 🟡 Moyenne |
| 8 | Connecter Onboarding | Première expérience guidée | 30min | 🟡 Moyenne |

**Total estimé : ~10 heures**

### Vérification après chaque tâche

```bash
./gradlew assembleDebug  # Obligatoire après chaque tâche

# Tâche 1 : importer un EPUB → rechercher un mot → vérifier résultats → taper → vérifier navigation
# Tâche 2 : ajouter un bookmark → ouvrir la liste → taper → vérifier navigation
# Tâche 3 : sélectionner du texte → Note → vérifier dialog → enregistrer → vérifier indicateur
# Tâche 7 : ./gradlew kspDebugKotlin (vérifier que Room compile avec les migrations)
```

### Messages de commit

```
Peuple sentence_fts lors de l'import EPUB pour activer la recherche
Répare la navigation depuis SearchScreen vers la position trouvée
Répare la navigation depuis BookmarkScreen vers la phrase marquée
Ajoute le dialog de saisie pour les notes utilisateur
Affiche un indicateur visuel pour les phrases avec note
Ajoute un color picker minimal pour les surlignages
Affiche les vrais titres de chapitres dans la table des matières
Unifie les overlays bas de l'écran — supprime les collisions
Supprime BookProgressDao et RecentBooksRepository (code mort)
Ajoute migration Room pour supprimer les tables obsolètes
Connecte OnboardingScreen au NavGraph avec DataStore flag
```
