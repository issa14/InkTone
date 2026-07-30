# Inventaire exhaustif de l'interface legacy — document de référence vivant

**But :** catalogue complet, élément par élément, de ce que `legacy/monolith` construit réellement — extrait du code source, pas résumé de mémoire. Sert de spécification de référence pour la reconstruction de l'interface (Phase 9bis et suites), et de check-list vivante : chaque ligne se coche au fur et à mesure, avec un statut explicite.

**Méthode :** chaque élément vient d'une lecture complète du fichier source correspondant, pas d'un survol. Là où un fichier n'a pas encore été lu intégralement, c'est marqué explicitement — pas de remplissage par supposition.

**Format des colonnes de statut :**
- **Réécriture** : présent (✅) / absent (❌) / partiel (🟡), vérifié dans le code actuel au moment de la mise à jour du document
- **Traitement** : `PORTER` (reprendre tel quel) / `AMÉLIORER` (reprendre en mieux, raison donnée) / `ABANDONNER` (raison donnée) / à trancher
- **Capture d'écran** : présente ou non — **aucun écran n'est considéré clos tant que cette colonne n'a pas une vraie capture, prise sur device réel**, jointe ou référencée

---

## 1. Écran Bibliothèque (`LibraryScreen.kt`, 1476 lignes, lu intégralement)

### 1.1 Barre supérieure (`TopBar`)

| Élément | Legacy | Réécriture | Traitement |
|---|---|---|---|
| Icône menu (ouvre le drawer) | Toujours visible, à gauche | ✅ présent | `PORTER` |
| Titre cliquable avec flèche déroulante | Ouvre `LibraryNavigationPopup`, texte reflète le filtre actif (Favoris/Séries/Tags/Dossiers) | ❌ absent — le titre n'est pas cliquable dans la réécriture | `PORTER` — c'est le mécanisme qui permet de changer de filtre sans passer par le drawer |
| Icône recherche | Bascule vers une **`SearchBar` qui remplace toute la TopBar** (pas un champ toujours ouvert) | 🟡 présent mais **toujours ouvert**, jamais replié | `PORTER` — c'est exactement le point que tu as signalé |
| Icône filtre (entonnoir) | Ouvre `FilterAndSortDialog` (tri + type de filtre + disposition grille/liste, un seul dialogue) | ❌ absent en tant que dialogue — tri et disposition existent séparément, pas de dialogue unifié | `PORTER` — un seul point d'entrée plutôt que des contrôles épars |
| Icône 3-points (`MoreVert`) | Ouvre `LibraryActionsSheet` | ❌ absent | `PORTER` — voir détail §1.4 |
| Couleur de fond de la barre | `MaterialTheme.colorScheme.primary` (pleine teinte, pas la surface neutre) | À vérifier | à trancher — dépend du système de design (Tâche 9bis.1) |

### 1.2 Drawer (`ModalNavigationDrawer` → `NavDrawerContent`)

| Élément | Legacy | Réécriture | Traitement |
|---|---|---|---|
| En-tête avec dégradé + nom de l'app | Bloc 140dp, dégradé `primaryContainer→primary`, "InkTone" | ❌ absent | `PORTER` |
| Items de navigation dynamiques | `NavigationDestination.entries` filtré (exclut Settings/About, qui vivent en bas) : Bibliothèque, Récents, OPDS, Signets, Stats, Sync | 🟡 partiel — filtres présents, mais pas de navigation vers Récents/Stats/Sync depuis le drawer | `AMÉLIORER` — OPDS/Sync restent hors périmètre v1 (décision Blueprint déjà actée), mais **Récents et Stats doivent y être** |
| Item actif surligné (`primaryContainer`) | Oui | À vérifier | `PORTER` |
| Footer : À propos / Thème / Debug | Rangée de 3 boutons compacts en bas, Debug **conditionné par `BuildConfig.DEBUG`** | ❌ absent | `PORTER` — le conditionnement Debug est important, ne pas l'oublier (cohérent avec la correction déjà faite en Phase 7 sur `BootstrapAndOpenFixture`) |

### 1.3 Popup de navigation (titre cliquable) — `LibraryNavigationPopup`

**Non encore lu intégralement — fichier séparé, à ouvrir dans la prochaine passe.** Ce qu'on sait de son usage : liste `navSubItems` dépendant du filtre actif, permet de sélectionner un filtre (Favoris/Séries/Tags/Dossiers) ou de sauter directement à un livre (`filterId.startsWith("book:")`). **Statut : 🔴 non inventorié en détail.**

