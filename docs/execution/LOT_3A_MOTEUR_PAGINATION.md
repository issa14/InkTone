# Lot 3a — Moteur de pagination réel

**Base :** branche `lot-2b-presentation-livres` à `16ef1a8`. Référence cible : `UX_FLOW_DESIGN.md` § Lecture — vue silencieuse (mode pagé), § Immersion.

**Série révisée (ordre A) :** **3a moteur de pagination** (ce lot) → 3b chrome silencieux → 3c sous-écrans du panneau → 3d couche TTS.

Ordre A retenu après chiffrage : il supprime la classe jetable, la passe device partielle et le reliquat porté sur trois lots qu'impliquait l'ordre B. Il place aussi le morceau techniquement le plus incertain du lecteur en premier, conformément au principe déjà appliqué à la réécriture (dérisquer les hypothèses techniques avant de construire l'UI au-dessus).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Décision actée en amont

**Pages virtuelles dans les deux modes** (défilement et pagé). Le moteur de ce lot doit donc être indépendant du mode : il paginera à partir du texte, du style et des dimensions du viewport, que le rendu final soit un pager horizontal ou une liste défilante. La **consommation** par la ligne de statut est le lot 3b ; ce lot fournit le calcul et l'utilise dans le mode pagé.

## Avertissement de périmètre

Ce lot est le plus risqué de la série. Il ne se limite pas à remplacer une formule : la mesure réelle du texte impose de changer la façon dont une page est rendue, ce qui touche au surlignage mot-à-mot, à la sélection de phrase et aux annotations. **Séquencer strictement** : moteur pur et testé d'abord, bascule du rendu ensuite. Ne pas mener les deux de front.

---

## Tâche 3a.1 — Moteur de pagination par mesure réelle

Aucune UI dans cette tâche. Composant pur, testable hors Compose UI.

**Ce qui existe et disparaît :** `PagedChapterContent.kt:51-74` — `charsPerPage = (800 * 18 / fontSizeSp)` puis accumulation de caractères. Aucune mesure du texte rendu, aucune prise en compte de la largeur du viewport, de l'interligne ni de la police. À supprimer, pas à ajuster.

**Contrat :**

```
interface VirtualPagination {
    fun pageCount(chapterIndex: Int): Int
    fun pageIndexAt(chapterIndex: Int, sentenceIndex: Int): Int
    fun sentenceRangeOf(chapterIndex: Int, pageIndex: Int): IntRange
}
```

**Implémentation par mesure :**

1. Construire le contenu du chapitre en `AnnotatedString`, **en préservant `ParagraphStyle`** (voir 3a.2 — les titres doivent rester des titres).
2. Mesurer via `TextMeasurer` avec le `TextStyle` réellement appliqué au rendu — taille de police, interligne, famille — et des contraintes de largeur égales à la largeur du viewport moins le padding horizontal effectif (16 dp aujourd'hui, `PagedChapterContent.kt:102`).
3. Depuis le `TextLayoutResult`, découper aux **frontières de lignes**, en lisant la géométrie réelle de chaque ligne (`getLineTop`/`getLineBottom`) et **jamais une hauteur de ligne constante**. Accumuler ces hauteurs réelles jusqu'à dépasser la hauteur utile du viewport, couper avant.

   Ce point est critique dès lors que 3a.2 restaure les `ParagraphStyle` : un chapitre mêle titres et texte courant, avec des interlignes, des tailles et des espacements de paragraphe différents. Toute formule du type `nombre de lignes × interligne` produira des pages trop pleines ou trop vides selon la densité de titres. La géométrie par ligne est la seule source acceptable.
4. Convertir chaque frontière de page en offset de caractère (`getLineStart`/`getLineEnd`), puis en index de phrase via `Sentence.startOffset`/`endOffset`, qui existent déjà.

**Ancrage de la position de lecture.** Invariant à respecter partout : **la position persistée est la phrase courante, jamais l'index de page.** Un index de page n'a de sens que pour un couple (style, viewport) donné et devient faux dès que l'un change.

Mécanique attendue à chaque recalcul — rotation, changement de taille de police, d'interligne ou de police :

1. Capturer `currentSentenceIndex` **avant** l'invalidation.
2. Recalculer la pagination pour les nouvelles dimensions.
3. Repositionner le pager sur `pageIndexAt(chapterIndex, sentenceIndexCapturé)`.

C'est précisément ce que `pageIndexAt` existe pour faire : la ré-indexation est une conséquence du contrat, pas un traitement spécial à la rotation. Le vérifier par test (3a.4, test 11) et sur appareil (point 10).

**Clé d'invalidation du cache** — recalculer si et seulement si l'un change : index de chapitre, taille de police, interligne, famille de police, largeur du viewport, hauteur du viewport, padding. Ne pas invalider sur changement de thème (les couleurs ne déplacent pas le texte).

**Aucune coupure au milieu d'une ligne**, et pas de ligne orpheline en bas de page. Le découpage par lignes mesurées le garantit ; le vérifier par test plutôt que le supposer.

`Remplace l'estimation au caractère par une pagination mesurée`

---

## Tâche 3a.2 — Basculer le rendu du mode pagé

Dépend de 3a.1. C'est la tâche à risque.

**Trois défauts corrigés ensemble, parce qu'ils ont la même cause** — le rendu d'une page comme `FlowRow` de composables `SentenceText` séparés (`PagedChapterContent.kt:100-130`) :

1. **`sentences.indexOf(sentence)`** (`PagedChapterContent.kt:107`) — recherche linéaire par phrase affichée, à l'intérieur de la boucle de rendu, donc réévaluée à chaque recomposition. O(n²) par page. Disparaît dès lors que la page connaît son `IntRange` de phrases (`sentenceRangeOf`) : l'index global se déduit par addition, sans recherche.
2. **`ParagraphStyle.NORMAL` en dur** (`PagedChapterContent.kt:108`) — les titres réellement présents dans l'EPUB sont rendus comme du texte courant en mode pagé, alors que le mode défilement les honore (`ReaderScreen.kt`, chemin `SentenceText` avec le style réel). C'est une régression par rapport à la décision d'immersion de la cible : l'app n'injecte pas de bannière de chapitre, mais elle doit respecter les titres du fichier. À corriger.
3. **Le `FlowRow`** juxtapose des phrases sans structure de paragraphe, ce qui empêche toute mesure fidèle et casse la justification.

**Cible du rendu :** une page = un bloc de texte construit depuis l'`AnnotatedString` du chapitre, tranché sur l'intervalle d'offsets de la page. Le surlignage mot-à-mot, la couleur d'annotation et l'état de sélection deviennent des `SpanStyle` appliqués sur cet `AnnotatedString`, au lieu de propriétés de composables distincts.

**Coût de recomposition — contrainte structurante.** En passant d'un `Text` par phrase à un bloc par page, l'unité de recomposition grossit : ce qui invalidait une phrase invalide désormais la page entière. Or le surlignage mot-à-mot change à cadence élevée pendant le TTS. Sans précaution, chaque mot prononcé relancerait mesure **et** placement de toute la page.

Exigence : **la mise à jour du surlignage ne doit invalider que la phase de dessin**, jamais la mesure ni le placement.

- Le `highlightedWordRange` et l'index de phrase courante sont lus **au plus tard**, via une lambda (`State` lu dans un bloc de dessin ou de composition différée), et non capturés comme paramètres du composable de page. Un paramètre change → recomposition ; un `State` lu en phase de dessin → redessin seul.
- L'`AnnotatedString` de la page, elle, ne dépend que du texte et du style — donc du couple (chapitre, style, viewport). Elle est `remember`-isée sur cette clé et **ne se reconstruit pas** à chaque mot.
- Le surlignage s'applique donc par-dessus un layout stable, pas en reconstruisant l'`AnnotatedString` à chaque mot. Reconstruire la chaîne annotée par mot prononcé est le piège exact à éviter : c'est fonctionnellement correct et catastrophique en performance.

**Vérification attendue :** mesurer le nombre de recompositions de la page pendant une lecture TTS continue. Si le compteur croît avec les mots prononcés, l'isolation a échoué. À rapporter comme un résultat, pas comme une case cochée.

**Trois comportements à ne pas perdre** — ils passaient par les composables par phrase :

| Comportement | Aujourd'hui | À reconstruire via |
|---|---|---|
| Appui long → sélection de phrase | `onLongClick` par `SentenceText` | `pointerInput` + `TextLayoutResult.getOffsetForPosition` → offset → index de phrase |
| Surlignage mot-à-mot pendant le TTS | `highlightedWordRange` par phrase | `SpanStyle` sur l'intervalle de mots dans l'`AnnotatedString` |
| Règle de lecture | `onGloballyPositioned` sur la phrase jouée (`PagedChapterContent.kt:120-124`) | position de ligne via `TextLayoutResult.getLineTop` |

**Conserver tel quel :** la page fantôme au-delà de la dernière (`pageCount = pages.size + 1`) et le `LaunchedEffect` de passage au chapitre suivant (`PagedChapterContent.kt:87-92`). C'est la correction d'un bug réel déjà trouvé à l'audit, avec son commentaire explicatif. Ne pas la refactoriser au passage.

`Rend chaque page depuis un bloc de texte mesuré`

---

## Tâche 3a.3 — Sortir la mesure du thread de composition

Le KDoc actuel affirme que le découpage se fait « hors thread UI » (`PagedChapterContent.kt:31`). C'est faux : le bloc est un `remember { }` (`PagedChapterContent.kt:55`), exécuté sur le thread de composition. Une mesure réelle sur un chapitre entier sera nettement plus lourde que l'accumulation de caractères actuelle — cette fausseté cesse d'être un détail de documentation.

**À faire :** construire le `TextMeasurer` en composition (il lui faut `Density`, `FontFamily.Resolver`, `LayoutDirection`), puis exécuter la mesure dans une coroutine sur `Dispatchers.Default`.

**Ne pas replier sur un rendu en mode défilement pendant le calcul.** C'était la consigne d'une version précédente de ce plan, et elle est mauvaise : basculer du défilement au pagé refait couler tout le texte, ce qui produit un saut d'affichage brutal au moment précis où l'utilisateur commence à lire.

**Ne pas non plus afficher un squelette de chargement**, qui ne montre aucun texte — dans une application de lecture, cela revient à faire attendre devant du vide.

**Mesure en deux temps, à privilégier :**

1. Mesurer **la première page seulement**, de façon synchrone ou quasi immédiate. C'est peu coûteux : on s'arrête dès que la hauteur du viewport est atteinte, sans parcourir le chapitre.
2. Afficher cette première page immédiatement, dans sa mise en forme **définitive**.
3. Compléter la pagination du reste du chapitre en arrière-plan.

L'utilisateur lit tout de suite, dans le rendu final, et rien ne bouge sous ses yeux. Le seul effet visible est que `pageCount` peut être provisoire pendant une fraction de seconde — à traiter en affichant un total indéterminé plutôt qu'un chiffre faux qui se corrigerait.

Si cette approche se révèle impraticable pour une raison que le code révèle à l'implémentation, **le signaler et proposer** plutôt que de retomber silencieusement sur le repli en défilement.

**Corriger le KDoc** pour qu'il décrive ce que le code fait. Une documentation qui contredit le code est un piège pour le prochain audit.

**Précharger** la pagination du chapitre suivant si `ChapterPreloader` s'y prête — à évaluer, pas à forcer.

`Déporte la mesure de pagination hors du thread de composition`

---

## Tâche 3a.4 — Tests

Le composant est enfin testable : `PagedChapterContent` n'a **aucun test** aujourd'hui, aucun fichier de test ne le référence.

**Tests unitaires du moteur** (JVM, sans Compose UI — c'est l'intérêt d'avoir extrait le calcul) :

1. `pageIndexAt` croît avec l'index de phrase, reste dans `[0, pageCount)`.
2. `pageCount ≥ 1` sur chapitre vide ; `sentenceRangeOf` ne renvoie jamais un intervalle vide sur une page rendue.
3. Aller-retour : pour tout index de phrase, `sentenceRangeOf(pageIndexAt(i))` contient `i`. C'est le test central de cohérence.
4. Réduire la taille de police **augmente** le nombre de pages ; l'augmenter le réduit. Relation monotone, pas de valeur figée.
5. Élargir le viewport réduit le nombre de pages.
6. Aucune phrase perdue : l'union des `sentenceRangeOf` sur toutes les pages couvre exactement `[0, sentences.lastIndex]`, sans trou ni recouvrement.
7. Invalidation du cache : un changement de thème ne déclenche **pas** de recalcul ; un changement d'interligne oui.
8. **Hauteurs mixtes** — un chapitre commençant par un titre produit un `pageCount` différent du même chapitre sans titre, à texte égal. Si les deux donnent le même nombre, la mesure utilise une hauteur de ligne constante et le point 3 de 3a.1 n'est pas respecté.
9. **Ré-indexation** — pour tout index de phrase et deux dimensions de viewport, `pageIndexAt` donne bien la page contenant cette phrase dans chacune. C'est la rotation testée sans appareil.

Le test 6 est le garde-fou principal — c'est lui qui attrape une coupure ratée. Les tests 8 et 9 couvrent les deux cas limites identifiés avant lancement.

**Tests Compose :**

8. Un titre EPUB s'affiche avec le style titre en mode pagé (non-régression du défaut corrigé en 3a.2).
9. Appui long sur une phrase déclenche la sélection avec le bon index global.
10. Le surlignage mot-à-mot se positionne sur la bonne phrase après changement de page.

`Ajoute les tests du moteur de pagination et du rendu pagé`

---

## Tâche 3a.5 — Consigner dans la cible

Ajouter dans `UX_FLOW_DESIGN.md`, § Lecture — vue silencieuse :

> **Mode pagé (lot 3a).** Les trois défauts signalés sont corrigés : pagination par mesure réelle du texte, plus par estimation au caractère ; recherche linéaire par phrase supprimée ; composant couvert par des tests. Un quatrième défaut trouvé à l'implémentation est corrigé au passage : le mode pagé forçait `ParagraphStyle.NORMAL`, rendant les titres de l'EPUB comme du texte courant — le respect des titres réels du fichier, acté au § Immersion, est rétabli. **Pages virtuelles disponibles dans les deux modes** ; leur affichage dans la ligne de statut arrive au lot 3b.

`Consigne la correction du mode pagé dans la cible`

---

## Vérifications sur appareil — lot 3a

Toutes en **mode pagé**, à activer par l'icône « Mode » du panneau.

| # | Avant (`16ef1a8`) | Après attendu |
|---|---|---|
| 1 | Pages coupées arbitrairement : lignes tronquées en bas, ou grands blancs | Chaque page remplit l'écran, aucune ligne coupée, aucun blanc anormal en bas |
| 2 | Un titre de chapitre EPUB s'affiche comme du texte courant | S'affiche avec le style titre, comme en mode défilement |
| 3 | Changer la taille de police redécoupe de façon incohérente | Le redécoupage est cohérent : texte plus grand = plus de pages, aucune phrase perdue |
| 4 | — | Parcourir un chapitre entier page par page : la dernière page contient bien la dernière phrase, rien n'a disparu |
| 5 | — | Swipe au-delà de la dernière page passe au chapitre suivant (non-régression du correctif existant) |
| 6 | — | Appui long sur une phrase la sélectionne, et c'est bien **cette** phrase qui est annotée |
| 7 | — | Lancer le TTS en mode pagé : le surlignage mot-à-mot suit le bon mot, y compris après changement de page |
| 8 | — | Règle de lecture activée : elle se positionne sur la ligne lue |
| 9 | Découpage sur le thread de composition | Ouvrir un chapitre long : la première page s'affiche immédiatement **dans sa mise en forme définitive**, aucun reflux du texte ensuite, aucun écran vide |
| 10 | — | Rotation de l'écran : la pagination se recalcule et **la phrase en cours de lecture reste affichée**, sur sa nouvelle page |
| 11 | — | Lecture TTS continue en mode pagé pendant ~1 min : pas de saccade, pas de chauffe anormale (symptômes d'une recomposition par mot) |

Le point 10 est celui que je vérifierais en premier : c'est le cas où un moteur mesuré échoue le plus volontiers, et il n'existait pas comme risque avec l'estimateur au caractère.

---

## Hors périmètre explicite

Barre du haut du lecteur, ligne de statut persistante, restructuration du panneau unifié en trois rangées → **lot 3b**.

Sommaire en bottom sheet, panneau Marque-pages à 3 onglets, panneau TT, Thème cyclique, Luminosité, Minuteur et rappel de repos oculaire, panneau Voix et son curseur de vitesse mort, popup de sélection Copier/Surligner/Note → **lot 3c**.

Barre pilule TTS, repli en FAB, onde sonore, swipe-down → **lot 3d**.

Hiérarchie du sommaire (`children` jamais observé non vide, TODO à `TableOfContentsSheet.kt:31`) → lot 3c, avec la fixture EPUB nécessaire.
