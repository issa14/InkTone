# Lot 2 — Bibliothèque

**Base :** branche `lot-1-coquille-navigation` à `87ad262`. Référence cible : `UX_FLOW_DESIGN.md` § Bibliothèque (état vide, état peuplé), § Menu déroulant du titre, § Popup de filtrage, § Bottom sheet 3-points.

Découpé en **deux lots séquentiels** : 2a touche l'état et la navigation, 2b touche le rendu d'un élément de liste. Surfaces disjointes, chacune finissable et vérifiable seule. **2b démarre après clôture device de 2a.**

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Correctif de périmètre hérité du lot 1

Le plan du lot 1 demandait de conserver « Régénérer les couvertures » et « Réinitialiser les couvertures » dans le bottom sheet. C'était une erreur de ce plan : `LibraryViewModel.kt:75-81` montre deux méthodes au corps vide, réduites à un commentaire `TODO`. Les deux entrées sont donc des contrôles décoratifs, contraires au critère 2. Traité en tâche 2a.5.

---

# LOT 2a — Barre supérieure et sélection de contenu

## Tâche 2a.1 — Étendre l'état de la bibliothèque

Préalable à tout le reste du lot. `LibraryUiState.kt` (inchangé depuis mon audit).

**Tri** — `LibrarySortOrder` n'a que 3 valeurs (`LibraryUiState.kt:55`). **Décision actée : « Récents » et « Récemment lus » sont fusionnés en une seule entrée**, le modèle n'ayant qu'un `lastOpened` (`Publication.kt:37`). Le tri cible passe donc de 5 à **4 entrées** :

| Cible révisée | État |
|---|---|
| Date d'import (défaut, récent d'abord) | ✅ `RECENTLY_ADDED` |
| Titre | ✅ `TITLE` |
| **Auteur** | ❌ à ajouter |
| Récents | ✅ `RECENTLY_OPENED` |

Une seule valeur à ajouter : `AUTHOR`, triant sur le premier auteur de `authors` (`Publication.kt:21`), livres sans auteur en fin de liste. Répercuter la fusion dans `UX_FLOW_DESIGN.md` (§ Popup de filtrage et § Bibliothèque état vide listent tous deux 5 entrées).

**Type de fichier** — absent de l'état. Ajouter un champ de sélection **multiple** (`Set<PublicationFormat>` ou équivalent), valeurs Tous / EPUB / TXT. `PublicationFormat` existe déjà avec `EPUB, TXT, PDF` (`Publication.kt:3`) — n'exposer que EPUB et TXT dans l'UI, PDF étant différé (ADR-017). Appliquer le filtre dans `displayedPublications` (`LibraryUiState.kt:37-52`).

**Disposition** — réduire `LibraryLayoutMode` de 3 à 2 valeurs : supprimer `GRID` (couverture + titre). C'est la variante explicitement écartée par la décision finale de la cible (§ Décision finale sur la disposition grille : « la grille couvertures seules est retenue telle que maquettée initialement — pas de titre ajouté sous la couverture »). Conséquences à traiter : `next()` (`60-64`), `icon()`/`label()` dans `LibraryScreen.kt`, le calcul `showTitle` de `LibraryContent`, et le défaut de `layoutMode` (`22`) qui passe à la mosaïque couvertures seules.

Le paramètre `showTitle` de `BookCover` (`BookCover.kt:68`) devient sans usage en grille — **ne pas le retirer à ce lot**, `PublicationListRow` l'utilise (`showTitle = false`). Le lot 2b tranchera.

`Étend l'état de la bibliothèque : tri par auteur, filtre par format, deux dispositions`

---

## Tâche 2a.2 — Popup de filtrage

Dépend de 2a.1.

Créer un **dialogue centré** (`AlertDialog` ou `Dialog`), pas un bottom sheet — choix délibéré de la cible pour le distinguer du menu 3-points. Quatre blocs :

1. **Trier par** — sélection unique, boutons radio. Défaut : Date d'import.
2. **Filtrer par** — sélection unique. Tous (défaut) / Non lu / En cours / Terminé → `FilterMode.ALL/UNREAD/IN_PROGRESS/READ`, qui existent déjà (`FilterMode.kt:23`).
3. **Mise en page** — deux icônes liste / mosaïque, état actif visuellement distinct.
4. **Type de fichier** — cases à cocher, **sélection multiple**, Tous / EPUB / TXT.

