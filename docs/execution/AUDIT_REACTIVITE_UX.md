# Audit de réactivité UX — « rien n'attend, rien ne saccade »

**Date :** 2026-08-27 — **Base :** `main` (`6749b85e`, v1.0.0-beta.2) —
**Méthode :** lecture statique du code seul, aucune mesure sur appareil.

**Périmètre demandé :** performance UX pure — rapidité perçue, fluidité
60 fps, latence de réponse au geste. PAS les fonctionnalités manquantes,
PAS la robustesse (couverte par `AUDIT_CONSOLIDATION_V1.md`).

**Règle appliquée (Blueprint §17.2) :** le code fait foi. Chaque constat
cite le fichier et la ligne qui le prouve.

**Règle supplémentaire, demandée explicitement :** un commentaire de code
ou un document qui **assume** une inefficience ne la retire pas de la
liste. Trois points ci-dessous (§3.2, §4.3, §3.4) portent dans le source
une justification écrite — report de R8, mesure « assez rapide pour
rester sur le thread de composition », « pas de mise en cache prématurée
tant qu'un coût réel n'est pas mesuré ». Ils figurent ici comme **défauts
à corriger**, pas comme arbitrages à rediscuter. Pour §3.4 en
particulier, la condition posée par son propre commentaire est remplie :
le coût réel est mesuré ci-dessous.

**Budget de référence :** 16,7 ms par frame à 60 fps (Blueprint §11.2,
cible Snapdragon 680). Tout ce qui suit se paie dans ce budget.

---

## 1. Méthode et limites

Quatre chemins critiques suivis dans le code, du point d'entrée jusqu'au
rendu :

1. **Démarrage à froid** — `InkToneApplication` → `MainActivity` →
   `InkToneNavHost` → premier frame de la Bibliothèque.
2. **Bibliothèque** — requête Room → `LibraryViewModel` → dérivations
   d'état → grille et couvertures.
3. **Ouverture et lecture** — `openPublication` → parsing paresseux →
   pagination → rendu paginé et défilement.
4. **Narration TTS** — ordonnanceur → surlignage mot à mot → mise à jour
   de l'état → recomposition.

**Limite déclarée, à ne pas masquer :** rien n'a été profilé sur
appareil. Tous les constats sont établis par lecture du code réel et les
emplacements sont vérifiés ligne par ligne, mais les volumes chiffrés
sont des **ordres de grandeur dérivés de la structure du code**, pas des
relevés. Deux points (§4.2 et §5.1) ont une cause lisible dans le source
mais une amplitude qui demande une confirmation au Layout Inspector ou au
Macrobenchmark avant et après correction.

---

## 2. Verdict global

**22 freins relevés, dont 5 bloquants.** Aucun ne relève d'une erreur
d'architecture : ce sont des dérivations non mémoïsées, du travail
répété plusieurs fois par seconde, et deux leviers de build jamais
activés. La plupart se corrigent sans toucher au découpage en modules ni
au pattern MVI.

Le motif dominant est unique et se répète sur les deux écrans
principaux : **un état monolithique recomposé à haute fréquence, dont
chaque passage réexécute des dérivations coûteuses non mises en cache.**
Le corriger au Lecteur (§3.3, §3.4) et à la Bibliothèque (§3.5) est le
meilleur rapport effet/effort du lot.

### Tableau de synthèse

