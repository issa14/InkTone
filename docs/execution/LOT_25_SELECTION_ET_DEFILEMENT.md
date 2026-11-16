# Lot 25 — Sélection libre solide et défilement fluide (audit UX D50)

**Base :** branche `audit-p2` (non fusionnée). Source : retour d'usage direct
d'Issa (2026-11-16) — « la sélection ne marche pas et le défilement est lent »,
antérieur à la réindexation de fond. Inclut D50 (une sélection ne peut pas
franchir un paragraphe ni une page).

Aucune migration Room prévue. L'état de sélection reste `ReaderUiState.freeSelectionRange`
(offsets absolus de caractère dans le chapitre, `Locator` seul système d'adressage).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

---

## Constat vérifié (V2206, 2026-11-16, build debug)

### Sélection en mode défilement : cassée

Appui long sur un mot (geste injecté par adb, 900 ms) : le mot est surligné
(`wordPath` de `ParagraphBlockDisplay`), mais **ni poignées ni menu d'actions**
(Surligner, Note, Copier) n'apparaissent, même après plusieurs secondes.

Cause lue dans le code (`BookBlockItem.kt`) :

- `ParagraphBlockDisplay` (`Text`) détecte l'appui long, calcule le mot et publie la
  sélection (`onFreeSelectionChanged`) ; `ParagraphBlockText` monte alors
  `ParagraphBlockField` (`BasicTextField`) avec une sélection initiale.
- Or Compose n'affiche poignées et barre d'outils (`TextToolbar.showMenu`, d'où
  `selectionBoundsInWindow` que `ReaderScreen.kt:1257` exige pour monter
  `SelectionActionPopup`) qu'à l'issue d'un geste de sélection **dans le champ**.
  Un champ monté avec une sélection programmatique, sans focus ni geste, n'affiche rien.
- Cet échange `Text` → `BasicTextField` vient de l'audit de réactivité §4.1 (un champ par
  paragraphe coûtait trop cher au défilement). Il a réglé le coût au prix de la sélection.

À vérifier au palier 25.1 : le mode paginé (un champ par page, `PageBlock`) garde son geste
natif et devrait fonctionner ; la limite structurelle « jamais à cheval » y est la même.

### Défilement : saccadé

Huit balayages dans Ubik, `dumpsys gfxinfo` : 94 images, **18 % saccadées**, médiane 17 ms,
90e centile 40 ms, 99e centile 200 ms, 17 images « UI thread lent », GPU 2 ms (hors cause).
Mesure faite pendant la réindexation (processeur à ~150 %) : à refaire au calme au palier
25.1 pour séparer la part du rendu de la part de la charge de fond. Les causes déjà
documentées dans `AUDIT_REACTIVITE_UX.md` (§3.3 état monolithique recomposé à chaque mot,
§4.1 à §4.5) sont à confirmer ou écarter par trace, pas à supposer corrigées.

---

## Décision de conception à valider (ADR-029 à écrire au palier 25.2)

Cible : un seul moteur de sélection, au grain du chapitre, sans `BasicTextField` en
défilement (donc moins cher au défilement ET sélection à cheval sur plusieurs paragraphes).

| Option | Principe | Pour | Contre |
|---|---|---|---|
| **A. Correctif minimal** | Après l'échange, demander le focus et forcer l'affichage du menu | Peu de code | Contourne Compose ; reste borné au paragraphe ; garde le coût du champ ; fragile |
| **B. `SelectionContainer` natif** | `Text` partout dans un `SelectionContainer` autour de la `LazyColumn` | Poignées, loupe et multi-paragraphes natifs, aucun geste réécrit | Compose 1.7 (BOM 2024.09.02) : la sélection dans une `LazyColumn` est limitée aux éléments composés ; correspondance entre sélection native et offsets absolus à vérifier ; contrôle limité |
| **C. Moteur maison** | Un geste d'appui long + glissement au niveau de la liste ; registre des `TextLayoutResult` des blocs visibles ; poignées dessinées par nous ; défilement automatique au bord | Contrôle total, offsets absolus natifs, à cheval sur tout le chapitre, aucun champ | Le plus de code ; poignées, loupe et accessibilité à réécrire |

**Recommandation :** palier 25.2 = spike mesuré de B puis de C sur appareil (même livre, mêmes
gestes), choix consigné en ADR-029. B si elle tient les offsets absolus et le multi-paragraphes ;
sinon C. A est écartée (ne traite ni D50 ni le coût).

---

## Paliers

### 25.1 — Mesure et reproduction (sans changement de comportement)

1. Script de mesure du défilement reproductible (`scripts/measure-scroll.sh` : balayages adb +
   `gfxinfo`), à utiliser avant/après chaque palier ; mesure de référence au calme.
2. Trace du défilement (Perfetto ou benchmark existant `benchmark/`) pour nommer les 2 ou 3
   premières causes réelles du fil principal.
3. Reproduire la sélection en mode paginé et en mode défilement ; consigner.
- **Sortie :** chiffres de référence et causes nommées dans ce document.

### 25.2 — Spike B puis C, décision ADR-029

- Prototype jetable sur un chapitre : sélection d'un mot, glissement sur plusieurs paragraphes,
  défilement automatique, menu d'actions, création d'un surlignage aux bons offsets.
- **Sortie :** ADR-029 (option retenue, alternatives écartées, mesures).

### 25.3 — Moteur de sélection en mode défilement

- Implémentation de l'option retenue ; suppression de l'échange `Text` ↔ `BasicTextField`.
- Menu d'actions (`SelectionActionPopup`) et création d'annotation inchangés (offsets absolus).
- Tests : logique pure de sélection (mot, extension, bornes) en JVM ; test instrumenté du geste.

### 25.4 — Défilement : correctifs des causes nommées en 25.1

- Un correctif par cause, chacun mesuré avant/après par le script du 25.1.

### 25.5 — Mode paginé : sélection à cheval sur plusieurs pages (D50, partie page)

- Même moteur appliqué à `PagedChapterContent` ; la sélection continue de page en page.
- À planifier après 25.3, selon l'option retenue ; peut devenir un lot suivant si trop gros.

### 25.6 — Accessibilité et retrait de l'ancien code

- TalkBack : actions de sélection, annonce de la plage ; retrait du code mort (`PageBlock`,
  `ParagraphBlockField` si remplacés). Mise à jour du KDoc de tête de `ReaderScreen.kt`
  (limite « jamais à cheval » à retirer) et du suivi de l'audit (D50).

## Critères de sortie du lot

- [ ] Appui long en défilement : poignées et menu d'actions visibles, sur appareil.
- [ ] Glissement d'un paragraphe à l'autre, puis surlignage créé aux bons offsets et retrouvé
      à la réouverture.
- [ ] Défilement : images saccadées sous 5 % et 99e centile sous 50 ms sur le même geste de
      référence (cibles à confirmer après la mesure du 25.1).
- [ ] Mode paginé : sélection au moins aussi bonne qu'avant ; à cheval sur deux pages si 25.5.
- [ ] Tests JVM de la logique de sélection ; tests instrumentés ajoutés verts.

## Ce que ce lot ne fait pas

- Pas de changement du modèle `Locator` ni de la persistance des annotations.
- Pas de réécriture du rendu des blocs (images, titres) hors ce qui sert la sélection.
- Les autres freins de l'audit de réactivité (Bibliothèque, mémoire, benchmark) restent hors lot.
