# Lot 3c — Position de lecture et sous-écrans de navigation

**Base :** `lot-2b-presentation-livres` à `b90c26e` (lots 3a et 3b intégrés). Référence cible : `UX_FLOW_DESIGN.md` § Lecture — HUD (Sommaire, Marque-pages), § Popup de sélection de texte.

**Série révisée :** 3a moteur ✅ → 3b chrome ✅ → **3c navigation** (ce lot) → 3d sous-écrans de réglages → 3e couche TTS → 3f sélection libre au mot (conditionnel, voir tâche 3c.5).

Le panneau compte huit sous-écrans. Les traiter en un lot unique reproduirait le fourre-tout que le découpage cherche à éviter. Coupe retenue : **navigation** ici (ce qui déplace le lecteur ou agit sur le texte), **réglages** en 3d (ce qui change l'apparence ou la voix). Surfaces disjointes.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

---

## Tâche 3c.1 — Position de lecture en mode défilement

**Priorité du lot.** Correctif reporté du lot 3b, à traiter en premier : il conditionne la cohérence de la ligne de statut et referme un antipattern legacy.

**État constaté.** Le lot 3b a corrigé le compteur de pages en pagé via `pagedLivePageIndex`, mais en défilement il retombe sur une estimation par fraction (`ReaderScreen.kt:400-405`). Le commentaire du code le documente honnêtement. Deux conséquences que la correction n'a pas couvertes :

1. **Le pourcentage reste figé.** `bookProgression` (`ReaderScreen.kt:412`) dérive de `currentSentenceIndex`, que rien ne met à jour pendant un défilement manuel — c'est exactement ce que constate le commentaire (`ReaderScreen.kt:386-387`). La ligne de statut affiche donc un compteur central qui avance et un pourcentage à droite immobile. Deux chiffres côte à côte, dérivés de sources différentes, qui se contredisent visiblement.
2. **La position de lecture n'est pas persistée en défilement silencieux.** C'est l'antipattern legacy déjà catalogué : la position n'était sauvegardée que pendant le TTS actif. Un utilisateur qui lit trois chapitres sans TTS puis ferme l'app rouvre là où le TTS s'était arrêté.

**L'estimation par fraction est en plus fausse par construction :** elle suppose une densité de texte uniforme sur le chapitre. Depuis que le lot 3a a restauré les `ParagraphStyle`, un chapitre à titres a des hauteurs de ligne inégales — la dérive croît avec la structure du chapitre.

**À faire :** dériver la phrase visible la plus haute, puis la page via le contrat.

- Le mode défilement dispose déjà d'un positionnement par phrase : `onGloballyPositioned` est utilisé pour la règle de lecture. S'en servir pour identifier la première phrase visible.
- En déduire la page par `pageIndexAt(chapterIndex, sentenceIndex)` — **page exacte**, et **source unique** avec le mode pagé, exactement l'argument qui a fait choisir `pagedLivePageIndex` côté pager.
- Remonter cette phrase dans `currentSentenceIndex` : le pourcentage redevient cohérent et la position de lecture se persiste enfin en défilement silencieux.

**Attention à la boucle.** `currentSentenceIndex` pilote aussi l'auto-scroll vers la phrase active pendant le TTS (`ReaderScreen.kt:194-197`). Écrire depuis le scroll et lire pour scroller peut osciller.

**Deux contraintes d'implémentation :**

1. **Encapsuler la détection dans un `derivedStateOf`.** Sans cela, la phrase visible est recalculée à chaque pixel de défilement et inonde la composition. Le `derivedStateOf` ne propage que les changements de **phrase**, pas de position.

2. **Ne pas utiliser `isScrollInProgress` comme garde.** C'est le réflexe naturel et il est faux ici : `ScrollableState.isScrollInProgress` vaut `true` pour **tout** défilement, y compris `animateScrollTo` programmatique — donc précisément pendant l'auto-scroll TTS que la garde doit exclure. La boucle resterait ouverte.

   Discriminer sur l'origine réelle du geste : soit la source d'interaction (`scrollState.interactionSource`, drag utilisateur), soit un drapeau posé explicitement autour de l'appel programmatique et levé à sa fin. **Vérifier ce point par test** (3c.6, test 2), pas par observation.

**Ne pas persister à chaque pixel** : throttler l'écriture (au changement de phrase, pas à chaque frame). Le `derivedStateOf` du point 1 y contribue mais ne suffit pas à lui seul pour l'écriture en base.

`Corrige la position de lecture en mode défilement`

---

## Tâche 3c.2 — Sommaire en bottom sheet

`TableOfContentsSheet.kt` — inchangé depuis l'audit initial, constats toujours valides.

**Divergences à corriger :**

| Cible | État |
|---|---|
| Bottom sheet | **Remplace tout l'écran** — `ReaderScreen.kt:164-172`, `return@Column` après le composable |
| Titre « Table des matières » | « Sommaire » (`TableOfContentsSheet.kt:72`) |
| Chapitre courant centré à l'ouverture | ✅ déjà fait (`TableOfContentsSheet.kt:55-58`) |
| Indentation hiérarchique | ✅ codée (`TableOfContentsSheet.kt:40-41,93`) mais **jamais vérifiée** |

**Contrainte d'implémentation :** `ModalBottomSheet` avec `skipPartiallyExpanded = true`. Sans cela, la feuille s'ouvre à mi-écran et un sommaire hiérarchique long s'y trouve tronqué à l'ouverture. Le centrage sur le chapitre courant décrit par la cible porte sur la **position de défilement** dans la feuille, pas sur sa hauteur — les deux ne s'opposent pas.

**Le point dur, c'est la hiérarchie.** `TableOfContentsSheet.kt:31-36` porte un TODO explicite : `children` n'a jamais été observé non vide sur un EPUB réel depuis les Fondations. L'indentation est donc du code jamais exercé — ni prouvé faux, ni prouvé juste.

À traiter par une **fixture EPUB à sommaire imbriqué** (parties contenant des chapitres) ajoutée aux tests, pas par une inspection visuelle. Deux issues possibles, toutes deux acceptables :

- `children` est bien peuplé par Readium → l'indentation fonctionne, le TODO se retire, un test le verrouille ;
- `children` reste vide → **le dire**, retirer le code d'indentation mort plutôt que le laisser en place, et consigner que la hiérarchie n'est pas disponible avec le parseur actuel.

Ne pas clore cette tâche sur « ça a l'air de marcher ».

`Convertit le sommaire en bottom sheet et vérifie la hiérarchie`

---

## Tâche 3c.3 — Panneau Marque-pages

`BookmarkListSheet.kt` — le sous-écran le plus éloigné de la cible.

**État :** remplace l'écran entier (`ReaderScreen.kt:174-182`, `return@Column`), **aucun onglet**, n'affiche **que les signets**, chaque ligne étant un titre suivi d'un « Supprimer » en texte (`BookmarkListSheet.kt:164-183`). Ni notes, ni surlignages, ni bouton de marquage.

**Cible :** panneau latéral gauche occupant ~85 % de la largeur, avec **3 onglets** — Notes · Surlignages · Marque-pages — et un bouton **toggle** « Marquer cette page » en tête.

- Les surlignages et notes viennent des annotations, déjà présentes dans l'état (`ReaderUiState.annotations`). Pas de nouvelle source à créer.
- Le bouton est un **toggle** : il reflète l'état de la page courante et permet de retirer le marque-page, pas seulement d'en ajouter.
- **Retirer alors le bouton `+ Signet`** en clair sous le panneau (`ReaderScreen.kt:350`), maintenu à titre transitoire depuis le lot 3b avec son commentaire. Son remplaçant existe désormais — c'est la condition posée à l'époque.

`Remplace la liste de signets par le panneau à trois onglets`

---

## Tâche 3c.4 — Popup de sélection de texte

**État :** `AnnotationColorPicker` (`ReaderScreen.kt:336`) — un sélecteur de **couleur** de surlignage avec Confirmer/Annuler, rendu en bas de l'écran, loin de la sélection. Ni « Copier », ni « Note ».

**Cible :** bloc positionné **près de la sélection**, trois options — Copier · Surligner · Note.

- **Copier** : mise en presse-papier du texte sélectionné, avec retour bref.
- **Surligner** : conserve le choix de couleur existant, mais en second temps — le premier niveau reste les trois verbes.
- **Note** : saisie de texte associée à la sélection. **Aucune migration Room n'est nécessaire** : le champ existe déjà, `Annotation.content: String?` (`Annotation.kt:17`) et `AnnotationEntity.content: String?` (ligne 29), la base restant en version 11. L'antipattern legacy catalogué n'était pas « le champ manque » mais « le champ n'est jamais rempli » — le travail est du câblage UI et de la persistance, pas du schéma. Conserver la nullabilité telle quelle : `null` distingue un surlignage sans note d'une note vidée volontairement.

**Positionnement du popup — contrainte d'implémentation.** Ne pas se baser sur des coordonnées d'écran calculées à la main. Utiliser un `PopupPositionProvider` alimenté par les `LayoutCoordinates` réelles de la zone sélectionnée, pour que le bloc suive la sélection lors d'un défilement, d'une rotation ou d'un changement de taille de police.

**Écart connu à conserver et re-consigner :** la sélection se fait **par phrase** (appui long puis extension), limitation d'API documentée et assumée (`ReaderScreen.kt:77-89` — `Selection`/`SelectionContainer` sont `internal` à `compose.foundation`, vérifié par le compilateur et non supposé). Ce lot ne change pas ce comportement ; il change ce que le popup propose. Le re-consigner explicitement dans la cible, qui décrit une sélection libre.

`Remplace le sélecteur de couleur par le popup de sélection de texte`

---

## Tâche 3c.5 — Prototype : sélection sur-mesure au mot

**Prototype jetable, hors production.** Ne rien livrer dans `main` depuis cette tâche : elle produit un branchement d'essai et une note de conclusion. L'implémentation réelle est un lot dédié (**3f**), déclenché seulement si le prototype conclut favorablement.

**La faisabilité n'est plus la question.** L'approche est arrêtée : `Modifier.pointerInput` + `detectDragGesturesAfterLongPress`, puis `TextLayoutResult.getOffsetForPosition()` pour l'index de caractère, `getWordBoundary(offset)` pour caler la sélection sur le mot, bornes stockées en état, rendu par `SpanStyle` superposé dans l'`AnnotatedString`, popup ancré sur les `LayoutCoordinates` de l'intervalle. C'est le mécanisme du `SelectionContainer` natif réimplémenté sans son API `internal`, et il conserve l'accès programmatique aux bornes dont `Annotation` a besoin.

**Ce que le prototype doit trancher, et rien d'autre :**

1. **Le rendu du mode défilement.** L'approche suppose un `TextLayoutResult` unique — vrai en pagé depuis le lot 3a, **faux en défilement** : `ReaderScreen.kt:244-266` rend encore un `FlowRow` de `SentenceText` séparés, donc un layout par phrase. Deux options à départager par la mesure, pas par préférence :
   - unifier le défilement sur un bloc mesuré comme en pagé — cohérent, mais un chapitre entier en un seul `Text` a un coût à chiffrer sur un roman long ;
   - router le tap vers le `TextLayoutResult` de la phrase touchée puis convertir en offset de chapitre — local, sans risque de performance, moins élégant.

2. **Le conflit de gestes en pagé.** Le drag horizontal après appui long coexiste avec le swipe du `HorizontalPager`. Vérifier qu'étendre une sélection vers la droite ne tourne pas la page, et documenter la consommation d'événement retenue.

3. **La conversion d'espace de coordonnées.** `getOffsetForPosition` renvoie un offset **local au texte mesuré** ; le lot 3a a délibérément retenu ces offsets locaux plutôt que `Sentence.startOffset` pour éviter un bug d'espace silencieux. `Annotation` s'adressant en `Locator`, prouver la conversion locale → chapitre → `Locator` dans les deux sens, sur un chapitre à titres.

**Portée du prototype :** mode pagé uniquement. Le mode défilement est l'objet de la décision 1, pas un périmètre à couvrir.

**Conclusion attendue**, écrite et chiffrée : option retenue pour le défilement, résultat du conflit de gestes, preuve de la conversion de coordonnées, et estimation du lot 3f. Si le prototype échoue sur l'un des trois points, la sélection par phrase est **actée dans la cible comme comportement définitif** — ce qui ferme le sujet aussi valablement.

`Prototype la selection au mot et tranche le rendu du mode defilement`

---

## Lot 3f — Sélection libre au mot (conditionnel)

**Non planifié à ce stade.** Déclenché uniquement sur conclusion favorable de la tâche 3c.5. Recensé ici pour que son périmètre ne se dissolve pas dans un autre lot :

- sélection au mot par appui long, extension par glissement ;
- **poignées persistantes** ajustables après relâchement du doigt — `detectDragGesturesAfterLongPress` s'arrête au relâchement, la sélection se fige ; il faut des composables de poignée dédiés avec leur propre `pointerInput` et une cible tactile d'environ 48 dp ;
- **auto-scroll pendant le glissement** pour sélectionner au-delà de la zone visible ;
- **sélection à cheval sur deux pages** en mode pagé : autorisée, bornée à la page, ou interdite — à trancher ;
- **sémantiques d'accessibilité.** Une sélection sur-mesure perd les sémantiques natives de sélection de texte, donc l'annonce TalkBack. Sur une application dont la mission est l'accessibilité de la lecture, c'est un critère de sortie, pas une finition — le lot 1 a déjà eu un correctif sur le chevauchement TalkBack/TTS ;
- **retrait de l'ancien modèle** : `selectionAnchorIndex`/`selectionFocusIndex` (`ReaderUiState.kt:42-43`), les intents `BeginSentenceSelection`/`ExtendSentenceSelection` (`ReaderViewModel.kt:134-138`) et le contournement documenté (`ReaderScreen.kt:77-89`) deviennent obsolètes. Les retirer, pas les faire cohabiter : deux modèles de sélection simultanés seraient une source de divergence.

---

## Tâche 3c.6 — Tests

1. **Position en défilement** — faire défiler sans TTS met à jour `currentSentenceIndex` ; le pourcentage et le compteur de page dérivent alors de la **même** valeur et ne peuvent pas diverger.
2. **Anti-boucle** — un défilement programmatique (auto-scroll TTS) ne réécrit **pas** la position, seul un défilement utilisateur le fait. Test à écrire de façon à **échouer** si la garde repose sur `isScrollInProgress`, qui vaut `true` dans les deux cas.
2 bis. **Fréquence** — faire défiler sur toute la hauteur d'un chapitre ne déclenche qu'un nombre d'émissions de l'ordre du nombre de phrases traversées, pas du nombre de frames (garde-fou du `derivedStateOf`).
3. **Persistance** — fermer puis rouvrir après un défilement silencieux restitue la position atteinte, pas celle du dernier arrêt TTS. C'est le test de non-régression de l'antipattern legacy.
4. **Sommaire** — s'ouvre en bottom sheet sans démonter le lecteur ; le contenu reste visible derrière. Test de hiérarchie sur la fixture EPUB imbriquée (3c.2).
5. **Marque-pages** — les 3 onglets affichent chacun leur source ; le toggle ajoute **et** retire ; le panneau ne démonte pas le lecteur.
6. **Popup de sélection** — les 3 actions émettent leur intent ; « Note » persiste réellement un texte dans `Annotation.content` et le relit. Un surlignage sans note conserve `content = null`, distinct d'une note vidée.
6 bis. **Positionnement** — le popup suit la zone sélectionnée après un défilement et après rotation.
7. **Non-régression** — plus de bouton `+ Signet` ; plus de `return@Column` sur les sous-écrans.

`Ajoute les tests de position de lecture et des sous-écrans de navigation`

---

## Tâche 3c.7 — Consigner dans la cible

Dans `UX_FLOW_DESIGN.md`, § Lecture — HUD :

> **État d'implémentation (lot 3c).** Sommaire et Marque-pages passent en surfaces superposées ; le lecteur n'est plus démonté. Hiérarchie du sommaire : [résultat de 3c.2, à écrire selon ce que la fixture démontre]. **Sélection de texte par phrase** (appui long puis extension) et non libre : limitation d'API Compose documentée et assumée depuis la Tâche 7.0 — le popup Copier/Surligner/Note s'applique à la sélection par phrase. Luminosité toujours absente de la rangée 3 (lot 3d).

`Consigne l état des sous-écrans de navigation dans la cible`

---

## Vérifications sur appareil — lot 3c

| # | Avant (`b90c26e`) | Après attendu |
|---|---|---|
| 1 | En défilement, le pourcentage reste figé pendant qu'on fait défiler | Le pourcentage progresse avec le défilement, cohérent avec le compteur de pages |
| 2 | Le compteur de page en défilement dérive sur un chapitre à titres | Page exacte, cohérente avec ce qui est affiché |
| 3 | Lire 2 chapitres sans TTS, fermer l'app, rouvrir → reprise au dernier arrêt TTS | Reprise là où la lecture s'est arrêtée |
| 4 | — | Lancer le TTS après un défilement manuel : pas d'oscillation, pas de saut de position |
| 5 | Sommaire : remplace tout l'écran | Bottom sheet ; le texte reste visible derrière |
| 6 | — | Sur un EPUB à parties et chapitres, l'imbrication est **soit** visible et correcte, **soit** déclarée indisponible |
| 7 | Marque-pages : liste de signets seule, plein écran | Panneau latéral, 3 onglets remplis ; le toggle ajoute et retire |
| 8 | Bouton `+ Signet` sous le panneau | Disparu, remplacé par le toggle |
| 9 | Sélection : sélecteur de couleur en bas d'écran | Popup près de la sélection, Copier / Surligner / Note |
| 10 | — | « Note » : saisir un texte, fermer, rouvrir le livre → le texte est toujours là |

Les points 1 à 4 valident la tâche 3c.1, seule tâche à risque réel du lot — le point 4 en particulier, qui piège l'oscillation.

---

## Hors périmètre explicite

Panneau TT (aperçu live, interligne, curseur continu), Thème cyclique déjà livré en 3b mais dont le panneau reste à nettoyer, Luminosité et sa 5ᵉ icône de rangée 3, Minuteur complet (chips 15/30/45 + roue personnalisée) et rappel de repos oculaire, panneau Voix (curseur de vitesse mort, sélecteur de voix, lien prononciation, bouton Stop câblé sur `Pause`) → **lot 3d**.

Barre pilule TTS, repli en FAB, onde sonore, swipe-down → **lot 3e**.

Implémentation de la sélection au mot (poignées, auto-scroll pendant le glissement, sélection à cheval, sémantiques d'accessibilité, retrait de l'ancien modèle) → **lot 3f**, conditionné à la conclusion de la tâche 3c.5. Le lot 3c livre le popup Copier / Surligner / Note **sur la sélection par phrase existante** ; seul le mécanisme de sélection est en jeu au 3f, pas le popup.

**Conclusion 3c.5** (voir `docs/execution/NOTE_3C5_PROTOTYPE_SELECTION_MOT.md`) : les trois points sont favorables, y compris le conflit de geste drag/pager — jugé bloquant sur une première mesure trop faible, levé sur mesure robuste (30/30, protocole renforcé). Ça rouvre la décidabilité du lot 3f sans le déclencher : la sélection par phrase reste le comportement livré par ce lot.

---

## Écart de périmètre signalé après coup

Le commit `763aaa2 Ajoute le rapport de crash Firebase Crashlytics en opt-in (ADR-014)`, livré sur cette branche pendant ce lot, **ne relève pas du lot 3c** — c'est le consentement crash reporting, renvoyé par la cible à l'écran d'onboarding. L'implémentation technique a été vérifiée (build, architecture, lancement sur appareil), mais **le flux de consentement UX n'a pas été audité dans le cadre de ce lot** : ni checklist ni tâche 3c ne le couvraient. Inscrit au périmètre du lot Onboarding pour audit (`docs/execution/LOT_ONBOARDING_PERIMETRE.md`) — non retiré de la branche, retirer un commit isolé après coup créerait du remous pour rien.
