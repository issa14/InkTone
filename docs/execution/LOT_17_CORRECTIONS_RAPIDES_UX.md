# Lot 17 — Corrections rapides UX (bas risque)

**Base :** `main`. Références : `docs/execution/UX_FLOW_DESIGN.md` (source de
la spec de conception, comparée au code réel pour ce lot), `CONTRIBUTING.md`
(règle « le code fait foi » — aucun statut « fait » sans commit/fichier/test
qui le prouve), `LOT_16_SURLIGNAGE_SYNC.md` (précédent directement
comparable, dernier lot mergé sur `main`).

Ce Lot est le premier d'une série de 3 issue d'un audit de réconciliation
UX↔code (comparaison complète de `UX_FLOW_DESIGN.md` au code Kotlin réel,
plus des bugs remontés en observant l'application). Les 2 autres volets —
navigation du drawer (chantier architectural) et complétion fonctionnelle
Bibliothèque/À propos (nécessite du travail domain/data) — sont traités
séparément dans `LOT_18_DRAWER_NAVIGATION_UNIFIEE.md` et
`LOT_19_COMPLETION_BIBLIOTHEQUE_A_PROPOS.md`, dans cet ordre. Ce Lot ne
contient que des corrections mécaniques, à fichier(s) isolé(s), sans
changement d'architecture ni de module.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

Claude Code ne déclare aucune tâche close sans preuve citée (fichier/ligne
ou test). Le fix mécanique du footer du drawer (tâche 3 ci-dessous) est
volontairement limité à l'ancrage bas — la refonte plus large du drawer
(hamburger uniforme, surlignage réel) est hors périmètre de ce Lot, voir
Lot 18.

## Constat vérifié (base du Lot)

Tous les points ci-dessous ont été vérifiés directement dans le code lors
de l'audit de réconciliation, pas seulement lus dans un document.

1. **`lastOpened` jamais écrit.** Le champ existe partout
   (`domain/src/main/kotlin/com/inktone/domain/model/Publication.kt`, entité
   Room avec index dédié dans `infrastructure/database/.../PublicationEntity.kt`,
   `PublicationDao` avec `ORDER BY lastOpened DESC`) mais aucun repository ni
   ViewModel ne l'écrit : `PublicationDao` n'expose que `setFavorite`/
   `setPinned` comme updates ciblées ; `ReaderViewModel.kt:438` ne fait que
   `publicationRepository.getById(...)` (lecture), jamais d'update.
   Conséquence : `LibraryUiState.resumeReadingPublication`
   (`feature/library/.../LibraryUiState.kt:51`,
   `publications.filter { it.lastOpened != null }.maxByOrNull { ... }`) est
   toujours vide, donc `ResumeReadingCard` — déjà codée, prévue en tête de
   grille/liste (`LibraryScreen.kt:588-611`) — ne s'affiche **jamais** en
   pratique. Code mort, pas une fonctionnalité active.
2. **FAB Import dupliqué.** `app/src/main/kotlin/com/inktone/app/InkToneNavHost.kt:125`
   câble `floatingActionButton = { ImportPickerButton() }` en permanence sur
   la route Bibliothèque, y compris bibliothèque peuplée.
   `feature/import/.../ImportPickerButton.kt` instancie sa **propre**
   `ImportViewModel` (`hiltViewModel()` par défaut) et son **propre**
   `rememberLauncherForActivityResult`, indépendants de ceux câblés dans
   `InkToneNavHost.kt:100-107` pour le menu 3-points/état vide
   (`onImportClick`). Les MIME types acceptés diffèrent : le FAB accepte
   EPUB/TXT/PDF (`ImportPickerButton.kt:27`), le second mécanisme
   n'accepte pas le PDF (`InkToneNavHost.kt:120`).
3. **Footer du drawer non ancré en bas.**
   `feature/library/.../LibraryScreen.kt:275-372` (`LibraryDrawerContent`) :
   `Column { Box(header, 140dp) ; Column(padding 16dp) { 6× NavigationDrawerItem ;
   Divider ; Row(footer) } }`, sans `fillMaxHeight()` ni
   `Spacer(Modifier.weight(1f))`. Le footer (Paramètres/Thèmes/À propos)
   atterrit juste après le dernier item de la liste principale au lieu
   d'être ancré en bas de la feuille (`DismissibleDrawerSheet`, qui occupe
   bien toute la hauteur disponible, elle).
