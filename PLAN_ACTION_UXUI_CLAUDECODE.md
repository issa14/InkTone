# Plan d'action UI/UX — InkTone
> À soumettre tel quel à Claude Code. Chaque tâche est autonome et testable.
> Branche de travail : `feature/ux-polish` depuis `develop`.
> Convention commits : verbe français à l'impératif, ex. `Remplace AccentBlue hardcodé par MaterialTheme.colorScheme.primary`.

---

## Contexte pour Claude Code

```
Projet Android : InkTone (anciennement ReadFlow), lecteur EPUB + TTS neuronal local.
Package : com.inktone
Stack : Kotlin + Jetpack Compose + Material 3 + Hilt + Room
Thème : InkToneTheme(theme: AppTheme) dans ui/theme/Theme.kt
  → AppTheme.PAPIER_ART → PapierArtColors (light, fond crème)
  → AppTheme.OBSIDIAN   → ObsidianColors (dark, fond quasi-noir)
  → AppTheme.NORDIC_FOG → NordicFogColors (light, fond gris-bleu)
  → AppTheme.SYSTEM     → OS dark/light

RÈGLE FONDAMENTALE : aucune couleur hardcodée dans les composables UI.
Toute couleur doit venir de MaterialTheme.colorScheme.* ou d'une valeur
passée explicitement en paramètre depuis l'appelant qui connaît le thème.

Les constantes suivantes dans Color.kt sont marquées "deprecated" :
AppBackground, SurfaceDark, SurfaceRaised, TextMain, TextMuted,
AccentBlue, BorderDark, BorderSoft, ShelfOverlay
→ NE PAS les utiliser dans les composables. Les remplacer.

Coil est déjà en dépendance (io.coil-kt:coil-compose:2.6.0).
```

---

## TÂCHE 1 — Supprimer toutes les couleurs hardcodées (UI critique)

**Fichiers concernés :** tous les fichiers `ui/**/*.kt` (17 fichiers identifiés)
**Durée estimée :** 3-4 heures

### Ce qu'il faut faire

Remplacer **chaque** occurrence de couleur hardcodée par son équivalent `MaterialTheme.colorScheme.*`.
Table de mapping à appliquer mécaniquement :

```
// DEPRECATED → REMPLACEMENT MaterialTheme 3
TextMain             → MaterialTheme.colorScheme.onBackground
TextMuted            → MaterialTheme.colorScheme.onSurfaceVariant
AccentBlue           → MaterialTheme.colorScheme.primary          (toute occurrence)
AccentTts            → MaterialTheme.colorScheme.secondary
SurfaceDark          → MaterialTheme.colorScheme.surface
SurfaceRaised        → MaterialTheme.colorScheme.surfaceVariant
BorderDark           → MaterialTheme.colorScheme.outline
BorderSoft           → MaterialTheme.colorScheme.outlineVariant
ShelfOverlay         → MaterialTheme.colorScheme.surface.copy(alpha = 0.07f)
AppBackground        → MaterialTheme.colorScheme.background

// COULEURS INLINE → REMPLACEMENT
Color.White          → MaterialTheme.colorScheme.onPrimary          (sur fond primary)
                       MaterialTheme.colorScheme.onSurface           (sur fond surface)
                       MaterialTheme.colorScheme.onBackground        (sur fond background)
Color.White.copy(alpha=0.6f) → MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
Color(0xFF333333)    → MaterialTheme.colorScheme.onSurface
Color(0xFF757575)    → MaterialTheme.colorScheme.onSurfaceVariant
Color(0xFF616161)    → MaterialTheme.colorScheme.onSurfaceVariant
Color(0xFF1A1A1A)    → MaterialTheme.colorScheme.surface  (CardBg dans SettingsScreen)
Color(0xFF0D0D0D)    → MaterialTheme.colorScheme.background
```

### Exceptions autorisées

Les couleurs suivantes **restent hardcodées** car elles appartiennent à la couche de présentation
du Reader (thème de lecture indépendant du thème app) et sont calculées depuis `ReaderTheme` :