| # | Défaut | Emplacement | Gravité | §  |
|---|--------|-------------|---------|----|
| 1 | Baseline Profile jamais câblé | `benchmark`, `build-logic` | Bloquant | 3.1 |
| 2 | R8 désactivé en release | `build-logic` | Bloquant | 3.2 |
| 3 | État du Lecteur monolithique, recomposé à chaque mot | `feature:reader` | Bloquant | 3.3 |
| 4 | `bookProgression` recalculé à chaque accès | `feature:reader` | Bloquant | 3.4 |
| 5 | Sept dérivations non mémoïsées en Bibliothèque | `feature:library` | Bloquant | 3.5 |
| 6 | Un `BasicTextField` par paragraphe | `feature:reader` | Élevée | 4.1 |
| 7 | Lecture et écriture d'état pendant le layout | `feature:reader` | Élevée | 4.2 |
| 8 | Mesure de la première page sur le thread principal | `feature:reader` | Élevée | 4.3 |
| 9 | Élargissement progressif : texte mesuré jusqu'à 3 fois | `feature:reader` | Élevée | 4.4 |
| 10 | Mesureur de chapitre quadratique | `feature:reader` | Élevée | 4.5 |
| 11 | `File.exists()` en composition, par couverture | `feature:library` | Élevée | 4.6 |
| 12 | `SubcomposeAsyncImage` dans la grille | `feature:library` | Élevée | 4.7 |
| 13 | `rememberCoverGradient` ne mémoïse rien | `feature:library` | Moyenne | 4.8 |
| 14 | Deux `ImageLoader` Coil sans budget mémoire | `feature:reader` | Élevée | 5.1 |
| 15 | Chapitres parsés jamais évincés de l'état | `feature:reader` | Élevée | 5.2 |
| 16 | Sondage à 50 Hz pendant toute la narration | `feature:reader` | Moyenne | 5.3 |
| 17 | Trois animations infinies pendant toute la lecture TTS | `feature:reader` | Moyenne | 5.4 |
| 18 | `collectAsState` au lieu de la variante à cycle de vie | `app`, `feature:*` | Moyenne | 5.5 |
| 19 | Aucune borne sur les requêtes de liste | `infrastructure:database` | Moyenne | 6.1 |
| 20 | Aucun outillage de mesure Compose | `build-logic` | Moyenne | 6.2 |
| 21 | `TextStyle` reconstruit hors `remember` | `feature:reader` | Faible | 6.3 |
| 22 | Module benchmark en `compileSdk 34` | `benchmark` | Faible | 6.4 |

---

## 3. Bloquants

### 3.1 Le Baseline Profile n'est jamais embarqué (#1)

**Constat.** `benchmark/src/main/kotlin/com/inktone/benchmark/BaselineProfileGenerator.kt`
existe et sa KDoc demande de lancer `./gradlew :app:generateBaselineProfile`.
**Cette tâche n'existe pas.** Le plugin `androidx.baselineprofile` n'est
appliqué nulle part : `grep -rn "baselineProfile" --include=*.kts .` ne le
trouve que dans un commentaire de `gradle/libs.versions.toml:110`. `:app`
n'a aucune dépendance `baselineProfile(project(":benchmark"))`
(`build-logic/convention/src/main/kotlin/InkToneApplicationConventionPlugin.kt`,
bloc `dependencies`), et rien n'est commité sous
`app/src/*/generated/baselineProfiles/`.

`androidx-profileinstaller` est bien en dépendance
(`app/build.gradle.kts:130`) mais n'a aucun profil applicatif à installer :
seuls les profils fournis par les bibliothèques AndroidX sont fusionnés.

**Conséquence.** Le générateur est décoratif. Au premier lancement, tout
le chemin critique — démarrage, grille, ouverture d'un livre, première
pagination, défilement — reste interprété puis compilé à chaud. C'est
exactement l'écart que la cible matérielle du projet subit le plus
durement, et il est aujourd'hui à zéro.

**Correction.** Appliquer le plugin producteur sur `:benchmark` et
consommateur sur `:app`, générer le profil sur appareil réel avec une
bibliothèque non vide (le parcours du générateur ne couvre ni l'ouverture
ni la pagination sur une bibliothèque vide — sa propre KDoc le signale),
committer le résultat. Aucune modification de code applicatif.

**Non couvert par un audit antérieur** — absent de
`AUDIT_CONSOLIDATION_V1.md` comme de `LOT_21_GAINS_RAPIDES_PERF_UX.md`.

---

### 3.2 R8 est désactivé en release (#2)

**Constat.** `build-logic/convention/src/main/kotlin/InkToneApplicationConventionPlugin.kt:101`
— `isMinifyEnabled = false` et `isShrinkResources = false`, posés
explicitement.

