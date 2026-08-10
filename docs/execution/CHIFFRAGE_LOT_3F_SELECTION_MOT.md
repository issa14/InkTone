# Chiffrage — Lot 3f, sélection libre au mot

Vérifié contre `acde37c`. Périmètre de référence : `LOT_3C_NAVIGATION_LECTEUR.md`
§ Lot 3f, conclusion de `NOTE_3C5_PROTOTYPE_SELECTION_MOT.md`. Décision de
déclenchement : produit, à Issa — ce document chiffre, ne déclenche pas.

## Ce qui change par rapport au chiffrage indicatif de la note 3c.5

La note donnait des poids relatifs (1/4, 1/3, 1/4, 1/6) sans les découper en
paliers livrables. Ci-dessous, même structure, mais recoupée contre le code
réel et reformée en paliers indépendamment poussables — pour ne pas répéter
le lot 3d monolithique (une session entière de confusion faute de découpage).

## Calibration — taille de lots déjà livrés (diff réel, pas une estimation)

| Lot | Fichiers | Lignes |
|---|---|---|
| Galerie de thèmes, palier B | 14 | 1 434 |
| Onboarding | 31 | 3 158 |
| Statistiques Palier 1 | 61 | 5 582 |
| Synchronisation | 98 | 8 298 |

Sert de référence de taille ci-dessous — pas de chiffre en jours/sessions,
faute de donnée de vélocité fiable sur ce dépôt (les lots 1-3e ont été
fusionnés en un seul PR squashé, aucune granularité de durée récupérable).

---

## Palier 3f.1 — Mécanisme de base, mode PAGED uniquement

**Scope :** `pointerInput` sibling + `detectDragGesturesAfterLongPress` (mécanisme
déjà validé 30/30 par le prototype, aucune inconnue de geste restante) +
`getOffsetForPosition`/`getWordBoundary` pour caler sur le mot + bornes en
état + rendu `SpanStyle` superposé + popup ancré sur les bornes.

**Fichiers touchés (confirmés) :** `PagedChapterContent.kt` (le
`pointerInput` unique `:381` gagne un sibling — sans toucher le tap
existant), `SelectionActionPopup.kt` (déjà un `PopupPositionProvider` —
réutilisable, juste une nouvelle source de bornes), `ReaderViewModel.kt`,
`ReaderUiState.kt` (nouvel état de sélection, coexistant temporairement avec
`selectionAnchorIndex`/`selectionFocusIndex` — coexistence à ne **pas**
laisser dépasser ce palier).

**Risque :** faible. C'est le seul point du chiffrage où l'inconnue
principale (conflit drag/pager) a déjà été mesurée sur appareil, pas
supposée.

**Taille indicative :** proche de Galerie de thèmes palier B — surface
localisée à 3-4 fichiers de production.

---

## Palier 3f.2 — Poignées persistantes + auto-scroll pendant le glissement

**Scope :** composables de poignée dédiés (`pointerInput` propre, cible
tactile ~48dp), auto-scroll pendant le glissement hors zone visible.

**Dépend de :** 3f.1 (le mécanisme de sélection de base doit exister).

**Risque :** le plus élevé du lot après l'accessibilité — **aucun
précédent dans le dépôt**, contrairement à tous les autres points du
prototype qui s'appuyaient sur du code de production existant (conversion
de coordonnées, popup positionné). La vérification ne pourra pas se faire
uniquement par test Compose — la validation gestuelle réelle (comme pour
3c.5) sera probablement nécessaire ici aussi, pas seulement du geste
synthétique `adb motionevent`.

**Taille indicative :** entre Galerie de thèmes B et Onboarding —
composants nouveaux, animation, mais surface de fichiers modérée.

---

## Palier 3f.3 — Extension au mode SCROLL + décision sélection à cheval

**Scope :** routage par phrase pour SCROLL (option retenue par le
prototype — ajout d'un `TextLayoutResult` local à `SentenceText`, pas de
refonte du mode SCROLL) ; trancher la sélection à cheval sur deux pages en
PAGED (autorisée / bornée à la page / interdite).

