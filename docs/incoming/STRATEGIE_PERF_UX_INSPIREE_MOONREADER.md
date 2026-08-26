# Stratégie — Améliorer InkTone (performance, fluidité, UX) inspirée de Moon+ Reader

> Document de travail. Chaque recommandation est fondée sur l'état **vérifié**
> du code InkTone (fichier, classe, citation), pas sur des suppositions.
> Règle appliquée : « seul le code fait foi » (CLAUDE.md §17.2).

- **Source d'inspiration** : analyse de l'APK Moon+ Reader Pro
  (`/tmp/moonreader/RAPPORT_PDF_MOONREADER.md`).
- **Date** : 2026-08-26.
- **Périmètre** : rapidité, fluidité, UX. Hors périmètre : le choix de moteur
  de rendu EPUB (Readium est acté et supérieur au rendu approximatif de Moon+).

> **Instruit le 2026-08-26** : ce document a été converti en deux lots
> d'exécution — `docs/execution/LOT_21_GAINS_RAPIDES_PERF_UX.md` (leviers à
> effort faible et écarts intention↔code) et
> `docs/execution/LOT_22_PERSISTANCE_ET_PARITE_ANNOTATIONS.md` (caches
> persistants, parité des annotations, complétion sync). Il reste la
> référence des constats et des preuves ; les lots portent l'exécution.
> **Deux affirmations de ce document ont été corrigées** lors de cette
> conversion (synthèse VITS non déterministe ; latence déjà masquée par le
> `LOOKAHEAD = 3` du pipeline gapless) : voir « Corrections apportées au
> document source » dans le Lot 22, qui fait foi sur ces deux points.

---

## 0. Principe directeur transféré depuis Moon+

> **Faire le travail lourd une seule fois, à l'import, en arrière-plan, puis
> lire vite.**

Chez Moon+, cela se traduit par : conversion MOBI→EPUB à l'import (`libmobi`),
scan de dossiers en thread (`autoImportNewBooksThread`), couvertures en fond,
cache du nombre de pages (`getPageCountWithCache`).

InkTone applique déjà **largement** ce principe (voir §1). Ce document
identifie les endroits où il n'est **pas encore** appliqué, avec des preuves.

---

## 1. Constat global : ce que InkTone fait déjà bien

| Mécanisme | État vérifié |
|---|---|
| Import one-shot en arrière-plan | `ImportWorker` (`Semaphore(4)`), `WorkManagerImportScheduler` (lots de 50), `ImportPublicationUseCase` |
| Hash + déduplication | `computeSha256` + index unique `publications.fileHash` |
| Parsing EPUB paresseux à l'import | `ReadiumPublicationParser.parseLazy` (métadonnées + TOC + coquilles de chapitres) |
| Une seule ouverture ZIP (K2) | `ReadiumPublicationRegistry.getOrOpen` (Mutex + cache) |
| Index FTS4 peuplé à l'import | `SentenceFtsEntity` / `RoomSearchService.indexSentences` |
| SAF exclusif (K5) | `persistReadPermission` avant insertion |
| Sync métadonnées seules | `BackupPayload` — « jamais les livres eux-mêmes » |
| Réduction de mouvement | `UserPreferences.reduceMotion` + `core/designsystem/ReducedMotion.kt` |
| Rendu PDF sans Android dans le domaine | `FixedPageRenderer` / `RenderedPage` (pixels bruts) |

**Conclusion** : InkTone est déjà au niveau (souvent au-dessus) de Moon+ sur
l'architecture. Les leviers restants sont des **optimisations ciblées**, pas
des refontes.

---

## 2. Levier 1 — Cache persistant du découpage et des timestamps TTS (rapidité)

**Levier le plus rentable pour la latence TTS, cœur de différenciation d'InkTone.**

### État vérifié

- Le découpage en phrases est fait par `FrenchSentenceSplitter`
  (`domain/service/FrenchSentenceSplitter.kt`, `BreakIterator` FR), unique
  call site `JsoupChapterParser.tokenizeSentences` (`infrastructure/parser/`).
