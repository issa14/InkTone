# ADR-029 : Moteur de sélection maison pour le mode défilement

**Status :** Accepted
**Date :** 2026-11-16

## Context

En mode défilement, chaque paragraphe est un item de `LazyColumn`. La sélection
libre (`ReaderUiState.freeSelectionRange`, offsets absolus du chapitre) servait
jusqu'ici la sélection native de Compose à travers un `BasicTextField` par
paragraphe, ce qui avait trois défauts :

- un champ par paragraphe coûtait trop cher au défilement (audit réactivité
  §4.1) ; le monter seulement au paragraphe sélectionné a cassé le menu et les
  poignées, Compose ne les montrant qu'après un geste dans le champ (lot 25,
  constat) ;
- une sélection ne pouvait pas franchir un paragraphe (audit UX D50) ;
- le défilement était bloqué tant qu'une sélection existait
  (`userScrollEnabled = freeSelectedRange == null`).

Le palier 25.0 (`1445590f`) a rendu tous les paragraphes en `Text` : la
sélection est dessinée depuis la plage absolue dans chaque bloc qu'elle touche,
et l'appui long publie le mot et les bornes du popup. Il manque les poignées, la
loupe, le glissement d'un paragraphe à l'autre et le défilement automatique.

## Options

**A. Correctif minimal** (rendre le focus au champ et forcer le menu) : écartée.
Elle contourne Compose, garde le coût du champ et la limite au paragraphe.

**B. `SelectionContainer` natif autour de la `LazyColumn`** : écartée sur
lecture de l'API, sans prototype.

- Foundation 1.7.2 (BOM 2024.09.02, celui du projet) : la surcharge qui expose
  la sélection (`selection: Selection?`, `onSelectionChange`) est `internal`, comme
  le type `Selection`. La surcharge publique ne rend jamais d'offsets.
- Foundation 1.13 (alpha, août 2026) ajoute `SelectionState` : il ne publie que
  `selectedTexts: List<AnnotatedString>`, sans offsets ni identité du `Text`
  d'origine. Retrouver `freeSelectionRange` depuis ces textes serait ambigu dès
  qu'un passage se répète.
- La sélection d'une `LazyColumn` se perd quand ses éléments quittent la
  composition.

Sans offsets absolus, ni surlignage ni note ne peuvent être créés : B ne remplit
pas le besoin, quelle que soit sa fluidité.

**C. Moteur maison au niveau de la liste** : retenue.

## Decision

1. **État** : inchangé. `freeSelectionAnchorOffset`/`freeSelectionFocusOffset`
   (offsets absolus du chapitre) restent la seule source de vérité ; aucune
   migration, aucun second système d'adressage (`Locator` seul).
2. **Rendu** : chaque paragraphe dessine la part de la plage qui le touche
   (`absoluteRangePath`), comme depuis 25.0.
3. **Registre des blocs visibles** : chaque paragraphe composé déclare sa plage
   absolue, son `TextLayoutResult` et ses coordonnées, et se retire en quittant la
   composition. Le registre convertit une position fenêtre en offset absolu
   (bloc le plus proche verticalement, puis `getOffsetForPosition`) et une plage
   absolue en points d'ancrage des poignées et en bornes de popup (union des
   blocs visibles).
4. **Poignées** : dessinées par le Lecteur, en surimpression de la liste, aux
   extrémités de la plage quand elles sont visibles ; cible tactile de 48 dp.
   Glisser une poignée garde l'autre extrémité fixe et déplace celle-ci au
   caractère ; croiser l'autre poignée inverse les rôles sans perte.
5. **Loupe** : `Modifier.magnifier` (public et stable dans foundation 1.7.2,
   sans opt-in), centrée sur le doigt pendant le glissement d'une poignée.
6. **Défilement** : libre avec une sélection active. Pendant un glissement de
   poignée, la liste défile d'elle-même quand le doigt approche du bord haut ou
   bas. Le popup est masqué pendant tout glissement ou défilement et réaffiché
   à l'arrêt si la plage a une partie visible.
7. **Accessibilité** : chaque paragraphe expose l'action sémantique
   `setSelection`, qui publie la même plage absolue que le geste.
8. **Mode paginé** : inchangé dans ce lot (`PageBlock`, sélection native par
   page). L'extension du moteur aux pages (D50, partie page) fera l'objet d'une
   décision séparée.

## Consequences

- Plus aucun `BasicTextField` en défilement : même coût de rendu qu'un `Text`.
- Le Lecteur porte du code de geste et de dessin de poignées que Compose
  fournissait ; la logique pure (extension, inversion, bornage, choix du bloc
  sous le doigt) est isolée et testée en JVM.
- Écart déclaré : le spike prévu au palier 25.2 sur la cohabitation des gestes et
  le défilement automatique n'a pas été fait à part ; ces deux risques sont
  vérifiés sur appareil au palier 25.3, avant tout commit.
- Si une version future de Compose expose des offsets de sélection publics,
  l'option B pourra être réexaminée : ce choix ne touche ni l'état ni la
  persistance.