4. **Padding racine de `ReaderScreen` qui décale le HUD.**
   `feature/reader/.../ReaderScreen.kt:331-340` : le `Box` racine applique
   `.padding(16.dp)` après `.fillMaxSize().background(...)`, et ce même
   `Box` héberge en overlay (`Modifier.align(...)`) aussi bien la zone de
   lecture que `ReaderTopBar`, `UnifiedControlPanel` et `StatusLineBar`.
   Conséquence double : la topbar démarre 16dp sous le bord de l'écran avec
   un à-plat de fond abrupt au-dessus (au lieu d'être collée au bord), et le
   panneau unifié est 32dp plus étroit que l'écran malgré son propre
   `Modifier.fillMaxWidth()` interne (il remplit son parent, déjà rétréci).
   Facteur aggravant possible : `ImmersiveReaderChrome.kt:35` masque les
   barres système (`controller.hide(systemBars())`) sans jamais appeler
   `WindowCompat.setDecorFitsSystemWindows(window, false)` nulle part dans
   le dépôt (confirmé par recherche exhaustive) — un vrai edge-to-edge est
   peu fiable sans cet appel et peut laisser un espace système résiduel non
   peint.
5. **`StatusLineBar` peu contrastée, illisible en thème clair.**
   `feature/reader/.../StatusLineBar.kt:54-69` est un `Row` nu : aucun des 3
   `Text()` n'a de `color =` explicite, contrairement à `ReaderTopBar` et
   `UnifiedControlPanel`, qui reçoivent tous les deux un
   `contentColor`/`accentColor` calé sur `ThemeColors.barContent(resolvedTheme)`.
   Le texte hérite donc d'une couleur ambiante non liée au thème de lecture
   actif. Le scrim ajouté en compensation dans `ReaderScreen.kt` (dégradé
   `Color.Transparent → ThemeColors.background(theme).copy(alpha = 0.9f)`,
   commentaire ~ligne 878) laisse filtrer 10% du texte défilé en dessous et
   ne corrige pas la cause racine (couleur de texte jamais fixée).
6. **Écarts mineurs vs `UX_FLOW_DESIGN.md`** (comportement identique,
   détail littéral différent) :
   - Couleur du thème Sombre : `domain/.../ReadingTheme.kt`, `OBSIDIENNE`
     a `backgroundColorHex = "#000000"` (noir pur) alors que le document
     (ligne 412) spécifie `#1c1b19` (gris très foncé chaud) ; le document
     note lui-même « à valider si un noir pur est préféré » — point resté
     en suspens, pas une régression, mais l'écart doit être corrigé dans un
     sens ou dans l'autre.
   - Icône favori : `feature/library/.../LibraryItemsScreen.kt:326-331`
     utilise `AppSymbol.Pin` (épingle) là où le document (ligne 658)
     spécifie une étoile — comportement identique (favoris remontent en
     tête, `LibraryItemDao.kt:27-30`), seule l'icône diffère.
   - Bannière de progression d'import : `LibraryScreen.kt:783` affiche
     `"Import : ${current} / ${total}"`, le document (ligne 290) spécifie
     `"Import en cours · 5/12"`.
   - Résumé de fin d'import : `ImportResultComponents.kt:47-49,63` agrège
     Corrupted/DrmProtected/UnsupportedFormat en un seul compteur générique
     « X échec(s) », le document (lignes 291, 296) donne l'exemple « 9
     importés · 2 doublons ignorés · 1 fichier corrompu » (catégories
     distinctes) — le détail par entrée distingue déjà bien le type
     (`ImportResultRow`), seul le résumé agrégé est simplifié.
7. **Dérive documentaire dans `UX_FLOW_DESIGN.md`** (le code a évolué, le
   document ne suit pas — zéro changement de code requis) :
   - Onboarding (lignes 42, 50-51, 58, 65, 73) : décrit des illustrations
     Canvas (`OnboardingIllustrations.kt`, fichier supprimé du dépôt) ;
     le code actuel (`OnboardingScreen.kt:132-157,165-195`) utilise
     l'icône de l'app (carte 1) et une liste verticale (carte 2), remplacés
     après retour utilisateur sur appareil réel, jamais reflété dans le
     document.
   - Panneau unifié du Lecteur (ligne 838, tableau lignes 378-389) : compte
     7 icônes (+ Luminosité), le code (`UnifiedControlPanel.kt`) en a
     réellement 10 (Mode et Recherche ajoutées après coup, déjà mentionnées
     dans le journal intermédiaire du document mais jamais dans sa
     conclusion).
   - Réglages (ligne 547) : le document dit « 6 cartes », le code
     (`SettingsScreen.kt:72-79`) en a 7 (dont « À propos », jamais compté
     par le document) — le commentaire du code lui-même est incohérent avec
     sa propre énumération.
   - Package `transition/` (`feature/reader/.../transition/`, transition
     animée entre chapitres, ajouté par le commit « Polissage lecteur » du
     14/08/2026) : comportement réel non documenté du tout.

## Tâches

1. **`lastOpened`** — écrire ce champ quand un livre est ouvert. Ajouter la
   méthode ciblée côté `PublicationDao`/`domain` (cohérent avec
   `setFavorite`/`setPinned`, pas un `@Update` généraliste), appelée depuis
   `ReaderViewModel` à l'ouverture. Pas de FAB dédié : la carte
   `ResumeReadingCard` déjà codée doit se mettre à fonctionner une fois le
   champ câblé. Commit : `Écrit lastOpened à l'ouverture d'un livre`.
2. **FAB Import** — supprimer `floatingActionButton = { ImportPickerButton() }`
   de `InkToneNavHost.kt:125`. Avant retrait, vérifier/aligner que le
   mécanisme conservé (menu 3-points + bouton état vide,
   `onImportClick`/`importLauncher` de `InkToneNavHost.kt:100-107`) accepte
   bien `application/pdf`, pour ne perdre aucune capacité au passage.
   Commit : `Retire le FAB Import dupliqué, aligne les MIME types PDF`.
3. **Footer du drawer** — ancrer en bas : `fillMaxHeight()` sur la colonne
   racine de `LibraryDrawerContent` + `Spacer(Modifier.weight(1f))` avant le
   footer. Ne pas toucher au `selected = true` de l'item Bibliothèque ni à
   la navigation des autres items — hors périmètre (Lot 18).
   Commit : `Ancre le footer du drawer Bibliothèque en bas de la feuille`.
4. **Padding racine `ReaderScreen`** — retirer le `.padding(16.dp)` du `Box`
   racine partagé par le HUD et la zone de lecture ; appliquer le padding
   uniquement là où il sert réellement (le contenu texte du livre), pas aux
   overlays HUD (`ReaderTopBar`, `UnifiedControlPanel`, `StatusLineBar`).
   Évaluer si `WindowCompat.setDecorFitsSystemWindows(window, false)` doit
   être ajouté dans `ImmersiveReaderChrome.kt` pour un edge-to-edge fiable ;
   si ajouté, vérifier sur device que ça ne régresse pas le comportement de
   `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Commit :
   `Corrige le padding racine du Reader qui décale la topbar et le panneau`.
5. **`StatusLineBar`** — fixer explicitement la couleur des 3 `Text()` sur
   `ThemeColors.barContent(resolvedTheme)` (même source que `ReaderTopBar`/
   `UnifiedControlPanel`), pour un contraste garanti sur tous les thèmes de
   lecture, y compris clair. Vérifier sur device que le scrim existant
   reste nécessaire ou peut être simplifié une fois la couleur de texte
   fixée. Commit : `Fixe la couleur de StatusLineBar sur le thème de lecture`.
6. **Écarts mineurs** — 4 corrections indépendantes, un commit chacune ou
   groupées si triviales :
   - Couleur thème Sombre : aligner code et document dans un sens choisi
     (probablement garder `#000000` et corriger le document, à confirmer —
     pas un choix visuel à trancher silencieusement).
   - Icône favori : `AppSymbol.Pin` → étoile, ou documenter l'icône actuelle
     comme décision assumée dans `UX_FLOW_DESIGN.md`.
   - Texte bannière d'import : aligner sur `"Import en cours · X/Y"`.
   - Résumé de fin d'import : distinguer les catégories d'erreur dans le
     résumé agrégé, pas seulement le détail par entrée.