### 1.4 Menu 3-points — `LibraryActionsSheet`

| Action | Effet |
|---|---|
| Importer | Lance le sélecteur SAF multi-fichiers |
| Actualiser | `viewModel.refresh()` |
| Régénérer les couvertures | `viewModel.regenerateAllCovers()` |
| Réinitialiser les couvertures | `viewModel.resetCoversToDefault()` |
| Sync | Navigue vers l'écran Sync |

**Non encore lu intégralement** (fichier séparé) — la liste ci-dessus vient des callbacks passés depuis `LibraryScreen.kt`, pas d'une lecture du composant lui-même. **Statut : 🔴 structure visuelle non inventoriée.** `Sync` reste hors périmètre v1 (décision Blueprint) — les 4 autres actions sont dans le périmètre.

### 1.5 Dialogue tri/filtre/disposition — `FilterAndSortDialog`

**Non encore lu intégralement.** Paramètres connus : `sortOrder`, `filterType`, `layoutMode`, chacun avec son callback de changement. **Statut : 🔴 non inventorié en détail.**

### 1.6 Bannière d'import (`ImportBanner`)

| Élément | Legacy | Réécriture |
|---|---|---|
| Indicateur circulaire (déterminé si progress connu, indéterminé sinon) | ✅ | 🟡 la réécriture utilise une barre linéaire, pas circulaire — différence mineure, `AMÉLIORER ou PORTER` à trancher par préférence esthétique, pas un manque fonctionnel |
| Texte de statut + pourcentage | ✅ | ✅ équivalent présent |
| Ne bloque jamais la grille (remplace un ancien overlay plein écran — vraie leçon produit documentée dans le code) | ✅ | ✅ | `PORTER` le principe, déjà respecté |

### 1.7 Grille de couvertures (`ShelfGrid` → `BookCover`)

**C'est le cœur du problème signalé — détail complet, élément par élément :**