**Conséquence.** Le commentaire sur place reconnaît déjà que l'argument
d'origine (AAB à 196 Mo) est caduc, réglé autrement par
`abiFilters arm64-v8a`. Reste ~40 Mo de dex non optimisé : autant de
chargement de classes et de vérification ART payés à chaque démarrage à
froid, cumulés avec §3.1.

**Correction.** Activer R8, écrire les règles `-keep` pour Readium et
onnxruntime (réflexion et JNI), valider sur appareil le parcours
import → lecture → TTS neuronal. À faire **avant** §3.1, pour que le
profil soit généré sur le binaire réellement livré.

**Antériorité.** Suivi comme R3 dans `AUDIT_CONSOLIDATION_V1.md:111` et
§4.2, statut « différé déclaré », avec une note de suivi demandant une
décision formelle « si la taille AAB devient limitante ». Ce critère de
réouverture est le mauvais : le motif n'est pas la taille de l'artefact
mais le coût de démarrage. Le point est donc rouvert ici sur ce
fondement, sans attendre le seuil de taille.

---

### 3.3 L'état du Lecteur est monolithique et se recompose à chaque mot (#3)

**Constat.** `feature/reader/src/main/kotlin/com/inktone/feature/reader/ReaderScreen.kt:195`
lit `state` — une `data class` de 97 champs
(`ReaderUiState.kt`) — à la racine d'un composable de 1792 lignes.
Pendant la narration, le collecteur de `currentWordRange`
(`ReaderViewModel.kt:211-217`) fait
`_state.value.copy(highlightedWordRange = …)` à chaque mot prononcé.

**Conséquence.** Tout le corps de `ReaderScreen` se recompose **trois à
cinq fois par seconde pendant toute la lecture audio**. Les enfants
sautent peut-être ; le corps, jamais. Chaque passage réévalue toutes les
clés de `remember` de la fonction, réalloue les lambdas non mémoïsables
et réexécute les dérivations de §3.4 et §6.3.

