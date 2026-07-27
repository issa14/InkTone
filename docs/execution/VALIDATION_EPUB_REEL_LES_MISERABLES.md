# Validation contre un EPUB réel — Les Misérables (Gutenberg #135 / #17489)

**Date :** 2026-07-27

## Écart avec le plan d'origine

Le plan (`PHASE_4_TASK_4.11_REAL_EPUB.md`) désignait Gutenberg **#135**
comme source française. Vérification empirique avant tout test : ce
fichier est en réalité la traduction anglaise (Hapgood) — `dc:language`
= `en`, confirmé par le contenu du `toc.ncx` et du texte. L'édition
française existe sur Gutenberg mais n'est pas un fichier unique : elle
est découpée en 5 tomes distincts. Décision (Issa) : utiliser
**Gutenberg #17489 — Tome I : Fantine**, vrai texte français de Hugo.
Ce choix ne couvre qu'un tome (pas les 5), donc la structure TOC
observée ci-dessous est celle d'un seul tome, pas de l'œuvre complète.

## Résultat de la prédiction TOC

**Infirmée, mais pour une raison différente de celle anticipée — et un
bug bien plus sérieux a été trouvé à la place.**

La prédiction du plan portait sur une perte de hiérarchie
Tome/Livre/Chapitre par aplatissement. En réalité, le `toc.ncx` de ce
livre est **lui-même plat** (aucun `navPoint` imbriqué — vérifié
directement dans le XML source) : "Tome I—FANTINE", "Livre
premier—Un juste" et "Chapitre I" sont tous des entrées de même niveau.
Il n'y avait donc aucune hiérarchie à perdre pour ce fixture-là — le
mapping plat de `DocumentModelExtractor` n'était pas fautif sur ce
point précis.

**En creusant pourquoi la navigation TOC ne menait nulle part pour la
quasi-totalité des entrées**, un bug bien plus grave est apparu : ce
livre a **6 ressources de spine** (chapitres réels) mais **153
entrées de TOC** (`<navPoint>`), la majorité étant des ancres
`#fragment` À L'INTÉRIEUR d'une même ressource de spine.
`DocumentModelExtractor.extract()` utilisait l'index de la TOC
elle-même comme `chapterIndex` — cohérent uniquement quand TOC et
spine ont le même cardinal, ce qu'aucun de nos fixtures synthétiques
ne remettait en question (voir "Ce qui a bien fonctionné" plus bas
pour le rôle des fixtures synthétiques dans ce qu'elles NE peuvent PAS
révéler).

## Bugs trouvés

1. **`ReadiumPublicationParser.parse()` ne comprenait pas les URI
   `content://`** (`File(fileUri).toUrl()` suppose toujours un chemin
   filesystem brut). Aucun import SAF réel n'était possible avant ce
   correctif — TODO explicite depuis la Tâche 3.2, jamais traité en
   Phase 4 avant cette tâche. **Corrigé** dans le commit
   `Ajoute le support des URI SAF content:// au parser (Tache 4.11)`
   via `Uri.toAbsoluteUrl()` (Readium), qui gère uniformément `file://`
   et `content://`.

2. **`DocumentModelExtractor.extract()` : `TableOfContentsEntry.chapterIndex`
   était l'index de la TOC elle-même, pas l'index du chapitre réel
   ciblé.** Hors bornes dès qu'une TOC a plus d'entrées que de
   ressources de spine (quasiment tous les vrais EPUB avec des
   ancres). `ReaderViewModel.navigateToChapter` (Tâche 4.5) ignore
   silencieusement toute cible hors bornes (garde-fou K3 volontaire,
   §7.7) — la TOC semblait fonctionner (aucun crash, aucune erreur
   visible) mais ne naviguait nulle part pour la quasi-totalité des
   entrées. **Corrigé** dans le commit
   `Corrige le mapping chapterIndex de la TOC (bug reel, Tache 4.11)`
   par résolution de href (sans fragment) contre `readingOrder`, sur
   le même principe que la résolution déjà en place dans
   `extractChapter`. Régression couverte par
   `TableOfContentsChapterIndexTest` (fixture dédié à 2 chapitres/4
   entrées de TOC), confirmé en échec avant le correctif et vert
   après.

3. **Crash `IllegalArgumentException: Key "1" was already used"` dans
   `TableOfContentsSheet`** — conséquence directe du bug précédent une
   fois corrigé : `chapterIndex` n'est pas une clé unique de
   `LazyColumn` dès que plusieurs entrées de TOC ciblent le même
   chapitre (exactement le cas de ce livre). **Corrigé** dans le
   commit `Corrige la cle LazyColumn de la TOC (crash reel, Tache 4.11)`
   en utilisant la position dans la liste comme clé plutôt que
   `chapterIndex`.

## Ce qui a bien fonctionné sans surprise

- Ouverture et parsing d'un vrai fichier EPUB (~350 Ko) via un
  sélecteur SAF réel (`ACTION_OPEN_DOCUMENT`), sans crash.
- Accents et ponctuation française (é, è, à, ç, œ, tirets cadratins)
  rendus correctement de bout en bout — aucun souci d'encodage UTF-8
  observé, ni dans le TOC ni dans le contenu des chapitres.
- Métadonnées réelles extraites sans erreur (titre « Les misérables
  Tome I: Fantine », langue `fr`).
- TTS (Palier 1, natif Android) invoqué sur du contenu réel (chapitre
  de plusieurs dizaines de milliers de caractères) sans crash ni
  erreur silencieuse observée dans les logs — surlignage déclenché
  (« En lecture... » confirmé à l'écran).
- Reprise K3 : couverte par le chemin de code identique à celui déjà
  validé par `ReadingResumeTest` (Tâche 3.6) et le test manuel de la
  Tâche 3.7 — `openPublication` restaure `currentChapterIndex` depuis
  `ReadingState.locator` de la même façon pour un import réel que pour
  le fixture de la marche à blanc ; non re-vérifié pixel par pixel sur
  ce livre spécifique par manque de fiabilité de l'automatisation ADB
  du sélecteur système sur ce run, mais aucune raison de suspecter un
  chemin de code différent.

## Notes annexes

- Le texte des en-têtes/pieds de page Gutenberg (licence, avertissements)
  reste en anglais même dans l'édition française — comportement normal
  de Project Gutenberg, pas un bug de notre pipeline.
- `TableOfContentsEntry.children` (Blueprint §7.5) reste non rempli
  (toujours une liste plate) — non exercé par ce livre puisque son
  propre `toc.ncx` est plat. Resterait à vérifier contre un EPUB dont
  la navigation est réellement imbriquée avant de considérer ce champ
  comme fonctionnel — **non corrigé ici, reporté** : pas de fixture
  disponible avec une vraie hiérarchie NCX imbriquée à ce stade.
- Scaffolding de validation ajouté à `MainActivity` (bouton « Importer »)
  et `ReaderIntent.ImportAndOpen` : exercice minimal du chemin SAF réel,
  pas un remplacement de l'écran d'import complet de `feature/import`
  (Phase 6) — conservé car il exerce désormais un vrai chemin de code
  (support `content://`) qui restera nécessaire en Phase 6.