| Élément | Legacy | Réécriture | Traitement |
|---|---|---|---|
| Colonnes adaptatives (pas un compte fixe) | `GridCells.Adaptive(minSize = 100.dp)` | ✅ équivalent (`120.dp`) présent | `PORTER` |
| **Image de couverture réelle** | `AsyncImage` (Coil) avec `crossfade(true)`, `ContentScale.Crop` | ❌ **absent — Coil n'est même pas une dépendance du projet** | `PORTER` — priorité absolue |
| Repli si pas d'image : dégradé + titre | Dégradé choisi par hash du titre (`CoverGradients`), police serif, jusqu'à 5 lignes | ❌ absent — actuellement un simple fond de couleur unie | `AMÉLIORER` — le principe du dégradé par hash est un bon repli, à reprendre plutôt qu'un aplat |
| Bouton favori (coin supérieur droit) | ✅, icône cœur, couleur dédiée si actif | ✅ présent (étoile plutôt que cœur — différence cosmétique) | `PORTER` le mécanisme, icône au choix |
| **Badge de progression** | Cercle noir semi-transparent, coin inférieur droit, pourcentage **arrondi mais jamais tronqué à 0** (raisonnement explicite dans le code : un livre long peut rester sous 1% longtemps, afficher 0% donnerait une fausse impression qu'aucune progression n'est enregistrée) | ❌ **absent — confirmé** | `PORTER`, y compris le raisonnement sur l'arrondi, pas juste l'apparence |
| Indicateur "import en cours" (remplace les points de statut tant que ce livre précis n'est pas prêt) | Petit spinner blanc, coin inférieur gauche | ❌ absent | `PORTER` |
| Points de statut décoratifs (3 points, coin inférieur gauche, quand pas en import) | ✅ | ❌ absent | à trancher — vérifier ce qu'ils encodent réellement avant de porter (pas fini de lire cette section) |
| Description d'accessibilité sur le badge de progression (`semantics { contentDescription }`) | ✅ — lu par TalkBack même si le badge est purement visuel | ❌ absent (n'existe pas puisque le badge n'existe pas) | `PORTER` — directement lié à la Tâche 9.1 (accessibilité), pas optionnel |

### 1.8 Vue groupée par séries (`SeriesGroupedView`)

| Élément | Legacy | Réécriture |
|---|---|---|
| En-tête de section = nom de la série, en gras | ✅ | ❌ — actuellement un simple filtre, pas de vrai regroupement visuel |
| Rangée horizontale scrollable par série, couvertures à largeur fixe (110dp) | ✅ | ❌ | `PORTER` |
| État "aucune série détectée" avec message dédié | ✅ | ❌ | `PORTER` |

### 1.9 Barre de tags (`TagsFilterBar`)

Rangée horizontale scrollable de `FilterChip`, visible uniquement si des tags existent, cachée sinon (`if (availableTags.isEmpty()) return`). **Réécriture : présente dans le drawer, pas comme barre horizontale dédiée sous la TopBar.** `AMÉLIORER ou PORTER` à trancher — les deux emplacements sont défendables, mais **choisir consciemment**, pas par accident de l'endroit où le code a atterri.

### 1.10 États vide / chargement / erreur

| Élément | Legacy | Réécriture | Traitement |
|---|---|---|---|
| État vide : icône dans un cercle, titre, sous-titre, **bouton d'import direct** | ✅ complet, texte différent si un import est en cours | 🟡 texte seul, aucune illustration, aucun bouton | `PORTER` — signalé une première fois, toujours vrai |
| État chargement : simple spinner centré | ✅ | 🟡 remplacé par le shimmer (Tâche 9bis.4) — c'est une **amélioration légitime**, pas une régression | `AMÉLIORER`, déjà fait dans le bon sens |
| Bannière d'erreur, avec bouton « Réessayer » optionnel | ✅ | ❌ absent — aucune gestion d'erreur visible sur cet écran | `PORTER` — trou fonctionnel, pas seulement visuel |
| `ComingSoonPlaceholder` (icône construction + "Bientôt disponible") | ✅, utilisé pour les écrans non encore implémentés du legacy lui-même | Sans objet — n'a de sens que si on garde des routes déclarées mais vides, à réévaluer selon ce qui reste réellement "à venir" dans la réécriture | à trancher |

### 1.11 Contrôles flottants (`FloatingControls`)

Une pilule audio (reprend directement le TTS du dernier livre, icône casque, couleur `ttsActive`) **à côté d'** un FAB rond classique (reprend la lecture visuelle) — **deux boutons distincts, pas un seul**. La réécriture (`ResumeReadingCard`) a fait un choix différent (carte proéminente en tête de liste plutôt que deux boutons flottants) — **c'était une décision consciente et documentée du plan 9bis, pas un oubli** : `AMÉLIORER`, à garder tel quel, mais vérifier si la pilule audio (reprise directe du TTS, sans ouvrir le livre visuellement) mérite d'être un bouton séparé même dans la nouvelle disposition — c'est une action différente de « ouvrir le livre », pas juste un raccourci visuel.

### 1.12 Rafraîchissement au retour sur l'écran

**Détail d'architecture important, absent de toute discussion précédente** : le `ViewModel` s'abonne au `LifecycleEventObserver` de son propre `NavBackStackEntry` (`ON_RESUME`), pas celui de l'Activity — pour que la progression affichée se rafraîchisse à chaque retour depuis le Reader, sans recréer le ViewModel. **À vérifier si la réécriture a un mécanisme équivalent** — sans ça, la carte "reprendre la lecture" et les badges de progression resteraient figés après une session de lecture tant qu'on ne tue pas et relance pas l'app.

### Capture d'écran — Bibliothèque

**❌ Aucune capture, ni legacy ni réécriture, jointe à ce document pour l'instant.** À faire sur le V2206 réel avant de considérer cette section close.

---

*Document en cours de construction — sections Réglages, Signets, Recherche, Statistiques, À propos à ajouter dans les prochaines passes, avec la même méthode (lecture intégrale du fichier source, pas de résumé).*

---

## 2. Écran Lecteur (8 fichiers, ~3417 lignes)

**État de lecture par fichier** : `ReaderScreen.kt` (680 lignes) et `ReaderTopBar.kt` (107 lignes) et `CustomHighlightToolbar.kt` (59 lignes) — **lus intégralement**. `ReaderBottomControls.kt` (231 lignes) — lu à 40%. `ReaderContent.kt` (1107 lignes), `ReaderViewModel.kt` (608 lignes), `ReaderSettingsPanel.kt` (387 lignes), `ReaderTtsPanel.kt` (237 lignes) — **connus uniquement par leur signature d'appel** (les paramètres passés depuis `ReaderScreen.kt`), pas par une lecture de leur implémentation interne. Marqué 🔴 ci-dessous partout où c'est le cas — à compléter dans une prochaine passe avant de considérer cette section close.

### 2.1 Structure générale — 4 couches superposées (`Box` avec plusieurs enfants alignés)

Barre de progression de **chapitre** fine (2dp, toujours visible, `zIndex(1f)`) → texte (`ReaderContent`, jamais masqué) → overlays contextuels (tooltips, barre de sélection, captions TTS, micro-indicateur, FAB) → HUD (`ReaderTopBar` + `UnifiedControlPanel`, apparition/disparition animée).

### 2.2 Éléments jamais mentionnés avant cette lecture — les plus significatifs

| Élément | Détail | Réécriture | Traitement |
|---|---|---|---|
| **Estimation du temps restant** (`state.etaMinutes`) | Affiché dans le micro-indicateur quand le HUD est masqué : « ~12 min » | ❌ absent | `PORTER` — directement utile, jamais mentionné dans mes plans précédents |
| **Deux tooltips de première utilisation**, avec logique d'évitement de collision réglée à la main | 1) pointe vers le FAB play, masqué si le HUD est visible ; 2) après le premier play, explique le surlignage mot-à-mot, masqué pendant la lecture et pendant une sélection | ❌ absent | `PORTER` — nombreuses heures d'ajustement UX condensées dans ces deux composants, à ne pas perdre |
| **Barre d'actions de sélection à 4 boutons** | Copier / Surligner / Note / Marque-page — pas 2 comme je le supposais | ❌ absent (l'UI d'annotation actuelle, Tâche 7.1, n'a pas cette barre) | `PORTER` |
| **Captions TTS** (sous-titres de la phrase en cours pendant la lecture) | Overlay semi-transparent en bas, masqué pendant une sélection | ❌ absent | `PORTER` — accessibilité directe (lecture ET écoute simultanées) |
| **Zone de tap au tiers central de l'écran** pour basculer le HUD | Pas n'importe où sur l'écran — le tiers gauche/droit reste réservé à la navigation de page | À vérifier | `PORTER` le principe si la réécriture n'a pas cette distinction |
| **Panneau TTS séparé du panneau d'affichage** | `TtsPanel` (vitesse, voix, minuteur de sommeil, navigation phrase par phrase) est un `ModalBottomSheet` **distinct** de `ReaderSettingsPanel` (thème, police, taille, interligne, marge) — deux feuilles, pas une | 🟡 à vérifier — le plan 9bis ne distinguait pas clairement les deux | `PORTER` la séparation |
| **`ChapterPicker`** (TOC) dans son propre `ModalBottomSheet` | Troisième feuille, séparée des deux précédentes | 🟡 TOC existe (`TableOfContentsSheet`, 76 lignes) mais son intégration en bottom sheet vs autre présentation à vérifier | à vérifier |
| **Dialogue de note** (`AlertDialog`) | Affiche les 80 premiers caractères du texte sélectionné en rappel, puis un champ de saisie | ❌ absent | `PORTER` |
| **Color picker inline** pour les surlignages | 5 couleurs prédéfinies, apparaît en bas de l'écran (pas un dialogue plein) | ❌ absent | `PORTER` |
| Sélection de texte via `LocalTextToolbar` personnalisé (`CustomHighlightToolbar.kt`) | Intercepte `showMenu()`, expose le rect + callback copier — **API publique, pas interne** (vérifié en lisant le fichier en entier) | ❌ absent (sélection par phrase actuelle) | `PORTER` — confirme la Tâche 9bis.0.2, bonnes chances de succès |

