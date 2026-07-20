# 🔍 InkTone — Audit indépendant UX, Fluidité & Performance

> **Date** : 2026-07-20
> **Auditeur** : Claude (Anthropic), analyse directe du code source
> **Périmètre audité** : dépôt public `github.com/issa14/ReadFlow`, branche `main`, commit `567d836` (2026-07-20 04:10)
> **Méthode** : lecture ligne par ligne du code réel (Kotlin/Compose), **aucune donnée tirée de `PROJECT_STATUS.md` ou des audits précédents** — ce rapport est volontairement indépendant des auto-évaluations déjà présentes dans le dépôt, pour éviter de simplement répéter ce qui a déjà été déclaré "✅ Fait".
> **Sévérités** : 🔴 Bloquant beta · 🟠 Important · 🟡 Qualité premium · 🟢 Bon point vérifié

---

## 0. Pourquoi ce rapport diffère des audits précédents

Le dépôt contient déjà plusieurs documents d'audit (`Plan_d_action.md`, `PLAN_ACTION_UXUI_CLAUDECODE.md`, `PLAN_FEATURE_AUDIT_CLAUDECODE.md`, `COLD_START_AUDIT.md`) qui annoncent un score de 8.0/10 et une progression à 95%. Ces documents sont réels et les corrections qu'ils décrivent sont bien présentes dans le code. Mais ils n'ont **jamais remis en question l'hypothèse centrale du produit** : est-ce que la position de lecture est réellement fidèle ? En creusant le code exécuté (pas les commentaires ni les noms de fonctions), la réponse est non — et c'est le sujet précis que tu as posé. C'est le fil rouge de ce rapport.

---

## 1. Synthèse exécutive

| # | Sujet | Sévérité | Verdict en une phrase |
|---|---|---|---|
| 1 | Persistance de la position de lecture | 🔴 | Ne fonctionne que pendant la narration TTS active ; la lecture silencieuse n'est jamais sauvegardée |
| 2 | Reprise visuelle à la réouverture | 🔴 | La position restaurée en base n'est jamais appliquée à l'écran tant que Play n'est pas pressé |
| 3 | Reprise audio (`startFrom`) | 🔴 | Codée en dur à `0` — la narration redémarre systématiquement au début du chapitre |
| 4 | % de progression (jaquette bibliothèque) | 🔴 | Pondéré par nombre de chapitres, pas par longueur réelle → faux sur tout livre aux chapitres inégaux |
| 5 | Doublon de tables de progression | 🟠 | `progress` et `reading_progress` stockent la même chose via deux chemins de code différents |
| 6 | Démarrage à froid (cold start) | 🟠 | Pas d'API Splash Screen → flash blanc perceptible |
| 7 | Import EPUB | 🟠 | Traitement intégral et séquentiel de tous les chapitres avant disponibilité du livre |
| 8 | Chargement de la bibliothèque | 🟠 | Requête N+1 sur la progression (une requête DB par livre, en boucle séquentielle) |
| 9 | Table des matières | 🟡 | Liste non paresseuse (`Column`+`forEach`), pas de scroll auto vers le chapitre courant |
| 10 | Surlignage "mot par mot" | 🟡 | Simulation par interpolation de caractères, pas une vraie synchro par timestamps |

Les points 1 à 4 forment un seul et même chantier : **le système de progression n'a jamais été conçu pour la lecture silencieuse**, alors même que c'est un lecteur EPUB à vocation généraliste. C'est le blocage le plus sérieux avant une beta.

---

## 2. 🔴 Dossier prioritaire — la progression de lecture n'est pas fiable

### 2.1 Ce que le code fait réellement

La persistance de la position (les deux tables `progress` et `reading_progress`) est câblée **exclusivement** sur le flux de lecture TTS :

```kotlin
// ReaderViewModel.kt, ligne ~246-277
orchestrator.playbackState.collect { pbs ->
    _uiState.update { it.copy(currentSentenceIndex = pbs.activeSentenceIndex, ...) }
    savedState["sentenceIndex"] = pbs.activeSentenceIndex   // process death
    calculateProgress(book, chapterIndex, pbs.activeSentenceIndex, pbs.totalSentences) // DB
}
```

`orchestrator.playbackState` n'émet **que pendant la narration audio**. Résultat : un utilisateur qui ouvre un livre, scrolle pendant 45 minutes sans jamais appuyer sur Play, puis ferme l'app — n'a **rien sauvegardé**. À la réouverture, il repart du dernier point où le TTS s'est arrêté (ou du début).

Il n'existe aucun mécanisme de secours : j'ai vérifié qu'il n'y a **aucun** `DisposableEffect`/`ON_STOP`/`onCleared()` dans `ReaderScreen.kt` ou `ReaderViewModel.kt` qui sauvegarderait la position au moment où l'utilisateur quitte l'écran (le seul `DisposableEffect` présent gère l'affichage immersif des barres système, rien d'autre).

### 2.2 Même en TTS, la reprise visuelle est cassée

À la réouverture d'un livre, `ReaderViewModel.loadBook()` calcule bien la bonne position :