**Correction.** Sortir `highlightedWordRange` de l'état monolithique et
le publier en `StateFlow` distinct, transmis en `State<IntRange?>` — le
motif est déjà appliqué en interne par `highlightedRangeState`
(`ReaderScreen.kt`, transmis à `PagedChapterContent`/`BookBlockItem`
précisément pour éviter d'invalider mesure et placement). Dans la foulée,
découper `ReaderUiState` par domaine (contenu, lecture audio, HUD,
sélection) pour qu'une cadence de sous-état ne dicte plus celle de
l'écran entier.

---

### 3.4 `bookProgression` parcourt tout le livre à chaque accès (#4)

**Constat.** `ReaderUiState.kt:217-237` calcule
`chapters.take(currentChapterIndex).sumOf(::chapterCharCount)` puis
`chapters.sumOf(::chapterCharCount)` — deux parcours de **toutes** les
phrases de **tous** les chapitres chargés, plus une `List` allouée par
`take()`. `chapterCharCount` (`ReaderUiState.kt:291-292`) somme
`sentence.text.length` sur l'intégralité de `chapter.sentences`.

Lue par la ligne de statut (`ReaderScreen.kt:1362` →
`StatusLineBar.kt:74`), **toujours visible**, donc réévaluée à chaque
recomposition provoquée par §3.3.

**Conséquence.** Le coût n'est pas constant : il croît pendant toute la
session au fil des chapitres qui s'accumulent dans l'état (§5.2). Sur un
roman long en fin de lecture, c'est plusieurs dizaines de milliers
d'itérations et d'invocations de lambda par recomposition, à 3-5
recompositions par seconde.

**Correction.** Calculer une fois par publication le total de caractères
et les cumuls par chapitre sous forme de tableau préfixe, dans le
ViewModel, invalidés au chargement d'un chapitre. La progression devient
une soustraction et une division.

**Note.** La KDoc sur place justifie le choix par « pas de mise en cache
prématurée tant qu'un coût réel n'est pas mesuré ». La condition est
remplie : le coût réel est celui décrit ici, et il est structurel, pas
conjoncturel.

**Défaut de justesse adjacent, hors périmètre perf mais à corriger avec.**
`totalCharsInPublication` ne somme que les chapitres **déjà parsés** —
les chapitres non chargés ont `sentences` vide. Le dénominateur grandit
donc au fil de la session : le pourcentage affiché n'est pas stable pour
une même position. Le tableau préfixe ci-dessus ne résout ce point que
s'il est alimenté par une longueur connue à l'import, pas par le contenu
paresseux.

---

### 3.5 La Bibliothèque recalcule toute la bibliothèque à chaque recomposition (#5)

**Constat.** `feature/library/src/main/kotlin/com/inktone/feature/library/LibraryUiState.kt:62-81`
expose **sept propriétés en `get()`**, sans mémoïsation. Par
recomposition de `LibraryScreen` :

| Accès | Emplacement | Coût |
|---|---|---|
| `availableSeries`, `seriesCounts`, `availableTags`, `tagCounts` | `LibraryScreen.kt:184-187` | 4 parcours complets + `distinct` + `sorted`, **même tiroir fermé**, et 4 instances neuves qui empêchent tout saut de recomposition en aval |
| `displayedPublications` | `LibraryScreen.kt:231, 688, 708, 724` | `filterAndSort` appelé **3 à 4 fois** |
| `resumeReadingPublication` | `LibraryScreen.kt:240` | 1 parcours supplémentaire |

`filterAndSort` (`LibraryUiState.kt:96-115`) enchaîne lui-même **deux
tris**, dont `sortedBy { it.title.lowercase() }` : le sélecteur est
réévalué à chaque comparaison, soit un nombre d'allocations de `String`
en O(n log n) **par appel**.

**Aggravant.** `LibraryIntent.SetSearchQuery` (`LibraryViewModel.kt:139`)
écrit directement dans l'état, **sans debounce** — contrairement à la
recherche plein texte, qui a bien son `debounce(300)`
(`feature/search/.../SearchViewModel.kt:37`). Chaque frappe déclenche
l'intégralité du tableau ci-dessus.

**Correction.**
1. Remonter les sept dérivations dans `LibraryViewModel`, calculées une
   fois par émission et stockées en champs de `LibraryUiState`. Le
   précédent existe déjà dans ce même ViewModel : `computeProgressMap` a
   été déplacé sur `defaultDispatcher` pour exactement cette raison
   (`LibraryViewModel.kt:277-283`).
2. Précalculer les clés de tri au lieu de les recalculer par comparaison,
   et fusionner les deux tris en un comparateur unique.
3. Débouncer la recherche de bibliothèque à 200-300 ms.

Le même `filterAndSort` est appelé par `LibraryDetailUiState.kt:33` :
la correction 2 profite aux deux.

---

## 4. Gigue mesurable

### 4.1 Chaque paragraphe est un `BasicTextField` (#6)

**Constat.** `feature/reader/.../rendering/BookBlockItem.kt:363` — en
mode défilement, chaque bloc de texte est un champ de saisie en lecture
seule. Idem par page en mode paginé
(`PagedChapterContent.kt:667`). S'y ajoute, **par bloc**, un
`CompositionLocalProvider` et un `object : TextToolbar` alloués sur place
(`BookBlockItem.kt:328-359`).

**Conséquence.** Un `BasicTextField` read-only coûte plusieurs fois un
`Text` : machinerie de focus, session d'entrée texte, poignées de
sélection, sémantique éditable. En défilement rapide, c'est le prix payé
par chaque paragraphe qui entre dans la fenêtre visible.

**Correction.** Rendre par `Text` et n'échanger contre un
`BasicTextField` que le bloc effectivement en cours de sélection. Le
`TextToolbar` et les couleurs de sélection se hissent au niveau de
l'écran plutôt que d'être reconstruits par bloc.

**Écart déclaré.** Le choix d'origine est motivé (sélection native exacte
via `getWordBoundary`, aucune réimplémentation gestuelle — KDoc de
`PageBlock`). La correction proposée conserve cette propriété pour le
bloc sélectionné, qui est le seul où elle sert.

---

### 4.2 Lecture et écriture d'état pendant la phase de layout (#7)

**Constat.** `BookBlockItem.kt:393-405` — le callback `onTextLayout` lit
`highlightedRange.value` **et** écrit `onCurrentLineY(...)`, qui remonte
dans `currentLineYDp` (`ReaderScreen.kt`) puis redescend dans
`ReadingRuler`.