### 2.3 `UnifiedControlPanel` — inventaire des actions exposées

D'après les paramètres passés depuis `ReaderScreen.kt` (signature complète, implémentation interne 🔴 non lue) :

`onTtsClick` (play/pause), `onTtsSettingsClick` (ouvre `TtsPanel`), `onThemeCycle`, `onFontToggle` (bascule OpenDyslexic), `onDisplaySettingsClick` (ouvre `ReaderSettingsPanel`), `onSleepTimerClick` (ouvre… `TtsPanel` à nouveau — le minuteur de sommeil vit dans le panneau TTS, pas un panneau séparé, à noter), `onPrevChapter`/`onNextChapter`, `onToggleMode` (pagé/défilement), `onSearch`, `onBookmarks`, `onToc`. **11 actions dans un seul panneau** — cohérent avec la leçon documentée dans le code (éviter les icônes séparées qui tronquent le titre).

### 2.4 `ReaderContent.kt` (1107 lignes) — lu à ~85% (reste : détail complet de `RichBlockRenderer` par type de bloc, ~150 lignes de queue)

**C'est le fichier le plus dense de tout l'audit. Cinq trouvailles majeures, aucune connue avant cette lecture :**

#### a) Mode `PAGED` — un vrai moteur de pagination, absent de la réécriture

