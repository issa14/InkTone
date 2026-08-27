# Lot 24 — Application immédiate de la couleur/type et navigation par swipe

**Base :** `main` après merge du `LOT_23_POPUP_ANNOTATIONS_ET_COULEURS_LIBRES.md`.
Aucune migration Room dans ce Lot — comportement ViewModel/UI uniquement.

Source : deux retours d'usage direct d'Issa après vérification device du
Lot 23 (popup de sélection restauré près de la sélection, tâche 7
abandonnée — voir écart déclaré du Lot 23).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

---

## Constat vérifié (base du Lot)

1. **`AnnotationColorPicker` exige une confirmation explicite.**
   (`SelectionActionPopup.kt:189-197`, `AnnotationColorPicker.kt` fin de
   `Row` des pastilles) : taper une pastille ne fait que mettre à jour
   `pendingColor`/`pendingKind` (état local du popup) ; il faut ensuite
   taper le bouton `Button("Surligner")` pour réellement appeler
   `onHighlight` → `ReaderIntent.ConfirmAnnotation` →
   `ReaderViewModel.confirmAnnotation` (toujours une création,
   `AddAnnotationUseCase`, jamais de mise à jour). `Button("Annuler")`
   appelle `onCancel` = `onDismiss`, qui ferme tout le popup.
2. **Le popup se ferme déjà sur un tap en dehors du texte sélectionné**,
   mais pas par le mécanisme qu'on pourrait croire. `Popup` a
   `dismissOnClickOutside = false` (délibéré, cf. KDoc
   `SelectionActionPopup.kt:146-160` — protège les poignées de sélection
   natives). La fermeture réelle passe par
   `ReaderIntent.ClearFreeSelection` (`BookBlockItem`/
   `PagedChapterContent`, tap hors sélection dans le texte lu) qui vide
   `freeSelectionRange`/`freeSelectionAnchorOffset` ; `selectionBoundsInWindow`
   devient alors `null` et `SelectionActionPopup` retourne tôt
   (`if (selectionBoundsInWindow == null) return`, ligne 99). **Ce
   mécanisme existe déjà** — le point 1 de la demande d'Issa n'a donc pas
   besoin d'un nouveau geste de fermeture, seulement de ne plus fermer
   prématurément sur un tap de couleur/type.