**Conséquence attendue.** Une lecture d'état instantané dans un callback
de layout abonne le layout à cet état : chaque mot prononcé invaliderait
alors la **mise en page** — pas seulement le dessin — de tous les
paragraphes visibles, règle de lecture activée. L'écriture d'état pendant
le layout déclenche par ailleurs une passe supplémentaire en aval.

Sur le même chemin, `drawAbsoluteRangeHighlight`
(`BookBlockItem.kt:419-427`, `PagedChapterContent.kt:790-802`) appelle
`getPathForRange`, qui alloue un `Path` à chaque dessin, pour chaque bloc
visible.

**Correction.** Sortir la lecture de `highlightedRange` du callback de
layout : conserver le `TextLayoutResult` et calculer la position de ligne
dans un effet, hors phase de mesure. Mettre en cache le `Path` de
surlignage tant que la plage et le layout ne changent pas.

**À confirmer à l'instrument.** C'est l'un des deux points dont
l'amplitude exacte demande le Layout Inspector avant et après. La cause,
elle, est lisible dans le source.

---

### 4.3 La première page est mesurée sur le thread principal (#8)

**Constat.** `feature/reader/.../pagination/ChapterPaginationState.kt:293`
— `measureFirstPage` (budget par défaut : 6000 caractères,
`ChapterTextMeasurer.kt:DEFAULT_PREFIX_CHAR_BUDGET`) s'exécute dans un
`LaunchedEffect`, donc sur le dispatcher principal. Les mesures
suivantes, elles, passent bien sur `Dispatchers.Default`
(`ChapterPaginationState.kt:310, 324, 332`).

**Conséquence.** L'effet est keyé sur `styleKey`, qui inclut taille de
police, interligne, police, marge, justification et dimensions du
viewport : la mesure est donc rejouée sur le thread principal à **chaque
cran de réglage et à chaque rotation**. L'utilisateur qui fait glisser le
curseur de police la paie à chaque cran, avec césure et justification
actives.