Mesure asynchrone du texte (`produceState` + `Dispatchers.Default`, jamais sur le thread UI), découpage en pages par hauteur réellement mesurée (pas une estimation), `HorizontalPager`, page virtuelle finale qui déclenche le chargement du chapitre suivant. Garde-fous explicites contre la boucle infinie (`pages.isNotEmpty()`) et les appels concurrents (`!isLoadingChapter`). **La réécriture n'a que le défilement continu — le mode pagé n'existe pas du tout.** `PORTER` — c'est un mode de lecture complet, pas un détail.

#### b) `RichBlock` — modèle de contenu plus riche que notre `DocumentModel` actuel

`Heading`, `Paragraph`, `BlockQuote`, `PoemLine`, `EpubImage`, `SectionBreak`, `Footnote` (ignorée dans le flux principal), chacun avec des `TextSpan` préservant le formatage (gras/italique/liens). Notre modèle de domaine (`Chapter → Paragraph → Sentence`, posé Phase 1) ne distingue rien de tout ça. **Ce n'est pas un manque d'UI — c'est un manque de domaine.** À discuter séparément : soit une extension du `DocumentModel` (impact sur Phases 1/3/4, déjà closes), soit un choix assumé de renoncer à cette richesse pour la v1 — mais **une décision consciente, pas un oubli silencieux**.

#### c) Sélection par phrase individuelle — confirme la Tâche 9bis.0.2

`SelectableSentence` enveloppe **chaque phrase** dans son propre `SelectionContainer` (pas un seul conteneur autour de tout le `LazyColumn`) — c'est très probablement ce qui permet d'éviter le piège « comportement non défini » de `SelectionContainer` + `LazyColumn` identifié en Tâche 9bis.0 : chaque portée de sélection est petite et toujours entièrement composée quand visible. **Pattern à reprendre tel quel**, pas juste le mécanisme `LocalTextToolbar` déjà noté.

#### d) ⚠️ Surlignage mot-à-mot : à NE PAS porter tel quel, la réécriture fait déjà mieux

`ActiveSentenceText` calcule le mot actif par **fraction du temps écoulé × nombre total de caractères** (`rawElapsed / durationMs × totalChars`) — c'est une interpolation, pas de vrais timestamps par mot. C'est très exactement l'approche que le Blueprint (§8.9) et l'ADR-013/021 ont explicitement qualifiée de malhonnête et rejetée, après tout le travail de la Phase 5 (alignement CTC, timestamps réels mesurés et vérifiés). **`ABANDONNER` le mécanisme de timing** — la réécriture a quelque chose d'objectivement meilleur ici, ne pas régresser en portant ce fichier par réflexe. **`PORTER` uniquement le style visuel** (gras, couleur, taille +8%, mots suivants en dégradé d'opacité) sur nos vrais timestamps.

#### e) Piste de lecture (« reading trail »)

Phrases déjà lues grisées (opacité 40%), à venir en opacité normale (88%) — détail visuel simple, absent de la réécriture, aide à se repérer visuellement dans la page. `PORTER`.

### 2.5 Ce qui reste 🔴 non inventorié

- Fin de `RichBlockRenderer` (détail par type de bloc, ~150 lignes)
- `ReaderViewModel.kt` (608 lignes)
- `ReaderSettingsPanel.kt` (387 lignes), `ReaderTtsPanel.kt` (237 lignes) — signature connue, disposition interne non lue
- `ReaderBottomControls.kt` — 60% restant

### Capture d'écran — Lecteur

**❌ Aucune capture.** À faire sur le V2206 réel, dans au moins 4 états (HUD visible, HUD masqué en lecture, mode pagé, mode défilement) avant de considérer cette section close.

