# Réconciliation — Flux cible (`UX_FLOW_DESIGN.md`) ↔ code réel

**Méthode :** dépôt `github.com/issa14/InkTone` cloné, branche `main` à `97ed564` (*« Corrige les régressions et bugs réels trouvés à l'audit du Reader (#34) »*). Aucun status file, aucune doc de phase lu comme source. Toutes les affirmations ci-dessous sont adossées à une lecture directe du source, citations `file:line`.

**Périmètre inspecté :** `app/` (navigation), `feature/{library,reader,onboarding,settings,statistics,import,search,player}`, `core/{ui,designsystem}`.

---

## 0. Constat transverse préalable — le graphe de navigation est cassé

À traiter avant l'écran par écran, parce qu'il change le statut de plusieurs écrans : **du code conforme mais inatteignable reste du cas (3) en pratique.**

### 0.1 L'écran Réglages est orphelin

`SettingsRoute` est déclaré (`app/src/main/kotlin/com/inktone/app/Routes.kt:32-33`) et une destination existe (`InkToneNavHost.kt:117-126`), mais **aucun `navigate(SettingsRoute)` n'existe dans tout le module `app`** — vérifié par grep exhaustif sur `app/src/main`. Il n'y a pas non plus d'item « Paramètres » dans le drawer (`LibraryScreen.kt:281-353`).

Conséquence en cascade :
- **`AboutRoute` est inatteignable** : sa seule entrée est `onOpenAbout` de `SettingsScreen` (`InkToneNavHost.kt:123`).
- **`PronunciationRulesRoute` est inatteignable** : même chose (`InkToneNavHost.kt:122`).
- Trois écrans réellement construits (Réglages, À propos, Règles de prononciation) sont donc du code mort à l'exécution.

### 0.2 Le pied de drawer est décoratif

`LibraryScreen.kt:347-348` affiche deux boutons de pied de drawer :

```
DrawerFooterItem("À propos", AppIcons.Info) { onOpenAbout() }
DrawerFooterItem("Thème", AppIcons.Appearance) { onOpenThemePicker() }
```

Or `LibraryScreen` est appelée depuis `InkToneNavHost.kt:71-81` **sans passer `onOpenAbout` ni `onOpenThemePicker`** — ces deux paramètres retombent sur leur valeur par défaut `{}` (`LibraryScreen.kt:115-116`). Les deux boutons ne font donc strictement rien au tap.

C'est exactement l'antipattern legacy déjà catalogué (« filtres décoratifs sans effet sur les données »), réintroduit ici. À traiter comme une régression, pas comme un manque.

### 0.3 Icônes fausses en production

`AppIcons.Loading = Icons.Outlined.HourglassEmpty` (`core/designsystem/.../AppIcons.kt:68`) est utilisé à trois endroits où il n'a aucun sens :
- **comme chevron du menu déroulant du titre** — `LibraryScreen.kt:437`, avec le commentaire explicite « flèche vers le bas via rotation » alors qu'aucune rotation n'est appliquée : c'est un sablier affiché à côté de « Bibliothèque » ;
- comme icône de l'item drawer « Récents » (`LibraryScreen.kt:291`) ;
- comme icône « Actualiser » du bottom sheet (`LibraryScreen.kt:494`).

Par ailleurs `AppIcons.CoverOnly` et `AppIcons.ReadingModePaged` pointent tous deux sur `Icons.Outlined.ViewDay` (`AppIcons.kt:74,80`) — le bouton de bascule de disposition affiche donc le même glyphe pour deux modes différents (`LibraryScreen.kt:545-549`).

Même famille de problème que l'erreur ☰-pris-pour-un-filtre signalée dans le document UX (§ Menu déroulant du titre, « à corriger dans l'implémentation, pas reproduire l'erreur »).

---

## 1. Onboarding — **(3) à planifier intégralement**

**Statut d'exécution : jamais affiché.** `feature:onboarding` est bien une dépendance du module `app` (`build-logic/convention/src/main/kotlin/InkToneApplicationConventionPlugin.kt:57`), mais `OnboardingScreen` n'est référencé **nulle part** dans `app/src/main` — grep sur `OnboardingScreen|onboarding` ne renvoie rien. `MainActivity.onCreate` va directement à `InkToneNavHost()` (`MainActivity.kt:74`), dont la `startDestination` est `LibraryRoute` (`InkToneNavHost.kt:60`). Il n'existe aucune `OnboardingRoute` dans `Routes.kt`.

**Contenu, en plus, à l'opposé de la décision actée.** Le document tranche : *« Différé au point de besoin réel, pas dans l'onboarding […] L'onboarding reste une pure présentation, sans rien de fonctionnel »* (UX §Onboarding). Le code fait précisément l'inverse — `OnboardingScreen.kt:19-31` définit trois étapes :

| Étape codée | `file:line` | Verdict |
|---|---|---|
| `Welcome` | `OnboardingScreen.kt:34-41` | Texte brut, pas d'illustration |
| `CrashConsent` (ADR-014) | `OnboardingScreen.kt:49-62` | **À retirer** — décision : au point de besoin |
| `VoiceDownload` (ADR-018) | `OnboardingScreen.kt:64-82` | **À retirer** — décision : au 1er lancement TTS |

Manquent également, par rapport au flux cible : le `HorizontalPager` (balayage horizontal), les illustrations, les 3 indicateurs de position, le bouton « Passer » sur les cartes 1-2, le bouton « Commencer » de sortie. Le composant actuel est un `when` sur un enum avec des `Column`/`Button` nus.

**À planifier :** réécriture complète du composable + création d'`OnboardingRoute` + arbitrage `startDestination` selon un flag « déjà vu » + déplacement des deux étapes fonctionnelles vers leurs points de besoin. Le `OnboardingViewModel` et son test (`OnboardingViewModelTest.kt`) sont à réévaluer, pas à conserver tels quels : ils pilotent un flux à 4 étapes qui n'existera plus.

---

## 2. Bibliothèque — barre supérieure — **(2) divergent**

Ordre cible : hamburger · titre adaptatif · recherche · **filtre** · 3-points.
Ordre codé (`LibraryScreen.kt:444-472`) : hamburger · titre · recherche · **tri** · **bascule de disposition** · 3-points.

| Élément cible | État réel | Écart |
|---|---|---|
| Hamburger → drawer | ✅ `LibraryScreen.kt:444-447` | conforme |
| Titre adaptatif + chevron | ⚠️ `LibraryScreen.kt:426-443` | le titre reflète bien le filtre actif, mais le « chevron » est un sablier (§0.3) |
| Recherche repliable | ✅ `LibraryScreen.kt:392,397-422,450-452` | conforme dans le principe (bascule d'état, pas d'animation — point resté ouvert côté doc aussi) |
| Icône **filtre** → popup | ❌ | **absente**. À sa place : un `DropdownMenu` de tri seul (`LibraryScreen.kt:453-465`) et un bouton de cycle de disposition (`466-468`) |
| 3-points → bottom sheet | ⚠️ `LibraryScreen.kt:469-471,483-509` | présent, contenu divergent (§4) |

Le popup de filtrage cible (§4 ci-dessous) regroupe tri + filtre + disposition + type de fichier en **un seul dialogue** ; le code les a éclatés en deux icônes distinctes dans la barre. C'est la divergence structurelle principale de cette barre.

---

## 3. Bibliothèque — état vide — **(2) divergent, écart faible**

`LibraryScreen.kt:747-777`. Structure globalement conforme (illustration centrée + titre + corps + CTA), textes différents :

| Cible | Codé | `file:line` |
|---|---|---|
| Illustration étagère en pointillés | `AppIcons.Reading` (icône simple) | `751-756` |
| « Votre bibliothèque est vide » | « Bibliothèque vide » | `758` |
| « Importez votre premier livre pour commencer à lire et écouter avec InkTone. » | « Importez un EPUB pour commencer votre bibliothèque. » | `763-764` |
| « Importer votre premier livre » | « Importer des livres » | `772` |

Le code gère en plus un cas `hasActiveImport` (`748,758,763,770`) absent du flux cible — comportement défendable, à acter ou retirer explicitement plutôt que laisser en zone grise.

À noter : les textes cibles sont eux-mêmes marqués « inventé par Claude, à valider » dans le document UX. Cet écran ne peut pas être clos tant que les textes réels ne sont pas tranchés.

---

## 4. Popup de filtrage — **(3) à planifier**

**N'existe pas.** Le dialogue centré à quatre blocs (Trier par / Filtrer par / Mise en page / Type de fichier) est absent. Les fonctions sont dispersées et incomplètes :

- **Trier par** — `DropdownMenu` en barre (`LibraryScreen.kt:457-464`) alimenté par `LibrarySortOrder`, qui n'a que **3 valeurs** : `TITLE, RECENTLY_ADDED, RECENTLY_OPENED` (`LibraryUiState.kt:55`). La cible en demande 5 : Date d'import / Titre / **Auteur** / Récents / Récemment lus. Le tri par auteur n'existe pas.
- **Filtrer par** — présent, mais sous forme de `FilterChip` en ligne sous la topbar (`LibraryScreen.kt:204-207,720-734`), pas dans un dialogue. Les 4 valeurs cibles existent bien (`FilterMode.ALL/UNREAD/IN_PROGRESS/READ`, `LibraryUiState.kt:683-691`).
- **Mise en page** — bascule cyclique à **3** modes (`LIST, GRID, GRID_COVERS`, `LibraryUiState.kt:58,60-64`) alors que la cible n'en retient que **2** (liste / mosaïque-couvertures-seules). Le mode `GRID` (couverture + titre) est précisément la variante **écartée** par la décision finale du document (§ Décision finale sur la disposition grille) — et c'est le mode **par défaut** dans le code (`LibraryUiState.kt:22`).
- **Type de fichier (Tous/EPUB/TXT, multi-sélection)** — **totalement absent**, aucun champ correspondant dans `LibraryUiState`.

---

## 5. Bottom sheet 3-points — **(2) divergent**

`LibraryScreen.kt:483-509`. Cinq actions cibles vs six actions codées, avec seulement deux recoupements :

| # | Cible | Présent ? | `file:line` |
|---|---|---|---|
| 1 | Importer des livres | ✅ | `493` |
| 2 | Couverture par défaut | ⚠️ libellé « Réinitialiser les couvertures » | `500-503` |
| 3 | Reconstruire les couvertures | ⚠️ libellé « Régénérer les couvertures » | `496-499` |
| 4 | Ouvrir un livre au hasard | ❌ absent | — |
| 5 | Synchroniser avec le cloud | ❌ absent | — |
| — | *(en trop)* Actualiser | — | `494` |
| — | *(en trop)* À propos | **mort** (§0.2) | `505` |
| — | *(en trop)* Thème | **mort** (§0.2) | `506` |

Les items « À propos » et « Thème » du bottom sheet souffrent du même défaut de câblage que ceux du pied de drawer : `onOpenAbout`/`onOpenThemePicker` sont propagés depuis `LibraryScreen` (`182-199`) mais jamais fournis par `InkToneNavHost`.

---

## 6. Bibliothèque — état peuplé — **(2) divergent**

### 6.1 Mosaïque

`BookCover.kt:97-180`. Trois superpositions cibles, aucune n'est conforme :

| Cible | Réel | `file:line` |
|---|---|---|
| Haut-droite : **cœur** (contour/plein) | **étoile** (`Icons.Filled.Star` / `Icons.Outlined.StarBorder`), teinte ambre codée en dur `Color(0xFFFFC107)` | `BookCover.kt:125-140` |
| Bas-gauche : **3-points → popup d'actions par livre** | `DecorativeDots` — trois cercles **non cliquables**, aucun `onClick`, aucun popup | `BookCover.kt:165` + `237-245` |
| Bas-droite : badge circulaire de progression | ✅ conforme (cercle noir 55%, `%` en clair) | `BookCover.kt:143-162` |

Le point médian est le plus grave : **le popup d'actions par livre (Épingler / Détails du livre / Télécharger la couverture / Retirer de la bibliothèque avec confirmation de suppression en cascade) n'existe nulle part dans le code.** `DecorativeDots` est littéralement de la décoration — le nom de la fonction le dit et le commentaire `// #6 Dots décoratifs — coin inférieur gauche (legacy)` le confirme. Aucune action destructive n'est donc câblée, et l'avertissement d'irréversibilité + suppression des marque-pages/notes associés (acté deux fois dans le document, § Bibliothèque peuplée et § Marque-pages vue globale) n'a aucun support.

Défaut secondaire : quand `showTitle = true`, le titre (`BookCover.kt:168-179`) et `DecorativeDots` (`165`) sont tous deux alignés `BottomStart` — ils se superposent.

### 6.2 Liste

`LibraryScreen.kt:614-665`. Conforme sur la hiérarchie typographique (titre `titleSmall` / auteur `bodySmall` grisé, `641-655`) et la miniature à gauche (`628-635`). Divergences :

- **Pas de 3-points** dans la rangée — seule l'étoile favori est présente (`657-663`). La cible demande cœur **et** 3-points côte à côte à l'extrême droite. La moitié de la décision (celle qui a demandé deux allers-retours de clarification, cf. § Historique de clarification du document) est absente.
- **Pas de barre de progression pleine largeur sous la rangée** avec pourcentage à droite. `progressPercent` n'est même pas passé au `BookCover` miniature (`629-634`, appel sans `progressPercent`), alors que la donnée existe dans `state.progressMap` (`LibraryUiState.kt:26`).

### 6.3 Élément hors flux cible

`SeriesGroupedView` (`LibraryScreen.kt:852-902`) insère des rangées horizontales groupées par série **au-dessus** de la grille en mode `ALL` (`228-235`). Rien de tel dans le flux cible, où les séries passent exclusivement par le menu déroulant du titre → écran de détail. À retirer ou à re-acter.

---

## 7. Menu déroulant du titre (Favoris/Séries/Tags) — **(2) divergent, écart structurel**

**Pattern faux.** La cible est un **flyout à deux colonnes** (catégories à gauche, sous-éléments à droite avec compteurs), choix motivé et opposé explicitement aux chips. Le code ouvre un **`ModalBottomSheet` à une seule colonne** (`LibraryScreen.kt:512-530`) listant à plat `SelectableFilters` = `ALL, FAVORITES, UNREAD, IN_PROGRESS, READ` (`683`).

Conséquences :
- **Séries et Tags ne sont pas dans ce menu** — ils sont relégués au drawer (`LibraryScreen.kt:307-338`), en listes verticales pour les séries/auteurs et en `LazyRow` de `FilterChip` pour les tags. C'est exactement l'endroit où le document précise *« Favoris / Séries / Tags — accessibles depuis ce menu déroulant, pas depuis ailleurs »* (UX §Bibliothèque état vide, précision marquée « importante d'Issa »).
- **« Auteurs » est présent dans le drawer** (`LibraryScreen.kt:317-326`) alors que la cible l'exclut nommément de la navigation dédiée.
- Aucun compteur par sous-élément (cible : « Trilogie du Vide (3) »).
- Une `TagsFilterBar` supplémentaire duplique les tags sous la topbar (`LibraryScreen.kt:208-213,820-840`) — troisième point d'entrée pour la même donnée.

### Écran de détail partagé Séries/Tags — **(3) à planifier**

**Inexistant.** Sélectionner une série ou un tag applique un filtre sur la grille courante (`LibraryScreen.kt:313,333`) ; il n'y a aucune navigation vers un écran dédié, aucune route correspondante dans `Routes.kt`, donc ni la topbar à deux niveaux (étiquette « SÉRIES » en petites capitales + nom en dessous), ni la flèche de retour remplaçant le hamburger, ni le rendu forcé en vue Liste.

---

## 8. Drawer — **(2) divergent**

`LibraryScreen.kt:249-356`.

| Cible | État | `file:line` |
|---|---|---|
| En-tête « InkTone » + dégradé | ✅ conforme | `259-280` |
| Récents | ⚠️ **faux comportement** — l'item existe mais son `onClick` est `onSelectFilter(FilterMode.ALL, null)`, il ne change **rien** au tri et ne navigue nulle part | `289-294` |
| Bibliothèque (actif par défaut) | ⚠️ rendu comme un des 5 `SelectableFilters`, pas comme une destination distincte | `282-288` |
| Marque-pages et Notes | ⚠️ libellé « Signets », navigue bien vers `BookmarksRoute` | `301-306` |
| Catalogues OPDS (placeholder badgé) | ❌ absent | — |
| Synchronisation | ❌ absent | — |
| Statistiques de lecture | ✅ présent, libellé « Statistiques » | `295-300` |
| Pied : Paramètres / À propos / Thèmes | ⚠️ **2 boutons sur 3**, tous deux morts (§0.2) — **« Paramètres » n'existe pas** | `343-353` |

L'item « Récents » mérite un mot : c'est un item de navigation qui prétend mener quelque part et n'y mène pas. `selected` le compare à `sortOrder == RECENTLY_OPENED` (`292`) mais `onClick` ne définit jamais ce tri (`293`). Troisième occurrence de la même famille de défaut que §0.2 et §6.1.

Éléments en trop dans le drawer, sans contrepartie dans le flux cible : les 5 filtres, les listes Séries/Auteurs/Tags (§7), et un bouton « Debug » conditionné à `BuildConfig.DEBUG` avec un `onClick` vide commenté `/* no-op pour l'instant */` (`350-352`).

---

## 9. Import — progression et retours — **(2) divergent, gros manque**

- **Bannière de progression non bloquante** : ✅ conforme. `ImportProgressBanner` (`LibraryScreen.kt:702-718`) est bien rendue **au-dessus** du contenu dans le `Column` du Scaffold (`203`), jamais en overlay plein écran. Elle gère le cas déterminé et le cas indéterminé. Le libellé est « Import : 5 / 12 » là où la cible écrit « Import en cours · 5/12 » — cosmétique.
- **Résumé de fin de lot** (« 9 importés · 2 doublons ignorés · 1 fichier corrompu » + action « Détails ») : ❌ **absent**.
- **Retour par fichier sur deux registres visuels** (ⓘ informationnel pour doublon vs ⚠ alerte pour corrompu/DRM/format) : ❌ **absent**.

Le domaine distingue pourtant bien les cinq cas depuis la Phase 6 — `ImportResult.Duplicate`, `.Corrupted`, `.DrmProtected`, `.UnsupportedFormat` sont tous produits par `domain/.../ImportPublicationUseCase.kt:44,52,58,59,60`. **Aucun de ces cas ne remonte jusqu'à l'UI** : `ImportViewModel` (`feature/import/.../ImportViewModel.kt:14-28`) n'expose aucun état, il se contente d'un `enqueue` sans retour. C'est bien, comme l'anticipait le document, « l'habillage visuel qui manque, pas la logique » — mais le chaînon d'état entre les deux manque aussi, ce n'est pas qu'une couche de peinture.

*Détail à nettoyer :* le lanceur SAF est déclaré deux fois — dans `InkToneNavHost.kt:64-70` et dans `ImportPickerButton.kt:46-52` — les deux étant actifs simultanément sur l'écran Bibliothèque (CTA vide + FAB).

---

## 10. Lecture — vue silencieuse — **(2) divergent**

`ReaderScreen.kt:96-417`.

### 10.1 Ligne de statut persistante — **(3) absente**

Les trois éléments (heure locale à gauche · `Chapitre X (page/total)` au centre · progression du livre `34,7%` à droite, virgule décimale) **n'existent nulle part**. Ce qui s'en rapproche le plus :

- `BookProgressBar` (`BookProgressBar.kt:201-207`) — une `LinearProgressIndicator` de 2dp **sans aucun texte**, donc pas de pourcentage, pas de virgule décimale. Elle est placée en **haut** de l'écran (`ReaderScreen.kt:151`), pas en bas, et est bien persistante (hors HUD) — c'est le seul point conforme à l'esprit de la ligne de statut.
- Un micro-indicateur ETA apparaît en bas quand le HUD est masqué (`ReaderScreen.kt:295-309`), mais il affiche `state.etaText` (temps restant estimé), pas les trois champs cibles.

Aucun compteur de pages virtuelles n'existe dans `ReaderUiState`.

### 10.2 Immersion — ✅ conforme

Aucune bannière de chapitre injectée par l'app : vérifié, le rendu ne produit que les `Sentence` du chapitre (`ReaderScreen.kt:212-267`). Le style `ParagraphStyle.HEADING` est bien honoré pour les titres réellement présents dans l'EPUB (`ReaderScreen.kt:456-463`), conformément à la distinction actée. Rien à faire.

### 10.3 Mode pagé — les trois défauts sont intacts

Le document les signale comme « à corriger avant l'implémentation finale ». Ils sont toujours là, à l'identique :

1. **Pagination par estimation de caractères** — `PagedChapterContent.kt:51-74`, `charsPerPage = (800 * 18 / fontSizeSp)`, aucune mesure de texte rendu.
2. **`sentences.indexOf(sentence)` par phrase affichée** — `PagedChapterContent.kt:107`, à l'intérieur du `forEach` de rendu, donc réévalué à chaque recomposition. Recherche linéaire O(n) par phrase, O(n²) par page.
3. **Aucun test** — confirmé, aucun fichier de test ne référence `PagedChapterContent` (grep sur l'arbre `test/` complet).

*Constat supplémentaire non signalé jusqu'ici :* le KDoc affirme que le découpage se fait « hors thread UI » (`PagedChapterContent.kt:31`). C'est faux — le bloc est un `remember { }` (`55`), il s'exécute sur le thread de composition. La documentation contredit le code.

---

## 11. Lecture — HUD — **(2) divergent, structure entière à revoir**

### 11.1 Barre du haut — **(3) absente**

Aucune `TopAppBar` dans le Reader. Ni flèche de retour, ni titre du livre, ni nom de l'auteur — le `ReaderScreen` est un `Column` nu (`ReaderScreen.kt:140-150`). La sortie du Reader repose entièrement sur le retour système.

### 11.2 Panneau unifié — répartition différente

`UnifiedControlPanel.kt:53-145`. Cible : 3 rangées (barre de progression / 5 icônes avec Play central / 4 icônes). Réel : 2 rangées.

**Rangée 1 codée** (`UnifiedControlPanel.kt:82-114`) : chapitre précédent · **Play** · chapitre suivant.
→ Le Play central proéminent est conforme (`FilledIconButton` 56dp, couleur d'accent, `93-107`). Mais la barre de progression du livre (rangée 1 cible) n'y est pas, et la navigation par chapitre est ici alors que la cible la place dans la **barre de contrôle TTS** (§12).

**Rangée 2 codée** (`UnifiedControlPanel.kt:128-142`) : 7 actions dans un `horizontalScroll`.

| Icône cible | Présente ? | `file:line` |
|---|---|---|
| Sommaire | ✅ | `141` |
| Marque-pages | ⚠️ libellé « Signets » | `140` |
| Play (central) | ✅ rangée 1 | `93-107` |
| Thème (bascule cyclique) | ❌ **absent** | — |
| TT (taille/interligne) | ⚠️ « Aa », ouvre un panneau qui fait thème **et** taille | `135` |
| Minuteur | ⚠️ « Veille », comportement divergent (§11.4) | `138` |
| Haut-parleur | ⚠️ « Voix » | `136` |
| Luminosité | ❌ **absent** | — |
| — | *(en trop)* Recherche | `139` |
| — | *(en trop)* Mode de défilement | `137` |

Le `horizontalScroll` (`131`) est une correction assumée d'un vrai bug de débordement — mais il traite le symptôme d'un panneau qui porte 7 actions au lieu des 4 prévues en rangée 3. Le passage à la structure 5+4 sur deux rangées résout la cause.

**Élément parasite :** un bouton `Button("+ Signet")` en clair, hors du panneau, sous celui-ci (`ReaderScreen.kt:367-371`). Rien de tel dans la cible — le marquage de page appartient au panneau Marque-pages (§11.5).

### 11.3 Déclenchement / auto-masquage — ✅ conforme

Visible à l'ouverture (`ReaderScreen.kt:103`), masqué après 4s (`ImmersiveReaderChrome.kt:202-207`), rappelé au tap sur la zone de lecture (`ReaderScreen.kt:145-148` et le relais `237-249`). Le `hudActivityTick` qui relance le délai à chaque interaction (`ReaderScreen.kt:109-113`) va au-delà de la cible et va dans son sens. Rien à faire.

### 11.4 Sous-écrans du panneau — état par icône

| Sous-écran cible | Statut | `file:line` |
|---|---|---|
| **Sommaire** — bottomsheet « Table des matières », chapitre courant centré, ±2 chapitres, indentation hiérarchique | ⚠️ **divergent** : ce n'est **pas un bottomsheet** — il remplace tout l'écran (`ReaderScreen.kt:164-172`, `return@Column`). Titre « Sommaire », pas « Table des matières » (`TableOfContentsSheet.kt:72`). Le centrage sur le chapitre courant est fait (`55-58`), l'indentation hiérarchique aussi (`40-41,93`) — mais `children` n'a **toujours jamais été observé non vide** sur un EPUB réel, TODO explicite laissé au code (`TableOfContentsSheet.kt:31-36`). C'est le point « jamais vérifié depuis les Fondations » que le document rouvre. | `TableOfContentsSheet.kt` |
| **Marque-pages** — panneau latéral gauche 85%, 3 onglets Notes/Surlignages/Marque-pages, bouton toggle « Marquer cette page » | ❌ **à planifier** : `BookmarkListSheet` remplace l'écran entier (`ReaderScreen.kt:174-182`), n'a **aucun onglet**, n'affiche **que les signets** (pas les notes ni les surlignages), et n'a pas de bouton de marquage. Chaque ligne = titre + « Supprimer » en texte (`BookmarkListSheet.kt:164-183`). | `BookmarkListSheet.kt` |
| **Play** | ✅ déclenche directement le TTS, n'ouvre rien | `ReaderScreen.kt:347-350` |
| **Thème** — bascule cyclique Clair→Sombre→Sépia, aucun retour visuel | ❌ **à planifier** : pas de bascule cyclique. Le choix de thème est noyé dans le panneau « Aa » sous forme de 3 cartes (`ReaderSettingsPanel.kt:65-78`) |
| **TT** — bottomsheet, aperçu du vrai texte en direct, 2 sliders continus (taille + **interligne**) | ⚠️ **divergent** : bottomsheet ✅ (`ReaderSettingsPanel.kt:49`), mais **pas d'aperçu en direct**, **pas de réglage d'interligne**, et le slider de taille a `steps = 19` donc **paliers discrets** alors que la cible demande du continu (`ReaderSettingsPanel.kt:84-89`) |
| **Minuteur** — 2 fonctions : chips 15/30/45 + roue personnalisée ; **rappel de repos oculaire** 1h par défaut avec popup + compte à rebours 60s | ⚠️ **très divergent** : le tap sur « Veille » **cycle** entre 15/30/45/60 sans rien ouvrir (`ReaderScreen.kt:353-356` + `nextSleepTimerMinutes`, `419-432`), avec un commentaire assumant l'écart (`421-425`). Des chips 15/30/45/60 existent, mais enfouies dans le panneau **Voix** (`ReaderTtsPanel.kt:257-276`). **Le rappel de repos oculaire n'existe nulle part** |
| **Haut-parleur** — voix (nom réel type « ff_siwis · Kokoro · Français »), volume, vitesse, lien « Ajouter une règle de prononciation » | ⚠️ **divergent + slider mort** (§11.6) |
| **Luminosité** — barre flottante, lecteur uniquement | ❌ **à planifier**, rien dans le code |

### 11.5 Popup de sélection de texte — **(2) divergent**

Cible : bloc positionné près de la sélection, trois options **Copier / Surligner / Note**.
Réel : `AnnotationColorPicker` (`ReaderScreen.kt:334-341`, `AnnotationColorPicker.kt`) — un sélecteur de **couleur** de surlignage avec Confirmer/Annuler, rendu en bas du `Column`, pas près de la sélection. Ni « Copier », ni « Note ». La sélection elle-même est par phrase (appui long puis extension), limitation d'API documentée et assumée (`ReaderScreen.kt:77-89`) — c'est un écart connu et justifié, à re-acter côté UX plutôt qu'à corriger à l'aveugle.

### 11.6 Deux contrôles décoratifs dans le panneau Voix

`ReaderTtsPanel` reçoit `currentSpeed = 1.0f` **codé en dur** avec un `// TODO: lire depuis preferences TTS speed` (`ReaderScreen.kt:398`) et `onSpeedChange = { /* TODO: UpdatePreferencesUseCase(speed) */ }` (`405`). Le slider de vitesse (`ReaderTtsPanel.kt:248-253`) **ne lit rien et n'écrit rien** — il revient à 1,0× à chaque ouverture et son déplacement n'a aucun effet.

Deuxième point : `onStop` est câblé sur `ReaderIntent.Pause` (`ReaderScreen.kt:402`) — le bouton Stop rouge (`ReaderTtsPanel.kt:235-241`) fait donc une pause.

Le sélecteur de voix et le lien « Ajouter une règle de prononciation » sont tous deux absents de ce panneau.

---

## 12. Lecture — couche TTS — **(3) à planifier intégralement**

Rien de la couche TTS cible n'existe :

- **Le panneau unifié n'est pas remplacé** au lancement du TTS — `UnifiedControlPanel` reste affiché tel quel, seule l'icône Play devient Pause (`UnifiedControlPanel.kt:102-106`).
- **Pas de barre pilule flottante** `cp - pp - r - ps - cs`. La navigation phrase-à-phrase existe (`ReaderTtsPanel.kt:202-211`) mais dans un bottomsheet séparé, ouvert manuellement, et la navigation par chapitre est ailleurs (panneau unifié).
- **Pas de repli en FAB après 4s**, pas d'indicateur d'onde sonore, pas de geste de swipe-vers-le-bas pour stopper.
- **Surlignage mot-à-mot** : ✅ présent et fonctionnel (`ReaderScreen.kt:471-496`), avec transition animée entre mots respectant `reduceMotion` (`470`). Conforme à l'exigence « actif en permanence ».
- **Captions : à retirer.** Le document tranche « désactivées — jugées trop encombrantes ». Elles sont **implémentées et actives** : overlay noir 65% pleine largeur en bas d'écran pendant toute la lecture (`ReaderScreen.kt:311-331`). C'est un cas (2) net : construit, puis décidé contre.

---

## 13. Récents — **(3) à planifier**

Aucun écran, aucune route. L'item drawer correspondant ne fait rien (§8). Manquent donc : la topbar simplifiée (retour + « Récents » seul), la restriction aux livres à progression ≥ 1 %, le tri par récence d'ouverture, la limite à 30, le rendu forcé en Liste et l'état vide sans bouton d'action.

Les briques existent partiellement — `LibrarySortOrder.RECENTLY_OPENED` (`LibraryUiState.kt:55`) et `progressMap` (`26`) — mais aucun assemblage.

---

## 14. Réglages — **(2) divergent**

`SettingsScreen.kt`. Écran réel : **7 `SectionGroup`** qui ne correspondent pas au découpage cible en 6 cartes.

| Carte cible | État |
|---|---|
| **1. Présets rapides** (2 cartes-boutons empilées avec toggles : Mode sombre → `OBSIDIAN`+`NIGHT` ; Accessibilité → OpenDyslexic+24sp+`DAY`+reduce-motion) | ❌ **absent**. Un simple `Button("Appliquer le preregalage d'accessibilite")` existe (`SettingsScreen.kt:181-186`) — pas un toggle, pas de preset Mode sombre, pas de désapplication |
| **2. Lecture** (Moteur, Voix, Vitesse d'élocution, Gain audio, Intonation/Pitch, Écouter un extrait) | ⚠️ éclaté sur deux sections « Lecture » (`124-130` : thème/taille/police) et « Voix » (`131-145` : moteur/voix/gain). **Manquent : vitesse d'élocution, intonation/pitch, bouton Écouter un extrait.** Le sélecteur de voix est un **cycle** (`136-139` + `nextVoiceProfileId`, `293-301`), pas un dialogue |
| **3. Appareil** (Apparence : Thème système + Couleurs dynamiques ; Accessibilité : Réduire animations + Police système) | ⚠️ les 4 réglages existent (`157-180`) mais répartis sur deux sections distinctes, sans le regroupement en une carte à deux sous-sections. Le sélecteur « Thème » de la section Lecture (`125`) porte sur `ReadingTheme` (thème de lecture), **pas** sur le thème système Système/Clair/Sombre attendu ici — le thème système n'est réglable nulle part |
| **4. Données** (Dossier des modèles, Exporter, Importer avec avertissement, Vider le cache avec taille + confirmation, Réinitialiser en couleur d'alerte) | ❌ **carte entièrement absente**. `BackupManager` (`data/.../backup/BackupManager.kt:28`) est construit et testé mais **n'est référencé par aucun écran** — vérifié par grep sur `feature/`, `app/`, `core/` : la seule occurrence est une mention en commentaire (`GlobalBookmarksUiState.kt:7`) |
| **5. Prononciation** (carte liste inline + dialogue modal) | ⚠️ existe en **écran séparé** (`PronunciationRulesScreen.kt`), atteint par une ligne « Gérer » (`144`) — pas la carte inline avec en-tête « Dictionnaire phonétique (n) » et bouton `+`. Écran actuellement inatteignable (§0.1) |
| **6. Performance & Bien-être** (Objectif quotidien avec dialogue-curseur 10-120 min, Rappel de repos oculaire, Intervalle par stepper 15 min) | ❌ **carte entièrement absente** |

Sections codées **sans contrepartie cible** : « Langue » (`146-150`), « Confidentialité » (`151-156`), « À propos » (`188-190`).

*Bug à relever :* `PickerDialog` place « Annuler » dans le slot `confirmButton` et n'a aucun bouton de confirmation (`SettingsScreen.kt:331`) — la sélection est validée au clic sur la ligne (`322,325`), ce qui est défendable, mais le libellé dans le slot de confirmation est trompeur.

---

## 15. Marque-pages et notes — vue globale — **(2) divergent, écart majeur**

`GlobalBookmarksScreen.kt`.

| Cible | État | `file:line` |
|---|---|---|
| Topbar : retour + « Marque-pages et notes » + recherche repliable + tri | ⚠️ topbar générique `BackScaffold` titrée **« Signets »** (`InkToneNavHost.kt:138`) ; la recherche est un `TextField` **toujours déployé** dans le corps (`GlobalBookmarksScreen.kt:53-60`) ; **pas d'icône de tri** |
| Puces de filtre Tous / Signets / Surlignages / Notes | ❌ absentes | — |
| Recherche par titre **ou contenu textuel** | ⚠️ titre d'ouvrage uniquement (`GlobalBookmarksUiState.kt`, placeholder `57`) | |
| Tri chronologique / alphabétique | ❌ absent, aucun champ de tri dans l'état | — |
| Cartes : extrait + annotation en italique + `Titre · Chapitre X · date` + étoile épinglée | ❌ rangée plate : icône + titre d'ouvrage + « Chapitre N » (`84-106`). **Pas d'extrait, pas de note, pas de date, pas d'épinglage** | |
| Clic → ouvre le lecteur au passage, avec flash temporaire | ⚠️ la navigation fonctionne (`74`, `GlobalBookmarksViewModel.kt:57-66`), **pas de flash/surlignage temporaire** à l'arrivée | |
| Swipe-to-dismiss avec confirmation | ❌ bouton poubelle, **suppression immédiate sans confirmation** (`103-105`, `GlobalBookmarksViewModel.kt:53`) | |

**Le point structurant :** cet écran ne montre que des `Bookmark`. `GlobalBookmarksViewModel` n'observe que `bookmarkRepository.observeAll()` (`GlobalBookmarksViewModel.kt:36-42`) — **surlignages et notes (`AnnotationRepository`) ne sont jamais chargés**. La cible en fait un centralisateur des trois types sur tous les livres ; le code est une liste de signets. Ce n'est pas un habillage à reprendre, c'est un écran à reconstruire sur une source de données élargie.

---

## 16. Statistiques de lecture — **(2) divergent, 1 section sur 4**

`StatisticsScreen.kt:42-81`.

| Section cible | État |
|---|---|
| **1. KPIs & Objectifs** — jauge **circulaire** + streak flamme + libellé de régularité ; **2 cartes Lecture visuelle / Écoute TTS** ; 3 cartes Livres finis / Pages lues / Mots parcourus | ⚠️ **partiel** : objectif du jour en `LinearProgressIndicator` (jauge **linéaire**, `63-67`), Série + Record en deux cartes (`72-75`), Livres terminés (`79`), Vitesse moyenne (`77`), Temps total (`78`). **Manquent : la ventilation lecture-visuelle vs écoute-TTS, Pages lues, Mots parcourus, le libellé de régularité** |
| **2. Graphiques d'Activité** — histogramme empilé Semaine/Mois avec variation % et marqueur du jour ; heatmap jours × créneaux avec pic horaire | ❌ **absent**. Le KDoc l'assume explicitement : *« Pas de graphique temporel […] `StatisticsUiState` n'expose que 3 valeurs agrégées, aucune série temporelle en base pour l'alimenter honnêtement »* (`StatisticsScreen.kt:36-40`). **Le blocage est en base, pas en UI** — c'est la contrainte structurante de cette section |
| **3. Carte Livre en cours + bouton Export CSV/JSON** | ❌ absent |
| **4. Écran dédié Détail par ouvrage** (sélecteur de livre, WPM, temps restant, historique de sessions avec icône œil/casque) | ❌ absent, aucune route |

Le refus documenté d'inventer un graphique sans données est cohérent avec le principe du projet et doit être respecté : la Section 2 se planifie comme **une tâche de données d'abord** (persistance d'une série temporelle d'événements de session), l'UI ensuite.

---

## 17. Synchronisation — **(3) à planifier intégralement**

**Zéro ligne.** Grep sur `WebDAV|SyncUiState|SyncAccount` : aucun résultat dans tout le dépôt. Ni écran Configuration, ni écran Opérationnel, ni état scellé, ni item drawer, ni route.

`BackupManager` (`data/.../backup/BackupManager.kt:28`) existe et est testé — c'est la brique « Fichier local (.rfbackup) » de l'écran Configuration, effectivement à brancher plutôt qu'à reconstruire, comme l'anticipait le document. Mais il n'est aujourd'hui **branché nulle part** dans l'UI.

---

## 18. Galerie de thèmes + Studio — **(3) à planifier intégralement**

**Zéro ligne.** Ni `ThemeGalleryScreen`, ni `ThemeStudioScreen`, ni aucune notion de thème personnalisé. Le bouton « Thème » du drawer et celui du bottom sheet pointent vers un `onOpenThemePicker` sans destination (§0.2), donc vers rien.

À noter comme brique réutilisable : `core/designsystem/ContrastRatio.kt` existe et a un test (`ColorContrastTest.kt`) — c'est le calcul dont le badge WCAG en direct du Studio a besoin.

---

## 19. À propos — **(2) divergent**

`core/ui/.../AboutScreen.kt`. Écran atteignable **uniquement** via Réglages, lui-même inatteignable (§0.1).

| Cible | État | `file:line` |
|---|---|---|
| Titre « À propos & Confidentialité » | ⚠️ topbar générique « A propos » (sans accent) | `InkToneNavHost.kt:154` |
| Hero + **badge de version cliquable** (clic court → snackbar, clic long → copie des specs système) | ⚠️ icône + nom + « Version 0.1.0 » en **texte non cliquable** (`50-51`). Pire : `versionName` a la valeur par défaut `"0.1.0"` et `AboutScreen()` est appelé **sans argument** (`InkToneNavHost.kt:155`) — la version affichée est **codée en dur**, pas `BuildConfig.VERSION_NAME` | `38,50-51` |
| Grille 3 colonnes Engagements & Confidentialité | ⚠️ deux `InfoCard` en prose (`62-64`), pas la grille à 3 piliers | |
| Dépôt GitHub + **Signaler un problème** (email pré-rempli avec diagnostic) | ⚠️ lien GitHub ✅ (`79-87`) ; **« Signaler un problème » absent** | |
| Accordéon Architecture & Licences avec badges colorés | ⚠️ liste statique non dépliable, licences en texte gris (`67-75,110-115`) | |
| **Correction Piper → Kokoro** | ✅ **déjà correct** : « Kokoro / ONNX Runtime (synthèse vocale) » (`71`) et « Kokoro / ONNX Runtime » en confidentialité (`63`). Aucune mention de Piper dans le fichier | `63,71` |
| Pied : copyright + année dynamique + développeur + licence MIT | ⚠️ « © 2026 InkTone. » — **année codée en dur**, pas de nom de développeur, pas de mention MIT | `90` |

Le `onOpenUrl` déclaré en paramètre (`38`) n'est jamais utilisé — l'écran utilise `LocalUriHandler` en interne (`39,85`). Paramètre mort à retirer.

---

## Synthèse

### (1) Conforme — rien à faire
- Immersion du lecteur, absence de bannière de chapitre injectée, respect de `ParagraphStyle.HEADING` (§10.2)
- Déclenchement / auto-masquage du HUD à 4s, y compris le relance-délai sur interaction (§11.3)
- Play du panneau unifié : déclenche directement le TTS, n'ouvre rien (§11.4)
- Surlignage mot-à-mot permanent, avec respect de `reduceMotion` (§12)
- Badge circulaire de progression en mosaïque (§6.1)
- Bannière d'import non bloquante (§9)
- Correction Piper → Kokoro déjà en place dans l'écran À propos (§19)

### (2) Construit mais divergent — à corriger
Barre supérieure Bibliothèque (§2) · état vide (§3) · bottom sheet 3-points (§5) · mosaïque et liste (§6) · menu déroulant du titre (§7) · drawer (§8) · import (§9) · vue silencieuse (§10) · panneau unifié et ses sous-écrans (§11) · Réglages (§14) · Marque-pages vue globale (§15) · Statistiques (§16) · À propos (§19)

### (3) Absent — à construire
Onboarding conforme (§1) · popup de filtrage (§4) · popup d'actions par livre + confirmation de suppression en cascade (§6.1) · écran de détail Séries/Tags (§7) · ligne de statut persistante (§10.1) · barre du haut du Reader (§11.1) · bascule de thème cyclique, luminosité, panneau Marque-pages à 3 onglets, rappel de repos oculaire (§11.4) · couche TTS complète : barre pilule + FAB (§12) · Récents (§13) · cartes Présets rapides / Données / Performance & Bien-être (§14) · graphiques d'activité + détail par ouvrage (§16) · Synchronisation (§17) · Galerie de thèmes + Studio (§18)

### Défauts à traiter en priorité, indépendamment du flux cible
Ce sont des régressions par rapport aux principes déjà actés du projet, pas des manques de conception :

1. **Réglages / À propos / Prononciation inatteignables** (§0.1) — trois écrans construits, zéro chemin d'accès.
2. **Cinq contrôles décoratifs sans effet** : pied de drawer × 2 et bottom sheet × 2 (§0.2), item « Récents » du drawer (§8), 3-points de couverture (§6.1), slider de vitesse TTS (§11.6). C'est l'antipattern legacy « filtres décoratifs » réintroduit à cinq endroits distincts.
3. **Icônes fausses** : sablier utilisé comme chevron, même glyphe pour deux modes de disposition (§0.3).
4. **Version et année codées en dur** dans À propos (§19) — donnée de diagnostic fausse dès la première release.
5. **Les trois défauts connus de `PagedChapterContent`** toujours intacts, plus une doc qui contredit le code sur le thread d'exécution (§10.3).

### Point resté volontairement ouvert, conservé
Affichage d'une **session mixte lecture/TTS** dans l'historique par ouvrage (§ Statistiques, Section 4). Le code ne l'a pas tranché non plus — et pour cause, la Section 4 n'existe pas et aucune série temporelle de sessions n'est persistée. Le point reste ouvert, non bloquant, à revoir quand la base de données pour la Section 2 sera posée.
