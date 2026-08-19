# Lot 18 — Drawer : navigation unifiée

**Base :** `main` + `LOT_17_CORRECTIONS_RAPIDES_UX.md` mergé (ce Lot dépend
du fix mécanique du footer fait au Lot 17, ne le refait pas). Références :
`docs/execution/UX_FLOW_DESIGN.md` (section Drawer, lignes 227-274),
`LOT_17_CORRECTIONS_RAPIDES_UX.md` (constat détaillé des bugs de drawer,
points 3 et 4).

Seul des 3 Lots de la série de réconciliation UX à portée architecturale :
il touche la navigation partagée (`InkToneNavHost`) et plusieurs écrans à
la fois, contrairement aux corrections isolées du Lot 17 et au travail
fonctionnel contenu du Lot 19. Isolé en Lot propre pour ne pas bloquer les
deux autres.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

## Constat vérifié

`feature/library/.../LibraryScreen.kt:313-320` (`LibraryDrawerContent`) a
`selected = true` en dur sur l'item Bibliothèque, avec un commentaire
l'assumant : *« LibraryDrawerContent n'est monté que depuis l'écran
Bibliothèque lui-même, il n'y a pas d'autre état possible »*. Vérifié vrai
dans `app/src/main/kotlin/com/inktone/app/InkToneNavHost.kt` : les 5 autres
destinations de la liste principale du drawer utilisent toutes une flèche
de retour (`onBack`), aucune n'a de hamburger pour rouvrir le drawer :

| Écran | Route | Navigation actuelle |
|---|---|---|
| Bibliothèque | `LibraryRoute` | Hamburger → drawer (seul écran avec accès direct) |
| Récents | `RecentsRoute` | `onBack`, flèche retour (`RecentsScreen.kt:47,64-65`) |
| Marque-pages et Notes | `BookmarksRoute` | `onBack`, flèche retour (`LibraryItemsScreen.kt:73,104-105`) |
| Catalogues OPDS | `OpdsRoute` | `onBack`, flèche retour (`CatalogDashboardScreen.kt:65,107`) |
| Synchronisation | `SyncRoute` | `onBack`, flèche retour (`SyncConfigurationScreen.kt:76,93-94`) |
| Statistiques de lecture | `StatisticsRoute` | **Aucune top bar du tout** (`StatisticsScreen.kt` — pas de `navigationIcon`, pas de `TopAppBar`) |

Conséquence : le drawer n'est physiquement accessible que depuis
Bibliothèque, ce qui rend `selected = true` « correct » par construction
mais empêche toute navigation directe entre destinations du drawer (il faut
toujours repasser par Bibliothèque) et casse le surlignage — l'item actif
n'est jamais reflété nulle part ailleurs que sur Bibliothèque.

**Décision déjà actée (à ne pas rouvrir) :** les 3 items du **footer** du
drawer (Paramètres, Thèmes, À propos) restent en navigation flèche de
retour classique, ne sont *pas* concernés par ce Lot — seuls les 6 items de
la **liste principale** (Récents, Bibliothèque, Marque-pages et Notes,
Catalogues OPDS, Synchronisation, Statistiques de lecture) doivent devenir
des destinations pairs avec hamburger uniforme.

## Décisions à prendre en ouverture de Lot

Ce constat n'a pas encore été transformé en paliers détaillés — à faire en
tout début d'exécution de ce Lot, pas anticipé ici :

1. **Où vit `drawerState` partagé ?** Aujourd'hui `DismissibleNavigationDrawer`
   + `drawerState` sont locaux à `LibraryScreen.kt:137-138`. Options à
   trancher : le lever au niveau de `InkToneNavHost` (un seul drawer
   enveloppant tout le sous-graphe des 6 écrans), ou dupliquer un
   `ModalNavigationDrawer` léger par écran partageant `LibraryDrawerContent`
   comme composant commun. La première option évite la duplication mais
   demande de restructurer `InkToneNavHost` autour d'un sous-graphe
   partagé ; la seconde est plus locale mais duplique le câblage
   `drawerState`/`scope.launch { drawerState.close() }` par écran.
2. **Comment `LibraryDrawerContent` connaît-il la destination active ?**
   Remplacer `selected = true` figé par un paramètre (ex.
   `selectedRoute: Route` ou un enum dédié `DrawerDestination`), comparé à
   chaque `NavigationDrawerItem`. Vérifier que ce paramètre reste correct
   après navigation profonde (ex. `BookStatisticsRoute`, poussé depuis
   `StatisticsRoute` — l'item « Statistiques de lecture » doit rester
   surligné, pas retomber sur aucun ou sur Bibliothèque).
3. **`StatisticsScreen` n'a aucune top bar** — il faut lui en ajouter une
   (hamburger + titre), pas la modifier. Vérifier au passage la cohérence
   avec `BookStatisticsScreen` (écran de détail poussé depuis Statistiques,
   qui a probablement déjà sa propre top bar avec flèche retour — à ne pas
   toucher, c'est un écran secondaire légitime, pas une destination
   principale du drawer).

## Ce qu'on ne fait pas dans ce Lot

- Le footer du drawer (Paramètres, Thèmes, À propos) : reste en flèche de
  retour, décision actée.
- Le fix mécanique d'ancrage du footer en bas : déjà fait au Lot 17.
- Tout écran secondaire poussé en profondeur depuis une destination
  principale (`BookStatisticsRoute`, `LibraryDetailRoute`,
  `ThemeStudioRoute`, `PronunciationRulesRoute`) : garde sa flèche de
  retour normale, n'est pas une destination du drawer.

## Critères de sortie du Lot

- [ ] `drawerState` partagé, accessible depuis les 6 destinations
      principales du drawer.
- [ ] `LibraryDrawerContent` reçoit la destination active en paramètre,
      plus de `selected = true` figé.
- [ ] Les 6 écrans (Récents, Bibliothèque, Marque-pages et Notes,
      Catalogues OPDS, Synchronisation, Statistiques de lecture) ouvrent
      tous le même drawer via hamburger, plus de flèche de retour sur ces
      6-là spécifiquement.
- [ ] Le surlignage reflète la destination réellement active, vérifié en
      naviguant entre au moins 3 destinations différentes sur device.
- [ ] `StatisticsScreen` a une top bar avec hamburger, cohérente avec les
      5 autres.
- [ ] Navigation profonde (ex. `BookStatisticsRoute`) toujours accessible
      et son propre retour toujours fonctionnel, non régressé.
- [ ] `./gradlew build` vert.