- Il est exécuté **à l'import** (pour l'index FTS, `ImportPublicationUseCase`)
  **et re-exécuté à la lecture** (`ReaderViewModel.loadChapterContentIfNeeded`,
  `PlaybackOrchestrator`), car le résultat n'est **pas persisté** : le cache
  `EpubChapterParser` est un `LruCache` mémoire de 5 Mo, vidé par
  `invalidate(publicationId)` à la fermeture du lecteur.
- Les **timestamps par mot** sont recalculés à **chaque** `synthesize()`, sans
  aucun cache :
  - Sherpa-ONNX → `CtcForcedAligner.align(...)` (Viterbi + CTC) ;
  - Android natif → `onRangeStart(...)` ;
  - Edge → `mapEdgeWordBoundaries(...)`.
  `AudioSegment.wordTimestamps` ne survit qu'à la phrase courante dans un
  `StateFlow` (`PlaybackOrchestrator._currentWordTimestamps`).
- **Aucune entité Room** ne stocke d'audio ni de timestamps (14 entités
  vérifiées, aucune TTS). Les usages de `cacheDir` sont des fichiers
  temporaires supprimés (`outputFile.delete()`).

### Recommandation

Persister, par publication, un **cache de synthèse TTS** hors du chemin
critique :

1. **Cache du découpage** : persister la liste des phrases (offsets) par
   chapitre — soit une entité `ChapterSentence` Room, soit un fichier
   sérialisé par publication. Élimine le re-`BreakIterator` + re-Jsoup à
   chaque ouverture.
2. **Cache des segments audio + timestamps** (le vrai gain) : ~~la synthèse
   Sherpa-ONNX est **déterministe**~~ **[CORRIGÉ — voir Lot 22 : `noiseScale`
   et `noiseScaleW` non nuls, aucune graine exposée, la synthèse VITS n'est
   pas reproductible ; audio et timestamps doivent donc être cachés
   ensemble]** pour un triplet
   `(publicationId, chapterIndex, sentenceOffset, voiceProfileId, hash des
   règles de prononciation)`. Cachez `AudioSegment` (audio + `wordTimestamps`)
   sur disque, clé = hash de ce quintuplet.

### Points de vigilance (non négociables)

- **ADR-021** : on cache du calcul **réel** (`CtcForcedAligner.align`), jamais
  de l'interpolation. Le cache doit être **invalidé** si la voix, les règles
  de prononciation ou le texte source changent.
- **Espace disque** : l'audio WAV est volumineux. Prévoir un plafond (LRU par
  publication, purge à la désinstallation du livre), éventuellement compression
  (Opus/AAC) si le décodeur le permet.
- **K2** : le cache ne doit pas ouvrir un second accès ZIP — il se construit
  à partir du `Chapter` déjà parsé.

### Effort / impact

| | |
|---|---|
| Effort | Moyen (1 entité + 1 service de cache + invalidation) |
| Impact | Élevé (latence TTS, autonomie, CPU) |
| Risque | Moyen (gestion d'invalidation — voir ci-dessus) |

---

## 3. Levier 2 — Césure française de qualité (UX typographique)

### État vérifié

- **Aucune césure maison** (grep exhaustif `Hyphenator` / `SoftHyphen` /
  `hyphenation` / `TeX` → zéro résultat).
- La césure est celle de la plateforme Compose : `Hyphens.Auto`, posée dans
  `ChapterPaginationState.kt` (construction du `TextStyle`) et reproduite dans
  `ReaderScreen.kt`, **uniquement quand `textJustified == true`**, solidaire de
  `TextAlign.Justify` + `LineBreak.Paragraph`.
- Le modèle `UserPreferences.textJustified` porte le commentaire explicite :
  « justifier sans césurer creuse des "rivières" blanches dans un texte
  français ». La césure n'est donc **jamais proposée seule** (hors
  justification).

### Recommandation