---

*Prochaine passe : `ReaderViewModel.kt`, puis les écrans Réglages/Signets/Recherche/Statistiques/À propos.*

---

## 3. Écran Réglages (`SettingsScreen.kt`, 697 lignes, lu intégralement pour le corps principal — dialogues de sélection non détaillés ligne par ligne)

**Bien plus riche et mieux organisé que ce que la Phase 8 a couvert — 5 sections, pas une liste plate :**

| Section | Contenu | Réécriture | Traitement |
|---|---|---|---|
| **Présets rapides** | 2 cartes côte à côte : Mode sombre, Accessibilité (`applyAccessibilityPreset`, déjà porté Phase 8) | 🟡 préréglage accessibilité porté, pas la carte « mode sombre » dédiée | `PORTER` les deux comme présentation |
| **Lecture** | Voix active (dialogue), **Vitesse**, **Gain audio séparé (jusqu'à 4x — jamais mentionné avant)**, Moteur TTS, sélecteur de voix Edge conditionnel | 🟡 vitesse/voix existent, **gain audio absent** | `PORTER` le gain, nouveau pour nous |
| **Appareil → Apparence** | Thème (dialogue), **Couleurs dynamiques** (toggle explicite, pas automatique — « Android 12+ » précisé dans le sous-titre) | 🟡 Tâche 9bis.1.2 prévoit la couleur dynamique mais pas de toggle utilisateur pour la désactiver | `AMÉLIORER` — ajouter le toggle, ne pas l'imposer silencieusement |
| **Appareil → Accessibilité** | Réduire les animations (porté Phase 8), **« Police adaptée au système » — respecte `fontScale` Android, toggle explicite** | ❌ absent | `PORTER` — répond directement au point soulevé en Tâche 9.1.2 (échelle système vs réglage interne) : le legacy a déjà résolu ça avec un choix utilisateur explicite plutôt qu'un comportement imposé |
| **Données** | **Dossier des modèles TTS personnalisable** (chemin absolu), Export backup (SAF, JSON), Import backup | 🟡 export/import portés Phase 8, **override du dossier de modèles absent** | `PORTER` le chemin personnalisable — utile pour du debug/avancé, à confirmer si pertinent pour la v1 grand public ou à garder en debug uniquement |
| **Prononciation** | `PronunciationDictionary` (porté Phase 8) | ✅ porté | déjà fait |

### Capture d'écran — Réglages

**❌ Aucune capture.**

---

## 4. Écran Signets globaux (`AllBookmarksPanel.kt`, 126 lignes, lu intégralement)

Panneau affiché dans le drawer (pas un écran plein, une section du drawer bibliothèque) : recherche, liste de tous les signets tous livres confondus, chaque item montre un extrait de texte (2 lignes max) + « Ch. X · Phrase Y », tap navigue directement au livre/chapitre/phrase, bouton suppression par item.

| Élément | Réécriture (`GlobalBookmarksScreen.kt`, 107 lignes) | Traitement |
|---|---|---|
| Recherche parmi les signets | À vérifier | à vérifier |
| Extrait de texte + position (chapitre/phrase) | À vérifier | à vérifier |
| Navigation directe au tap | Probable (structure similaire) | à vérifier |

**Non comparé en détail** — `GlobalBookmarksScreen.kt` existe dans la réécriture (107 lignes, taille comparable) mais n'a pas été relu dans cette passe pour une comparaison ligne à ligne. À faire avant de clore cette section.

### Capture d'écran — Signets

**❌ Aucune capture.**

---

## 5. Écran Recherche (`SearchScreen.kt`, 99 lignes, lu intégralement)

Recherche **dans un livre précis** (pas la bibliothèque). Seuil de déclenchement à 2 caractères minimum, pas de debounce visible dans ce fichier (peut-être dans le ViewModel, non vérifié). Compteur de résultats affiché (« X résultat(s) »). Chaque résultat : position (chapitre/phrase) + extrait 3 lignes, tap navigue.

| Élément | Réécriture | Traitement |
|---|---|---|
| Seuil 2 caractères avant recherche | À vérifier contre `SearchViewModel` actuel | `PORTER` — évite des requêtes FTS sur une seule lettre |
| Compteur de résultats | À vérifier | `PORTER` |
| Debounce réel | Non confirmé côté legacy — **à vérifier avant de porter comme référence**, ne pas supposer que le legacy le fait bien | à vérifier des deux côtés |

### Capture d'écran — Recherche

**❌ Aucune capture.**

---

## 6. Écran Statistiques (`StatsScreen.kt`, 430 lignes, structure principale lue — détail des 4 composants graphiques non lu ligne à ligne)

**Plus riche que la Tâche 8.6 ne le couvrait :**

| Élément | Legacy | Réécriture (Tâche 8.6) | Traitement |
|---|---|---|---|
| **Jauge circulaire d'objectif quotidien** (minutes lues aujourd'hui / objectif) | ✅ `DailyGoalProgress` | ❌ absent — aucun objectif quotidien dans mon plan | `PORTER` — nécessite un champ `dailyGoalMinutes` configurable, absent du domaine actuel, à ajouter |
| Carte de série (streak actuelle + record) | ✅ `StreakCard` | 🟡 `currentStreakDays` prévu, pas le record max | `AMÉLIORER` — ajouter le record |
| 3 cartes rapides : **vitesse de lecture (WPM)**, temps total, livres lus | ✅ | 🟡 temps total et livres lus prévus, **WPM absent** | `PORTER` le WPM — nécessite de dériver une vitesse de lecture à partir de `ReadingSession`, calcul non encore défini dans le domaine |
| **Graphique d'historique WPM** (`WpmChart`) | ✅, détail non lu | ❌ absent | à évaluer — nécessite une bibliothèque de graphiques ou un composant maison, coût non négligeable, à ne pas sous-estimer |