**Dans la barre du haut** (`LibraryTopBar`) : remplacer l'icône de tri et le bouton de bascule de disposition par **une seule icône filtre** qui ouvre ce dialogue. Retirer le `DropdownMenu` de tri (`isSortMenuExpanded` et son bloc) et l'`IconButton` de `onCycleLayout`. Ordre final de la barre : hamburger · titre · recherche · filtre · 3-points.

Retirer aussi `FilterRow` (rangée de chips sous la topbar) et son appel — le filtre vit désormais dans le dialogue. `LibraryIntent.CycleLayout` est remplacé par un intent de sélection directe de disposition.

**Icône :** utiliser un vrai symbole de filtre. La cible signale explicitement l'erreur à ne pas reproduire (§ Menu déroulant du titre : le ☰ pris pour un filtre est « un symbole de menu générique, pas un vrai symbole de filtre »).

`Remplace le tri et la bascule de disposition par le popup de filtrage`

---

## Tâche 2a.3 — Flyout du titre à deux colonnes

Dépend de 2a.1.

Remplacer le `ModalBottomSheet` à une colonne (`showNavPopup`, `LibraryScreen.kt:505+`) par un **flyout à deux colonnes** ancré sous le titre :

- **Colonne gauche, catégories :** Tous / Favoris / Séries / Tags. **Pas « Auteur »** (la cible l'exclut nommément de la navigation dédiée : le filtre par auteur reste simple, il vit dans le popup de filtrage depuis 2a.1). **Pas « Dossiers »** (concept legacy jamais demandé).
- **Colonne droite, sous-éléments :** uniquement pour Séries et Tags, chacun avec un compteur — format `Trilogie du Vide (3)`. Les données existent : `availableSeries`, `availableTags` (`LibraryUiState.kt:29-30`).

Comportement au clic :

| Catégorie | Action |
|---|---|
| Tous | filtre `ALL` sur la grille |
| Favoris | filtre `FAVORITES` sur la grille |
| Séries → un élément | **navigue** vers l'écran de détail (2a.4) |
| Tags → un élément | **navigue** vers l'écran de détail (2a.4), même patron |

Le titre de la barre reste adaptatif (déjà le cas) et le chevron est déjà correct depuis le lot 1.

**Animation :** transition standard. La cible a tranché l'abandon du morphing (§ Bibliothèque écran complet, point 2).

`Remplace le menu du titre par un flyout à deux colonnes`

---

## Tâche 2a.4 — Écran de détail Séries/Tags

Dépend de 2a.3. **Un seul écran réutilisable** pour les deux cas, seuls l'étiquette et la liste changent.

- Route typée dans `Routes.kt`, portant la catégorie (série ou tag) et la valeur sélectionnée.
- **Barre du haut :** flèche de retour (remplace le hamburger — on n'est plus à la racine), étiquette de catégorie en petites majuscules espacées (`SÉRIES` / `TAGS`), nom de l'élément en dessous en plus grand, tronqué en ellipse si trop long. Hiérarchie vérifiée : petite étiquette **au-dessus**, grand nom **en dessous**. Icônes conservées : **recherche et filtre uniquement**, ni 3-points ni hamburger.
- **Corps :** les livres correspondants en **vue Liste**, même disposition que la Bibliothèque peuplée. Cette liste bénéficiera automatiquement des corrections du lot 2b (cœur + 3-points + barre de progression) si elle réutilise le même composable de rangée — **la réutiliser, ne pas dupliquer**.

`Ajoute l'écran de détail partagé Séries et Tags`

---

## Tâche 2a.5 — Retirer les entrées mortes restantes

**Bottom sheet 3-points.** `LibraryViewModel.kt:75-81` : `regenerateCovers()` et `resetCovers()` ont un corps vide. Les deux entrées correspondantes sont décoratives. **Les retirer** du bottom sheet, ainsi que les intents `RegenerateCovers`/`ResetCovers` et les deux méthodes vides — pas de code mort laissé en place. Elles reviendront avec leur logique, à leur lot.

Le bottom sheet ne garde alors que « Importer » et « Actualiser ». Les trois actions cibles manquantes (« Couverture par défaut », « Reconstruire les couvertures », « Ouvrir un livre au hasard », « Synchroniser avec le cloud ») restent hors périmètre tant que leur logique n'existe pas — les deux premières sont précisément les couvertures ci-dessus.

**Drawer.** Une fois 2a.3 et 2a.4 livrés, retirer du drawer les sections transitoires marquées au lot 1 : les `SelectableFilters`, Séries, Auteurs, Tags. Retirer aussi la `TagsFilterBar` sous la topbar — troisième point d'entrée redondant pour la même donnée. Le drawer retombe sur ses 3 destinations + 2 boutons de pied.

**Scorie lot 1.** L'item « Bibliothèque » du drawer a `onClick = {}`. Lui faire au minimum refermer le drawer.

`Retire les actions de couverture sans logique et les filtres transitoires du drawer`

---

## Tâche 2a.6 — État vide

**Décision actée : les textes du document cible sont validés.** L'état vide entre donc dans le périmètre. `EmptyState` (`LibraryScreen.kt:741`) est à aligner :

| Élément | Actuel | Cible validée |
|---|---|---|
| Titre | « Bibliothèque vide » | « Votre bibliothèque est vide » |
| Corps | « Importez un EPUB pour commencer votre bibliothèque. » | « Importez votre premier livre pour commencer à lire et écouter avec InkTone. » |
| Bouton | « Importer des livres » | « Importer votre premier livre » |
| Illustration | `AppIcons.Reading` (icône générique) | Étagère avec emplacements de livres en pointillés |

**Sur l'illustration :** c'est un asset vectoriel à produire, pas un choix d'icône Material. Créer un `VectorDrawable` dédié. Si l'asset ne peut pas être produit dans ce lot, **le signaler explicitement** et livrer les trois textes seuls — ne pas laisser passer l'icône générique en la présentant comme conforme.

**Cas `hasActiveImport`** — le code gère une variante quand un import est en cours (« Import en cours… » / pas de bouton, `LibraryScreen.kt:741,758,763,770`). Ce cas est absent de la cible mais correspond à un état réel et utile. **Le conserver et le consigner** dans `UX_FLOW_DESIGN.md` comme ajout à la cible, plutôt que de le laisser en zone grise. À signaler dans le rapport de livraison pour arbitrage.

`Aligne l'état vide de la bibliothèque sur les textes validés`

---

## Tâche 2a.7 — Tests Compose

Étendre `feature/library/src/androidTest/.../LibraryDrawerContentTest.kt` ou créer les fichiers voisins.

1. Popup de filtrage : sélectionner un tri émet l'intent correspondant ; cocher EPUB puis TXT produit bien une sélection **multiple** (les deux cochés simultanément) ; changer la disposition émet l'intent.
2. Flyout : cliquer une série émet la navigation, pas un changement de filtre ; cliquer « Favoris » émet un changement de filtre, pas une navigation. C'est la distinction structurante de 2a.3.
3. Compteurs du flyout : une série à 3 tomes affiche `(3)`.
4. Non-régression : le drawer n'affiche plus de filtres ni de sections Séries/Auteurs/Tags ; le bottom sheet n'affiche plus les deux actions de couverture.

`Ajoute les tests Compose du filtrage et du flyout de titre`

---

## Vérifications sur appareil — lot 2a

| # | Avant (`87ad262`) | Après attendu |
|---|---|---|
| 1 | Barre : hamburger · titre · recherche · **tri** · **disposition** · 3-points | hamburger · titre · recherche · **filtre** · 3-points |
| 2 | Aucun filtrage par format de fichier | Cocher EPUB seul masque les TXT ; cocher les deux les réaffiche |
| 3 | Pas de tri par auteur | Trier par auteur réordonne réellement la liste |
| 4 | 3 dispositions en cycle, défaut = grille avec titres | 2 dispositions, défaut = mosaïque couvertures seules |
| 5 | Menu du titre : une colonne, 5 filtres, ni Séries ni Tags | Deux colonnes ; Séries et Tags présents avec compteurs |
| 6 | Cliquer une série applique un filtre sur place | Ouvre un écran dédié, titre à deux niveaux, retour fonctionnel |
| 7 | Bottom sheet : « Régénérer » et « Réinitialiser » ne font rien | Les deux entrées ont disparu |
| 8 | Drawer : filtres + Séries + Auteurs + Tags | 3 destinations + 2 boutons de pied, rien d'autre |
| 9 | Tags visibles à trois endroits | Uniquement dans le flyout du titre |
| 10 | Menu de tri : 3 entrées, pas d'auteur | 4 entrées, dont Auteur ; le tri par auteur réordonne réellement |
| 11 | État vide : « Bibliothèque vide » / « Importer des livres » | Textes validés de la cible, illustration étagère (ou signalement explicite si l'asset manque) |

---

# LOT 2b — Présentation des livres

**Ne pas démarrer avant clôture device de 2a.** Cible : `UX_FLOW_DESIGN.md` § Bibliothèque état peuplé.

## Tâche 2b.1 — Champ d'épinglage dans le domaine

`Publication` (`Publication.kt:17-38`) n'a **aucun champ** d'épinglage. Ajouter `isPinned: Boolean = false`, l'entité Room correspondante, **une migration Room explicite** (jamais de `fallbackToDestructiveMigration`, antipattern legacy catalogué), le use case de bascule sur le modèle de `ToggleFavoriteUseCase`, et la remontée des livres épinglés en tête de liste dans `displayedPublications`.

`Ajoute l'épinglage au modèle de publication`

---

## Tâche 2b.2 — Use case de suppression

`PublicationRepository.delete(id)` existe (`PublicationRepository.kt:20`) mais aucun use case ne l'expose. Ajouter `DeletePublicationUseCase` et l'intent correspondant.

**Ne rien écrire pour la cascade** : `BookmarkEntity`, `AnnotationEntity`, `ReadingStateEntity` et `ReadingSessionEntity` sont toutes déclarées `onDelete = ForeignKey.CASCADE` sur `publicationId`. La suppression des marque-pages et notes est déjà garantie par le schéma — l'avertissement de l'UI est donc factuellement exact sans logique supplémentaire. **Le vérifier par un test** plutôt que le supposer : supprimer une publication ayant marque-pages et annotations, constater qu'ils disparaissent.

`Ajoute la suppression de publication avec sa cascade vérifiée`

---

## Tâche 2b.3 — Popup d'actions par livre

Dépend de 2b.1 et 2b.2. Remplace `DecorativeDots` (`BookCover.kt:165`, `237-245`) — trois cercles sans `onClick`, purement décoratifs — par un **vrai menu 3-points** en bas-gauche de la couverture, sur fond sombre semi-transparent, ouvrant un popup d'actions :

| Action cible | Statut |
|---|---|
| Épingler | ✅ inclus (2b.1) |
| Détails du livre | ✅ inclus — bottom sheet alimenté par les champs déjà présents dans `Publication` : sous-titre, auteurs, éditeur, langue, description, format, taille, nombre de chapitres, sujets, date d'import. Aucune donnée à créer |
| ~~Télécharger la couverture~~ | **retirée de la cible, décision actée** — pas un report, une suppression. Mettre à jour `UX_FLOW_DESIGN.md` § Bibliothèque état peuplé pour que la liste n'en compte plus que 3 |
| Retirer de la bibliothèque | ✅ inclus (2b.2) |

Le popup compte donc **3 actions**, pas 4.

**Confirmation de suppression, texte obligatoire :** action irréversible, et précise que **les marque-pages et notes associés à ce livre seront également supprimés**. C'est acté deux fois dans la cible (§ Bibliothèque état peuplé et § Marque-pages vue globale). Ne pas l'abréger.

Corriger au passage le chevauchement : quand le titre était affiché, il partageait l'alignement `BottomStart` avec les points décoratifs. La disposition retenue en 2a.1 (couvertures seules) supprime le cas, mais vérifier qu'aucun autre appelant ne le réintroduit.

`Remplace les points décoratifs par le menu d'actions par livre`

---

## Tâche 2b.4 — Cœur, mode liste, retrait de la vue groupée

**Cœur au lieu de l'étoile.** `BookCover.kt:125-140` utilise `Icons.Filled.Star`/`Icons.Outlined.StarBorder` avec une teinte ambre codée en dur `Color(0xFFFFC107)`. La cible demande un **cœur** — contour si non favori, plein/coloré si favori. Passer par `MaterialTheme.colorScheme`, pas une couleur littérale : la convention du projet réserve les couleurs codées en dur au triplet de thème du Reader. Même correction dans `PublicationListRow`, qui duplique l'étoile.

**Mode liste** (`PublicationListRow`) — deux manques :
- **Cœur et 3-points côte à côte**, alignés à l'extrême droite, pas empilés. Décision affinée en deux allers-retours dans la cible, à respecter précisément.
- **Barre de progression pleine largeur sous chaque rangée**, avec le pourcentage à droite — une barre linéaire, pas le badge circulaire de la mosaïque. La donnée existe (`state.progressMap`) mais n'est **pas passée** au `BookCover` miniature aujourd'hui. La câbler.

**Retirer `SeriesGroupedView`** et son appel : rangées horizontales groupées par série insérées au-dessus de la grille, sans contrepartie dans le flux cible. Les séries passent désormais par le flyout et l'écran de détail (2a.3/2a.4).

**Préréglage d'accessibilité** — la cible a tranché (§ Décision finale, et § Réglages) : le préréglage bascule **aussi automatiquement vers le mode Liste**. `SettingsIntent.ApplyAccessibilityPreset` existe déjà ; y ajouter la bascule de disposition. Si `layoutMode` n'est pas persisté dans les préférences, le signaler plutôt que de le contourner.

`Remplace l'étoile par le cœur et complète le mode liste`

---

## Tâche 2b.5 — Tests Compose

1. Le menu 3-points d'une couverture ouvre le popup, et chacune de ses 3 actions émet son intent (garde-fou du critère 2 : `DecorativeDots` aurait échoué ce test).
2. La confirmation de suppression est **bloquante** : refuser n'appelle pas le use case ; accepter l'appelle une fois.
3. Le texte de confirmation mentionne bien les marque-pages et notes.
4. Mode liste : cœur et 3-points sont sur la même rangée, la barre de progression est présente et reflète `progressMap`.
5. Non-régression : plus aucune vue groupée par série au-dessus de la grille.

Ajouter aussi le test de cascade de 2b.2 côté `infrastructure/database` (androidTest, Room réel).

`Ajoute les tests Compose du menu par livre et de la suppression`

---

## Vérifications sur appareil — lot 2b

| # | Avant (fin 2a) | Après attendu |
|---|---|---|
| 1 | Trois points en bas de couverture : rien au tap | Ouvre un popup à 3 actions |
| 2 | Aucun moyen de supprimer un livre depuis l'app | Retirer → confirmation mentionnant marque-pages et notes → le livre disparaît |
| 3 | — | Après suppression, ouvrir Marque-pages et Notes : les entrées du livre supprimé ont disparu (cascade réelle, pas théorique) |
| 4 | Favori = étoile ambre | Favori = cœur, couleur issue du thème |
| 5 | Mode liste : étoile seule, pas de progression | Cœur + 3-points côte à côte à droite, barre de progression pleine largeur avec pourcentage |
| 6 | Rangées horizontales par série au-dessus de la grille | Disparues |
| 7 | Préréglage d'accessibilité : disposition inchangée | Bascule en mode Liste |
| 8 | — | Épingler un livre le remonte en tête ; l'état survit à un redémarrage de l'app (migration Room réelle) |

---

## Décisions actées avant lancement — à répercuter dans `UX_FLOW_DESIGN.md`

Ces trois points étaient ouverts ; ils sont tranchés. Aucun n'est un écart à traîner : la cible elle-même est mise à jour.

1. **Tri fusionné** — « Récents » et « Récemment lus » deviennent une seule entrée. Le tri passe de 5 à 4 entrées (§ Popup de filtrage, § Bibliothèque état vide).
2. **« Télécharger la couverture » retirée** de la cible. Le popup d'actions par livre passe de 4 à 3 actions (§ Bibliothèque état peuplé).
3. **Textes de l'état vide validés** tels qu'écrits dans le document — la mention « inventé par Claude, à valider » est à retirer (§ Bibliothèque état vide).

Ces mises à jour du document cible font partie du lot, pas d'un suivi séparé : la cible et le code avancent ensemble.

## Reste ouvert, non bloquant

- **Cas `hasActiveImport`** de l'état vide (2a.6) — présent dans le code, absent de la cible. Conservé et consigné, à arbitrer après vérification device.
- **Illustration de l'état vide** — asset vectoriel à produire ; si impossible dans ce lot, signalé plutôt que masqué.
- **Textes de l'onboarding** — toujours non validés, mais l'onboarding est hors périmètre du lot 2 de toute façon.

## Hors périmètre explicite du lot 2

Barre du haut du Reader, ligne de statut, panneau unifié, couche TTS, Récents, Synchronisation, Galerie de thèmes, Onboarding, cartes manquantes des Réglages, sections manquantes des Statistiques.
