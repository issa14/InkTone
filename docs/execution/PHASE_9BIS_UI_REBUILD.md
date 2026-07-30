# Phase 9bis — Reconstruction de l'interface (au-dessus du niveau legacy)

**Dépend de :** Phases 4 à 8 (logique complète, jamais habillée), audit UX legacy (message précédent)
**Précède :** Tâche 9.1 (accessibilité — délibérément mise en pause jusqu'à la clôture de cette phase), puis Phase 10
**Principe directeur :** porter ce qui est bon du legacy, corriger ce qui doit l'être en portant, et dépasser le niveau legacy partout où l'état de l'art Android 2026 le permet sans risque de stabilité. Jamais un simple copier-coller.

**Révision :** Tâche 9bis.3.2 corrigée après revue — barre de progression **du livre entier** (`Locator.computeProgression`, écrite en Phase 1, jamais branchée) plutôt que la barre par chapitre copiée du legacy. Les deux niveaux d'information cohabitent, ce n'est pas un remplacement.

---

## Tâche 9bis.0 — Deux décisions techniques, vérifiées avant tout code

### 9bis.0.1 — Navigation : Compose Navigation 2.8+ typée, pas Navigation 3

**Vérifié** : Navigation 3 existe (réécriture complète, back prédictif natif, routes typées par construction) mais reste **en alpha** (`1.0.0-alpha07`, API instable par nature). Compose Navigation 2.8+ a des **routes typées stables** depuis fin 2024 (`@Serializable` + classes de données, plus de routes en chaîne de caractères comme le legacy) — mûr, pas expérimental.

**Décision, cohérente avec le reste du projet** (même raisonnement que le verrouillage Readium 3.0.0, Phase 3) : Navigation 2.8+ typée maintenant, Navigation 3 en évolution future une fois stabilisée (Blueprint §16, à noter). Le legacy utilisait des routes en chaîne (`"reader/{bookId}?jumpChapter=..."`) — **on fait mieux ici, pas pareil** :

```kotlin
// feature/reader (ou un module navigation dedie si la complexite le justifie)
@Serializable
data class ReaderRoute(val publicationId: String, val jumpChapter: Int? = null, val jumpSentence: Int? = null)

@Serializable
data class BookmarksRoute(val publicationId: String, val publicationTitle: String)

@Serializable
data class SearchRoute(val publicationId: String, val publicationTitle: String)

@Serializable object LibraryRoute
@Serializable object SettingsRoute
@Serializable object StatisticsRoute
@Serializable object AboutRoute
```

Plus de désérialisation manuelle de query params comme le legacy (`backStackEntry.arguments?.getString(...)`) — le compilateur garantit la cohérence des arguments.

### 9bis.0.2 — Sélection de texte : `CustomHighlightToolbar` re-vérifié contre notre Compose Foundation actuel

**Ne pas porter en confiance** — le legacy a été écrit contre une version de Compose antérieure. `LocalTextToolbar`/`TextToolbar` sont publics depuis longtemps, donc l'approche a de bonnes chances de fonctionner, mais **à confirmer par la pratique**, même discipline que chaque API tierce dans ce projet :

```kotlin
// Spike, avant de remplacer la selection par phrase (Tache 7.1) :
// 1. Reproduire HighlightTextToolbar (legacy) contre la version actuelle
//    de Compose Foundation utilisee dans le projet.
// 2. Verifier que TextRange/selection fournis par le toolbar personnalise
//    correspondent bien a des offsets exploitables pour reconstruire un
//    Locator (Tache 7.1) - le legacy stockait probablement chapitre/phrase
//    differemment, ne pas supposer une correspondance 1:1.
// 3. Si ca fonctionne : la selection au caractere pres remplace la
//    selection par phrase - AMELIORATION reelle par rapport au legacy
//    ET a l'etat actuel de la Tache 7.1, pas juste un retour en arriere.
// 4. Si ca ne fonctionne pas (regression Compose depuis l'ecriture du
//    legacy) : garder la selection par phrase actuelle, documenter
//    pourquoi, ne pas s'obstiner.
```

**Résultat de la vérification (2026-07-29)** : `LocalTextToolbar`/`TextToolbar`/`TextToolbarStatus` sont toujours publics et inchangés dans la version de Compose Foundation utilisée par le projet (`composeBom = 2024.09.02`, non modifiée depuis Phase 0 — vérifié par `git log` sur `gradle/libs.versions.toml`) : `HighlightTextToolbar` (legacy) compile tel quel. **Mais** ce mécanisme n'expose que le `Rect` de la bulle et les callbacks `onCopyRequested`/`onSelectAllRequested` — **jamais un `TextRange` ou des offsets de sélection**. Or `AnnotationSelectionHandler` (Tâche 7.1, commentaire déjà en place) avait déjà établi que `Selection`/`SelectionContainer(selection, onSelectionChange, content)` **contrôlé** est `internal` dans `androidx.compose.foundation:foundation` — confirmé de nouveau ici, aucune régression ni amélioration Compose depuis. **Conclusion : la sélection au caractère près reste bloquée par l'API publique actuelle, indépendamment de `CustomHighlightToolbar`.** Décision (point 4 du spike) : garder la sélection par phrase (Tâche 7.1) telle quelle en 9bis.3.4, ne pas porter `CustomHighlightToolbar` — aucune API publique à laquelle le brancher pour l'instant.

**Commit :** `Verifie CustomHighlightToolbar et confirme le choix de Navigation Compose 2.8 type`

---

## Tâche 9bis.1 — Système de design (porté et dépassé)

**Objectif :** porter `Color.kt`/`Type.kt`/`Shape.kt`/`Spacing.kt`/`AppIcons.kt` (553 lignes, legacy) dans `core/designsystem`, avec deux améliorations réelles au-delà du niveau legacy.

### 9bis.1.1 — Port de base, vérifié contre le contraste WCAG AA (Tâche 9.1.3, déjà construite)

Porter tel quel, package adapté — **puis** faire tourner le test de contraste déjà écrit en Tâche 9.1.3 sur les couleurs portées. Le legacy notait 7/10 visuellement mais n'a jamais été vérifié formellement — corriger toute couleur qui échoue, ne pas supposer que « ça avait l'air bien » suffit pour un score de contraste réel.

### 9bis.1.2 — Amélioration : couleur dynamique (Material You), avec repli

**Justification, pas une lubie** : la couleur dynamique (thème dérivé du fond d'écran de l'utilisateur) est documentée comme **le standard par défaut des nouvelles apps Android en 2026** — un signal « app native premium bien intégrée au système » immédiatement reconnaissable, que le legacy n'avait pas.

```kotlin
@Composable
fun InkToneTheme(useDynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        // API 31+ uniquement - notre minSdk est 26 (Phase 0), repli
        // obligatoire pour ~toutes les versions Android en dessous.
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isSystemInDarkTheme() -> InkToneDarkColorScheme  // palette portee du legacy (Color.kt)
        else -> InkToneLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = InkToneTypography, shapes = InkToneShapes, content = content)
}
```

**Point d'attention** : la couleur dynamique s'applique au **chrome de l'app** (barres, boutons, surfaces) — **jamais aux thèmes de lecture** (`ReadingTheme.LIGHT/DARK/SEPIA`, Tâche 1.3/4.7), qui restent des choix éditoriaux délibérés de l'utilisateur pour le confort de lecture, pas soumis à la couleur du fond d'écran. Ne pas mélanger les deux systèmes de couleur.

### 9bis.1.3 — Amélioration : typographie dédiée à la lecture longue, distincte du chrome UI

**Le `Type.kt` legacy (122 lignes) couvre probablement le chrome de l'app** (boutons, titres d'écran) — pas nécessairement optimisé pour des heures de lecture continue. Ajouter une échelle typographique **séparée**, appliquée uniquement au texte du livre dans `ReaderScreen` :