3. **`UpdateAnnotationUseCase` existe déjà** (`domain/usecase/UpdateAnnotationUseCase.kt`,
   utilisé par Lot 22 tâche 11 pour l'édition de note) : aucun nouveau cas
   d'usage nécessaire pour appliquer une mise à jour de couleur/type en
   direct, seulement un nouveau point d'appel.
4. **`BookmarkPanel` a 3 onglets pilotés par un `Int` local**
   (`selectedTab`, `mutableIntStateOf`) et un `TabRow` classique — aucun
   `HorizontalPager`, donc aucun swipe. Le contenu par onglet est déjà un
   simple `when` sur `BookmarkPanelTab.entries[selectedTab]`
   (`BookmarkPanel.kt:167-171`), ce qui se prête directement à un
   `HorizontalPager` sans reprendre les trois composables `*Tab`.
5. **Le libellé du 3ᵉ onglet est `"Marque-pages"`**
   (`BookmarkPanelTab.BOOKMARKS`, `BookmarkPanel.kt:70`), déjà en
   `maxLines = 1` + `TextOverflow.Ellipsis` + police réduite d'un cran
   (correctif device antérieur, commentaire ligne 151-153) — la troncature
   que Issa observe est ce correctif qui coupe le mot sur un onglet
   étroit, pas un bug d'affichage nouveau. `UX_FLOW_DESIGN.md` nomme aussi
   ce même onglet "Marque-pages" à plusieurs endroits (§ Marque-pages —
   panneau latéral, lignes ~397/415/417/419) — ce Lot renomme uniquement
   *cet onglet précis*, pas le titre d'écran ("Marque-pages et notes") ni
   la destination de drawer globale ("Marque-pages et Notes"), non
   demandés et hors périmètre (voir décision 3).

---

## Décisions arrêtées

1. **Application immédiate, popup non fermé.** Taper une pastille de
   couleur ou une icône de type (Surlignage/Souligné/Barré) applique tout
   de suite l'annotation dans le texte et laisse le popup ouvert : un
   second tap sur une autre pastille/type **modifie la même annotation**
   (jamais une nouvelle superposée). Les boutons `Surligner`/`Annuler` de
   `AnnotationColorPicker` disparaissent : il n'y a plus rien à confirmer.
2. **Fermeture = tap en dehors, avant ou après application.** Le
   mécanisme déjà en place (constat 2) suffit : aucun nouveau geste,
   seulement retirer ce qui fermait prématurément le popup sur un choix de
   couleur.
3. **« Personnaliser » garde son flux à deux temps** (confirmé par Issa) :
   `CustomColorDialog` reste une boîte de dialogue avec bouton
   `Appliquer`/`Annuler` propre. Une fois validée, la couleur personnalisée
   suit la même règle que les préréglages (décision 1) : appliquée
   immédiatement à l'annotation en cours, popup toujours ouvert derrière.
4. **Le mode `NOTE_INPUT` n'est pas concerné.** Une note reste un texte
   qu'on rédige puis valide explicitement (`Enregistrer`) — l'application
   immédiate ne s'applique qu'au choix de couleur/type du mode
   `COLOR_PICKER` (surlignage sans note). Pas de demande d'Issa sur ce
   point, pas de changement.
5. **Renommage ciblé : `"Marque-pages"` → `"Signets"` pour le 3ᵉ onglet du
   panneau uniquement.** Le titre d'écran (« Marque-pages et notes ») et
   la destination de drawer restent inchangés — seul le libellé court
   tronqué change, cohérent avec la demande d'Issa ("libellé à changer").
6. **Swipe en complément du tap sur onglet, pas en remplacement.**
   `HorizontalPager` synchronisé bidirectionnellement avec `TabRow`
   (patron Material3 standard) : swiper change l'onglet actif et
   inversement, aucune des deux interactions n'est retirée.

---

## Tâches

### Palier A — Application immédiate de la couleur/du type

1. **État de l'annotation « en cours d'édition dans le popup »** :
   `ReaderUiState` gagne un identifiant nullable (ex.
   `pendingAnnotationId: String?`) qui distingue « aucune annotation créée
   pour cette sélection » de « une annotation existe déjà, la modifier ».
   Réinitialisé par `ReaderIntent.ClearFreeSelection` **et** dès que
   `freeSelectionRange` change vers une plage différente (point de
   vigilance : sans cette seconde remise à zéro, sélectionner un nouveau
   passage réutiliserait par erreur l'id de l'annotation précédente).
   Commit : `Ajoute l'etat d'annotation en cours d'edition au popup de selection`.
2. **`ReaderViewModel.confirmAnnotation` devient créer-ou-mettre-à-jour** :
   si `pendingAnnotationId == null`, comportement actuel (`AddAnnotationUseCase`,
   mémorise le nouvel id) ; sinon, relit l'annotation existante par cet id
   et appelle `UpdateAnnotationUseCase` avec la nouvelle couleur/le nouveau
   type (mêmes `startLocator`/`endLocator`/`content`/`createdAt`, seul
   `color`/`kind`/`updatedAt` changent). `recentAnnotationColors` continue
   à se mettre à jour à chaque application, y compris les suivantes.
   Commit : `Applique la couleur et le type d'annotation en direct`.
3. **`AnnotationColorPicker` perd ses boutons `Surligner`/`Annuler`** :
   chaque `ColorSwatch`/`AnnotationKindOption` appelle directement le
   callback d'application (renommer `onSelect`/`onConfirm` en un seul
   `onApply: (AnnotationColor) -> Unit` cohérent, `onSelectKind` appelle
   aussi l'application avec la couleur courante). `SelectionActionPopup`
   ne repasse plus en mode `ACTIONS` après un tap couleur/type — reste en
   `COLOR_PICKER` jusqu'à la fermeture externe du popup (décision 2).
   Commit : `Retire la confirmation explicite du selecteur de couleur`.
4. **`CustomColorDialog` applique immédiatement à la validation** :
   `onConfirm` du dialogue appelle désormais le même chemin d'application
   que les pastilles (décision 3), pas seulement `onSelect` (mise à jour
   de l'état local sans effet visible tant que `Surligner` n'existe plus).
   Commit : `Applique la couleur personnalisee immediatement a la validation`.
5. **Tests** : `pendingAnnotationId` nul → premier tap crée
   (`AddAnnotationUseCase` appelé) ; second tap sur une autre couleur/type
   met à jour la même annotation (`UpdateAnnotationUseCase` appelé avec le
   même id, pas de second `AddAnnotationUseCase`) ; nouvelle sélection
   (changement de `freeSelectionRange`) réinitialise `pendingAnnotationId`
   (le tap suivant crée, ne modifie pas l'annotation précédente).
   Commit : `Teste l'application en direct de la couleur et du type d'annotation`.

### Palier B — Signets et swipe entre onglets

6. **Renomme le libellé du 3ᵉ onglet** `BookmarkPanelTab.BOOKMARKS` de
   `"Marque-pages"` à `"Signets"` (décision 5). Vérifier que le test
   existant qui cible ce libellé (`BookmarkPanelTest`, à confirmer au
   palier) est mis à jour dans le même commit.
   Commit : `Renomme l'onglet Marque-pages en Signets`.
7. **`HorizontalPager` synchronisé avec le `TabRow` existant** (décision 6),
   patron Material3 standard : `rememberPagerState(pageCount = { BookmarkPanelTab.entries.size })`,
   `LaunchedEffect(selectedTab)` fait défiler le pager vers la page
   sélectionnée par tap, `LaunchedEffect(pagerState.currentPage)` met à
   jour `selectedTab` après un swipe — une seule source de vérité affichée
   à la fois, jamais de boucle de mise à jour mutuelle non convergente.
   Chaque page du pager appelle le `when` déjà existant (constat 4),
   aucune reprise des composables `NotesTab`/`HighlightsTab`/`BookmarksTab`.
   Commit : `Ajoute le swipe entre les onglets du panneau de signets`.
8. **Tests** : swipe change bien l'onglet affiché et son indicateur
   (`TabRow`) ; tap sur un onglet fait toujours défiler le pager vers la
   bonne page.
   Commit : `Teste le swipe entre les onglets du panneau de signets`.

---

## Ce qu'on ne fait pas dans ce Lot

- **Renommage du titre d'écran ou de la destination de drawer**
  (« Marque-pages et notes ») — seul le libellé du 3ᵉ onglet change
  (décision 5). Un renommage plus large nécessiterait de mettre à jour
  `UX_FLOW_DESIGN.md` en plusieurs endroits et n'a pas été demandé.
- **Application immédiate en mode `NOTE_INPUT`** (décision 4) — une note
  reste validée explicitement par `Enregistrer`.
- **Nouveau geste de fermeture du popup** — le mécanisme existant
  (`ClearFreeSelection` sur tap dans le texte) suffit (constat 2,
  décision 2).
- **Retrait du `TabRow`** — le swipe s'ajoute, ne remplace rien
  (décision 6).

---

## Points de vigilance (non négociables)

- **Une seule annotation par session de popup** : `pendingAnnotationId`
  doit être réinitialisé à chaque nouvelle sélection, pas seulement à la
  fermeture du popup — sinon un second passage sélectionné pourrait
  écraser silencieusement la couleur d'une annotation sans rapport.
- **`Locator` unique** : la mise à jour via `UpdateAnnotationUseCase` ne
  touche jamais `startLocator`/`endLocator` — seuls `color`/`kind`/
  `updatedAt` changent sur une couleur/un type appliqués en direct.
- **Trois canaux visuels séparés** (rappel Lot 22/23) : ce Lot ne change
  que le canal annotation, jamais le surlignage TTS ni la sélection.
- **`TabRow`/`HorizontalPager`** : convergence à sens unique par
  interaction (tap → pager suit ; swipe → tab suit), jamais les deux
  `LaunchedEffect` actifs en même temps sur le même changement (boucle).

---

## Critères de sortie du Lot

- [ ] Taper une pastille de couleur ou une icône de type applique
      l'annotation dans le texte sans fermer le popup ; retaper une autre
      pastille/type change l'aspect de la même annotation (pas de doublon).
- [ ] « Personnaliser » (sliders RGB) applique aussi immédiatement à la
      validation, sans bouton `Surligner` supplémentaire.
- [ ] Un tap en dehors de la sélection ferme le popup, que l'utilisateur
      ait déjà appliqué une couleur ou non — l'annotation appliquée avant
      fermeture reste telle quelle.
- [ ] Sélectionner un nouveau passage après avoir déjà annoté le
      précédent crée une nouvelle annotation, ne modifie jamais l'ancienne.
- [ ] Le 3ᵉ onglet du panneau de signets affiche « Signets » sans
      troncature.
- [ ] Le panneau de signets répond à la fois au tap sur un onglet et au
      swipe horizontal, dans les deux sens, sans désynchronisation entre
      l'indicateur d'onglet et le contenu affiché.
- [ ] `./gradlew build` vert.