```kotlin
// ReaderViewModel.kt, ligne 324-334
val dbProgress = orchestrator.loadProgress(bookId)
val position = resolvePosition(dbProgress?.chapterIndex, dbProgress?.sentenceIndex, ...)
loadChapter(position.chapterIndex, position.sentenceIndex)
```

Mais **l'écran ne l'affiche pas**. Le scroll et le surlignage actif utilisent une autre source :

```kotlin
// ReaderContent.kt, ligne 120
val activeIdx = playbackState.activeSentenceIndex   // ⚠️ vient de l'orchestrator, pas du ViewModel
```

`orchestrator.playbackState` est un `MutableStateFlow(PlaybackState())` dont la valeur par défaut a `activeSentenceIndex = 0`, et il n'est mis à jour qu'au **démarrage effectif d'une narration**. Donc : le chapitre restauré s'affiche correctement, mais le scroll (`LaunchedEffect(activeIdx) { lazyListState.animateScrollToItem(...) }`, ligne 436-442) place systématiquement l'utilisateur **en haut du chapitre**, pas à la phrase où il s'est arrêté — tant qu'il n'a pas relancé la lecture.

### 2.3 Et quand on relance la lecture, ça repart quand même à zéro

```kotlin
// ReaderViewModel.kt, fonction play(), ligne 475-478
orchestrator.play(
    chapter.sentences, voice = s.voice, speed = s.speed, startFrom = 0,   // ⚠️ codé en dur
    bookTitle = book.title, chapterTitle = chapter.title, bookId = book.id, chapterIndex = s.currentChapterIndex
)
```

`startFrom = 0` est une constante littérale. Même si `s.currentSentenceIndex` contient la bonne valeur restaurée, elle n'est **jamais transmise** à l'orchestrateur. Concrètement : rouvrir un livre au chapitre 12, phrase 40, puis appuyer sur Play → la voix recommence à lire le chapitre 12 depuis la phrase 0.

À noter aussi : `stop()` remet `currentSentenceIndex = 0` dans l'état local (ligne 500), ce qui peut visuellement "perdre" la position affichée même si la DB, elle, garde la bonne valeur.

### 2.4 Une fonctionnalité à moitié câblée : `characterOffset`

`ReadingProgress` (la table `reading_progress`) contient un champ `characterOffset`, correctement rempli à l'écriture (`sent?.startOffset`, `PlaybackOrchestrator.kt` ligne 681/770-793) — mais **jamais lu**. `ResolveReadingPositionUseCase` ne s'en sert pas du tout. C'est une donnée capturée pour rien : soit une fonctionnalité de granularité fine abandonnée en cours de route, soit un import mort. Dans les deux cas, ça illustre bien le problème que tu décris : des pièces du puzzle existent séparément, mais rien n'est branché bout en bout.

### 2.5 Le pourcentage affiché sur la jaquette est structurellement biaisé

```kotlin
// CalculateReadingProgressUseCase.kt
val fraction = (chapterIndex + sentenceIndex.toFloat() / totalSentences) / book.totalChapters
```

Chaque chapitre compte pour "1 unité", quelle que soit sa longueur réelle. Or `totalChapters` provient directement du nombre d'entrées de la TOC (`flatToc.size`, `BookRepositoryImpl.kt` ligne 181) — qui inclut typiquement page de titre, dédicace, préface, etc., comptées comme des chapitres à part entière. Sur un roman avec une préface d'une page et un chapitre 1 de 40 pages, terminer la préface affichera déjà plusieurs % de "progression", et un chapitre très long comptera pour le même poids qu'un chapitre très court.

**Ce n'est donc ni millimétré, ni fidèle au livre entier** — c'est une fraction de position dans la table des matières, pas une fraction de contenu réellement lu.

### 2.6 Deux tables qui font (presque) la même chose

