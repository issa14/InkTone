# Lot 3b — Chrome silencieux du lecteur

**Base :** `lot-2b-presentation-livres` à `be8533b` (lot 3a intégré). Référence cible : `UX_FLOW_DESIGN.md` § Lecture — vue silencieuse, § Lecture — HUD.

**Série :** 3a moteur de pagination ✅ → **3b chrome silencieux** (ce lot) → 3c sous-écrans du panneau → 3d couche TTS.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Décisions actées en amont

1. **Pages virtuelles dans les deux modes** — défilement et pagé.
2. **La Recherche reste une icône du panneau** — la rangée 3 de la cible passe de 4 à 5 icônes. Écart à consigner.
3. Le lot 3a ayant livré un moteur mesuré, **les numéros de page sont désormais des valeurs vérifiables**. Aucune valeur provisoire dans ce lot.

---

## Tâche 3b.1 — Remonter la pagination au-dessus du mode de rendu

Préalable à 3b.4. C'est la tâche structurante du lot, et la seule qui présente un vrai risque.

**Problème :** la mesure et le `VirtualPaginationEngine` vivent aujourd'hui **dans** `PagedChapterContent` (`PagedChapterContent.kt:135` et suivantes). En mode défilement, ce composable n'est pas monté — donc aucune pagination n'existe. Or la décision actée impose des pages virtuelles dans les deux modes.

**À faire :** hisser l'état de pagination au-dessus du choix de mode, pour qu'un seul calcul serve la ligne de statut *et* le rendu pagé.

- **Le calcul reste côté Compose, sous `ReaderScreen`.** `TextMeasurer` exige `Density`, `FontFamily.Resolver` et `LayoutDirection`, tous liés à la composition — il ne peut pas vivre dans le `ReaderViewModel`.
- **Ne pas faire transiter le résultat de mesure par un `ReaderIntent`.** Un aller-retour par le ViewModel crée une boucle état → recomposition → mesure → intent → état, avec une latence et un risque de rebouclage à chaque changement de style. Utiliser un porteur d'état au niveau composable sous `ReaderScreen`, passé en paramètre à ses deux consommateurs (ligne de statut et `PagedChapterContent`).
- `PagedChapterContent` devient **consommateur** de cet état au lieu de le produire. Il ne mesure plus lui-même.
- La mesure en deux temps de 3a.3 (première page prioritaire, reste en arrière-plan) est conservée telle quelle. Ne pas la refactoriser au passage.
- En mode défilement, la pagination sert uniquement à alimenter le compteur : ne rien changer au rendu défilant.

**Hauteur utile — formule unique pour les deux modes :**

```
hauteurUtile = hauteurViewport − paddingHaut − paddingBas
```

La même valeur doit être fournie au moteur quel que soit le mode. Deux pièges concrets :

- en pagé, `hauteurViewport` est celle de la zone du pager ; en défilement, celle de la zone de lecture visible — **ce doit être la même mesure**, prise au même endroit de l'arborescence ;
- la ligne de statut étant persistante (3b.4), elle réduit la zone de lecture **dans les deux modes**. Si elle n'est déduite que d'un côté, les totaux divergeront.

Sans cette unicité, le même chapitre annonce deux totaux différents selon le mode — incohérence immédiatement visible par l'utilisateur qui bascule. Vérifié par test (3b.7, test 3) et sur appareil (point 6).

`Remonte l'état de pagination au-dessus du mode de rendu`

---

## Tâche 3b.2 — Compléter la clé d'invalidation de pagination

Défaut latent trouvé à la revue du plan. Sans conséquence aujourd'hui, **bloquant au lot 3c**.

`PaginationStyleKey` (`VirtualPagination.kt:40-47`) déclare `lineHeightSp` et `fontFamilyKey`, mais `PagedChapterContent.kt:126-127` les alimente en dur :

```
lineHeightSp = fontSizeSp,
fontFamilyKey = "default",
```

Ni l'interligne ni la police n'étant réglables aujourd'hui, la clé se comporte correctement. Mais le panneau TT du lot 3c ajoute exactement ces deux réglages — et à ce moment-là la clé ne s'invalidera pas alors que la mise en page aura changé : **pagination périmée, sans aucun signal**. Le symptôme apparaîtra en 3c et sera attribué au panneau TT, pas à sa cause.