```kotlin
// ReaderScreen.kt — CONSERVER tel quel (triplet calculé)
val (bgColor, textColor, accentColor) = when (state.readerTheme) {
    ReaderTheme.NIGHT -> Triple(Color(0xFF0D0D0D), Color.White, Color(0xFFFFB74D))
    ReaderTheme.DAY   -> Triple(Color(0xFFFAFAFA), Color(0xFF1A1A1A), Color(0xFF0091EA))
    ReaderTheme.SEPIA -> Triple(Color(0xFFF4ECD8), Color(0xFF3C2F2F), Color(0xFFB65D30))
}
// Ces couleurs sont PASSÉES EN PARAMÈTRE aux composables enfants, pas lues depuis theme.
```

### Cas particulier : SettingsScreen.kt

```kotlin
// AVANT
private val CardBg = Color(0xFF1A1A1A)  // ← cassé en thème clair
private val AccentBlue = Color(0xFF4FC3F7)  // ← valeur différente de LibraryScreen !

// APRÈS : supprimer ces deux private val, utiliser MaterialTheme à la place
// Card background → CardDefaults.cardColors() sans containerColor explicite (défaut theme)
// AccentBlue → MaterialTheme.colorScheme.primary
```

### Vérification

```bash
# Doit retourner 0 après correction
grep -rn "TextMain\|TextMuted\|AccentBlue\|SurfaceDark\|SurfaceRaised\|BorderDark\|BorderSoft\|ShelfOverlay\|AppBackground\|CardBg" \
  app/src/main/java/com/inktone/ui --include="*.kt" | grep -v "Color.kt" | wc -l
```

---

## TÂCHE 2 — Migrer LibraryScreen vers ModalNavigationDrawer

**Fichier :** `ui/screen/library/LibraryScreen.kt`
**Durée estimée :** 1.5 heure

### Problème