| Table | Entité | Écrite par | Lue par |
|---|---|---|---|
| `progress` | `ProgressEntity` (`bookId, currentChapterIndex, currentSentenceIndex, totalProgressFraction`) | `CalculateReadingProgressUseCase` (déclenché par le collector `playbackState` du ViewModel) | `LibraryViewModel` (jaquettes, filtres Lu/Non lu) |
| `reading_progress` | `ReadingProgress` (`bookId, chapterIndex, sentenceIndex, characterOffset`) | `PlaybackOrchestrator.saveProgressAsync()` (déclenché en interne pendant la narration) | `ResolveReadingPositionUseCase` (reprise à l'ouverture) |

Les deux sont déclenchées par le même événement (transition de phrase TTS) mais via deux chemins de code séparés, avec deux modèles de données différents pour la même information (chapitre + phrase). Rien n'empêche aujourd'hui une divergence (ex. un chapitre change via `nextChapter()` manuel pendant une pause : `currentChapterIndex` du ViewModel évolue, mais `orchestrator` garde son propre `chapterIndex` interne capturé au dernier `play()`).

### 2.7 Conséquence en cascade sur la bibliothèque

`LibraryViewModel.kt` classe les livres "Non lus" via `progressMap[book.id] <= 0.01f` (ligne 245) — donc tout livre lu uniquement en scroll silencieux reste **indéfiniment classé "non lu"**, peu importe combien de temps l'utilisateur a réellement passé dessus.

---

## 3. 🔴 Plan de correction — progression de lecture

C'est le chantier n°1 avant toute beta, parce qu'il touche à la promesse de base d'un lecteur ebook : "je reprends où j'en étais".

| # | Action | Détail |
|---|---|---|
| A | **Découpler la sauvegarde de position du TTS** | Ajouter un `snapshotFlow { lazyListState.firstVisibleItemIndex }` (débouncé ~500ms) dans `ScrollContent`/`PagedContent`, remontant vers le ViewModel un événement "position lue manuellement". Il faut distinguer scroll *utilisateur* et scroll *programmatique* (déclenché par `animateScrollToItem` lors d'un changement TTS) pour éviter une boucle de rétroaction — par ex. un flag `isProgrammaticScroll` le temps de l'animation. |
| B | **Unifier la source de vérité affichée** | Remplacer `val activeIdx = playbackState.activeSentenceIndex` par une valeur qui vaut `playbackState.activeSentenceIndex` si `isSpeaking`, sinon `viewModel.uiState.currentSentenceIndex` (déjà correctement restauré par `loadBook()`). C'est le fix le plus simple et le plus impactant du rapport — quelques lignes, effet immédiat. |
| C | **Réparer `startFrom`** | `orchestrator.play(..., startFrom = s.currentSentenceIndex, ...)` au lieu de `0`. Vérifier que `PlaybackOrchestrator.play()` gère bien un `startFrom > 0` en interne (initialisation du buffer de synthèse à la bonne phrase). |
| D | **Fusionner les deux tables** | Ne garder qu'une seule table de position (`reading_progress` a le meilleur design — `characterOffset` inclus). Ajouter une colonne `totalProgressFraction` calculée dessus. Migration Room (bump de version) qui fusionne les deux tables existantes (prendre la ligne avec le `updatedAt` le plus récent par livre) pour ne pas perdre la progression des testeurs déjà actifs. |
| E | **Pondérer le % par longueur réelle** | À l'import (`importEpub`), stocker le nombre de caractères (ou de mots) de chaque chapitre ainsi que le cumul avant chaque chapitre (`cumulativeCharsBeforeChapter`). Le calcul devient `(cumulativeCharsBeforeChapter[chapitre] + offsetCaractère) / totalCaractèresLivre`. C'est un vrai indicateur "tout le livre", indépendant du découpage TOC. |
| F | **Réutiliser `characterOffset`** | Une fois (E) en place, `characterOffset` devient utile pour un positionnement infra-phrase si un jour le rendu par page/scroll fin le justifie ; sinon, le retirer proprement plutôt que le laisser comme donnée fantôme. |
| G | **Tests de non-régression dédiés** | Un test qui simule : ouverture → scroll manuel sans lecture audio → fermeture → réouverture → vérifie chapitre/phrase/scroll restaurés. Aujourd'hui, aucun test du dépôt ne couvre ce scénario (les tests existants ciblent `PlaybackOrchestrator`, pas le cas "lecture silencieuse").

---

## 4. 🟠 Ouverture de l'application (cold start)

- **Pas d'API Splash Screen** (`androidx.core:core-splashscreen`). `MainActivity.onCreate()` appelle directement `setContent {}` — sur un appareil d'entrée/milieu de gamme (le Snapdragon 680 ciblé), il y a un écran blanc/noir bref avant le premier frame Compose. Coût : quelques centaines de ms de perception "app qui rame", alors que le fix (une dépendance + un thème splash) est peu coûteux.
- **Risque de flicker à l'onboarding** : `isFirstLaunch` est un `StateFlow` collecté avec `initialValue = true` (`MainActivity.kt`). Si le vrai état DataStore (`false` pour un utilisateur existant) arrive après le tout premier frame, l'utilisateur peut voir un flash d'onboarding avant de retomber sur la bibliothèque.
- 🟢 **Bon point** : `InkToneApplication.onCreate()` est minimaliste (pas d'init lourde au démarrage — pas de chargement du moteur ONNX/TTS avant l'ouverture d'un livre). Le moteur TTS est initialisé paresseusement dans `ReaderViewModel`, pas dans `Application`.
- 🟢 **Bon point** : toutes les méthodes de tous les DAO du dépôt sont `suspend` ou renvoient un `Flow` — aucune requête Room synchrone/bloquante trouvée qui pourrait geler le thread principal.
- 🟢 **Bon point** : un `baseline-prof.txt` réel existe (129 lignes) avec un module de benchmark dédié (`benchmark/src/main/java/com/inktone/benchmark/StartupBenchmark.kt`).

---

## 5. 🟠 Import & parsing des livres

Dans `BookRepositoryImpl.importEpub()` (`app/src/main/java/com/inktone/data/repository/BookRepositoryImpl.kt`, ligne 88-286) :

- L'import copie le fichier, ouvre la publication Readium, extrait les métadonnées/couverture — **puis parcourt et traite l'intégralité des chapitres** (extraction HTML, découpage en phrases, extraction des blocs riches, indexation FTS) **avant** que le livre soit considéré comme prêt. Le livre n'apparaît utilisable qu'une fois 100% du traitement terminé.
- Ce traitement est **entièrement séquentiel** (`for (i in 0 until totalChapters)`), sans parallélisation, alors que chaque chapitre est indépendant des autres (à l'exception de la numérotation des blocs riches, facilement isolable par chapitre).
- Impact concret : pour un roman de 400 pages / 40 chapitres, l'import peut prendre plusieurs secondes à quelques dizaines de secondes sur un Snapdragon 680, période pendant laquelle l'utilisateur ne peut rien faire d'autre que regarder la barre de progression.
- 🟢 **Bon point** : la progression d'import est bien granulaire et remontée à l'UI (`onProgress(fraction, status)`), les erreurs par section (métadonnées, série, ISBN) sont interceptées individuellement sans faire échouer tout l'import, et un EPUB corrompu déclenche un nettoyage propre des fichiers partiels (`epubDir.deleteRecursively()`).

**Recommandation** : paralléliser le traitement des chapitres avec une concurrence limitée (ex. `Dispatchers.IO.limitedParallelism(4)` + `async`/`awaitAll`), après avoir rendu l'indexation des blocs riches indépendante du compteur global partagé. Objectif : diviser le temps d'import par 2-3 sur les appareils multi-cœurs ciblés, sans changer l'expérience "livre dispo seulement une fois prêt" (qui reste un choix défendable pour la fiabilité, tant que c'est rapide).

---

## 6. 🟠 Bibliothèque

`LibraryViewModel.loadBooks()` (ligne 88-119) :

```kotlin
val books = bookRepository.getAllBooks()
books.forEach { book ->
    val progress = bookRepository.getProgress(book.id)   // ⚠️ 1 requête DB par livre, en boucle
    progressMap[book.id] = progress?.totalProgressFraction ?: 0f
}
```

C'est une requête N+1 classique : pour une bibliothèque de 200 livres, ce sont 200 allers-retours Room séquentiels à chaque ouverture de la bibliothèque, avant même de pouvoir afficher la grille. Le fix est direct : une seule requête `SELECT * FROM progress WHERE bookId IN (:ids)` (Room supporte nativement les listes en paramètre), puis construction de la map en mémoire.

- 🟢 **Bon point** : la grille (`LazyVerticalGrid`, `LibraryScreen.kt` ligne 467-474) utilise des clés stables (`items(books, key = { it.id })`), ce qui évite les recompositions/animations parasites lors du tri ou du filtrage.
- 🟢 **Bon point** : les couvertures passent par Coil (`AsyncImage`), donc décodage hors thread principal (déjà corrigé par un audit précédent, vérifié toujours en place).

---

## 7. 🟡 Table des matières

`ChapterPicker` (`app/src/main/java/com/inktone/ui/screen/reader/ReaderTopBar.kt`, ligne 67-106) :

```kotlin
Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
    tocEntries.forEach { entry -> /* ... */ }
}
```

Deux problèmes :
1. **Pas de `LazyColumn`** : tous les items de la TOC sont composés d'un coup, non recyclés. Sans impact visible sur un roman à 20-30 chapitres, mais ne scale pas pour un livre à TOC profonde (encyclopédie, recueil, ouvrage technique avec sous-sections).
2. **Pas de scroll initial vers le chapitre courant** : la ligne active est bien mise en surbrillance (`isCurrent`), mais si l'utilisateur est au chapitre 42 sur 50, ouvrir la table des matières l'affiche depuis le chapitre 1 — il doit scroller manuellement pour retrouver sa position en cours. C'est un vrai frein à l'intuitivité pour un usage quotidien.

**Recommandation** : passer à `LazyColumn` + `items(tocEntries, key = { it.index })`, et initialiser le `LazyListState` avec `initialFirstVisibleItemIndex` correspondant à `currentChapter` (ou déclencher un `scrollToItem` sans animation à l'ouverture du bottom sheet).

---

## 8. 🟡 Surlignage "mot par mot" — attention à ne pas vendre ce que ce n'est pas

Le surlignage au mot n'est **pas** une synchronisation réelle par timestamps du moteur TTS. C'est une interpolation temporelle basée sur la proportion de caractères (`ReaderContent.kt`, ligne 736-800) :

```kotlin
val fraction = (rawElapsed.toFloat() / durationMs).coerceIn(0f, 1f)
val targetCharCount = fraction * totalChars
// on avance mot par mot jusqu'à atteindre targetCharCount
```

Le mot actif progresse à une vitesse *linéaire dans le temps, proportionnelle à sa longueur en caractères* — pas à sa durée d'énonciation réelle. En français, ça décroche visiblement dans plusieurs cas fréquents :
- Ponctuation avec pause prosodique (virgule, point, tiret) — le calcul ignore les silences.
- Abréviations et liaisons ("M." prononcé "Monsieur", "les enfants" avec liaison) — la durée audio réelle du mot n'a aucun rapport avec son nombre de caractères écrits.
- Emphase ou intonation de fin de phrase — le débit n'est jamais parfaitement constant.

Ce n'est pas nécessairement un défaut rédhibitoire (beaucoup d'apps TTS grand public font une approximation similaire), mais **la documentation interne du projet (`PROJECT_STATUS.md`) présente ce point comme "surlignage synchronisé phrase par phrase" sans mentionner que le mot, lui, est simulé** — ça mérite d'être documenté honnêtement, ne serait-ce que pour éviter de sur-promettre en beta fermée à des lecteurs qui vont immédiatement remarquer le décalage sur les phrases longues ou ponctuées.

**Deux pistes, à ne pas confondre en termes d'effort :**
- *Amélioration légère (quelques heures)* : pondérer l'estimation par une approximation phonétique plutôt que par le nombre brut de caractères (compter les lettres muettes différemment, donner un poids fixe minimal à la ponctuation forte pour simuler une micro-pause). Ça reste une simulation mais réduit le décalage perçu.
- *Investissement plus lourd* : vérifier si le binding sherpa-onnx utilisé pour Piper expose un flux de timestamps par mot/phonème (à confirmer selon la version exacte du binding) ; à défaut, un alignement forcé léger post-synthèse (détection d'énergie/silences sur le signal audio déjà généré) donnerait une vraie synchro, au prix d'un vrai chantier technique. À ne pas mettre dans le même sprint que la beta.

---

## 8bis. 🔴 Import EPUB — cause racine de la lenteur perçue

Au-delà du traitement séquentiel de tous les chapitres (§5), il y a un problème algorithmique plus grave, découvert en creusant `BookRepositoryImpl.kt` :

```kotlin
// extractRawHtml() — appelé une fois par fichier spine de CHAQUE chapitre
ZipFile(epubFile).use { zip ->
    val entry = zip.entries().asSequence().find { it.name.endsWith(href) || it.name == href }
    ...
}

// extractAndSaveImage() — appelé une fois par image de CHAQUE chapitre
ZipFile(epubFile).use { zip ->
    val entry = zip.entries().asSequence().find { ... } ?: zip.entries().asSequence().find { ... }
    ...
}
```

L'archive ZIP de l'EPUB est **rouverte et son sommaire relu en scan linéaire** à chaque appel — une fois par fichier HTML de chapitre, et une seconde fois (avec jusqu'à deux scans de repli) par image rencontrée. Pour un livre de 40 chapitres et 30 images, ça représente potentiellement 70+ ouvertures de `ZipFile` et 100+ parcours linéaires du sommaire de l'archive, en pur gaspillage d'I/O, avant même le travail utile (parsing HTML, découpage en phrases).

**Correction (pas un patch)** : ouvrir le `ZipFile` **une seule fois** en tête de `importEpub()`, indexer ses entrées une seule fois dans une `Map<String, ZipEntry>`, et faire transiter cette référence dans `extractRawHtml()` / `extractAndSaveImage()` / `extractCoverHeuristic()` au lieu de rouvrir l'archive à chaque appel. Combiné à la parallélisation par chapitre déjà recommandée en §5, c'est le vrai levier pour diviser le temps d'import par un facteur significatif (5-10x plausible sur un livre illustré), pas une micro-optimisation.

---

## 8ter. 🔴 Adaptation à l'écran — absence structurelle, pas un détail cosmétique

Trois preuves vérifiées dans le code, qui pointent toutes vers la même cause : **aucune couche d'adaptation à la taille d'écran n'existe dans l'app**.

**a) Top bar du lecteur surchargée** (`ReaderTopBar.kt`, lignes 35-62) : retour + 4 `IconButton` (mode lecture, recherche, signets, TOC) = 5 cibles tactiles de 48dp minimum chacune (~240dp), dans une `Row` où le titre/sous-titre du livre doit se partager le reste de la largeur. Sur un écran étroit, le titre est quasi systématiquement tronqué (`maxLines = 1` + ellipsis). Incohérence supplémentaire : le fichier importe directement `Icons.Outlined.*` avec des `@Suppress("DEPRECATION")`, alors que l'app dispose déjà d'un point d'entrée centralisé `AppIcons.kt` (utilisé dans `ReaderContent.kt`, `SettingsScreen.kt`, `SyncSettingsScreen.kt`) — la tâche "uniformisation des icônes" d'un précédent audit n'a donc pas couvert ce fichier.

**b) Zéro détection de taille d'écran dans tout le dépôt** — recherche exhaustive de `WindowSizeClass`, `calculateWindowSizeClass`, `LocalConfiguration`, `screenWidthDp` : aucun résultat. Conséquences concrètes :
- La grille de bibliothèque est `GridCells.Fixed(3)` (`LibraryScreen.kt` ligne 468) — 3 colonnes fixes, identiques sur un petit téléphone et sur une tablette 10 pouces.
- La marge horizontale du texte en lecture (`horizontalMarginDp`, `ReaderViewModel.kt` ligne 52) est un réglage utilisateur borné 8-48dp, sans plafond de largeur de ligne — sur une tablette en paysage, ça produit des lignes de texte bien trop longues pour un confort de lecture (les apps de référence limitent la largeur de colonne de texte quelle que soit la largeur d'écran disponible).

**c) Pagination et rotation d'écran** (`PagedContent`, `ReaderContent.kt` lignes 178-260) — à noter en positif d'abord : la pagination du mode "PAGED" est un vrai travail sérieux, basé sur une mesure réelle du texte (`rememberTextMeasurer()`), pas une approximation par nombre de phrases fixe. Mais elle est intégralement recalculée à chaque changement de taille de conteneur (rotation, redimensionnement en multi-fenêtrage), et `rememberPagerState` conserve le même **numéro** de page après ce recalcul — pas le même **contenu**, puisque les limites de page changent avec la nouvelle mise en page. Résultat : tourner l'écran en cours de lecture peut faire atterrir l'utilisateur sur un passage différent de celui affiché avant rotation.

**Correction (pas un patch)** :
- Introduire `androidx.compose.material3.windowsizeclass` (ou l'équivalent adaptive actuel) comme couche transverse, pas comme correctif isolé par écran.
- Bibliothèque : `GridCells.Adaptive(minSize = ...)` au lieu de `Fixed(3)`.
- Lecture : plafonner la largeur effective du bloc de texte (ex. ~65-75 caractères par ligne) indépendamment de la largeur d'écran, quitte à centrer avec des marges plus généreuses sur grand écran.
- Top bar : ne garder que retour + un point d'entrée unique vers les actions secondaires (le `UnifiedControlPanel` existe déjà et est le bon endroit pour ça), plutôt que d'empiler les icônes.
- Pagination : ancrer la position sur un repère de contenu stable (index de phrase / offset caractère dans le chapitre) plutôt que sur un numéro de page brut, et re-résoudre la page correcte après un recalcul de pagination.

---

## 9. Points positifs vérifiés (pour ne pas fausser le tableau)

- Architecture DAO 100% asynchrone (`suspend`/`Flow`), aucun appel bloquant trouvé.
- `Application` légère, initialisation paresseuse du moteur TTS.
- Baseline profile réel + module de benchmark de démarrage dédié.
- Grille de bibliothèque : clés stables, chargement d'images asynchrone.
- Gestion d'erreurs d'import robuste et granulaire, avec nettoyage propre en cas d'échec.
- Callback de progression d'import fin (pas juste un spinner générique).
- Pagination du mode "PAGED" basée sur une vraie mesure de texte (`rememberTextMeasurer`), pas une approximation par nombre de phrases — travail d'ingénierie sérieux, même s'il reste un point de fragilité sur la rotation d'écran (§8ter).

---

## 10. Plan d'action consolidé

| Priorité | Chantier | Fichiers principaux | Effort estimé |
|---|---|---|---|
| 🔴 1 | Sauvegarder la position même sans TTS (scroll manuel) | `ReaderContent.kt`, `ReaderViewModel.kt` | Moyen |
| 🔴 2 | Afficher la position restaurée dès l'ouverture (avant tout Play) | `ReaderContent.kt` (source de `activeIdx`) | Faible |
| 🔴 3 | Corriger `startFrom = 0` en dur | `ReaderViewModel.kt` (fonction `play()`) | Faible |
| 🔴 4 | Pondérer le % de progression par longueur réelle de contenu | `BookRepositoryImpl.kt` (import), `CalculateReadingProgressUseCase.kt` | Moyen-élevé |
| 🟠 5 | Fusionner `progress` / `reading_progress` (migration Room) | `entity/`, `database/`, `InkToneDatabase.kt` | Moyen |
| 🟠 6 | Requête groupée pour la progression de la bibliothèque | `LibraryViewModel.kt`, `ProgressDao.kt` | Faible |
| 🟠 7 | API Splash Screen | `MainActivity.kt`, `build.gradle.kts` | Faible |
| 🟠 8 | Paralléliser le parsing des chapitres à l'import | `BookRepositoryImpl.kt` | Moyen-élevé |
| 🔴 8bis | Ouvrir le ZIP une seule fois à l'import (au lieu d'une réouverture + scan linéaire par chapitre/image) | `BookRepositoryImpl.kt` | Moyen |
| 🔴 8ter | Couche d'adaptation d'écran transverse (WindowSizeClass, grille adaptative, largeur de texte plafonnée, top bar allégée) | `ReaderTopBar.kt`, `LibraryScreen.kt`, `ReaderContent.kt`, `ReaderViewModel.kt` | Élevé |
| 🟡 9 | TOC en `LazyColumn` + scroll vers le chapitre courant | `ReaderTopBar.kt` (`ChapterPicker`) | Faible |
| 🟡 10 | Documenter honnêtement le surlignage mot-à-mot comme approximation ; améliorer la pondération | `architecture.md`, `ReaderContent.kt` | Faible (doc) / Moyen (algo) |
| 🟡 11 | Tests de non-régression : reprise après lecture silencieuse | `test/` | Moyen |
| 🟡 12 | Ancrer la pagination sur un repère de contenu stable pour survivre à une rotation d'écran | `ReaderContent.kt` (`PagedContent`) | Moyen |
| 🔴 13 | Retirer `MANAGE_EXTERNAL_STORAGE` et l'écran `FilesScreen.kt` associé (risque de rejet Play Store) | `AndroidManifest.xml`, `FilesScreen.kt` | Faible |
| 🔴 14 | Intégrer un SDK de crash reporting (Crashlytics ou équivalent) avant toute beta | `build.gradle.kts`, `InkToneApplication.kt` | Faible |
| 🟠 15 | Premier vrai test d'intégration UI (au moins le parcours reprise de lecture, §2) | `androidTest/` | Moyen |
| 🟡 16 | Message d'erreur explicite pour les EPUB protégés par DRM | `BookRepositoryImpl.kt` (`importEpub`) | Faible |
| 🟡 17 | Synchronisation automatique en arrière-plan (au lieu de manuelle uniquement) | `SyncManager.kt`, ajout `WorkManager` | Moyen-élevé |

**Recommandation de séquencement** : traiter 1→4 comme un seul chantier cohérent (ils touchent le même code et se valident ensemble avec le test du point 11), avant tout ce qui est cosmétique. C'est ce qui te fera passer d'une app "qui a l'air prête" à une app réellement fiable pour une beta — le reste (5 à 10) peut suivre en parallèle sans bloquer.

---

## 11. Comment je recommande de valider après correction

Avant de considérer ce chantier clos, un scénario de test manuel simple et systématique :
1. Ouvrir un livre, scroller manuellement (sans jamais presser Play) sur ~5 chapitres, fermer l'app par balayage (pas juste mise en arrière-plan).
2. Rouvrir l'app → le livre doit se rouvrir exactement à l'endroit scrollé, sans action supplémentaire.
3. Presser Play → la narration doit démarrer à la phrase affichée, pas au début du chapitre.
4. Vérifier le % affiché sur la jaquette avant/après sur un livre à chapitres très inégaux (ex. import d'un livre avec une préface d'une page et un chapitre de 50 pages) — le saut de % doit être proportionnel au contenu, pas au chapitre.

Si ces quatre points passent, le socle "reprise de lecture" est enfin fiable — et c'est probablement le signal le plus fort que tu pourras montrer à tes premiers testeurs beta.

---

## 12bis-avant. Revue systématique complémentaire — au-delà de la fiabilité fonctionnelle

La section précédente (chantiers §2-3, §8bis, §8ter) couvre la **fiabilité fonctionnelle** : est-ce que l'app fait ce qu'elle promet. Mais "top-tier" se joue sur deux couches supplémentaires, vérifiées ici systématiquement (conformité store, exploitation post-lancement, robustesse aux cas réels, tenue à l'échelle) plutôt qu'au hasard des découvertes.

### 🔴 A. `MANAGE_EXTERNAL_STORAGE` toujours actif — risque de rejet Play Store

`AndroidManifest.xml` déclare toujours `android.permission.MANAGE_EXTERNAL_STORAGE`, et ce n'est pas un reliquat mort : `FilesScreen.kt` (atteignable depuis `LibraryScreen.kt`) construit un écran dédié qui affiche *"Pour parcourir vos fichiers EPUB, Android nécessite une autorisation"* avec un bouton ouvrant `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`. Google Play exige une déclaration spéciale pour cette permission, réservée en pratique aux gestionnaires de fichiers/antivirus/outils de sauvegarde système — une app de lecture EPUB n'a pas de justification recevable, et le risque de rejet ou de suspension au dépôt est concret. Le SAF (déjà utilisé par ailleurs dans l'app pour l'import) couvre le même besoin sans cette permission. **Correction : supprimer entièrement `FilesScreen.kt` et la permission associée du manifeste**, pas juste ne plus l'appeler.

### 🔴 B. Aucune télémétrie de crash

Recherche exhaustive (Crashlytics, Sentry, Bugsnag, ACRA) dans `build.gradle.kts` et tout le code source : aucun résultat. En beta, un crash chez un testeur ne remonte rien d'exploitable sans un signalement manuel. Pour un solo dev, c'est l'outil de base pour piloter une beta plutôt que la subir.

### 🟠 C. Le seul test UI existant est un test vide

`app/src/androidTest/java/com/inktone/ui/screen/reader/ReaderScreenTest.kt` — unique test instrumenté du dépôt — a un corps de `setContent {}` littéralement vide, avec le commentaire *"placeholder for future UI test infrastructure"*. Les 111 tests unitaires valident chaque brique isolément (bon travail), mais rien ne teste leur câblage entre elles — c'est structurellement pour ça que le bug de progression du §2 a pu passer inaperçu : chaque use case était juste pris séparément.

### 🟡 D. Synchronisation manuelle, pas automatique

`SyncManager.kt` est plus solide qu'attendu (chiffrement via `CryptoEngine.kt`, deux backends `GoogleDriveClient.kt`/`WebDavClient.kt`, fusion via `mergePayload()`) mais déclenché uniquement par bouton (`SyncSettingsViewModel.backup()`/`restore()`) — pas de synchro automatique en arrière-plan (`WorkManager` absent) à l'ouverture/fermeture d'un livre, contrairement à Whispersync/Kobo/Apple Books.

### 🟡 E. Aucune détection des EPUB protégés par DRM

Un livre emprunté avec DRM Adobe (cas fréquent en médiathèque) tombera sur le message générique *"Fichier EPUB corrompu ou illisible"* de `importEpub()` — trompeur pour un cas courant, pas un cas limite exotique.

### 🟡 F. Requêtes bibliothèque non paginées

`BookDao.getAllBooks()` (`SELECT * FROM books ORDER BY addedAt DESC`) n'a pas de `LIMIT`. Sans impact visible aujourd'hui, mais combiné à la requête N+1 du §6, ne tiendra pas une bibliothèque de plusieurs milliers de livres.

### Grille à trois couches

| Couche | Ce qu'elle couvre | État |
|---|---|---|
| 1. Fiabilité fonctionnelle | Progression de lecture (§2-3), vitesse d'import (§8bis), adaptation d'écran (§8ter) | 🔴 Cassée, plan d'action donné |
| 2. Conformité & exploitation | Permission `MANAGE_EXTERNAL_STORAGE` (§A), absence de crash reporting (§B) | 🔴 Bloquant pour le dépôt store / le pilotage de la beta |
| 3. Solidité aux cas réels & à l'échelle | Tests d'intégration (§C), sync auto (§D), DRM (§E), pagination (§F) | 🟡 Fonctionne en usage normal, fragile sur les cas limites qu'une vraie beta rencontrera |

**Limite honnête de cet audit** : une revue de code peut détecter ce qui est structurellement absent ou mal câblé — ce qui a été fait ici, deux fois, avec méthode. Elle ne peut pas garantir le comportement sur la diversité réelle des appareils Android, sur un corpus de vrais EPUB mal formés, ou sous une charge réelle de plusieurs heures de TTS. C'est précisément pourquoi la couche 2 (crash reporting en particulier) n'est pas optionnelle : sans elle, la beta ne fait que déplacer le problème au lieu de le révéler.

---

## 12. Réponse honnête : est-ce que ça suffit pour rivaliser avec le top 3 ?

**Non, pas avec le seul chantier 1-4 (progression de lecture).** Ce chantier fait passer InkTone de "l'app ne tient pas sa promesse de base" à "l'app fait ce qu'elle annonce" — c'est un plancher de fiabilité indispensable, pas un plafond de qualité. Le top 3 du marché (Kindle, Apple Books, Kobo, ou Moon+ Reader Pro pour la comparaison la plus pertinente vu ton positionnement) se joue sur des couches que l'audit initial ne couvrait pas :

| Dimension | État actuel InkTone | Niveau top 3 |
|---|---|---|
| Fiabilité de la position de lecture | 🔴 Cassée (chantier §2-3) | Table basse, non négociable |
| Vitesse d'import | 🔴 Pénalisée par des réouvertures ZIP redondantes (§8bis) | Import quasi instantané même sur gros livre illustré |
| Adaptation à la taille d'écran | 🔴 Absente à 100% (§8ter) | Standard depuis des années (téléphone/tablette/paysage) |
| Pagination | 🟢 Mesure réelle du texte, fragile seulement sur rotation | Comparable, à la robustesse sur rotation près |
| Surlignage synchronisé | 🟡 Simulé par interpolation de caractères, pas de vrais timestamps | Certains concurrents ont le même problème avec TTS tiers ; peu ont un vrai alignement mot-à-mot |
| Stabilité à l'échelle (grosses bibliothèques, gros livres, changements de config) | ⚪ Non testée, aucun test d'UI/config change trouvé | Testée en continu, des années de retours utilisateurs |
| Richesse fonctionnelle (sync, annotations, stats, recherche) | 🟢 Base solide et déjà assez complète pour un solo dev | Plus profonde mais pas hors de portée |

Ce que je peux dire avec confiance : en traitant sérieusement §2-3 (progression), §8bis (import) et §8ter (adaptation d'écran) — sans bricolage, comme demandé — InkTone atteindrait un niveau de **fiabilité et de cohérence** qui le rendrait crédible pour une beta fermée, et probablement au-dessus de la moyenne des lecteurs EPUB indépendants sur le Play Store. Mais "top 3" implique aussi des années de rodage sur la diversité des appareils et des formats EPUB réels (les EPUB "sauvages" sont souvent mal formés), ce qu'aucun audit de code ne peut garantir — seul un vrai programme de beta avec des lecteurs qui utilisent l'app au quotidien, sur des livres variés, peut le révéler. C'est d'ailleurs exactement pour ça qu'une beta fermée a du sens comme prochaine étape, une fois §2-3, §8bis et §8ter traités : pas pour "finir", mais pour commencer à mesurer contre la réalité plutôt qu'à l'aveugle.