1. **Vérifier la locale de césure** : `Hyphens.Auto` se fonde sur la locale du
   texte. S'assurer que le `TextStyle` porte une locale `fr` (ou `fr-FR`) pour
   que l'hyphenation Android utilise les règles françaises. C'est un correctif
   **léger et à fort impact** pour un produit francophone-first.
2. **Optionnel (plus lourd)** : intégrer une césure à motifs TeX français
   (équivalent du `SHTextTeXHyphenator` de Moon+) si la césure de plateforme
   s'avère insuffisante (mots composés, guillemets, `-t-on`, `-je`). À ne
   faire que **mesuré** : benchmark de qualité avant/après.
3. **Exposer la césure indépendamment de la justification** si la mesure le
   justifie (aujourd'hui le modèle ne permet pas « césure sans justification »).

### Effort / impact

| | |
|---|---|
| Effort | Faible (locale) / Élevé (moteur TeX maison) |
| Impact | Moyen-élevé (justification propre en français) |
| Risque | Faible |

---

## 4. Levier 3 — Pré-analyse persistée des chapitres et phrases (ouverture)

### État vérifié

- À l'import, InkTone parse les **coquilles** de chapitres (`parseLazy`) et
  indexe les phrases en FTS4, puis `invalidate()` purge le cache.
- **Aucune entité `ChapterEntity` ni `SentenceEntity`** en base : seuls
  `chapterCount` / `pageCount` (compteurs dans `PublicationEntity`) et
  `sentence_fts` (texte + `charOffset`) survivent.
- À la lecture, le contenu de chapitre est **re-parsé** (Jsoup) et
  **re-découpé** à chaque ouverture : `ReaderViewModel.loadChapterContentIfNeeded`
  → `EpubChapterParser.parseChapter` → `JsoupChapterParser.parse` →
  `FrenchSentenceSplitter.split`.

### Recommandation

Matérialiser le `DocumentModel` (ou au minimum les chapitres + phrases) à
l'import, pour que l'ouverture d'un livre ne re-paie pas le parsing HTML :

- Entité `ChapterEntity` (`publicationId`, `index`, `href`, `title`, `blocks`
  sérialisés) ou fichier sérialisé par publication.
- Entité `SentenceEntity` (offsets par chapitre) — au-delà de la FTS, pour le
  TTS et la reprise.

C'est exactement le « faire une fois, lire vite » de Moon+
(`SplitCountCache` / `BaseEBook$Chapter`).

### Effort / impact

| | |
|---|---|
| Effort | Moyen-élevé (migration Room + serialisation) |
| Impact | Élevé (ouverture instantanée, reprise TTS immédiate) |
| Risque | Moyen (cohérence du cache vs source, re-import sur changement) |

---

## 5. Levier 4 — Uniformiser le découpage de phrases (correction d'incohérence)

### État vérifié

- `FrenchSentenceSplitter` n'est appliqué **que pour EPUB** (via
  `JsoupChapterParser`).
- **PDF** : `PdfTextExtraction.kt` utilise `PDF_SENTENCE_BOUNDARY =
  Regex("""(?<=[.!?])\s+""")`.
- **TXT** : `TxtPublicationParser.kt` utilise `sentenceBoundary =
  Regex("""(?<=[.!?])\s+""")`.

Le spike `docs/spikes/sentence-tokenizer-comparison.md` annonce
« FrenchSentenceSplitter comme source unique pour EPUB, PDF et TXT », mais le
code réel ne l'applique pas encore à PDF/TXT. C'est un **écart entre doc et
code** à corriger : la regex naïve découpe mal les abréviations françaises
(`Dr.`, `M.`, `etc.`), dégradant le TTS et le surlignage mot-à-mot sur ces
formats.

### Recommandation

Remplacer les deux regex naïves par `FrenchSentenceSplitter` (PDF et TXT),
avec un test de non-régression sur les abréviations françaises.

### Effort / impact

| | |
|---|---|
| Effort | Faible |
| Impact | Moyen (cohérence TTS sur PDF/TXT) |
| Risque | Faible (attention aux offsets PDF/TXT) |

---

## 6. Levier 5 — Rendu PDF : pipeline, cache de pages, fallback gracieux