```kotlin
val ReadingTypography = TextStyle(
    lineHeight = 1.6.em,      // plus genereux que le chrome UI standard -
                                // confort de lecture longue duree, pas juste esthetique
    letterSpacing = 0.01.em,
    // Optical sizing / graisse variable si la police portee le supporte -
    // a verifier contre la police reellement embarquee (Tache 9bis.1.1),
    // pas suppose disponible.
)
```

**Commit :** `Porte le systeme de design legacy, ajoute couleur dynamique et typographie de lecture dediee`

---

## Tâche 9bis.2 — Navigation réelle (remplace l'état à 3 cas)

**Objectif :** `NavHost` avec les routes typées de la Tâche 9bis.0.1, remplace `AppScreen` (état minimal posé en Phase 7) — **extension consciente de ce qui existait**, pas une réécriture qui jette le travail précédent : `AppScreen.Library`/`Reader` deviennent `LibraryRoute`/`ReaderRoute`, le `BackHandler` manuel est remplacé par le back stack réel de `NavHost`.

```kotlin
@Composable
fun InkToneNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onBookClick = { id -> navController.navigate(ReaderRoute(id)) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onStatsClick = { navController.navigate(StatisticsRoute) },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderScreen(
                publicationId = route.publicationId,
                jumpChapter = route.jumpChapter,
                onBack = { navController.popBackStack() },
                onBookmarksClick = { title -> navController.navigate(BookmarksRoute(route.publicationId, title)) },
                onSearchClick = { title -> navController.navigate(SearchRoute(route.publicationId, title)) },
            )
        }
        // BookmarksRoute, SearchRoute, SettingsRoute, StatisticsRoute, AboutRoute -
        // meme schema, pas detaille ligne par ligne ici.
    }
}
```