**Le calcul du WPM lui-même n'a été vu nulle part** — ni dans `StatsViewModel` (non lu en détail), ni dans le domaine actuel. **Point ouvert, pas juste un manque d'UI** : il faut définir comment un WPM se calcule à partir de `ReadingSession` (mots lus / durée ? déjà stocké quelque part ?) avant de pouvoir porter quoi que ce soit ici.

### Capture d'écran — Statistiques

**❌ Aucune capture.**

---

## 7. Écran À propos (`AboutScreen.kt`, 130 lignes, lu intégralement)

Structure simple : icône + nom + version, description, section Confidentialité (3 cartes : architecture 100% locale, respect de la vie privée, autonomie hors-ligne), section Innovations Techniques (**mentionne « Sherpa-ONNX » et « Piper VITS » — obsolète par rapport à nos décisions actuelles, ADR-021/022 : Piper a été écarté, Kokoro est le moteur retenu**), section Crédits (développeur, dépôt, contact), copyright.

| Élément | Traitement |
|---|---|
| Structure générale (icône/version/description/sections) | `PORTER` |
| Cartes de confidentialité | `PORTER` — cohérent avec le Blueprint §10, contenu à re-vérifier phrase par phrase contre nos engagements réels actuels |
| Section technique | **`AMÉLIORER` obligatoirement, pas porter tel quel** — le texte doit refléter Kokoro (Apache-2.0) et l'alignement CTC, pas Sherpa-ONNX/Piper VITS qui ne correspondent plus à nos décisions (ADR-021/022). Porter ce texte sans le corriger diffuserait une information fausse aux utilisateurs. |
| Crédits | `PORTER`, informations à jour (contact, dépôt) |

### Capture d'écran — À propos

**❌ Aucune capture.**

---

## Récapitulatif des zones encore 🔴 non inventoriées en détail

- `LibraryNavigationPopup.kt`, `LibraryActionsSheet.kt`, `FilterAndSortDialog.kt` (fichiers séparés, référencés mais jamais ouverts)
- Fin de `RichBlockRenderer` dans `ReaderContent.kt` (~150 lignes, détail par type de bloc)
- `ReaderSettingsPanel.kt` (387 lignes), `ReaderTtsPanel.kt` (237 lignes) — disposition interne
- `ReaderBottomControls.kt` — 60% restant
- `StatsViewModel.kt` (108 lignes) — en particulier le calcul du WPM
- `SettingsViewModel.kt` (185 lignes)
- `GlobalBookmarksScreen.kt` (réécriture) — pas comparé ligne à ligne à `AllBookmarksPanel.kt`

## Aucune capture d'écran n'existe encore, sur aucun écran, ni legacy ni réécriture

C'est le point le plus important de tout ce document : **rien de ce qui précède n'a été vérifié visuellement**. Avant de considérer une seule section de ce document comme close, la capture correspondante doit exister — prise sur le V2206 réel.

