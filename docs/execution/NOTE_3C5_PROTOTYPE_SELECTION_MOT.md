# Note de conclusion — Tâche 3c.5, prototype de sélection sur-mesure au mot

**Statut : conclusion définitive, prototype jetable, aucun code livré dans
`main`.** Les trois questions ont été tranchées — deux par lecture du code
de production, une par test empirique sur appareil réel (V2206), **révisée
une seconde fois** après un premier verdict qui s'est avéré reposer sur une
mesure trop faible (voir Point 2 — Révision).

**Verdict final : le point 2 est levable.** Le mécanisme prescrit par le
plan (`pointerInput` sibling + `detectDragGesturesAfterLongPress` +
`change.consume()`, sans mécanisme supplémentaire) évite le retournement
de page de façon reproductible — 30 essais consécutifs sur trois
configurations de geste, 30/30 en faveur de la sélection, zéro
retournement. **La sélection par phrase reste néanmoins le comportement
d'InkTone v1** — ce point rouvre la porte du lot 3f pour une décision
future, il ne change pas le périmètre livré par le lot 3c (voir
Conclusion).

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

**Verdict final, sur mesure statistiquement robuste : le conflit est
évitable avec le mécanisme prescrit par le plan, tel quel — pas besoin de
mécanisme supplémentaire.** Ce verdict inverse une première conclusion
(« conflit confirmé, intermittent ») posée sur une mesure trop faible (3
répétitions dont une inconcluante) — voir Révision ci-dessous pour
l'historique complet, gardé volontairement plutôt qu'effacé.

### Méthode (les deux passes)

Spike jetable ajouté temporairement à `PagedChapterContent.kt`/`PageBlock` :
un second `Modifier.pointerInput`, sibling de celui portant
`detectTapGestures` existant, avec `detectDragGesturesAfterLongPress`
(`onDrag` appelant `change.consume()` — l'implémentation exacte prescrite
par le plan). Test par séquences `adb shell input touchscreen motionevent
DOWN/MOVE.../UP` (contrôle précis du timing, contrairement à `input swipe`
qui ne permet pas de tenir la position avant de glisser). Résultat lu dans
l'état affiché — numéro de page (`StatusLineBar`) et présence du popup de
sélection (`text="Copier"`) — via dump `uiautomator`, pas les logs
(`Log.d` du spike jamais capturé par `logcat` sur cet appareil malgré
présence confirmée dans le dex de l'APK installée, cause non identifiée
et non bloquante).

**Contrôle systématique** avant chaque série : un swipe franc de même
forme (mêmes coordonnées, même vitesse) mais **sans** appui long
préalable doit tourner la page à chaque fois — élimine l'hypothèse que le
geste de test soit simplement trop faible pour déclencher le pager, ce
qui rendrait un « pas de changement de page » non concluant. Vérifié
positif avant chaque campagne de mesure ci-dessous.

### Révision — pourquoi la première conclusion est tombée

La première passe (session précédente) reposait sur **3 répétitions
manuelles, dont une inconcluante** (déjà en butée de fin de chapitre) —
soit 2 mesures exploitables sur un phénomène qu'on soupçonnait aléatoire.
Verdict à l'époque : 1 échec, 1 succès, conflit jugé « confirmé et
intermittent ». Un second passage, avec un protocole renforcé demandé
explicitement (10 répétitions minimum par condition, script automatisé
plutôt que des appels `adb` manuels enchaînés à la main, mesure en milieu
de chapitre), a entièrement renversé ce verdict — la variance observée la
première fois était un artefact de la mesure (timing manuel peu
reproductible entre appels `Bash` séparés), pas une propriété réelle du
geste.

### Mesure finale — harnais automatisé (script Python pilotant `adb`)

Trois campagnes de 10 répétitions consécutives, réinitialisation de
l'état entre chaque essai (fermeture du popup, retour à la même page
médiane), toutes avec contrôle préalable positif :