Le drawer actuel est un `Surface + AnimatedVisibility` manuel qui ignore :
- Back gesture Android
- `DrawerState` Compose (accessibility)
- Predictive Back (Android 14+)
- TalkBack (le drawer n'annonce pas son état)

### Implémentation

```kotlin
// AVANT (simplifié)
var showDrawer by remember { mutableStateOf(false) }

Box(modifier = Modifier.fillMaxSize()) {
    Surface(...) { Scaffold(...) { ... } }
    if (showDrawer) { DrawerOverlay(onDismiss = { showDrawer = false }) }
    NavDrawer(visible = showDrawer, ...)
}

// APRÈS
val drawerState = rememberDrawerState(DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(310.dp)
        ) {
            NavDrawerContent(
                currentDest = state.currentDestination,
                onNavigate = { dest ->
                    viewModel.navigateTo(dest)
                    scope.launch { drawerState.close() }
                },
                onThemeToggle = { viewModel.toggleTheme() },
                onDebug = onDebugClick
            )
        }
    }
) {
    // Le Scaffold existant vient ici (inchangé)
    Scaffold(
        topBar = {
            TopBar(
                onMenu = { scope.launch { drawerState.open() } },
                ...
            )
        }
    ) { ... }
}
```

### Adapter NavDrawerContent

Extraire le contenu actuel du `NavDrawer` composable dans une fonction `NavDrawerContent` séparée
(header gradient + items + footer buttons). Supprimer les composables `DrawerOverlay` et `NavDrawer`
qui deviennent inutiles.

---

## TÂCHE 3 — Empty State actionnable dans LibraryScreen

**Fichier :** `ui/screen/library/LibraryScreen.kt`
**Durée estimée :** 45 minutes

### Implémentation

```kotlin
// REMPLACER EmptyView() par :
@Composable
private fun EmptyLibraryView(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icône dans cercle accent
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Commencez votre bibliothèque",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Importez un fichier .epub depuis vos fichiers ou parcourez un catalogue OPDS.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importer un livre")
        }
    }
}
```

Adapter l'appelant dans `LibraryScreen` pour passer le launcher :

```kotlin
// Dans le when block :
state.books.isEmpty() && !state.isLoading -> EmptyLibraryView(
    onImport = { epubPicker.launch(arrayOf("application/epub+zip")) }
)
```

Appliquer le même empty state pour `NavigationDestination.RECENTS` si `recent.isEmpty()`.

---

## TÂCHE 4 — ErrorBanner Material 3

**Fichier :** `ui/screen/library/LibraryScreen.kt`
**Durée estimée :** 30 minutes

### Remplacement

```kotlin
// SUPPRIMER ErrorBanner() actuel, CRÉER :
@Composable
private fun ErrorBanner(
    message: String,
    actionLabel: String = "OK",
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Erreur",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(
                    actionLabel,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

**Note :** Supprimer l'emoji `❌` du message avant de l'afficher si présent dans le ViewModel
(`error?.removePrefix("❌ ")`). Le format des erreurs doit être : "Ce qui s'est passé. Quoi faire."
Sans "Erreur :" en préfixe, sans ponctuation exclamative.

---

## TÂCHE 5 — Couvertures via Coil AsyncImage

**Fichier :** `ui/screen/library/LibraryScreen.kt`
**Durée estimée :** 30 minutes

### Problème

`rememberBookCoverPainter()` appelle `BitmapFactory.decodeFile()` dans `remember()`,
ce qui bloque le thread de composition pendant le décodage image.

### Remplacement

```kotlin
// SUPPRIMER rememberBookCoverPainter() entièrement.

// Dans BookCover(), remplacer le bloc if (coverBitmap != null) par :
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

// Dans le Box de la couverture :
if (book.coverPath != null) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(book.coverPath)
            .crossfade(true)
            .build(),
        contentDescription = "Couverture de ${book.title}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    // Le gradient (background du Box) reste visible le temps du chargement
} else {
    // Titre sur fond gradient (comportement actuel, CONSERVER)
    Text(
        book.title,
        fontFamily = FontFamily.Serif,
        ...
    )
}
```

Coil gère nativement : cache LRU, chargement asynchrone sur IO, placeholder pendant le load,
crossfade à l'apparition. Le gradient background reste visible pendant le chargement.

---

## TÂCHE 6 — Accessibilité : contentDescription manquants

**Fichiers :** tous les fichiers `ui/**/*.kt`
**Durée estimée :** 1 heure

### Règles

1. **Icônes décoratives** (à côté d'un texte explicite) → `contentDescription = null` ✓ (déjà correct)
2. **Icônes interactives seules** (IconButton sans texte) → `contentDescription = stringResource(R.string.cd_xxx)`
3. **Canvas** (StatsScreen) → `Modifier.semantics { contentDescription = "..." }`
4. **Tailles minimales** : tout élément tapable = `Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)`

### strings.xml à créer

```xml
<!-- app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">InkTone</string>

    <!-- Navigation -->
    <string name="cd_menu_open">Ouvrir le menu</string>
    <string name="cd_search">Rechercher</string>
    <string name="cd_filter">Filtrer et trier</string>
    <string name="cd_more_options">Plus d\'options</string>
    <string name="cd_close_search">Fermer la recherche</string>

    <!-- Library -->
    <string name="cd_book_progress">Progression : %d%%</string>
    <string name="cd_book_cover">Couverture de %s</string>
    <string name="cd_resume_reading">Reprendre la lecture audio</string>
    <string name="cd_import_books">Importer des livres</string>

    <!-- Reader -->
    <string name="cd_tts_play">Lire</string>
    <string name="cd_tts_pause">Mettre en pause</string>
    <string name="cd_tts_stop">Arrêter</string>
    <string name="cd_tts_previous">Phrase précédente</string>
    <string name="cd_tts_next">Phrase suivante</string>
    <string name="cd_back_to_library">Retour à la bibliothèque</string>
    <string name="cd_font_dyslexic_toggle">Police OpenDyslexic : activée/désactivée</string>
    <string name="cd_theme_cycle">Changer de thème</string>

    <!-- Stats -->
    <string name="cd_stats_daily_goal">Objectif quotidien : %d minutes sur %d</string>
    <string name="cd_stats_streak">Série de lecture : %d jours</string>
    <string name="cd_stats_weekly_chart">Graphique de lecture hebdomadaire</string>
</resources>
```

### Corrections prioritaires par fichier

**LibraryScreen.kt** (28 occurrences `tint = Color.White` dont plusieurs sur icônes interactives)
```kotlin
// AVANT
IconButton(onClick = onSearch) {
    Icon(Icons.Default.Search, "Rechercher", tint = Color.White)
}

// APRÈS
IconButton(onClick = onSearch) {
    Icon(Icons.Default.Search,
        contentDescription = stringResource(R.string.cd_search),
        tint = MaterialTheme.colorScheme.onPrimary)
}
```

**StatsScreen.kt** (3 Canvas sans semantics)
```kotlin
// AVANT
Canvas(modifier = Modifier.fillMaxSize()) { drawArc(...) }