**Dépend de :** 3f.1. Indépendant de 3f.2 (peut se faire en parallèle ou
avant selon préférence).

**Décision produit à prendre avant ce palier, pas pendant :** le choix
"sélection à cheval interdite" réduit ce palier à une extension SCROLL
pure ; "autorisée" ou "bornée" ajoute une gestion d'état inter-pages non
triviale. Le chiffrage de ce palier dépend directement de ce choix — à
trancher par Issa avant le début du palier, comme demandé pour la
sélection à cheval dans le périmètre initial.

**Taille indicative :** petite si "interdite" retenue (extension
localisée à `SentenceText`) ; moyenne sinon.

---

## Palier 3f.4 — Sémantiques d'accessibilité TalkBack

**Scope :** exposer la sélection sur-mesure aux services d'accessibilité —
perdue de fait puisque `SelectionContainer` natif n'est pas utilisable
(`internal`, cause déjà établie en 3c.4).

**Ce que le dépôt montre, et ce que ça signifie pour le chiffrage :** le
seul précédent d'annonce TalkBack sur ce composant (`liveRegion =
LiveRegionMode.Polite` pour la phrase en cours pendant le TTS, lot 1) a
été **retiré** après vérification sur appareil — chevauchement avec la
voix TTS déjà active (`ReaderScreen.kt:562-567`). Ce n'est donc pas un
patron réutilisable tel quel : le seul antécédent connu de ce sujet dans
ce dépôt est un retrait, pas une réussite.

**Recommandation, pas un chiffrage :** ce palier ne devrait pas être
chiffré à l'aveugle comme les autres. Même méthode que 3c.5 pour le geste
— un prototype jetable dédié, qui tranche par mesure sur appareil (TalkBack
actif, palpation de la sélection) avant d'engager le palier réel. Sans ça,
le risque est de découvrir le problème après avoir livré 3f.1-3f.3, au
pire moment pour changer d'approche.

**Taille indicative :** inconnue tant que le spike n'a pas eu lieu — c'est
le point du prototype que la note 3c.5 elle-même qualifiait de sensible,
et le seul des quatre où le dépôt n'offre aucun signal de faisabilité,
positif ou négatif.

---

## Palier 3f.5 — Retrait de l'ancien modèle par phrase

**Scope :** `selectionAnchorIndex`/`selectionFocusIndex`
(`ReaderUiState.kt:55-56`), les intents `BeginSentenceSelection`/
`ExtendSentenceSelection` (`ReaderViewModel.kt:171-174`), le contournement
documenté (`ReaderScreen.kt:89-93`).

**Dépend de :** 3f.1 à 3f.3 couvrant la totalité de la surface actuellement
gérée par l'ancien modèle (PAGED et SCROLL). Ne pas le faire cohabiter au-delà
de ce point — deux modèles de sélection simultanés est explicitement
signalé comme source de divergence dans le périmètre initial.

**Risque :** faible, mécanique une fois les paliers précédents en place.

**Taille indicative :** petite — retrait de code mort, pas d'ajout.

---

## Séquencement recommandé

```
3f.1 (base PAGED, risque faible)
  │
  ├─→ 3f.2 (poignées + auto-scroll, risque élevé, sans précédent)
  ├─→ 3f.3 (SCROLL + décision cheval, taille conditionnée par la décision)
  │
3f.4 (accessibilité — spike de faisabilité D'ABORD, indépendamment du
      reste, pour ne pas découvrir le problème en fin de lot)
  │
3f.5 (retrait ancien modèle, après 3f.1-3f.3 seulement)
```

Le spike d'accessibilité (3f.4) peut être mené en parallèle de 3f.1, sur le
même modèle que 3c.5 : jetable, hors `main`, conclusion écrite avant
d'engager le palier réel. C'est le seul point du lot où le dépôt ne donne
aucun signal — tout le reste s'appuie sur du code de production existant
et vérifié.

## Ce que ça ne dit pas

Aucune estimation en jours ou en sessions — pas de donnée de vélocité
fiable sur ce dépôt pour la calibrer honnêtement. La taille relative
ci-dessus (comparée aux lots déjà livrés) est le niveau de précision que
les faits disponibles permettent.
