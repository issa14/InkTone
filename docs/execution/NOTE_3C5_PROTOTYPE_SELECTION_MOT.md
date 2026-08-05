# Note de conclusion — Tâche 3c.5, prototype de sélection sur-mesure au mot

**Statut : prototype jetable, aucun code livré dans `main` depuis cette tâche**
(conforme à la consigne du lot). Cette note documente la conclusion et les
preuves d'analyse statique rassemblées ; elle ne remplace pas la
vérification sur appareil listée en écart ci-dessous.

**Écart déclaré à l'ouverture** : la vérification empirique de cette tâche
suppose un appareil connecté pour manipuler réellement un geste de
sélection (`detectDragGesturesAfterLongPress`) et observer le conflit avec
`HorizontalPager`. Cette session n'avait pas d'appareil/émulateur
disponible. Les points 1 et 3 ci-dessous sont tranchés par lecture directe
du code de production (mesure vérifiable, pas une supposition) ; le point 2
reste **non vérifié empiriquement** et est donc traité comme un blocage
ouvert, pas comme conclu favorablement par défaut.

## Point 1 — Rendu du mode défilement

**Tranché par lecture du code : un bloc mesuré unique n'est pas acquis
gratuitement en mode SCROLL.**

`ReaderScreen.kt`, bloc `ReadingMode.SCROLL` (voir aussi le lot 3c.1
au-dessus) rend toujours un `FlowRow` de composables `SentenceText`
distincts, un par phrase — pas un unique `Text`/`TextLayoutResult` comme en
mode PAGED (`PagedChapterContent.kt`, un seul `Text` par page depuis le lot
3a). Ce n'est pas un oubli : le lot 3a a délibérément choisi cette
architecture pour PAGED (voir le commentaire de tête de
`PagedChapterContent.kt`, "un seul bloc de texte tranché... plus le FlowRow
de composables SentenceText séparés"), et le lot 3c.1 vient de s'appuyer
explicitement sur le fait que chaque phrase reste un composable adressable
individuellement en mode SCROLL (`onGloballyPositioned` par phrase, voir
`sentenceTopOffsetsPx`).

**Option retenue : router le tap vers le `TextLayoutResult` de la phrase
touchée puis convertir en offset de chapitre** (la seconde option du plan,
pas l'unification sur un bloc mesuré unique) :

- Unifier le défilement sur un bloc mesuré unique demanderait de retirer le
  point d'ancrage par phrase que 3c.1 vient d'introduire pour la position
  de lecture (`sentenceTopOffsetsPx`, `currentLineYDp`) et de le
  reconstruire différemment (offsets dans le bloc unique plutôt que
  positions de composables) — un changement d'architecture du mode
  SCROLL entier, pas une addition localisée à la sélection.
- Le coût d'un `Text` unique portant tout un chapitre (romans longs,
  plusieurs centaines de phrases) sur `verticalScroll` (pas
  `LazyColumn`, choix déjà actif et documenté en tête de `ReaderScreen.kt`)
  n'est pas mesuré dans ce dépôt — l'estimer sérieusement demande un test
  sur un chapitre réel long, hors de portée de cette session sans appareil.
- La route "par phrase" reste cohérente avec l'existant : chaque
  `SentenceText` a déjà son propre layout Compose ; lui ajouter un
  `TextLayoutResult` capté localement (`onTextLayout`) pour la conversion
  position→offset intra-phrase est une extension, pas une reconstruction.

**Chiffrage de ce sous-point pour le lot 3f** : faible risque, coût
d'implémentation proche de ce que PAGED a déjà (conversion locale→offset via
`getOffsetForPosition`), addition à `SentenceText` plutôt que refonte du
mode SCROLL. Estimé à environ un quart du lot 3f total (voir chiffrage
global en fin de note).

## Point 2 — Conflit de gestes en mode pagé

**Non vérifié empiriquement — reste un blocage ouvert, pas conclu.**

Analyse statique : `PagedChapterContent.kt` compose `HorizontalPager` avec
`PageBlock` dedans, et `PageBlock` attache déjà son propre
`Modifier.pointerInput(pageOffsetRange) { detectTapGestures(onLongPress,
onTap) }` (tap et appui long, pas de drag) sur le même `Text` que celui que
`HorizontalPager` swipe horizontalement. `detectTapGestures` et le geste de
swipe du pager coexistent aujourd'hui sans conflit documenté — mais
`detectDragGesturesAfterLongPress` (le geste que la sélection au mot
ajouterait) est structurellement différent : c'est un drag, pas un tap, et
un drag horizontal après appui long sur la même zone que le `HorizontalPager`
swipe est précisément le cas que le plan signale comme à vérifier "par la
mesure, pas par préférence".

Sans appareil pour jouer le geste réel, cette session ne peut pas trancher
si `detectDragGesturesAfterLongPress` posé en plus de `detectTapGestures`
sur le même `Text` laisse le `HorizontalPager` intercepter le swipe une
fois le drag commencé, ou l'inverse (sélection qui tourne la page malgré
elle). C'est exactement le risque que la tâche demande de vérifier avant
d'écrire du code de production — il n'est pas résolu ici.

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
  ou un résultat de recherche — `locator.charOffset in
  sentence.startOffset..sentence.endOffset`.

Le point sensible signalé par le lot 3a (offsets locaux au texte mesuré,
pas `Sentence.startOffset`, "pour éviter un bug d'espace silencieux") est
donc déjà résolu et vérifié en production pour le mode PAGED — la même
conversion s'applique sans changement pour une sélection au mot, seule la
granularité (caractère plutôt que phrase entière) change à l'intérieur du
même mécanisme. Aucun nouveau risque de conversion identifié sur ce point.

## Conclusion

**Deux points sur trois sont tranchés favorablement par le code déjà en
production** (1 et 3). **Le troisième (conflit de gestes en mode pagé)
n'est pas vérifié** faute d'appareil dans cette session — la clause du plan
("si le prototype échoue sur l'un des trois points, la sélection par
phrase est actée dans la cible comme comportement définitif") s'applique
donc par défaut tant que ce point n'est pas levé : le lot 3f **reste
non déclenché**, la sélection par phrase reste le comportement définitif
documenté (voir 3c.7 ci-dessous) jusqu'à ce qu'une session avec accès
appareil vérifie le point 2 et rouvre la décision.

**Chiffrage indicatif du lot 3f, si le point 2 se vérifie favorable** :
routage par phrase pour SCROLL (~1/4), poignées persistantes + auto-scroll
pendant le glissement (~1/3, le plus gros morceau — composables de poignée
dédiés, cible tactile 48 dp, pas de précédent dans le dépôt), sémantiques
d'accessibilité TalkBack (~1/4, sensible — le lot 1 a déjà eu un correctif
sur le chevauchement TalkBack/TTS, signal que ce point demande une passe
dédiée plutôt qu'un ajout rapide), retrait de l'ancien modèle de sélection
par phrase (~1/6, mécanique une fois le nouveau modèle en place). Estimation
haute confiance sur la structure des postes, basse confiance sur la
durée absolue faute de mesure sur device.
