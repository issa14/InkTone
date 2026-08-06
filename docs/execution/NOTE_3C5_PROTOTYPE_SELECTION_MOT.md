# Note de conclusion — Tâche 3c.5, prototype de sélection sur-mesure au mot

**Statut : conclusion définitive, prototype jetable, aucun code livré dans
`main`.** Les trois questions ont été tranchées — deux par lecture du code
de production, une par test empirique sur appareil réel (V2206). Le
prototype ayant échoué sur cette dernière, la sélection par phrase est
actée comme comportement définitif (voir Conclusion).

## Précédent — constats du spike `SelectableSentenceSpike` (Tâche 1.1.1)

Avant de trancher les trois questions ci-dessous, un point d'histoire : un
premier spike de sélection existait déjà dans le dépôt
(`SelectableSentenceSpike.kt`, Partie 1 — Fondations UI, jamais référencé
depuis, retiré dans le même commit que cette note). Il explorait une
approche différente de celle retenue par ce lot — un `SelectionContainer`
natif par phrase avec interception de `LocalTextToolbar`, plutôt que
`pointerInput`/`getOffsetForPosition`/`getWordBoundary` — mais ses constats,
validés sur device (V2206, 2026-07-30), restent des faits établis sur la
plateforme, pertinents pour situer le risque des trois questions
ci-dessous :

- Sélection intra-phrase fonctionnelle (mot → phrase entière) avec
  `SelectionContainer` natif.
- Interception de `LocalTextToolbar` → callback applicatif fonctionnelle.
- Recyclage `LazyColumn` : la sélection survit au cycle sortie d'écran →
  recyclage → retour, sans décalage d'index ni de texte.
- Limitation structurelle déjà identifiée à l'époque : pas de sélection
  inter-phrases avec cette approche — c'est précisément la limite que ce
  lot devait vérifier s'il était possible de lever autrement (via
  `pointerInput` bas niveau plutôt que `SelectionContainer`). Conclusion
  de la présente note : la lever proprement se heurte à un autre obstacle
  (le conflit de geste, point 2), pas au même.

## Point 1 — Rendu du mode défilement

**Tranché par lecture du code : un bloc mesuré unique n'est pas acquis
gratuitement en mode SCROLL.**

`ReaderScreen.kt`, bloc `ReadingMode.SCROLL` rend toujours un `FlowRow` de
composables `SentenceText` distincts, un par phrase — pas un unique
`Text`/`TextLayoutResult` comme en mode PAGED (`PagedChapterContent.kt`,
un seul `Text` par page depuis le lot 3a). Ce n'est pas un oubli : le lot
3a a délibérément choisi cette architecture pour PAGED, et le lot 3c.1
s'appuie explicitement sur le fait que chaque phrase reste un composable
adressable individuellement en mode SCROLL (`onGloballyPositioned` par
phrase, voir `sentenceTopOffsetsPx`).