### État vérifié

- Contrat domaine : `FixedPageRenderer` → `FixedPageDocument.renderPage(...)`
  → `RenderedPage` (pixels bruts). **Une seule implémentation** :
  `PdfPageRendererImpl` (PDFium `io.legere:pdfiumandroid` 1.0.20), chemin
  **100 % bitmap CPU** (`Bitmap.createBitmap` ARGB_8888 + `renderPageBitmap`).
- **Aucun OpenGL / SurfaceTexture / TextureView** dans le dépôt (les seules
  occurrences sont des faux positifs du mot « réglage »).
- **Aucune abstraction de moteur avec fallback** côté rendu PDF (les seuls
  `fallback` du projet concernent le TTS : `FallbackTtsEngine`).
- `FixedPageContent` : `BitmapCache(maxSize = 5)` (LRU mémoire), affichage
  `Image` + zoom par transformation `graphicsLayer` (transform GPU d'affichage,
  pas un pipeline de rendu GPU), re-rasterisation à résolution supérieure au
  relâchement du geste (debounce).

### Recommandation

Le double pipeline CPU/OpenGL de Moon+ est **surdimensionné** pour InkTone :
PDFium CPU + transform GPU Compose est déjà un bon design. À la place :

1. **Pré-rendu des pages adjacentes** : augmenter le `BitmapCache` et
   pré-rendre la page suivante/précédente pendant l'idle (supprime le blanc au
   swipe).
2. **Cache disque des pages rendues** (par `publicationId` + `pageIndex` +
   résolution) pour une reprise instantanée.
3. **Fallback gracieux** : si `renderPage` échoue (page corrompue, OOM),
   afficher un placeholder + journaliser, plutôt qu'un crash — dans l'esprit du
   message de Moon+ (« rendering error → basculer »), mais sans moteur GPU.

### Effort / impact

| | |
|---|---|
| Effort | Faible-moyen |
| Impact | Moyen (fluidité PDF, robustesse) |
| Risque | Faible |

---

## 7. Levier 6 — Animations de page et auto-scroll visuel (fluidité UX)

### État vérifié

- **Aucun style d'animation de tournage de page** : `PagedChapterContent`
  utilise le snap par défaut du `HorizontalPager` Compose. La seule animation
  de transition est la **transition de chapitre** par tirage (`transition/` :
  `ChapterTransitionState`, `ChapterTransitionMath` (amorti 0.5, rebond
  ressort), `ChapterTransitionConnection`, `ChapterTransitionIndicator`).
- **Aucun auto-scroll visuel** : le seul « auto-scroll » est le suivi de la
  phrase active pendant la narration TTS (`ReaderScreen` :
  `scrollState.animateScrollToItem(targetBlock)` sur `currentSentenceIndex`).
  Aucune vitesse de défilement réglable (grep `autoScroll|scrollSpeed` vide).
- **Réduction de mouvement** : présente et globalement respectée
  (`ReducedMotion.kt`, `Motion.tween/gestureSpring`, `FixedPageContent`
  `scrollToPage` vs `animateScrollToPage`). **Exception vérifiée** : le rebond
  du geste de tirage utilise `spring(...)` en dur dans `PagedChapterContent`
  et `ReaderScreen` (sans passer par `Motion.gestureSpring`) — le drapeau
  `reduceMotion` n'y est pas appliqué.

### Recommandation

1. **Auto-scroll visuel** (mode « lecture mains libres ») : défilement continu
   à vitesse réglable, **en respectant `reduceMotion`** et en s'arrêtant sur
   interaction. C'est le `do_pdf_smooth_autoscroll` de Moon+, transféré au
   mode SCROLL.
2. **Styles de transition de page** : ajouter 2-3 styles discrets (fondu,
   glissement) en option, **dégradés vers le snap par défaut quand
   `reduceMotion`**.
3. **Correctif accessibilité** : faire passer les `spring(...)` en dur par
   `Motion.gestureSpring` (conformité « Accessible from Day One »).

### Effort / impact