// APRÈS — jauge objectif quotidien
Canvas(
    modifier = Modifier
        .fillMaxSize()
        .semantics {
            contentDescription = context.getString(
                R.string.cd_stats_daily_goal,
                state.todayReadingMinutes,
                state.dailyGoalMinutes
            )
        }
) { drawArc(...) }
```

**BookCover badge de progression** (badge 8sp → taille minimale 44dp)
```kotlin
// AVANT : Surface 26dp avec texte 8sp
Surface(modifier = Modifier.size(26.dp), ...) {
    Text("${pct}%", fontSize = 8.sp)
}

// APRÈS : badge accessible
Box(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(6.dp)
        .semantics {
            contentDescription = context.getString(R.string.cd_book_progress, pct)
        }
) {
    Surface(
        modifier = Modifier.sizeIn(minWidth = 28.dp, minHeight = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Text("${pct}%", fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
```

---

## TÂCHE 7 — Restructurer les contrôles Reader (UnifiedControlPanel)

**Fichier :** `ui/screen/reader/ReaderBottomControls.kt`
**Durée estimée :** 1 heure

### Principe

Créer une hiérarchie à deux niveaux : contrôles primaires (Play/Skip) au centre, actions
secondaires (Voix, Police, Thème, Veille) sur une rangée inférieure avec icône + label.

```kotlin
@Composable
fun UnifiedControlPanel(
    isPlaying: Boolean,
    accentColor: Color,
    panelBg: Color,
    useOpenDyslexic: Boolean = false,
    onTtsClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    onThemeCycle: () -> Unit,
    onFontToggle: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,    // NOUVEAU — extraire du TtsPanel
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelBg.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                ))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── RANGÉE PRIMAIRE : Skip ← Play/Pause → Skip ──────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Chapitre précédent
                IconButton(
                    onClick = onPrevChapter,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_tts_previous),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(24.dp))

                // Play/Pause central — élément focal
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTtsClick()
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.cd_tts_pause else R.string.cd_tts_play
                        ),
                        modifier = Modifier.size(28.dp),
                        tint = panelBg
                    )
                }

                Spacer(Modifier.width(24.dp))

                // Chapitre suivant
                IconButton(
                    onClick = onNextChapter,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.cd_tts_next),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = accentColor.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )

            Spacer(Modifier.height(8.dp))

            // ── RANGÉE SECONDAIRE : actions avec labels ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryAction(
                    icon = Icons.Outlined.Headphones,
                    label = "Voix",
                    tint = accentColor.copy(alpha = 0.7f),
                    onClick = onTtsSettingsClick
                )
                SecondaryAction(
                    icon = Icons.Default.FormatSize,
                    label = "Police",
                    tint = if (useOpenDyslexic) accentColor
                           else accentColor.copy(alpha = 0.5f),
                    onClick = onFontToggle
                )
                SecondaryAction(
                    icon = Icons.Default.Palette,
                    label = "Thème",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onThemeCycle
                )
                SecondaryAction(
                    icon = Icons.Outlined.Timer,
                    label = "Veille",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onSleepTimerClick
                )
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = tint,
            style = MaterialTheme.typography.labelSmall)
    }
}
```

---

## TÂCHE 8 — Barre de progression Reader + ETA

**Fichiers :** `ui/screen/reader/ReaderScreen.kt`, `ui/screen/reader/ReaderViewModel.kt`
**Durée estimée :** 1.5 heure

### Partie A — Ajouter etaMinutes dans ReaderUiState

```kotlin
// ReaderViewModel.kt — ajouter dans ReaderUiState :
data class ReaderUiState(
    ...
    val etaMinutes: Int? = null,         // NOUVEAU
    val chapterProgressFraction: Float = 0f,  // NOUVEAU (0.0 à 1.0 dans le chapitre)
)

// Dans le ViewModel, calculer etaMinutes :
// Les données nécessaires sont déjà disponibles :
// - state.currentSentenceIndex et state.totalSentences pour la progression
// - chapter.sentences.drop(currentSentenceIndex).sumOf { it.wordCount }
//   pour estimer les mots restants (si Sentence a un wordCount, sinon text.split(" ").size)
// - averageWpm depuis ReadingSessionDao