**À faire :** alimenter les deux champs depuis les valeurs de style réellement appliquées au rendu, en même temps que le déplacement de 3b.1. Si l'interligne et la police ne sont pas encore exposés dans l'état, les câbler sur la valeur effective utilisée par le `TextStyle` de rendu, pas sur une constante.

**Test associé** (3b.7, test 7) : deux `PaginationStyleKey` ne différant que par l'interligne, puis que par la famille de police, ne sont pas égales — donc invalident. Le test doit échouer sur le code actuel.

`Complète la clé d invalidation avec l interligne et la famille de police`

---

## Tâche 3b.3 — Exposer titre et auteur

`ReaderUiState.Ready` ne porte ni titre ni auteur — vérifié sur la totalité de la classe. La barre du haut ne peut donc rien afficher aujourd'hui.

Les ajouter à l'état, alimentés à l'ouverture de la publication. **Ne pas les recharger depuis un repository dans le composable** : l'état est la source unique de la vue.

`Expose le titre et l'auteur du livre dans l'état du lecteur`

---

## Tâche 3b.4 — Ligne de statut persistante

Dépend de 3b.1 et 3b.2.

Barre fine en bas d'écran, **hors HUD** — visible en permanence, y compris panneau masqué. Trois zones :

| Position | Contenu |
|---|---|
| Gauche | Heure locale, format 24 h |
| Centre | `Chapitre 3 (12/47)` — index de chapitre + page virtuelle |
| Droite | Progression du livre, **virgule décimale** : `34,7%` |

- La progression vient de `state.bookProgression` (`ReaderUiState.kt:69`), déjà calculée via `Locator.computeProgression()`. Formatage à une décimale, **virgule** et non point — locale française.
- Le compteur de pages vient du contrat `VirtualPagination`, jamais d'un calcul local.

**Horloge — alignement sur la minute pleine.** Un `produceState` cadencé toutes les 60 s à partir du moment où l'écran s'ouvre affiche une heure en retard de 0 à 59 s sur l'horloge système. Aligner le premier tick :

```
delay(60_000 - System.currentTimeMillis() % 60_000)
```

puis cadencer à la minute. **Réaligner au retour de veille** : un lecteur laissé en arrière-plan puis repris afficherait sinon une heure figée jusqu'au tick suivant. Suivre le cycle de vie plutôt que supposer que la coroutine a continué.

**Inserts système — obligatoire.** La ligne étant persistante et collée au bas de l'écran, la barre de gestes Android la recouvrirait. Appliquer `navigationBarsPadding()`.

À lire **via l'API d'insets, jamais en `dp` figé** : le lecteur est immersif, les barres système apparaissent et disparaissent, et l'insert change avec elles — une valeur constante serait fausse la moitié du temps. Prévoir aussi le cas paysage sur écran à découpe (`displayCutout`).

**Portée du compteur :** page **dans le chapitre courant**, soit `Chapitre 3 (12/47)` = page 12 sur 47 du chapitre 3. C'est la lecture la plus naturelle du format cible, non explicitée dans le document. **À confirmer à la vérification device** (point 5) ; si l'attendu est une pagination à l'échelle du livre, seule l'implémentation change, pas le contrat.

**Retirer le micro-indicateur ETA** (`ReaderScreen.kt:291-309`) : il occupe la même zone quand le HUD est masqué et n'existe pas dans la cible. `state.etaText` (`ReaderUiState.kt:96`) devient alors sans consommateur — le supprimer aussi, ou signaler s'il a un autre usage. Consigner ce retrait : l'ETA est une information réelle, sa suppression est un choix d'alignement, pas une évidence.

`Ajoute la ligne de statut persistante du lecteur`

---

## Tâche 3b.5 — Barre du haut du lecteur

Dépend de 3b.3. Le `ReaderScreen` n'a aujourd'hui **aucune barre du haut** (`ReaderScreen.kt:136`, `Column` nu) : la sortie repose entièrement sur le retour système.