| | |
|---|---|
| Effort | Faible (auto-scroll + correctif spring) / Moyen (styles) |
| Impact | Moyen (confort de lecture) |
| Risque | Faible |

---

## 8. Levier 7 — Câbler OpenDyslexic (correctif accessibilité)

### État vérifié

- La police **est embarquée** : `core/designsystem/.../res/font/opendyslexic_regular.otf`,
  chargée dans `Type.kt` (`OpenDyslexicFamily`).
- Le préréglage d'accessibilité **force** `OPEN_DYSLEXIC`
  (`ApplyAccessibilityPresetUseCase` → `reduceMotion = true` + OpenDyslexic).
- **MAIS** `ThemeColors.toComposeFontFamily` mappe
  `OPEN_DYSLEXIC → ComposeFontFamily.SansSerif` (commentaire : « sans-serif en
  repli tant qu'aucune police embarquée n'est fournie »). Résultat : le
  préréglage OpenDyslexic **rend du SansSerif**, pas de l'OpenDyslexic.

C'est un écart entre l'intention (accessibilité) et le rendu réel, signalé
mais non corrigé. Le correctif est trivial et aligne le code sur le principe
« Accessible from Day One ».

### Recommandation

Mapper `OPEN_DYSLEXIC → OpenDyslexicFamily` (la police est déjà embarquée),
avec un test de non-régression sur `effectiveFontFamily` + `toComposeFontFamily`.

### Effort / impact

| | |
|---|---|
| Effort | Très faible |
| Impact | Élevé (accessibilité réelle, pas simulée) |
| Risque | Nul |

---

## 9. Levier 8 — Polices françaises et finitions sync (petites finitions)

### État vérifié

- **Polices embarquées** : 3 seulement (`literata_variable.ttf`,
  `work_sans_variable.ttf`, `opendyslexic_regular.otf`), toutes OFL, **aucune
  police française dédiée** à empattements pour la lecture longue (Literata est
  utilisée comme « accent narratif » du chrome, pas comme police de lecture).
- **Sync** : déjà le bon modèle — `BackupPayload` = bookmarks, annotations,
  règles de prononciation, thèmes, `readingStates`, `readingSessions`
  (`BackupManager` : « jamais les livres eux-mêmes »). Lacunes vérifiées :
  - `readingSessions` sont **uploadées mais jamais fusionnées**
    (`SyncNowManager.mergeRemoteSnapshots`) ;
  - les conflits de position (`PositionConflict`) vont en file
    `PendingConflictEntity` et ne sont **jamais tranchés automatiquement**.

### Recommandation