private fun updateEta(currentSentenceIndex: Int, totalSentences: Int, chapter: Chapter) {
    viewModelScope.launch(Dispatchers.Default) {
        val sessions = readingSessionDao.getAllSync()
        val totalWords = sessions.sumOf { it.wordsRead.toLong() }
        val totalSeconds = sessions.sumOf { it.durationSeconds }
        if (totalSeconds < 60) return@launch   // pas assez de données

        val wpm = (totalWords * 60.0 / totalSeconds).toInt().coerceIn(80, 500)
        val remainingSentences = chapter.sentences.drop(currentSentenceIndex)
        val wordsRemaining = remainingSentences.sumOf { it.text.split(" ").size }
        val etaMin = (wordsRemaining / wpm.toDouble()).toInt().coerceAtLeast(1)
        val chapterPct = if (totalSentences > 0)
            currentSentenceIndex.toFloat() / totalSentences else 0f

        _uiState.update { it.copy(etaMinutes = etaMin, chapterProgressFraction = chapterPct) }
    }
}
// Appeler updateEta() quand currentSentenceIndex change.
```

**Note :** Injecter `ReadingSessionDao` dans `ReaderViewModel` via Hilt (il est déjà dans `AppModule`).

### Partie B — Afficher dans ReaderScreen

```kotlin
// ReaderScreen.kt — remplacer le bloc micro-indicateur actuel par :