Ajouter une barre appartenant au **HUD** — donc soumise à l'auto-masquage d'`ImmersiveReaderChrome`, pas persistante comme la ligne de statut :

- Flèche de retour à gauche → sortie du lecteur.
- Titre du livre, tronqué en ellipse sur une ligne.
- Auteur en dessous, plus petit et atténué.

Fond translucide cohérent avec le panneau du bas. Apparaît et disparaît **en même temps** que le panneau, jamais indépendamment.

`Ajoute la barre du haut du lecteur`

---

## Tâche 3b.6 — Restructurer le panneau unifié

`UnifiedControlPanel.kt` passe de 2 rangées à 3.

**Rangée 1 — barre de progression du livre.** Déplacer `BookProgressBar` depuis le haut de l'écran (`ReaderScreen.kt:147`) vers la première rangée du panneau. Elle cesse d'être persistante : c'est la ligne de statut (3b.4) qui porte désormais l'information permanente.

**Rangée 2 — 5 icônes, Play central proéminent :**

Sommaire · Marque-pages · **Play** · Thème · TT

- Le `FilledIconButton` 56 dp existant (`UnifiedControlPanel.kt:93-107`) est conservé tel quel.
- **Thème** — nouvelle action : bascule **cyclique** Clair → Sombre → Sépia, sans ouvrir de panneau, sans retour visuel autre que le changement lui-même. Le KDoc actuel (`UnifiedControlPanel.kt:48-51`) note que `onThemeCycle` avait été omis faute d'intent : l'intent existe désormais (`SetOverrides`, `ReaderUiState.kt:169`), l'omission n'a plus lieu d'être. Mettre à jour ce KDoc, qui deviendrait faux.
- **TT** — l'actuel « Aa » renommé, ouvre le panneau existant. Son contenu est retravaillé en 3c.
- **Marque-pages** — l'actuel « Signets » renommé.

**Rangée 3 — 4 icônes à ce lot :**

Minuteur · Haut-parleur · Mode défilement · Recherche

**Luminosité n'est pas ajoutée ici** : son action (barre flottante) arrive en 3c. L'afficher maintenant produirait une icône morte — même principe que le masquage des destinations sans écran au lot 1, et que la décision déjà prise dans le KDoc du panneau. La rangée 3 atteindra ses 5 icônes en 3c.

**Retraits :**
- Boutons chapitre précédent / suivant de la rangée 1 (`UnifiedControlPanel.kt:87,109`) : la cible les place dans la barre de contrôle TTS (lot 3d). **Vérifier avant de retirer** que le Sommaire permet toujours de changer de chapitre — sinon la navigation par chapitre disparaît pendant deux lots.
- Le `horizontalScroll` (`UnifiedControlPanel.kt:131`) : il compensait 7 actions sur une rangée. Avec 5 et 4, il n'a plus lieu d'être. Le retirer et **vérifier** l'absence de débordement sur petit écran, plutôt que le conserver par précaution.

**À ne pas retirer :** le bouton `+ Signet` en clair sous le panneau (`ReaderScreen.kt:350`). Il n'existe pas dans la cible, mais c'est la seule façon de poser un marque-page tant que le panneau Marque-pages de 3c n'est pas livré. Le laisser avec un commentaire `// Transitoire : remplacé par le toggle du panneau Marque-pages au lot 3c`.

`Restructure le panneau unifié en trois rangées`

---

## Tâche 3b.7 — Tests

1. **Ligne de statut, formatage** — virgule décimale, une seule décimale : `34,7%`, ni `34.7%` ni `34,70%`.
2. **Compteur de pages** — la ligne de statut affiche le `pageCount` du contrat, et il change quand la taille de police change.
3. **Cohérence entre modes** — pour un même chapitre, même style et même hauteur utile, le `pageCount` est **identique** en défilement et en pagé. C'est le garde-fou de 3b.1.
4. **Panneau** — les 9 icônes des rangées 2 et 3 émettent chacune leur intent ; aucune n'a de callback vide. Non-régression : pas d'icône Luminosité, pas de boutons chapitre précédent/suivant.
5. **Barre du haut** — la flèche de retour émet la sortie ; titre et auteur sont affichés depuis l'état.
6. **Persistance** — la ligne de statut reste affichée quand le HUD est masqué ; la barre du haut et le panneau disparaissent ensemble.
7. **Clé d'invalidation** — deux `PaginationStyleKey` ne différant que par `lineHeightSp`, puis que par `fontFamilyKey`, ne sont pas égales. Ce test doit **échouer sur le code actuel** (les deux champs y sont constants) : s'il passe avant la tâche 3b.2, c'est qu'il ne teste pas ce qu'il prétend.
8. **Thème et pagination** — non-régression du comportement déjà correct : un changement de thème ne modifie pas `PaginationStyleKey` et ne déclenche aucun recalcul.
9. **Horloge** — le premier délai calculé est bien `60_000 − (now % 60_000)` et non `60_000`.