**Correction.** Basculer aussi cette mesure sur `Dispatchers.Default` via
`newBackgroundMeasurer()` — le mécanisme et sa justification de sûreté
(le `TextMeasurer` de composition n'est pas thread-safe, d'où un
mesureur dédié par mesure d'arrière-plan) existent déjà juste au-dessus.
Débouncer `styleKey` pendant un geste continu sur un curseur de réglage.

**Note.** La KDoc affirme « assez rapide pour rester sur le thread de
composition (3a.3) ». Non vérifié par une mesure, et faux dès que la
justification et la césure sont actives.

---

### 4.4 L'élargissement progressif mesure le même texte jusqu'à trois fois (#9)

**Constat.** `ChapterPaginationState.kt:305-317` — à la reprise en milieu
de chapitre, `nextBudget` double à chaque tour **et la mesure repart de
zéro** : 6 k, 12 k, 24 k, 48 k, 96 k, soit ~186 000 caractères mesurés
pour en couvrir 96 000 (`MAX_PROGRESSIVE_WIDENINGS = 4`). La mesure
complète du chapitre suit immédiatement (`ligne 324`), une troisième fois
sur le même texte. Le préchargement du chapitre suivant enchaîne derrière
(`ligne 332`).

**Correction.** Mesurer par lots incrémentaux en conservant les lots déjà
mesurés — `ChapterTextMeasurer.measureRich` découpe déjà en lots de
`MAX_BATCH_CHARS` et accumule, la structure s'y prête directement — et
enchaîner sur la mesure complète dès le premier ou deuxième doublement.

---

### 4.5 Le mesureur de chapitre est quadratique (#10)

**Constat.** `ChapterTextMeasurer.kt:308` —
`sentences.filter { it.blockIndex == originalIndex }` est appelé **dans
la boucle sur les blocs** : coût en O(blocs × phrases), avec une `List`
allouée par bloc. Un chapitre de 400 blocs et 3000 phrases représente
1,2 million de comparaisons par mesure — à multiplier par trois avec §4.4.

Le même schéma est dupliqué dans `measurableOffsetCount`
(`ChapterPaginationState.kt:130-137`), qui reproduit délibérément la
règle du mesureur — et donc aussi son coût.

**Correction.** Construire une fois un `Map<Int, List<Sentence>>` indexé
par `blockIndex` et le partager entre les deux appelants. L'invariant qui
les lie reste couvert par le test existant qui les compare sur un même
chapitre ; le coût devient linéaire.

---

### 4.6 Un appel disque par couverture, par composition (#11)

**Constat.** `feature/library/.../BookCover.kt:92-95` —
`File(publication.coverUri!!).takeIf { it.exists() }`, hors `remember`,
évalué pour chaque couverture visible à chaque composition.

**Conséquence.** Un appel système `stat()` placé dans le chemin de rendu
d'une frame. Pendant un lancer de défilement dans la grille, c'est de
l'entrée-sortie disque sur le thread principal, à chaque frame, multiplié
par le nombre de vignettes visibles.

**Correction.** Envelopper la résolution du modèle dans un
`remember(publication.coverUri)`, ou mieux : laisser Coil traiter
l'absence de fichier par son état d'erreur, qui affiche déjà le même
`CoverPlaceholder`.

---

### 4.7 `SubcomposeAsyncImage` là où `AsyncImage` suffit (#12)

**Constat.** `BookCover.kt:139`. Coil documente `SubcomposeAsyncImage`
comme la variante lente — une subcomposition par élément — et recommande
`AsyncImage` avec des `Painter` dans les listes. Ici les emplacements
`loading` et `error` ne contiennent que le **même** `CoverPlaceholder` :
la subcomposition n'achète rien et se paie à chaque vignette.

**Correction.** Passer à `AsyncImage` et rendre le dégradé de repli sous
l'image plutôt qu'en emplacement subcomposé.

---

### 4.8 `rememberCoverGradient` ne mémoïse rien (#13)

**Constat.** `BookCover.kt:299` — malgré son nom, la fonction ne contient
aucun `remember` : une `List` de huit `Pair<Color, Color>` et un `Brush`
sont alloués à chaque composition, pour chaque vignette sans couverture.

**Correction.** Hisser la palette en constante de fichier et envelopper
le `Brush` dans `remember(title)` — ce que le nom promet déjà.

---

## 5. Mémoire et énergie

### 5.1 Deux `ImageLoader` Coil concurrents, sans budget mémoire (#14)

**Constat.** `ReaderScreen.kt:807-813` construit un `ImageLoader` dédié
par publication EPUB (pour enregistrer `EpubImageFetcher.Factory`), sans
appel à `.memoryCache { }`. Il s'ajoute au chargeur par défaut de
l'application, utilisé par les couvertures de la Bibliothèque.

**Conséquence.** Deux caches mémoire dimensionnés chacun, par défaut, à
environ un quart du tas disponible. Sur un appareil à faible mémoire,
c'est ce qui transforme un aller-retour Bibliothèque ↔ Lecteur en rafale
de ramasse-miettes.

**Correction.** Un seul `ImageLoader` applicatif, injecté par Hilt, avec
le `Fetcher` EPUB enregistré une fois pour toutes et un budget mémoire
explicite. Le résolveur de ressources devient une donnée portée par la
clé de requête (`EpubImageKey` existe déjà), pas une raison de construire
un second chargeur.

**À confirmer à l'instrument.** Second des deux points demandant une
mesure — ici le profileur mémoire, sur un aller-retour répété entre les
deux écrans.

---

### 5.2 Les chapitres parsés ne sont jamais évincés de l'état (#15)

**Constat.** `EpubChapterParser.kt:73` borne correctement la mémoire du
parseur par un `LruCache` en octets. Mais `ReaderViewModel.kt:1149-1151`
recopie chaque chapitre parsé dans `_state.value.chapters` et **ne l'en
retire jamais** : `grep` sur `evict|unload|trimMemory` dans
`ReaderViewModel.kt` ne renvoie rien.

**Conséquence.** L'état conserve une référence forte sur tout chapitre
visité, ce qui **neutralise en aval le budget du parseur**. L'empreinte
croît linéairement avec l'avancement dans le livre, et avec elle le coût
de §3.4. Une lecture de plusieurs heures d'un roman long finit avec
l'intégralité du texte en `BookBlock` riches dans le tas.

**Correction.** Ne garder le contenu riche que pour une fenêtre glissante
autour du chapitre courant — celle du préchargement existant, N-1 à N+2,
`ReaderViewModel.preloadAdjacentChapters` — et rendre les autres à leur
coquille `ChapterContent.Rich` vide. Le parseur les reproduira depuis son
cache, dont c'est précisément le rôle.

---

### 5.3 Sondage à 50 Hz pendant toute la narration (#16)

**Constat.** `PlaybackOrchestrator.kt:945-958`, avec
`WORD_TRACKING_STEP_MS = 20L` (`ligne 1015`) : une boucle de sondage de
la position de lecture s'exécute cinquante fois par seconde tant que le
TTS joue.

**Nuance à conserver.** Elle tourne sur `Dispatchers.IO`
(`PlaybackOrchestrator.kt:149`) et son émission est gardée par un test
d'égalité (`ligne 954`) : elle ne provoque **pas** de gigue directe. Le
coût est énergétique — cinquante réveils par seconde pendant des heures
de narration.

**Correction.** Cadencer sur la durée du mot en cours plutôt qu'à
intervalle fixe : la prochaine échéance est connue par les
`WordTimestamp`, il suffit d'attendre jusqu'à elle avec une marge de
recalage sur la position réelle.

---

### 5.4 Trois animations infinies pendant toute la lecture audio (#17)

**Constat.** `TtsPillBar.kt:246-256` — les trois barres de l'onde sonore
sont animées en `infiniteRepeatable` tant que `isActive` est vrai et que
`reduceMotion` est faux.

**Conséquence.** Le pipeline de rendu ne redescend jamais à l'inactivité
pendant une narration : chaque frame est produite, écran allumé, pour
trois barres de quelques pixels.

**Correction.** Suspendre l'animation dès que la pilule n'est plus
réellement visible. La lecture immersive masque déjà la barre du haut
(`ReaderScreen.kt:1194`, gate `isHudVisible`) ; vérifier que la même
condition coupe bien l'animation, et non seulement l'affichage.

---

### 5.5 Collecte d'état sans prise en compte du cycle de vie (#18)

**Constat.** `collectAsState()` est utilisé partout —
`ReaderScreen.kt:195`, `LibraryScreen.kt:120`, `LibraryItemsScreen.kt:82`,
`MainActivity.kt` — alors que `androidx-lifecycle-runtime-compose` est
déjà déclaré au catalogue (`gradle/libs.versions.toml:70`).

**Conséquence.** Les collecteurs restent actifs écran éteint et
application en arrière-plan, y compris ceux qui alimentent l'interface
pendant la narration — c'est-à-dire exactement le cas d'usage où l'écran
est éteint le plus longtemps.

**Correction.** Remplacer par `collectAsStateWithLifecycle()`. La
dépendance est présente ; le changement est mécanique.

---

## 6. Données et outillage

### 6.1 Aucune borne sur les requêtes de liste (#19)

**Constat.** `infrastructure/database/.../dao/LibraryItemDao.kt:15` filtre
par `LIKE '%…%'` sur trois colonnes (`excerpt`, `note`,
`publicationTitle`), sans index utilisable et sans `LIMIT`.
`PublicationDao.kt:12` (`observeAll`) remonte toute la table.

**Conséquence.** Tout arrive en mémoire d'un bloc, puis traverse les
dérivations de §3.5.

**Correction.** Paginer par `PagingSource`, ou à défaut borner par
`LIMIT` avec chargement à la demande. La recherche sur les extraits
gagnerait à passer par la table plein texte déjà présente
(`SentenceFtsDao`) plutôt que par `LIKE`.

---

### 6.2 Aucun outillage de mesure Compose (#20)

**Constat.** Aucun `stabilityConfigurationFile` ni `reportsDestination`
dans `build-logic` (`grep -rn "composeCompiler|stabilityConfiguration|reportsDestination" --include=*.kts .`
ne renvoie rien). Les modèles du domaine vivent dans un module JVM pur
(`InkToneDomainConventionPlugin.kt` applique `kotlin.jvm` et interdit
explicitement tout plugin Android) : ils sont donc inconnus du compilateur
Compose du point de vue de la stabilité.

**Conséquence.** Personne ne peut aujourd'hui vérifier quels composables
sautent réellement une recomposition et lesquels ne le font jamais. Les
corrections §3.3, §3.5 et §4.1 se feraient à l'aveugle.

**Correction.** Déclarer un fichier de configuration de stabilité listant
`com.inktone.domain.model.*`, et activer les rapports du compilateur
derrière une propriété Gradle. **À faire avant** les corrections de §3 et
§4.

---

### 6.3 `TextStyle` reconstruit hors `remember` (#21)

**Constat.** `ReaderScreen.kt:862` — le style de rendu du mode défilement
est construit à chaque recomposition de l'écran, puis redistribué à
chaque `BookBlockItem` visible. Combiné à §3.3, c'est une allocation par
mot prononcé, propagée dans toute la liste.

**Correction.** `remember` sur les valeurs de réglage dont il dérive —
taille, interligne, police, justification, couleur.

---

### 6.4 Le module benchmark compile contre un autre SDK que l'application (#22)

**Constat.** `benchmark/build.gradle.kts` — `compileSdk = 34` et
`targetSdk = 34`, contre `compileSdk = 35` / `targetSdk = 35` pour `:app`
(`InkToneApplicationConventionPlugin.kt`).

**Conséquence.** Les mesures ne portent pas exactement sur les
comportements de plateforme que l'application déclare cibler — dont le
rendu bord à bord imposé par Android 15, qui touche directement le coût
de mise en page.

**Correction.** Aligner sur 35 **avant** de produire le moindre chiffre,
y compris le profil de §3.1.

---

## 7. Ordre d'exécution proposé

L'ordre n'est pas indifférent : les quatre premiers points rendent les
suivants mesurables.

### Palier A — rendre les choses mesurables

`#22` → `#20` → `#2` → `#1`, dans cet ordre.

Aligner le benchmark, activer les rapports de stabilité Compose, activer
R8, puis générer et committer le Baseline Profile **sur le binaire
réellement livré**. Tant que ce palier n'est pas passé, chaque correction
suivante s'évalue à l'estime.

Aucun de ces quatre points ne touche au code applicatif, sauf les règles
`-keep` de `#2`.

### Palier B — casser les cascades de recomposition

`#3`, `#4`, `#5`, `#21`.

Meilleur rapport effet/effort du lot : aucune ne change l'architecture,
toutes suppriment du travail répété plusieurs fois par seconde. `#5` est
indépendant du Lecteur et peut se traiter en parallèle.

### Palier C — alléger le rendu du texte

`#6`, `#7`, `#8`, `#9`, `#10`.

Le plus gros bloc de travail, et celui qui touche le cœur du produit. À
faire une fois les rapports de stabilité disponibles (palier A), avec une
mesure Macrobenchmark avant et après sur un chapitre long.

### Palier D — grille et images

`#11`, `#12`, `#13`, `#14`, `#19`.

Corrections courtes et sans risque, à l'exception de `#14` qui demande de
refondre l'injection du chargeur d'images.

### Palier E — mémoire et énergie

`#15`, `#16`, `#17`, `#18`.

Invisibles sur une session courte, déterminants sur une lecture de
plusieurs heures — c'est-à-dire sur l'usage réel du produit.

---

## 8. Ce que cet audit ne couvre pas

- **Aucune mesure sur appareil.** Voir §1. Les paliers A et C prévoient
  les mesures qui manquent.
- **Latence de synthèse TTS** (temps entre l'appui sur Lecture et le
  premier son) : dépend du moteur Sherpa-ONNX et de `warmUp()`, non
  auditée ici.
- **Coût d'import d'un EPUB** : `ImportWorker` tourne hors du chemin
  interactif, hors périmètre « fluidité 60 fps ».
- **Réseau et synchronisation** : hors périmètre.