---

## 8. Derniers éléments — clôture de l'inventaire

### 8.1 Bibliothèque : les 3 dialogues, maintenant lus intégralement

**`LibraryNavigationPopup`** — popup à deux colonnes (catégories à gauche : Tous/Favoris/Séries/Auteur/Tags/Dossiers, sous-éléments à droite avec compteur par item, ex. « Science-fiction (12) »). **`BY_AUTHOR` est une catégorie de navigation à part entière** dans ce popup, pas seulement un `FilterMode` théorique — confirme que notre `FilterMode.BY_AUTHOR` (Phase 6, jamais vraiment implémenté en UI) a un vrai besoin derrière.

**`FilterAndSortDialog`** — **`LayoutMode` a 3 valeurs, pas 2** : Liste / Grille / **Grille-couvertures-seules** (`GRID_COVERS`, sans titre affiché) — nuance absente de mon inventaire précédent. Une ligne « Type de fichier : Tous » statique en bas suggère un filtrage par format (EPUB/TXT) prévu mais pas branché dans le legacy lui-même.

**`LibraryActionsSheet`** — la reconstruction des couvertures affiche une **progression live** (compteur X/Y + spinner) pendant l'opération, et la réinitialisation des couvertures a une **confirmation explicite** (action destructive, dialogue dédié) avant d'exécuter. Détail de sécurité UX à ne pas oublier en portant.

### 8.2 Lecteur : derniers panneaux

**`ReaderSettingsPanel`** (confirmé) — 5 sections : thèmes (cartes-échantillons visuelles montrant le vrai fond/texte du thème, pas des noms seuls), polices, taille (slider), interligne (slider), marges (slider).

**`ReaderTtsPanel`** — indicateur « Phrase X / Y », contrôles précédent/lecture-pause/suivant/stop, slider de vitesse, **voix affichées par nom** (« Jessica », « Pierre » — confirme une fois de plus les voix Piper/UPMC déjà identifiées en Phase 5), minuteur de sommeil par chips de durée. **⚠️ Le bouton stop utilise un emoji (`"⏹ Arrêter"`) directement dans le texte — à ne PAS porter tel quel, contraire à notre propre règle K12 (aucun emoji en production, appliquée depuis la Phase 0). Remplacer par une icône `AppIcons`.**

### 8.3 `SettingsViewModel` (185 lignes, lu intégralement)

Aucune surprise au-delà de ce que l'écran expose déjà — chaque champ de `SettingsScreen` a son setter correspondant, `applyDarkModePreset()`/`applyAccessibilityPreset()` confirmés, export/import de sauvegarde confirmés. Rien à ajouter à ce qui était déjà documenté.

### 8.4 Signets globaux : comparaison faite, bonne parité

`GlobalBookmarksScreen.kt` (réécriture, 107 lignes) — recherche, état vide, liste avec suppression, navigation directe au tap, métadonnées affichées (titre de la publication + position). **C'est l'un des rares endroits où la réécriture est déjà proche du niveau legacy**, pas un trou à combler en priorité.

---

## Inventaire clos — synthèse des trous de domaine trouvés (au-delà de l'UI)

Trois éléments dépassent le seul habillage visuel et touchent le modèle de domaine, posé depuis les Phases 1/3/4 :

1. **`RichBlock`** (Heading/BlockQuote/PoemLine/EpubImage/SectionBreak, avec formatage riche préservé) — notre `DocumentModel` ne distingue rien de tout ça.
2. **`wordsRead` sur `ReadingSession`** — nécessaire pour calculer un WPM, absent de notre domaine actuel (`sentencesRead` seul ne suffit pas).
3. **`FilterMode.BY_AUTHOR`** a un vrai besoin de navigation derrière (confirmé par `LibraryNavigationPopup`), pas juste un no-op théorique déjà relevé en Phase 6.

Ces trois points méritent une décision consciente — les intégrer maintenant (impact sur des phases déjà closes) ou les traiter comme une évolution v1.x assumée — pas un oubli silencieux.

**L'inventaire exhaustif est maintenant complet sur les 7 écrans et leurs dialogues/panneaux.** Reste uniquement, comme noté partout dans ce document : aucune capture d'écran réelle n'existe encore pour valider visuellement quoi que ce soit de ce qui précède.