**Option retenue : router le tap vers le `TextLayoutResult` de la phrase
touchée puis convertir en offset de chapitre** (la seconde option du
plan, pas l'unification sur un bloc mesuré unique) :

- Unifier le défilement sur un bloc mesuré unique demanderait de retirer
  le point d'ancrage par phrase que 3c.1 a introduit pour la position de
  lecture (`sentenceTopOffsetsPx`, `currentLineYDp`) et de le reconstruire
  différemment — un changement d'architecture du mode SCROLL entier, pas
  une addition localisée à la sélection.
- Le coût d'un `Text` unique portant tout un chapitre sur `verticalScroll`
  (pas `LazyColumn`, choix déjà actif et documenté en tête de
  `ReaderScreen.kt`) n'a pas été mesuré sur un roman long dans le cadre de
  cette note — la décision ne repose pas sur ce chiffrage, elle repose sur
  le coût de reconstruction de l'ancrage de position (ci-dessus), plus
  déterminant et déjà établi par simple lecture du code.
- La route « par phrase » reste cohérente avec l'existant : chaque
  `SentenceText` a déjà son propre layout Compose ; lui ajouter un
  `TextLayoutResult` capté localement (`onTextLayout`) pour la conversion
  position→offset intra-phrase est une extension, pas une reconstruction.

**Chiffrage de ce sous-point pour un éventuel lot 3f** (voir Conclusion :
non déclenché) : coût proche de ce que PAGED a déjà (conversion
locale→offset via `getOffsetForPosition`), addition à `SentenceText`
plutôt que refonte du mode SCROLL.

## Point 2 — Conflit de gestes en mode pagé

**Vérifié empiriquement sur appareil réel (V2206, session courante) :
conflit confirmé, et pire qu'un simple échec déterministe — c'est une
course entre gestionnaires de gestes, à l'issue imprévisible.**

**Méthode.** Spike jetable ajouté temporairement à `PageBlock`
(`PagedChapterContent.kt`) : un second `Modifier.pointerInput`, sibling de
celui portant `detectTapGestures` existant, avec
`detectDragGesturesAfterLongPress` (`onDrag` appelant `change.consume()`
— l'implémentation exacte que le plan prescrivait). Test par séquences
`adb shell input touchscreen motionevent DOWN/MOVE.../UP` (contrôle
précis du timing, contrairement à `input swipe` qui ne permet pas de tenir
la position avant de glisser) : appui à position fixe pendant 700 ms
(au-delà du seuil de long-press), puis glissement horizontal rapide vers
la droite. Résultat lu directement dans l'état affiché — numéro de page
(`StatusLineBar`, dump `uiautomator`) et présence du popup de sélection —
pas dans les logs (`Log.d` du spike jamais capturé par `logcat` sur cet
appareil malgré présence confirmée dans le dex de l'APK installée — cause
non identifiée, non bloquante puisque l'état UI observé est une preuve
plus directe que des lignes de log).

**Contrôle préalable** : un swipe franc de même amplitude sans appui long
préalable tourne bien la page à chaque fois (`input touchscreen swipe`,
150 ms) — élimine l'hypothèse que le geste de test soit simplement trop
faible pour déclencher le pager, ce qui aurait rendu un « pas de
changement de page » non concluant.

**Résultat sur 3 répétitions du geste appui-long-puis-glissement-rapide,
mêmes paramètres** (position de départ, durée d'appui, vitesse de
glissement) :

| # | Résultat |
|---|---|
| 1 | La page tourne (le pager gagne) |
| 2 | Inconclusive (déjà en butée de fin de chapitre) |
| 3 | La sélection s'étend, popup affiché, la page NE tourne PAS (le geste de sélection gagne) |

Le pager et le détecteur de glissement-après-appui-long réagissent tous
deux au même flux d'événements tactiles, et lequel « gagne » dépend d'une
course de timing sur laquelle `change.consume()` posé dans un
`pointerInput` sibling n'a pas de prise déterministe — cohérent avec le
modèle de Compose où `HorizontalPager` (via `Modifier.scrollable`/
nested scroll) peut intercepter le geste à une passe antérieure
(`PointerEventPass.Initial`, de l'extérieur vers l'intérieur) à celle où
le `pointerInput` du contenu (`PointerEventPass.Main`) consomme
l'événement — la consommation tardive ne peut pas revenir sur une
interception déjà faite en amont.

**Ce que ça signifie pour une implémentation de production** : le
mécanisme prescrit par le plan (`pointerInput` sibling +
`change.consume()`) ne suffit pas. Le fixer proprement demanderait de
participer explicitement au protocole `NestedScrollConnection` du pager
(intercepter/refuser le geste de scroll horizontal pendant une sélection
active) — un mécanisme distinct, plus intrusif, non prévu par le plan
d'origine et non chiffré ici.

## Point 3 — Conversion d'espace de coordonnées

**Tranché par lecture du code : la conversion locale → chapitre → `Locator`
existe déjà et est exercée en production, dans les deux sens.**

`PagedChapterContent.kt` :
- Locale → absolue (chapitre) : `sentenceIndexForOffset` convertit un
  offset renvoyé par `getOffsetForPosition` (local au `Text` de la page)
  en ajoutant `pageOffsetRange.first`, puis retrouve l'index de phrase —
  exactement la conversion que 3c.5 doit prouver, déjà en production
  (`onOffsetLongPress`/`onOffsetTap`).
- Absolue → `Locator` : `AnnotationSelectionHandler.resolveSelection`
  (utilisé par `ReaderViewModel.confirmAnnotation`) convertit un index de
  phrase en `Locator` via `Sentence.startOffset`/`endOffset` — chemin déjà
  testé (`AnnotationSelectionHandlerTest.kt`).
- `Locator` → absolue (sens retour) : `ReaderViewModel.navigateToLocator`
  fait déjà cette conversion pour restaurer une position depuis un signet
  ou un résultat de recherche.

Le point sensible signalé par le lot 3a (offsets locaux au texte mesuré,
pas `Sentence.startOffset`, « pour éviter un bug d'espace silencieux »)
est donc déjà résolu et vérifié en production pour le mode PAGED — la
même conversion s'applique sans changement pour une sélection au mot,
seule la granularité (caractère plutôt que phrase entière) change à
l'intérieur du même mécanisme. Aucun nouveau risque de conversion
identifié sur ce point.

## Conclusion

**Le prototype échoue sur le point 2, vérifié empiriquement, pas
supposé.** La clause du plan s'applique explicitement : *« si le
prototype échoue sur l'un des trois points, la sélection par phrase est
actée dans la cible comme comportement définitif — ce qui ferme le sujet
aussi valablement »*.

**La sélection par phrase (appui long puis extension) est donc le
comportement définitif d'InkTone v1**, consigné dans `UX_FLOW_DESIGN.md`
(§ Lecture — HUD, état d'implémentation lot 3c). Le lot 3f (sélection
libre au mot, poignées persistantes, auto-scroll pendant le glissement,
sélection à cheval sur deux pages, sémantiques d'accessibilité) **ne sera
pas déclenché** sur la base de ce plan — le rouvrir supposerait une
approche différente sur le point 2 (participation explicite au
`NestedScrollConnection` du pager), non chiffrée ici et non actée.