7. **Mise à jour de `UX_FLOW_DESIGN.md`** — corriger les 4 dérives
   documentaires listées au constat (onboarding, décompte icônes panneau,
   décompte cartes Réglages, documenter `transition/`). Zéro changement de
   code. Commit : `Met à jour UX_FLOW_DESIGN.md sur les dérives constatées`.

## Ce qu'on ne fait pas dans ce Lot

- Refonte du drawer (hamburger uniforme, surlignage réel, top bar
  `StatisticsScreen`) — Lot 18.
- Écran À propos, actions du menu 3-points Bibliothèque (random/sync/
  couvertures), statut de la carte WebDAV — Lot 19.
- Tout item déjà confirmé conforme par l'audit (Statistiques, Galerie de
  thèmes/Studio, PDF, Bibliothèque état vide/peuplé, marque-pages/notes,
  reste du panneau Lecteur) n'est pas retouché.

## Critères de sortie du Lot

- [ ] `lastOpened` écrit à l'ouverture d'un livre ; `ResumeReadingCard`
      visible sur device après ouverture d'au moins un livre (vérifié sur
      appareil, pas seulement en test unitaire).
- [ ] Un seul mécanisme d'import restant, PDF inclus.
- [ ] Footer du drawer visuellement ancré en bas sur device.
- [ ] Topbar du Reader collée au bord supérieur, panneau unifié pleine
      largeur, vérifiés sur device.
- [ ] `StatusLineBar` lisible en thème clair, sombre et sépia, vérifié sur
      device.
- [ ] Les 4 écarts mineurs corrigés ou formellement actés comme décision
      assumée dans `UX_FLOW_DESIGN.md`.
- [ ] `UX_FLOW_DESIGN.md` à jour sur les 4 points de dérive documentaire.
- [ ] `./gradlew build` vert.