1. **Police de lecture française** : embarquer 1-2 polices à empattements de
   qualité (ex. « Source Serif 4 », « Spectral », « EB Garamond », « Luciole »
   pour l'accessibilité) et les exposer dans l'enum `FontFamily` du domaine.
2. **Sync** : compléter la fusion des `readingSessions` (agrégation
   idempotente) et améliorer l'UX de résolution des conflits de position
   (choix explicite dans `SyncConflictBottomSheet`).

### Effort / impact

| | |
|---|---|
| Effort | Faible (polices) / Moyen (fusion sessions) |
| Impact | Moyen (confort de lecture, sync complète) |
| Risque | Faible |

---

## 10. Ce qu'il ne faut PAS copier de Moon+

| Anti-pattern Moon+ | Pourquoi l'éviter |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` + copie dans `/sdcard/Books/` | K5 — le SAF d'InkTone est strictement supérieur |
| Rendu CSS approximatif (`HtmlToSpannedConverter`) | Readium donne la fidélité CSS complète |
| Duplication des fichiers à l'import | Modèle URI + SAF d'InkTone évite le gaspillage |
| Double pipeline CPU/OpenGL pour le PDF | Surdimensionné pour InkTone (PDFium CPU + transform GPU suffit) |
| Exécution JavaScript embarqué (Duktape) | Surface d'attaque, hors besoin |

---

## 11. Priorisation

| # | Levier | Impact | Effort | Ordre suggéré |
|---|---|---|---|---|
| 7 | Câbler OpenDyslexic | Élevé | Très faible | **1** (quick win accessibilité) |
| 4 | Uniformiser le découpage PDF/TXT | Moyen | Faible | **2** (corrige un écart doc/code) |
| 2 | Locale `fr` de la césure | Moyen | Faible | **3** (typographie FR) |
| 6 | Auto-scroll + correctif `reduceMotion` (springs) | Moyen | Faible | **4** (fluidité + accessibilité) |
| 1 | Cache persistant découpage + timestamps TTS | Élevé | Moyen | **5** (gros levier, plus lourd) |
| 3 | Pré-analyse persistée chapitres/phrases | Élevé | Moyen-élevé | **6** (complémentaire du 1) |
| 5 | Pré-rendu + cache disque PDF | Moyen | Faible-moyen | **7** |
| 8 | Polices FR + fusion sessions sync | Moyen | Faible-moyen | **8** |

---

## 12. Annexe — preuves (chemins + citations)

### Découpage / timestamps TTS (levier 1, 4)
- `domain/service/FrenchSentenceSplitter.kt` — `object`, `BreakIterator.getSentenceInstance(Locale.FRENCH)`.
- `infrastructure/parser/.../JsoupChapterParser.kt` — `tokenizeSentences` → `FrenchSentenceSplitter.split(fullText)` (unique call site).
- `infrastructure/parser/.../PdfTextExtraction.kt:24` — `PDF_SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+""")`.
- `infrastructure/parser/.../TxtPublicationParser.kt:39` — `sentenceBoundary = Regex("""(?<=[.!?])\s+""")`.
- `domain/service/TtsEngine.kt` — `AudioSegment.wordTimestamps` ; `SherpaOnnxTtsEngine` → `CtcForcedAligner.align(...)` ; `AndroidNativeTtsEngine` → `onRangeStart` ; `EdgeTtsEngine` → `mapEdgeWordBoundaries`.
- `infrastructure/parser/.../EpubChapterParser.kt` — `LruCache` mémoire 5 Mo + `invalidate(publicationId)`.
- Entités Room (14) — aucune TTS/audio/timestamps.

### Césure (levier 2)
- `feature/reader/.../pagination/ChapterPaginationState.kt` — `TextStyle(..., hyphens = if (justified) Hyphens.Auto else Hyphens.None, lineBreak = if (justified) LineBreak.Paragraph else LineBreak.Unspecified)`.
- `feature/reader/.../ReaderScreen.kt` — même triplet (mode SCROLL).
- Grep `Hyphenator|SoftHyphen|hyphenation|TeX` → zéro résultat.

### Pré-analyse persistée (levier 3)
- `domain/usecase/ImportPublicationUseCase.kt` — hash → dédup → `parse` → `persistReadPermission` → insert → FTS → `invalidate`.
- `infrastructure/parser/.../ReadiumPublicationParser.kt` — `parseLazy` (métadonnées + TOC + coquilles).
- Aucune entité `ChapterEntity` / `SentenceEntity` (vérifié dans `database/entity/`).

### Rendu PDF (levier 5)
- `domain/service/FixedPageRenderer.kt` — interface, une seule implémentation.
- `infrastructure/parser/.../PdfPageRendererImpl.kt` — PDFium, bitmap ARGB_8888.
- `feature/reader/.../FixedPageContent.kt` — `BitmapCache(maxSize = 5)`, zoom `graphicsLayer`.
- Grep `OpenGL|SurfaceTexture|TextureView|GLSurfaceView|EGL|android.opengl` → zéro occurrence réelle.

### Animations / auto-scroll (levier 6)
- `feature/reader/.../transition/` — uniquement transition de chapitre (tirage).
- `feature/reader/.../PagedChapterContent.kt` — `HorizontalPager` (snap par défaut).
- `feature/reader/.../ReaderScreen.kt` — auto-scroll TTS uniquement (`animateScrollToItem`).
- `core/designsystem/ReducedMotion.kt` + `Motion.kt` ; springs en dur dans `PagedChapterContent` / `ReaderScreen`.

### Polices / sync (leviers 7, 8)
- `core/designsystem/.../res/font/opendyslexic_regular.otf` + `Type.kt` (`OpenDyslexicFamily`).
- `feature/reader/.../ThemeColors.kt` — `DomainFontFamily.OPEN_DYSLEXIC -> ComposeFontFamily.SansSerif`.
- `data/backup/BackupModels.kt` — `BackupPayload` (métadonnées seules).
- `data/sync/SyncNowManager.kt` — upload `snapshot-*.json`, `device-fleet.json`, `activity-log.json` ; `readingSessions` non fusionnées.

---

## 13. Sélection de texte, surlignage et notes

### 13.1 Constat : InkTone est déjà supérieur sur les fondations

| Aspect | InkTone (vérifié) | Moon+ (vérifié) |
|---|---|---|
| Adressage | `Locator` (`resourceHref`, `chapterIndex`, `charOffset`) | `chapter + splitIndex + position + length` (fragile au reflow) |
| Modèle d'annotation | `Annotation` unique + `content` (note) + `excerpt` | 1 ligne `notes` (bookmark/note/highlight/underline/strike) |
| Sélection | `BasicTextField` lecture seule + sélection native Compose + `TextToolbar` custom (`LocalTextToolbar`) | sélection native Android sur `MyLayout` |
| Canaux visuels | 3 séparés : annotation (`SpanStyle`), TTS (`WordHighlightColor`), sélection (`SelectionHighlightColor`) | mélangés dans `MRTextView$MRSpan` |
| Types d'annotations | **surlignage + note** uniquement | surlignage + **souligné + barré** + note + signet |

À ne pas copier : l'adressage fragile de Moon+ et le mélange des canaux
visuels. InkTone est plus propre — ne pas régresser.

### 13.2 Écarts à combler (prouvés dans le code)

1. **Souligné / barré absents** — `Annotation.kt` n'a pas de champ de type ;
   `LibraryItemType { BOOKMARK, HIGHLIGHT, NOTE }` ne comporte pas
   underline/strike. Les seuls underline/strike du code sont des **styles
   sémantiques EPUB** (`SpanStyles.INSERTED/DELETED` →
   `BookBlockStyleMapper.buildTextDecoration`), pas des annotations
   utilisateur. Moon+ prouve que les lecteurs attendent ces types.
   → Ajouter un `AnnotationKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH }`
   (migration Room + rendu `TextDecoration`), sans casser le `Locator`.

2. **`Bookmark.note` jamais rempli** — le champ existe (`Bookmark.kt`) mais
   `ReaderViewModel.toggleBookmarkAtCurrentPosition` ne pose ni `note` ni
   `title`. Moon+ attache une note au signet.
   → Proposer une saisie de note optionnelle à la création du signet.

3. **`paragraphIndex` jamais renseigné** —
   `AnnotationSelectionHandler.resolveCharRange` retourne un `Locator` avec
   `paragraphIndex = null`. `charOffset` (ancre chapitre-absolue) reste
   stable, mais remplir `paragraphIndex` renforce la robustesse et
   l'exploitabilité (recherche, FTS, reprise partielle).
   → Renseigner `paragraphIndex` au moment de la résolution de la sélection.

4. **Palette de couleurs fixe** — 5 couleurs `AnnotationColor`, aucune
   palette persistée ni réordonnée par usage. Moon+ mémorise les couleurs
   utilisées (`SELECT distinct highlightColor`, `updateGlobalHighlightColors`).
   → Persister les couleurs récemment utilisées et les proposer en tête du
   sélecteur (faible coût).

5. **`BookmarkPanel` en lecture seule** — onglets Notes / Surlignages /
   Marque-pages en affichage pur, pas d'édition/suppression. Moon+ permet
   l'édition.
   → Ajouter édition de note et suppression depuis le panneau.

6. **Actions de sélection limitées** — `SelectionActionPopup` propose
   Copier · Surligner · Note (pas de Partager ni de Chercher). Moon+
   (sélection native) offre le partage.
   → Ajouter « Partager » (intent), éventuellement « Chercher » (pointe
   vers l'index FTS existant).

### 13.3 Priorisation

| # | Écart | Impact | Effort | Ordre |
|---|---|---|---|---|
| 2 | `Bookmark.note` jamais rempli | Moyen | Faible | 1 |
| 3 | `paragraphIndex` renseigné | Moyen (robustesse) | Faible | 2 |
| 4 | Palette de couleurs persistée | Moyen (UX) | Faible | 3 |
| 6 | Partager depuis la sélection | Faible-moyen | Faible | 4 |
| 1 | Souligné / barré | Élevé (parité fonctionnelle) | Moyen | 5 |
| 5 | Édition depuis `BookmarkPanel` | Moyen | Moyen | 6 |

### 13.4 Preuves (chemins + citations)

- `domain/model/Annotation.kt` — `enum AnnotationColor { YELLOW, GREEN, BLUE, PINK, ORANGE }` ; `data class Annotation(..., content: String? = null, excerpt: String? = null, isPinned: Boolean = false, ...)`.
- `domain/model/Bookmark.kt` — `val note: String? = null` (jamais rempli).
- `domain/model/LibraryItem.kt` — `enum LibraryItemType { BOOKMARK, HIGHLIGHT, NOTE }`.
- `feature/reader/.../AnnotationSelectionHandler.kt` — `resolveCharRange(...)` retourne `Locator` sans `paragraphIndex`.
- `feature/reader/.../SelectionActionPopup.kt` — actions `Copier` / `Surligner` / `Note` uniquement.
- `feature/reader/.../BookmarkPanel.kt` — onglets `NOTES / HIGHLIGHTS / BOOKMARKS` en lecture seule.
- `feature/reader/.../PagedChapterContent.kt` + `rendering/BookBlockItem.kt` — `drawWithContent` : `WordHighlightColor` (TTS), `SelectionHighlightColor` (sélection), `SpanStyle(background=annotation.color)` (permanent).
- Moon+ : `create table notes (..., highlightColor, bookmark, note, original, underline, strikethrough, ...)` ; `SELECT distinct highlightColor FROM notes` ; `updateGlobalHighlightColors` ; `setPdfHighlightColor`.

### 13.5 L'action box de sélection (UI)

**État InkTone (vérifié, `SelectionActionPopup.kt`)** — déjà excellent :

- `PopupPositionProvider` alimenté par les vraies `LayoutCoordinates` de la
  sélection (centré, bascule au-dessus/en-dessous selon la place).
- 3 modes : `ACTIONS` (Copier · Surligner · Note) → `COLOR_PICKER` (couleur
  en second temps) → `NOTE_INPUT`.
- 2 bugs device corrigés et documentés : `dismissOnClickOutside = false`
  (sinon saisir une poignée détruisait la sélection) ; `focusable = true`
  seulement en mode note (sinon vol du focus fenêtre → masquage des poignées).

**Moon+ (vérifié)** : ActionMode natif Android
(`getCustomSelectionActionModeCallback` / `setCustomSelectionActionModeCallback`)
→ barre flottante système (Copier · Tout sélectionner · Partager · Recherche
web) ; plus `PrefShareText` (`com.flyersoft.moonreaderp.PrefShareText` + 6
classes internes), un écran de préférences dédié au partage du texte
sélectionné.

**À en tirer (action box uniquement)** :

| Idée | Effort |
|---|---|
| Ajouter « Partager » (`ACTION_SEND`, texte + contexte livre) | Faible |
| Ajouter « Chercher » (pointe vers l'index FTS4 existant) | Faible-moyen |
| Partage avec contexte (titre + auteur + chapitre) | Faible |
| Overflow « … » pour les actions secondaires | Faible |

**À ne pas copier** : « Tout sélectionner » (inutile en lecture), l'ActionMode
natif (le `TextToolbar` custom + `Popup` Compose est plus contrôlable), et la
surcharge du menu PDF de Moon+ (conserver « 3 primaires + couleur en second
temps »).