`Ajoute les tests du chrome de lecture`

---

## Tâche 3b.8 — Consigner dans la cible

Ajouter dans `UX_FLOW_DESIGN.md`, § Lecture — HUD :

> **État d'implémentation (lot 3b).** Rangée 3 du panneau : **5 icônes** et non 4 — la Recherche dans le livre y est conservée, faute d'autre point d'entrée vers l'écran de recherche (décision actée). Luminosité absente jusqu'au lot 3c, son action n'existant pas encore. Navigation par chapitre retirée du panneau, en attente de la barre de contrôle TTS (lot 3d) ; entre-temps elle passe par le Sommaire. Micro-indicateur ETA retiré : absent de la cible.

`Consigne les écarts du chrome de lecture dans la cible`

---

## Vérifications sur appareil — lot 3b

| # | Avant (`be8533b`) | Après attendu |
|---|---|---|
| 1 | Aucune barre du haut ; sortie par le retour système seul | Flèche de retour, titre et auteur ; disparaissent avec le HUD après 4 s |
| 2 | Aucune ligne de statut | Heure, `Chapitre X (p/total)`, `34,7%` — **visibles même HUD masqué** |
| 3 | Barre de progression fine en haut, permanente | Déplacée en rangée 1 du panneau ; disparaît avec le HUD |
| 4 | Panneau : 2 rangées, 7 actions défilables horizontalement | 3 rangées, 5 + 4 icônes, **aucun défilement horizontal**, aucun débordement sur petit écran |
| 5 | — | Confirmer que `(12/47)` désigne bien la page **dans le chapitre** et non dans le livre |
| 6 | — | Basculer défilement ↔ pagé sur le même chapitre : **le total de pages ne change pas** |
| 7 | — | Avancer dans le texte en défilement : le compteur de page progresse aussi (pas figé à 1) |
| 8 | Aucune bascule de thème rapide | L'icône Thème cycle Clair → Sombre → Sépia, changement immédiat |
| 9 | Navigation par chapitre dans le panneau | Retirée ; le Sommaire permet toujours de changer de chapitre |
| 10 | Micro-indicateur ETA en bas quand HUD masqué | Remplacé par la ligne de statut |
| 11 | — | Le bouton `+ Signet` est toujours là et fonctionne (transitoire jusqu'à 3c) |
| 12 | — | Barre de gestes Android affichée : la ligne de statut reste **entièrement lisible**, non recouverte. Idem en paysage |
| 13 | — | Laisser l'app en arrière-plan 3 min, revenir : l'heure affichée est juste immédiatement, pas au tick suivant |
| 14 | — | Cycler le thème pendant la lecture en mode pagé : changement **instantané**, aucun reflux du texte, le total de pages ne bouge pas |

Les points 6 et 7 sont ceux que je passerais en premier : ils vérifient la tâche 3b.1, seule tâche à risque réel du lot.

---

## Hors périmètre explicite

Sommaire en bottom sheet, panneau Marque-pages à 3 onglets, panneau TT (aperçu live, interligne, curseur continu), Luminosité, Minuteur complet et rappel de repos oculaire, panneau Voix et son curseur de vitesse mort, popup de sélection Copier/Surligner/Note, hiérarchie du sommaire (`children` jamais observé non vide, `TableOfContentsSheet.kt:31`) → **lot 3c**.

Barre pilule TTS, repli en FAB, onde sonore, swipe-down → **lot 3d**.