// 1. Barre de progression fine en haut (toujours visible)
Box(modifier = Modifier.fillMaxSize().background(bgColor).onSizeChanged { ... }) {

    // Barre de progression chapitre — 2dp, toujours présente, non obstruante
    LinearProgressIndicator(
        progress = { state.chapterProgressFraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.TopCenter)
            .zIndex(1f),
        color = accentColor.copy(alpha = 0.6f),
        trackColor = Color.Transparent
    )

    // ... contenu existant ...

    // 2. Micro-indicateur enrichi (HUD masqué) — remplacer Text("%.1f%%")
    if (!state.isHudVisible && chapter != null) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ch. ${state.currentChapterIndex + 1} / ${state.book?.totalChapters ?: 1}",
                color = textColor.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
            state.etaMinutes?.let { eta ->
                Text("·", color = textColor.copy(alpha = 0.25f), fontSize = 11.sp)
                Text(
                    "~$eta min",
                    color = accentColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
```

---

## TÂCHE 9 — Connecter LibraryNavigationPopup au ViewModel

**Fichier :** `ui/screen/library/LibraryScreen.kt`
**Durée estimée :** 1 heure

### Problème

`LibraryNavigationPopup` hardcode des données fictives (`"Black Wings"`, `"Contes et nouvelles"`).
Plusieurs catégories mappent toutes à `FilterMode.ALL` sans distinction.

### Implémentation

```kotlin
// 1. Ajouter dans LibraryUiState :
data class LibraryUiState(
    ...
    val navSubItems: Map<String, List<NavSubItem>> = emptyMap(),  // NOUVEAU
)

data class NavSubItem(val label: String, val count: Int, val filterId: String)

// 2. Dans LibraryViewModel — charger depuis Room :
private fun loadNavSubItems() {
    viewModelScope.launch(Dispatchers.IO) {
        // Auteurs et leur nombre de livres
        val byAuthor = bookDao.getAllBooks().first()
            .groupBy { it.author }
            .map { (author, books) -> NavSubItem(author, books.size, "author:$author") }
            .sortedBy { it.label }

        // "Tous les livres" → count total
        val allItem = NavSubItem("Tous les livres", bookDao.getAllBooks().first().size, "all")

        _uiState.update { it.copy(
            navSubItems = mapOf(
                "Tous les livres" to listOf(allItem),
                "Auteur"          to byAuthor,
                "Favoris"         to emptyList(), // à implémenter avec bookmarks
                "Séries"          to emptyList(), // à implémenter avec metadata EPUB
                "Tags"            to emptyList(),
                "Dossiers"        to emptyList()
            )
        )}
    }
}

// 3. Passer state.navSubItems à LibraryNavigationPopup et supprimer le remember hardcodé :
@Composable
private fun LibraryNavigationPopup(
    navSubItems: Map<String, List<NavSubItem>>,  // NOUVEAU paramètre
    onDismiss: () -> Unit,
    onFilterSelect: (FilterMode) -> Unit,
    currentFilter: FilterMode
) {
    // Remplacer val subItems = remember { when(selectedCategory) { ... } } par :
    val subItems = navSubItems[selectedCategory] ?: emptyList()
    // ... reste inchangé
}
```

---

## TÂCHE 10 — Supprimer dictionnaire phonétique du TtsPanel

**Fichier :** `ui/screen/reader/ReaderTtsPanel.kt` + `ui/screen/settings/SettingsScreen.kt`
**Durée estimée :** 45 minutes

### Principe

Le dictionnaire de prononciation est une fonctionnalité avancée qui n'a pas sa place dans
le panneau de contrôle principal de lecture. Le déplacer dans `SettingsScreen`.

### Ce qu'il faut faire

1. **Dans `ReaderTtsPanel.kt`** : Supprimer le bloc `Column` dictionnaire phonétique
   (le `if (isRulesExpanded || pronunciationRules.isNotEmpty())`) ainsi que les paramètres
   `pronunciationRules`, `onAddPronunciationRule`, `onDeletePronunciationRule`, `onTogglePronunciationRule`
   de la signature de `TtsPanel`. Supprimer aussi `showAddRuleDialog` et `isRulesExpanded`.

2. **Dans `ReaderScreen.kt`** : Supprimer les paramètres correspondants dans l'appel à `TtsPanel`.

3. **Dans `SettingsScreen.kt`** : Ajouter une section "Prononciation" après la section TTS,
   contenant le même composant `PronunciationDictionary` (à extraire en composable réutilisable).

4. **Dans `SettingsViewModel`** : S'assurer que `pronunciationRules`, `onAddPronunciationRule`,
   `onDeletePronunciationRule`, `onTogglePronunciationRule` sont exposés (ils le sont probablement
   déjà si le ViewModel les injectait via ReaderViewModel — à vérifier).

---

## Récapitulatif et ordre d'exécution

| # | Tâche | Fichiers | Durée | Priorité |
|---|-------|----------|-------|----------|
| 1 | Supprimer couleurs hardcodées | 17 fichiers UI | 3-4h | 🔴 Critique |
| 2 | ModalNavigationDrawer | LibraryScreen.kt | 1.5h | 🔴 Critique |
| 3 | Empty state actionnable | LibraryScreen.kt | 45min | 🔴 Critique |
| 4 | ErrorBanner Material 3 | LibraryScreen.kt | 30min | 🟠 Haute |
| 5 | Coil AsyncImage | LibraryScreen.kt | 30min | 🟠 Haute |
| 6 | Accessibilité (strings + semantics) | 6+ fichiers | 1h | 🔴 Critique |
| 7 | Restructurer contrôles Reader | ReaderBottomControls.kt | 1h | 🟠 Haute |
| 8 | Barre progression + ETA | ReaderScreen + ViewModel | 1.5h | 🟡 Moyenne |
| 9 | Connecter NavPopup au ViewModel | LibraryScreen + ViewModel | 1h | 🟡 Moyenne |
| 10 | Déplacer dictionnaire phono | TtsPanel + Settings | 45min | 🟢 Basse |

**Total estimé : ~12 heures**

### Ordre recommandé

Commencer par **tâches 1 → 2 → 3 → 4** dans cet ordre : la tâche 1 (couleurs) est un
prérequis pour voir correctement le résultat des autres sur tous les thèmes.

```bash
# Après chaque tâche, vérifier que le build compile :
./gradlew assembleDebug

# Après la tâche 1, vérifier qu'il ne reste aucune constante deprecated :
grep -rn "TextMain\|TextMuted\|AccentBlue\|SurfaceDark\|SurfaceRaised\|BorderDark\|CardBg\|AppBackground" \
  app/src/main/java/com/inktone/ui --include="*.kt" | grep -v "Color.kt"

# Test lint
./gradlew lintDebug
```

### Message de commit final suggéré

```
Refactorise l'UI pour cohérence thème, accessibilité et UX

- Remplace 97 constantes deprecated par MaterialTheme.colorScheme.*
- Migre vers ModalNavigationDrawer (back gesture + TalkBack)
- Ajoute un empty state actionnable avec CTA import
- Remplace ErrorBanner emoji par pattern Material 3 error
- Migre BookCover vers Coil AsyncImage (chargement async)
- Ajoute 18 strings d'accessibilité + semantics Canvas stats
- Restructure UnifiedControlPanel (primaire/secondaire)
- Ajoute barre progression + ETR dans ReaderScreen
- Connecte LibraryNavigationPopup aux données Room
- Déplace dictionnaire phonétique vers SettingsScreen
```
