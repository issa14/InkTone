# Lot 25 — Sélection libre solide et défilement fluide (audit UX D50)

**Base :** branche `lot-25-selection-defilement`, posée sur `audit-p2` (non fusionnée) :
l'ordre d'intégration dans `main` suit celui d'`audit-p2`. Source : retour d'usage direct
d'Issa (2026-11-16) — « la sélection ne marche pas et le défilement est lent »,
antérieur à la réindexation de fond. Inclut D50 en mode défilement (une sélection ne peut
pas franchir un paragraphe) ; la partie « page » de D50 est sortie du lot (voir la fin du document).

Aucune migration Room prévue. L'état de sélection reste `ReaderUiState.freeSelectionRange`
(offsets absolus de caractère dans le chapitre, `Locator` seul système d'adressage).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

---

## Constat vérifié (V2206, 2026-11-16, build debug)

### Sélection en mode défilement : cassée depuis `40d9c650` (2026-08-28)

Appui long sur un mot (geste injecté par adb, 900 ms) : le mot est surligné, mais
**ni poignées ni menu d'actions** (Surligner, Note, Copier) n'apparaissent, même après
plusieurs secondes.

Cause lue dans le code (`BookBlockItem.kt`) :

- `ParagraphBlockDisplay` (`Text`) détecte l'appui long, calcule le mot et publie la
  sélection (`onFreeSelectionChanged`) ; `ParagraphBlockText` monte alors
  `ParagraphBlockField` (`BasicTextField`) avec une sélection initiale.
- Le surlignage visible est le `selectionPath` **de `ParagraphBlockField`** :
  `ParagraphBlockDisplay` ne dessine que le mot TTS (`wordPath`), pas la sélection. L'échange
  de composable a donc bien lieu ; c'est le champ monté qui reste muet.
- Compose n'affiche poignées et barre d'outils (`TextToolbar.showMenu`, seul endroit qui
  publie les bornes que `ReaderScreen.kt:1257` exige pour monter `SelectionActionPopup`)
  qu'à l'issue d'un geste de sélection **dans le champ**. Un champ monté avec une sélection
  programmatique, sans focus ni geste, n'affiche rien.
- Cet échange `Text` → `BasicTextField` vient de l'audit de réactivité §4.1 (commit
  `40d9c650`, « Ne monte un BasicTextField qu'au bloc qui porte la sélection »). Il a réglé
  le coût au prix de la sélection, **sans qu'aucun test le voie** : aucun test instrumenté
  ne fait un appui long en mode défilement puis n'attend le menu. La régression a vécu
  deux mois et demi.

Ce que le code permet déjà, et qui allège la suite :

- `ReaderIntent.SetFreeSelection` accepte n'importe quelle plage du chapitre ; `sliceChapterText`
  et la création d'annotation travaillent en offsets absolus. **Le modèle sait déjà
  représenter une sélection à cheval** ; seule la vue la borne.
- `absoluteRangePath` et `rangeBoundsInWindow` dessinent et mesurent une plage absolue
  dans n'importe quel bloc : le rendu multi-paragraphe d'une plage n'est pas à inventer.

Ce que la vue bloque, et que le lot doit lever :

- `ParagraphBlockText.ownsGlobalSelection` exige que la plage tienne **entière** dans le
  bloc : une plage à cheval n'est aujourd'hui dessinée nulle part.
- `resolveSelectionPopupBounds` ne connaît qu'un bloc propriétaire : les bornes du popup
  d'une plage à cheval doivent être l'union des blocs visibles qu'elle touche.
- `LazyColumn(userScrollEnabled = freeSelectedRange == null)` (`ReaderScreen.kt`) :
  le défilement est **bloqué** tant qu'une sélection existe. Incompatible avec une sélection
  plus longue que l'écran ; à remplacer par un défilement automatique pendant le
  glissement d'une poignée, et un défilement libre (popup masqué) sinon.

### Défilement : saccadé, mesure à refaire

Huit balayages dans Ubik, `dumpsys gfxinfo` : 94 images, **18 % saccadées**, médiane 17 ms,
90e centile 40 ms, 99e centile 200 ms, 17 images « UI thread lent », GPU 2 ms (hors cause).

Cette mesure ne peut pas servir de référence, pour trois raisons :

1. Faite pendant la réindexation (processeur à ~150 %).
2. Sur un build **debug** (ni R8 ni compilation anticipée) : `scripts/measure-reader-frames.sh`
   le dit lui-même, seules les comparaisons avant/après d'un même build y valent.
3. Sur 94 images, le 99e centile est la pire image. Il en faut quelques centaines,
   sur trois passages au moins, pour qu'un centile élevé veuille dire quelque chose.

Les causes déjà documentées dans `AUDIT_REACTIVITE_UX.md` (§3.3 état monolithique, déjà
traité par `ReaderStructuralPolicy`/`WithLiveValue` ; §4.1 à §4.5) sont à confirmer ou écarter
par trace. Candidats visibles à la lecture, **à ne corriger que si la trace les désigne** :

- `items(blocks, key = …)` sans `contentType` : images, séparateurs et paragraphes
  partagent le même réservoir de recyclage.
- `onGloballyPositioned { textCoordinates = it }` sur chaque paragraphe : rappel à chaque
  image de défilement et écriture d'un `mutableStateOf`, pour une valeur lue seulement
  au tap (un simple porteur hors snapshot suffit).
- `buildBlockAnnotatedString` parcourt toutes les annotations du chapitre pour chaque bloc
  qui entre à l'écran.

---

## Décision de conception (ADR-029 à écrire au palier 25.2)

Cible : un seul moteur de sélection, au grain du chapitre, sans `BasicTextField` en
défilement (donc moins cher au défilement ET sélection à cheval sur plusieurs paragraphes).

| Option | Principe | Pour | Contre |
|---|---|---|---|
| **A. Correctif minimal** | Après l'échange, demander le focus et forcer l'affichage du menu | Peu de code | Contourne Compose ; reste borné au paragraphe ; garde le coût du champ ; fragile |
| **B. `SelectionContainer` natif** | `Text` partout dans un `SelectionContainer` autour de la `LazyColumn` | Poignées, loupe et multi-paragraphes natifs | Foundation 1.7.2 (BOM 2024.09.02) : la seule surcharge qui expose la `Selection` (`selection`, `onSelectionChange`) est `internal`, comme le type `Selection` ; la surcharge publique ne rend jamais les offsets. Aucun moyen de produire `freeSelectionRange` sans passer par le presse-papiers. Sélection perdue quand ses éléments quittent la composition. |
| **C. Moteur maison** | Appui long + glissement au niveau de la liste ; registre des `TextLayoutResult` des blocs visibles ; poignées dessinées par nous ; défilement automatique au bord | Offsets absolus natifs, à cheval sur tout le chapitre, aucun champ ; dessin et bornes déjà écrits (`absoluteRangePath`, `rangeBoundsInWindow`) | Geste, poignées, test d'appartenance et accessibilité à écrire |

**Recommandation : C, sans spike de B.** B est disqualifiée par la lecture de l'API publique,
pas par une mesure : à confirmer en dix minutes au palier 25.2 (si une version de
Compose plus récente, accessible sans montée de BOM risquée, rend `Selection` publique,
le noter dans l'ADR), pas en prototype. A est écartée (ne traite ni D50 ni le coût).
Le spike du 25.2 porte uniquement sur les deux risques réels de C : la cohabitation de
l'appui long avec le geste de défilement de la `LazyColumn`, et le défilement automatique
pendant le glissement d'une poignée.

---

## Paliers

### 25.0 — Rétablir le menu en mode défilement (première brique de C, pas un correctif jetable)

Seul palier livrable sans attendre la décision : il rend la fonction à l'utilisateur et
pose les bases de C sans rien qu'il faudra défaire.

1. **Test de régression d'abord** (instrumenté, `feature/reader/src/androidTest`) : appui
   long sur un paragraphe en mode défilement → `SelectionActionPopup` affiché, plage
   publiée = le mot visé. Il doit échouer sur `HEAD` avant correction.
2. `ParagraphBlockDisplay` dessine la sélection libre à partir de la plage absolue
   (`absoluteRangePath`, comme le mot TTS), qu'elle tienne entière dans le bloc ou non.
3. À l'appui long, `ParagraphBlockDisplay` publie lui-même les bornes du mot
   (`rangeBoundsInWindow` → `onFreeSelectionBoundsInWindow`) : le popup apparaît.
4. Suppression de `ParagraphBlockField` et de l'échange `Text` ↔ `BasicTextField`.
   Le tap hors sélection efface la sélection (aujourd'hui fait par le champ).
- **Écart déclaré :** sélection au mot, sans poignées, jusqu'au 25.3.
- **Sortie :** appui long → menu → surlignage créé, sur appareil ; test vert.

### 25.1 — Mesure de référence (sans changement de comportement)

1. Réutiliser `scripts/measure-reader-frames.sh scroll` (ne pas écrire un second script),
   au calme (aucun travail de fond : vérifier l'absence de réindexation avant la mesure).
2. Ajouter au module `benchmark/` un test `FrameTimingMetric` de défilement du Lecteur, sur
   le build de benchmark (R8, profil de référence), et ajouter le défilement du Lecteur au
   parcours de `BaselineProfileGenerator`. C'est ce build qui porte les critères chiffrés ;
   le script debug sert aux comparaisons rapides avant/après.
3. Trace Perfetto (ou `androidx.tracing` dans le benchmark) pour nommer les 2 ou 3
   premières causes réelles du fil principal, en confrontant les candidats listés plus haut.
4. Reproduire la sélection en mode paginé (`PageBlock`, geste natif dans le champ)
   et consigner.
- **Sortie :** chiffres de référence (debug et benchmark, trois passages au moins) et
  causes nommées dans ce document ; cibles chiffrées du lot fixées à partir d'eux.

### 25.2 — ADR-029 et spike des risques de C

- Lecture de l'API (B) : confirmation écrite que la sélection native n'expose pas d'offsets.
- Prototype jetable des deux risques de C : appui long puis glissement dans la `LazyColumn`
  sans que la liste vole le geste ; défilement automatique au bord pendant le glissement,
  avec un bloc qui sort de la composition au milieu d'une sélection.
- **Sortie :** ADR-029 (option retenue, alternatives écartées et pourquoi, mesures du spike).

### 25.3 — Moteur de sélection en mode défilement

- Registre des blocs visibles (`TextLayoutResult` + coordonnées, par plage absolue), alimenté
  par les blocs et lu par le geste ; test d'appartenance position → offset absolu.
- Poignées dessinées par nous (cibles tactiles de 48 dp, loupe via `Modifier.magnifier` si
  disponible sans API expérimentale, sinon écart déclaré), glissement d'un paragraphe à
  l'autre, défilement automatique au bord.
- Défilement de la liste rendu possible avec une sélection active (retrait de
  `userScrollEnabled = freeSelectedRange == null`) : popup masqué pendant le défilement,
  réaffiché à l'arrêt si la plage est visible.
- `resolveSelectionPopupBounds` : bornes = union des blocs visibles de la plage.
- Accessibilité dans le même palier, pas à la fin : action sémantique de sélection
  (`setSelection`) sur les paragraphes, annonce de la plage sélectionnée.
- Tests JVM de la logique pure : mot sous le doigt, extension, inversion quand une poignée
  croise l'autre, bornage au chapitre, correspondance registre → offset. Test instrumenté du
  glissement entre deux paragraphes.

### 25.4 — Défilement : correctifs des causes nommées en 25.1

- Un correctif par cause, chacun mesuré avant/après (script debug, puis benchmark), un commit
  par cause. Le retrait de `BasicTextField` du 25.0 se mesure ici aussi.

### 25.5 — Retrait et documentation

- KDoc de tête de `ReaderScreen.kt` et de `BookBlockItem.kt` : retirer « jamais à cheval »
  pour le mode défilement (il reste vrai en paginé). Suivi de l'audit : D50 partie
  défilement faite (commits cités), partie page reportée.
- `PageBlock` n'est **pas** du code mort : c'est le rendu du mode paginé, il reste.

## Critères de sortie du lot

- [x] Test instrumenté « appui long en défilement → menu » vert, et rouge sur `40d9c650`.
- [x] Appui long en défilement : poignées et menu d'actions visibles, sur appareil.
- [x] Glissement d'un paragraphe à l'autre, avec défilement automatique, puis surlignage
      créé aux bons offsets et retrouvé à la réouverture.
- [x] Défilement possible avec une sélection active ; la sélection survit à l'aller-retour
      d'un paragraphe hors écran.
- [x] Défilement, build de benchmark : cibles fixées au 25.1 tenues (ordre de grandeur
      visé : moins de 5 % d'images saccadées, 99e centile sous 32 ms, Blueprint §11.2).
- [x] Mode paginé : sélection au moins aussi bonne qu'avant (vérifié sur appareil).
- [x] TalkBack : sélection atteignable et plage annoncée.
- [x] Tests JVM de la logique de sélection ; tests instrumentés ajoutés verts.

## Ce que ce lot ne fait pas

- **Sélection à cheval sur deux pages en mode paginé (D50, partie page) : reportée à un
  lot suivant, écart déclaré.** Glisser une poignée au bord d'un `HorizontalPager` pose
  un problème d'interaction distinct (tourner la page pendant le glissement, ou une action
  « Étendre à la page suivante » dans le menu) qui mérite sa propre décision, une fois
  le moteur de C éprouvé en défilement.
- Pas de changement du modèle `Locator` ni de la persistance des annotations.
- Pas de réécriture du rendu des blocs (images, titres) hors ce qui sert la sélection.
- Pas de montée du BOM Compose dans ce lot.
- Les autres freins de l'audit de réactivité (Bibliothèque, mémoire) restent hors lot.

---

## Bilan d'exécution (2026-11-16)

**Lot clos par Issa le 2026-11-16, en l'état** : critères de sortie tenus ; cause 1 du palier 25.4 sans correctif mesurable, écart déclaré ci-dessous.

| Palier | Résultat | Preuve |
|---|---|---|
| 25.0 | Menu d'actions rétabli en défilement, `BasicTextField` retiré ; validé sur appareil | `1445590f`, `BookBlockItemSelectionTest` |
| 25.1 | Benchmark, procédure sûre, mesures de référence (script et `FrameTimingMetric`) et trace des images longues (voir ci-dessous) | `916fa75e`, `9eb0c7a0`, `4a21664a`, `6cfe2d4c`, commit de ce bilan |
| 25.2 | ADR-029 : moteur maison, `SelectionContainer` écarté sur lecture de l'API | `47f6471a` |
| 25.3 | Poignées, loupe, glissement entre paragraphes, défilement automatique et libre, `setSelection` ; 9 points validés sur appareil (mode paginé et TalkBack compris) | `150da8cd`, `96446a7a`, `SelectionGeometryTest` |
| 25.4 | Cause 2 corrigée (profil embarqué dans le build mesuré, profil régénéré) ; cause 1 essayée sans gain, retirée (voir ci-dessous) | `e948fd66` et commits suivants |
| 25.5 | KDoc et suivi de l'audit (D50 fait en défilement, partie page reportée) | `e12058ce` |

### Mesure de référence (V2206, build `benchmark`, au calme, après 25.3)

`scripts/measure-reader-frames.sh scroll 6`, trois passages dans un chapitre long
(Zombie Island, chapitre 8), processeur à ~92 % libre :

| Passage | Images | Saccadées | p50 / p90 / p99 | > 32 ms |
|---|---|---|---|---|
| 1 | 105 | 3 (2,9 %) | 11 / 14 / 29 ms | 0 |
| 2 | 109 | 3 (2,8 %) | 11 / 13 / 23 ms | 0 |
| 3 | 111 | 3 (2,7 %) | 10 / 13 / 22 ms | 0 |

`ReaderScrollBenchmark` (même build, trois itérations de 5 balayages vers le bas puis 5
vers le haut) : 579 images par itération ; durée CPU par image p50 7,7 ms, p90 11,8 ms,
p99 20,3 ms ; dépassement d'échéance p99 5,1 ms.

Trace Perfetto (itération 1) : 4 images sur 639 au-delà de 16,6 ms (0,6 %), la plus longue
à 21,2 ms. Causes :

1. Composition des paragraphes qui entrent à l'écran : phase « animation » de 11 à 15 ms
   (`Recomposer:recompose`), puis tracé (`Record View#draw()`), dans 3 images.
2. Premier toucher d'un geste : `AndroidOwner:onTouch` à 13 ms, dans 1 image.

Critères tenus : moins de 5 % d'images saccadées, 99e centile sous 32 ms, aucune image
au-delà de 32 ms (Blueprint §11.2). Les 18 % du constat initial venaient d'un build debug
mesuré pendant la réindexation : ni le build ni la charge n'étaient représentatifs. Le
palier 25.4 n'a donc pas de cause à corriger ; les candidats lus dans le code (constat)
restent non confirmés et ne sont pas touchés.

### Écarts déclarés

- Palier 25.4, cause 1 : `contentType` par type de bloc sur les items de la `LazyColumn`
  (réutilisation de la composition d'un paragraphe sorti) essayé puis retiré, faute de gain
  mesuré — p99 CPU 20,3 → 20,8 ms, dépassement p99 5,1 → 5,5 ms, dans le bruit.
  Cause 2 (premier toucher, 13 ms) : du code non compilé d'avance. Le build `benchmark`
  n'embarquait pas le Baseline Profile de la release (`src/release/generated/…`) ; corrigé,
  il mesure désormais ce que l'app publiée exécute : CPU p99 20,3 → 16,2 ms, dépassement p99
  5,1 → 1,9 ms, images longues 4 → 1 sur 640, plus aucune image d'entrée longue. Pour l'app
  publiée, qui avait déjà ce profil, c'était donc un artefact de mesure, pas un défaut.
  Profil ensuite régénéré avec le défilement du Lecteur (4 109 entrées InkTone contre 2 303,
  sélection du lot 25 comprise) : pas de gain mesurable sur ce scénario (p99 17,3 ms, dans
  le bruit), conservé parce que l'ancien ne couvrait plus le code actuel du Lecteur.
  Reste une image longue sur 640 (composition, cause 1).
- Un appui long suivi d'un glissement sans lever le doigt n'étend pas la sélection ;
  l'extension passe par les poignées.
- Incident : `:benchmark:connectedBenchmarkBenchmarkAndroidTest` a désinstallé l'app de
  l'appareil de test (bibliothèque effacée, réimportée ensuite) ; procédure sûre documentée
  dans `ReaderScrollBenchmark`.