| Campagne | Paramètres du geste | Résultat |
|---|---|---|
| Référence 1 | DOWN (600,800), maintien 700 ms, 5×MOVE/25 ms vers (100,800), UP | **10/10 sélection**, 0/10 retournement |
| Référence 2 (paramètres différents, contrôle de robustesse) | DOWN (650,1050), maintien 900 ms, 6×MOVE/15 ms vers (150,1050), UP | **10/10 sélection**, 0/10 retournement |
| Condition A (`userScrollEnabled = false` posé dans `onDragStart`, en plus du mécanisme de référence) | Identique à Référence 1 | **10/10 sélection**, 0/10 retournement |

**30 essais, 30 succès, 0 échec.** Conformément au protocole (« arrêter
dès qu'une condition atteint 10/10 »), la mesure s'arrête ici — la
Condition B (geste séparé) et la Condition C (consommation en
`PointerEventPass.Initial`) n'ont pas été nécessaires.

### Ce que ça signifie pour une implémentation de production

Le mécanisme prescrit par le plan d'origine (`pointerInput` sibling +
`detectDragGesturesAfterLongPress` + `change.consume()`, **sans**
`userScrollEnabled` ni participation à `NestedScrollConnection`) suffit,
sur la mesure disponible, à empêcher le pager de tourner la page pendant
un glissement de sélection. L'hypothèse d'interception en
`PointerEventPass.Initial` par le pager — qui aurait rendu la
consommation tardive en `Main` sans effet — ne se vérifie pas dans les
faits : soit le pager ne réclame pas le geste à cette passe dans ce cas
précis, soit la consommation en `Main` suffit à l'en dissuader avant qu'il
n'agisse. Le mécanisme exact reste à documenter précisément si le lot 3f
est un jour lancé (voir Conclusion), mais son **effet observable** est
net et reproductible.

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

**Les trois points sont favorables.** Le prototype ne bute plus sur
aucun des trois obstacles identifiés par le plan : le point 1 et le point
3 étaient déjà tranchés par lecture du code, le point 2 — celui qui avait
initialement fermé le sujet — est maintenant vérifié levable sur une
mesure robuste (30/30, protocole renforcé). La clause d'arrêt du plan
(« si le prototype échoue sur l'un des trois points, la sélection par
phrase est actée comme définitif ») **ne s'applique donc plus**.

**Ce que ça change, et ce que ça ne change pas.** Ceci rouvre la
**décidabilité** du lot 3f — il redevient un choix produit légitime,
chiffrable, plutôt qu'une porte fermée par une preuve d'échec. Ça **ne
déclenche pas** le lot 3f pour autant, et ça **ne change rien** au
périmètre livré par le lot 3c : **la sélection par phrase (appui long
puis extension) reste le comportement d'InkTone v1**, tel que consigné
dans `UX_FLOW_DESIGN.md` (§ Lecture — HUD, état d'implémentation lot 3c).
Décider de lancer le lot 3f est une décision produit distincte, qui
appartient à Issa — cette note fournit de quoi la prendre en connaissance
de cause, elle ne la prend pas à sa place.

**Chiffrage indicatif du lot 3f, si lancé** : routage par phrase pour
SCROLL (~1/4, voir point 1), mécanisme drag/pager du point 2 déjà
vérifié — retire l'inconnue la plus risquée du chiffrage initial —,
poignées persistantes + auto-scroll pendant le glissement (~1/3, le plus
gros morceau restant — composables de poignée dédiés, cible tactile
48 dp, pas de précédent dans le dépôt), sémantiques d'accessibilité
TalkBack (~1/4, sensible — le lot 1 a déjà eu un correctif sur le
chevauchement TalkBack/TTS), retrait de l'ancien modèle de sélection par
phrase (~1/6, mécanique une fois le nouveau modèle en place). Estimation
de structure, pas de durée absolue — aucune mesure de temps de
développement réel dans cette note.