**Amélioration au-delà du legacy** : retour prédictif Android (le geste de balayage arrière montre un aperçu de l'écran précédent) — pas natif en Navigation Compose 2.8 comme le serait Navigation 3, mais activable explicitement :

```kotlin
// AndroidManifest.xml
android:enableOnBackInvokedCallback="true"

// Dans chaque ecran, remplacer BackHandler simple par PredictiveBackHandler
// (androidx.activity.compose) pour l'animation de previsualisation - a
// verifier ecran par ecran, pas suppose fonctionner partout par defaut.
```

**Commit :** `Remplace l'etat de navigation minimal par NavHost type, active le retour predictif`

---

## Tâche 9bis.3 — Reader complet (le cœur de l'expérience, priorité absolue de cette phase)

**Objectif :** porter les 8 fichiers legacy (~2700 lignes cumulées) en les répartissant selon notre architecture MVI déjà posée (Tâches 3.5/4.7/5.5/7.0/7.1) — pas un seul fichier monolithique comme le legacy `ReaderContent.kt` (1107 lignes), une vraie décomposition en composants.

### 9bis.3.1 — Mode immersif et HUD auto-masqué

```kotlin
@Composable
fun ImmersiveReaderChrome(isHudVisible: Boolean, onAutoHide: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(isHudVisible) {
        if (isHudVisible) { delay(4000); onAutoHide() }
    }
    content()
}
```

Porté quasi tel quel — le legacy avait déjà la bonne solution ici, rien à améliorer.

### 9bis.3.2 — Barre de progression du livre entier (corrigé — pas le chapitre) et TOC hiérarchique

**Correction actée après revue** : le legacy affiche une barre de progression **par chapitre** — probablement plus simple à calculer dans son architecture, pas un choix délibérément meilleur. `Locator.computeProgression()` (Tâche 1.1, Blueprint §3.2) a été écrite précisément pour une progression **livre entier** et n'a jamais été branchée nulle part depuis la Phase 1. C'est l'occasion de la brancher pour de vrai, pas de reproduire le raccourci du legacy.

**Les deux niveaux cohabitent**, pas un remplacement d'un par l'autre — cohérent avec ce que font les lecteurs premium de référence : la barre persistante fine montre la progression du **livre entier** (ce qui compte pour l'utilisateur — « je suis à 80% »), la position dans le chapitre reste disponible via la TOC et un indicateur secondaire discret dans le panneau de contrôle (Tâche 9bis.3.3), jamais la barre principale.

```kotlin
// ReaderViewModel — calcule une seule fois a l'ouverture (le DocumentModel
// complet est deja en memoire depuis la Tache 4.6, rien de nouveau a charger) :
private fun computeTotalChars(chapters: List<Chapter>): Int =
    chapters.sumOf { chapter -> chapter.paragraphs.sumOf { it.sentences.sumOf { s -> s.text.length } } }

// A chaque changement de position, mis en cache dans l'etat, pas recalcule
// a chaque recomposition :
val bookProgression = Locator.computeProgression(
    locator = currentLocator,
    totalCharsBeforeChapter = charsBeforeCurrentChapter, // somme partielle des chapitres precedents
    totalCharsInPublication = state.totalChars,
)
```

```kotlin
@Composable
fun BookProgressBar(progression: Float) {
    LinearProgressIndicator(
        progress = { progression },
        modifier = Modifier.fillMaxWidth().height(2.dp), // fin et persistant, comme le legacy
    )
}
```

Porter `ChapterPicker` (legacy `ReaderTopBar.kt`) pour la TOC — indentation par niveau, défilement automatique vers le chapitre courant, déjà bien conçu. **Brancher sur le vrai `TableOfContentsEntry.children`** (domaine posé Phase 1, jamais rempli tant qu'aucun test multi-niveaux n'existait — Tâche 4.11 avait noté ce point comme non vérifié) : c'est le moment de le vérifier pour de vrai, avec un fixture EPUB à hiérarchie réelle (Tome/Livre/Chapitre), pas juste porter l'UI en espérant que les données suivent.

### 9bis.3.3 — Panneau de contrôle unifié (`UnifiedControlPanel`)

Porter la structure (play/pause central avec retour haptique, navigation chapitre, accès réglages/TOC/recherche/signets/minuteur de sommeil) — **le legacy a déjà résolu ici un vrai problème d'UX** (icônes séparées qui tronquaient le titre, note trouvée dans le code) : ne pas revenir en arrière sur cette leçon.

**Amélioration** : le minuteur de sommeil (`onSleepTimerClick`) n'a aucune brique domaine actuellement — nouveau, pas dans les 10 phases :
```kotlin
// domain/model/SleepTimer.kt - NOUVEAU
data class SleepTimerState(val remainingMs: Long, val fadeOutEnabled: Boolean = true)
// Fondu sonore en fin de minuteur (pas un arret brutal) - detail premium
// absent du legacy (a verifier - peut-etre deja present, ne pas supposer).
```

### 9bis.3.4 — Sélection de texte (résolu par la Tâche 9bis.0.2)

**Résultat du spike (9bis.0.2) : `CustomHighlightToolbar` ne donne accès à aucun `TextRange`/offset, seulement au `Rect` de la bulle.** La sélection au caractère près reste bloquée par l'API Compose Foundation publique actuelle (`SelectionContainer` contrôlé `internal`). Décision : **garder la sélection par phrase (Tâche 7.1) telle quelle**, `AnnotationSelectionHandler` inchangé — pas de régression, pas de nouvelle dépendance introduite pour rien.

### 9bis.3.5 — Surlignage mot-à-mot animé (amélioration réelle, rien d'équivalent dans le legacy)

**Le legacy n'avait pas de vrais timestamps CTC** (c'était encore l'ère Sherpa-ONNX supposé natif, avant qu'on découvre et corrige ça en Phase 3/5 de la réécriture) — donc son surlignage était nécessairement plus simple. On a maintenant de vrais `WordTimestamp` (ADR-022) : transition douce entre mots plutôt qu'un changement brut :

```kotlin
val highlightOffset by animateIntAsState(
    targetValue = currentWordCharOffset,
    animationSpec = tween(durationMs = reducedMotionDuration(150)), // Tache 8.4, respecte le reglage systeme
)
```

### 9bis.3.6 — Amélioration accessibilité, directement liée à ta situation

**Réglette de lecture** (« reading ruler ») — une bande semi-transparente qui suit la ligne en cours, aide documentée pour plusieurs conditions visuelles et la dyslexie, absente du legacy :

```kotlin
@Composable
fun ReadingRuler(currentLineY: Float, enabled: Boolean) {
    if (!enabled) return
    Box(Modifier.fillMaxWidth().offset(y = currentLineY.dp).height(32.dp)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)))
}
```

Réglage activable dans `SettingsScreen` (Tâche 9bis.4), désactivé par défaut (pas tout le monde n'en a besoin), mentionné explicitement dans le préréglage d'accessibilité (Tâche 8.4, extension à ajouter).

**Commit(s), un par sous-tâche 9bis.3.x — pas un seul commit géant pour tout le Reader**

---

## Tâche 9bis.4 — Bibliothèque complète

**Objectif :** porter drawer, popup de filtre, dialogue tri/type/disposition, recherche intégrée, menu 3-points, vue par séries, barre de tags, état vide, FAB reprise — remplace la grille nue de la Tâche 6.6.

### Améliorations au-delà du legacy

**Chargement squelette plutôt qu'un simple spinner** — pendant que les couvertures se chargent, afficher des rectangles animés (effet « shimmer ») à la place des placeholders vides : perception de rapidité, standard des apps premium actuelles (Spotify, Kindle) que le legacy n'avait pas.

```kotlin
@Composable
fun BookCoverShimmer() {
    val shimmerAlpha by rememberInfiniteTransition().animateFloat(
        0.3f, 0.7f, infiniteRepeatable(tween(reducedMotionDuration(800)), RepeatMode.Reverse),
    )
    Box(Modifier.aspectRatio(2f/3f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)))
}
```

**Carte « reprendre la lecture » proéminente en tête de grille**, pas seulement un FAB flottant discret (legacy) — plus visible, cohérent avec le principe d'accessibilité du projet (moins de recherche visuelle nécessaire pour l'action la plus fréquente).

**Transition de contenu partagé** (couverture du livre qui « s'étire » de la grille vers l'écran de lecture, `SharedTransitionLayout` — Compose stable depuis 1.7) plutôt qu'un simple `fade`/`slide` : signal premium immédiatement perceptible, faisable proprement uniquement parce qu'on a une vraie navigation typée maintenant (Tâche 9bis.2).

**Recherche intégrée** — réutilise `SearchService`/`SearchPublicationUseCase` (Phase 7) déjà construits pour la recherche **dans un livre** ; la recherche **dans la bibliothèque** (titre/auteur, pas plein texte) est une requête différente, plus simple, sur `PublicationRepository` — ne pas confondre les deux, ne pas réutiliser FTS pour un filtrage de titres qui n'en a pas besoin.

**Commit :** `Reconstruit LibraryScreen complet avec drawer, filtres, recherche, ameliorations shimmer et transition partagee`

---

## Tâche 9bis.5 — Réglages enrichis

**Objectif :** le legacy (697+185 lignes) est plus riche que la Tâche 8.1 — porter la structure complète (sections organisées, pas une liste plate), y ajouter les réglages des Tâches 9bis.1.2 (couleur dynamique on/off), 9bis.3.6 (réglette de lecture).

**Amélioration** : `LargeTopAppBar` Material 3 avec effet de collapse au défilement plutôt qu'un en-tête statique — détail moderne standard, absent du legacy.

**Commit :** `Enrichit SettingsScreen, ajoute les reglages des ameliorations de cette phase`

---

## Tâche 9bis.6 — Écrans restants

**Signets globaux** (`AllBookmarksPanel`, accessible depuis le drawer) — port direct, peu de risque.

**Statistiques** — porter la structure visuelle (graphiques/cartes), brancher sur `feature/statistics` déjà fonctionnel (Tâche 8.6) plutôt que sur les données legacy.

**À propos** — port direct, mise à jour des mentions de licences (Kokoro Apache-2.0, polices, bibliothèques tierces — vérifier que chaque dépendance ajoutée depuis la Phase 0 y figure, pas seulement celles connues du legacy).

**Commit :** `Porte les ecrans signets globaux, statistiques et a propos`

---

## Checklist finale de sortie de Phase 9bis

**Vérifié (2026-07-29/30)** : `./gradlew testDebugUnitTest :app:assembleDebug` vert (542 tâches, 0 échec) après le dernier commit listé — pas une affirmation sur plan, le build réel est passé. `./gradlew build` complet (avec lint pleine échelle sur les 18 modules) n'a pas pu être mené à terme dans cette session (timeout de commande à 590s, pas un échec de build) ; `checkArchitectureRules` a été vérifié séparément sur les modules touchés et est vert.

| # | Critère | État | Commit |
|---|---|---|---|
| 1 | Navigation typée choisie et justifiée (pas Nav3, encore alpha) | Fait | `7e5b557` |
| 2 | `CustomHighlightToolbar` vérifié contre la version Compose actuelle, pas supposé | Fait — conclusion : API insuffisante, sélection par phrase conservée | `7e5b557` |
| 3 | Système de design porté, contraste WCAG AA vérifié sur les couleurs portées | Fait (palette Signature uniquement, voir 9bis.1.1) | `011be99` |
| 4 | Couleur dynamique avec repli correct sous API 31 | Fait, réglage exposé dans Settings | `011be99`, `dea2c75` |
| 5 | `NavHost` réel, retour prédictif activé | Fait — activé au niveau manifeste ; **pas vérifié écran par écran** faute d'émulateur/device dans cette session, voir note ci-dessous | `6302b40` |
| 6 | Reader complet : immersif, TOC hiérarchique, panneau unifié, sélection, surlignage animé, réglette de lecture | Fait avec deux réserves explicites : TOC hiérarchique implémentée (aplatissement + indentation) mais **jamais vérifiée avec un fixture EPUB à hiérarchie réelle** (TODO dans `TableOfContentsSheet.kt`) ; réglette de lecture existe comme composant + réglage persisté mais **pas encore consommée par `ReaderScreen`** (TODO dans `ReadingRuler.kt`) | `6c07001`, `81658ed`, `1a56d50`, `ab41685` |
| 7 | Bibliothèque complète avec améliorations (shimmer, carte reprise, transition partagée) | Fait sauf transition de contenu partagée, **explicitement non implémentée** (changement invasif non vérifiable visuellement dans cette session, voir KDoc `LibraryScreen.kt`) | `d5274c5` |
| 8 | Réglages enrichis | Fait | `dea2c75` |
| 9 | Écrans restants portés | Fait | `48b9bb7` |

**Réserve globale** : aucun rendu de ces écrans n'a été observé sur un émulateur ou un device réel dans cette session (environnement sans affichage graphique) — la vérification s'est limitée à la compilation, aux tests unitaires/JVM et à `checkArchitectureRules`. Une passe manuelle sur device (ou `./gradlew build` complet avec lint) reste à faire avant de considérer la Phase 9bis visuellement validée, au-delà de "compile et les tests passent".

Phase 9bis close sous ces réserves. **Reprendre alors la Tâche 9.1 (accessibilité)** — sur les vrais écrans enrichis cette fois, pas des squelettes qui auraient exigé un second passage.
