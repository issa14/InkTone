# Lot 21 — Gains rapides perf/UX et finitions de sélection

**Base :** `main` (dernier lot mergé : `LOT_20_RESTAURATION_SHERPA_UPMC.md`).
Source : `docs/incoming/STRATEGIE_PERF_UX_INSPIREE_MOONREADER.md` (analyse
comparative Moon+ Reader ↔ code réel InkTone, 2026-08-26). Ce Lot est le
premier des deux volets d'exécution issus de ce document ; le second,
`LOT_22_PERSISTANCE_ET_PARITE_ANNOTATIONS.md`, traite les chantiers lourds
(caches persistants, parité des types d'annotation, complétion sync).

**Objectif :** encaisser tous les leviers à effort faible du document — dont
deux **écarts entre l'intention et le code réel** (OpenDyslexic non câblé,
découpage de phrases incohérent entre EPUB et PDF/TXT) — sans toucher aux
caches persistants ni au modèle d'annotation.

**Critère de sélection des tâches :** surface de code limitée, aucun
chantier d'architecture, au plus **une** migration Room additive.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

Aucune tâche n'est déclarée close sans citer le fichier, la ligne ou le test
qui le prouve (CLAUDE.md — « le code fait foi »).

---

## Constat vérifié (base du Lot)

Repris du document source, re-vérifié dans le code au moment de la rédaction
de ce Lot :

1. **OpenDyslexic embarquée mais jamais rendue.** La police existe
   (`core/designsystem/.../res/font/opendyslexic_regular.otf`, chargée en
   `OpenDyslexicFamily` dans `core/designsystem/.../Type.kt:17`) et
   `ApplyAccessibilityPresetUseCase` force `OPEN_DYSLEXIC`, mais **deux**
   sites de mapping la ramènent à SansSerif :
   `feature/reader/.../ThemeColors.kt:67` et
   `feature/settings/.../ThemeRenderUtils.kt:18`
   (`DomainFontFamily.OPEN_DYSLEXIC -> ComposeFontFamily.SansSerif`). Le
   préréglage d'accessibilité rend donc du SansSerif.
2. **Découpage de phrases incohérent selon le format.**
   `FrenchSentenceSplitter` (`domain/service/FrenchSentenceSplitter.kt`,
   `BreakIterator` FR) n'a qu'un call site :
   `infrastructure/parser/.../JsoupChapterParser.kt` (EPUB). PDF et TXT
   utilisent une regex naïve — `PdfTextExtraction.kt:24` et
   `TxtPublicationParser.kt:39`, toutes deux `Regex("""(?<=[.!?])\s+""")` —
   qui casse sur les abréviations françaises (`M.`, `Dr.`, `etc.`) et
   dégrade le TTS et le surlignage mot-à-mot sur ces formats. Le spike
   `docs/spikes/sentence-tokenizer-comparison.md` annonce déjà
   `FrenchSentenceSplitter` comme source unique pour les trois formats :
   écart doc ↔ code.
3. **Césure sans locale explicite.** `Hyphens.Auto` est posé dans
   `feature/reader/.../pagination/ChapterPaginationState.kt` et
   `ReaderScreen.kt`, uniquement quand `textJustified == true`. Aucune
   locale n'est portée par le `TextStyle` : l'hyphenation Android se
   rabat sur la locale système, pas nécessairement `fr`. Aucune césure
   maison n'existe (grep `Hyphenator|SoftHyphen|hyphenation|TeX` → zéro).
4. **`reduceMotion` contourné par des springs en dur.** `ReducedMotion.kt`
   et `Motion.kt` sont respectés partout sauf le rebond du geste de tirage :
   `spring(...)` écrit en dur dans `PagedChapterContent.kt` et
   `ReaderScreen.kt`, sans passer par `Motion.gestureSpring`.
5. **`Bookmark.note` jamais rempli.** Le champ existe
   (`domain/model/Bookmark.kt`), `ReaderViewModel.toggleBookmarkAtCurrentPosition`
   ne pose ni `note` ni `title`.
6. **`paragraphIndex` jamais renseigné.**
   `feature/reader/.../AnnotationSelectionHandler.kt` → `resolveCharRange(...)`
   retourne un `Locator` avec `paragraphIndex = null`. `charOffset` reste
   l'ancre stable ; `paragraphIndex` renforce la robustesse sans créer de
   second système d'adressage (le `Locator` reste le seul — CLAUDE.md).
7. **Palette de couleurs figée.** 5 valeurs `AnnotationColor`
   (`domain/model/Annotation.kt`), aucun ordre d'usage mémorisé.
8. **Actions de sélection limitées.** `SelectionActionPopup.kt` : Copier ·
   Surligner · Note. Pas de Partager. Le reste de ce composant est déjà
   bon (positionnement réel, 3 modes, 2 bugs device corrigés et
   documentés) : **ne rien y régresser**.
9. **Rendu PDF sans repli.** `PdfPageRendererImpl` (PDFium, bitmap CPU) ;
   `feature/reader/.../FixedPageContent.kt` : `BitmapCache(maxSize = 5)`.
   Aucun repli si `renderPage` échoue (page corrompue, OOM).
10. **Trois polices embarquées seulement** (`literata_variable.ttf`,
    `work_sans_variable.ttf`, `opendyslexic_regular.otf`), aucune police de
    lecture à empattements dédiée au français.

---

## Décisions arrêtées

Tranchées avant exécution (2026-08-26), pas à rouvrir en cours de lot sans
raison nouvelle.

1. **La césure reste solidaire de la justification.** Le modèle
   `UserPreferences` ne permet pas « césure sans justification », et le
   commentaire de `UserPreferences.textJustified` documente le raisonnement
   inverse (« justifier sans césurer creuse des "rivières" blanches dans un
   texte français »). Ce Lot corrige **uniquement la locale**. Le
   découplage, comme le moteur de césure à motifs TeX, reste conditionné à
   une mesure sur device — jamais décidé par principe.
2. **Une seule police de lecture ajoutée : Source Serif 4** (OFL,
   variable, diacritiques françaises complètes, s'accorde avec Literata et
   Work Sans déjà embarquées). Une seule, parce que les valeurs de l'enum
   `FontFamily` du domaine sont **persistées en préférence** : une valeur
   ajoutée ne se retire plus, contrairement à un fichier de police.
   Écartées : EB Garamond (hauteur d'x trop faible sur téléphone), Spectral
   (second choix acceptable), **Luciole** — intéressante pour la basse
   vision, mais sa licence n'est **pas OFL** et n'a pas été vérifiée ; le
   dépôt ne distribue que de l'OFL à ce jour. Ne pas l'embarquer sans
   vérification de licence explicite.
3. **L'auto-scroll reste dans ce Lot, en dernier palier.** Il ajoute une
   préférence persistée, donc une migration Room additive — la seule du
   Lot. Le placer en fin de séquence permet de le sortir du périmètre sans
   rien casser si le Lot doit être livré plus tôt. Version retenue : une
   vitesse réglable, arrêt à la première interaction, désactivé quand
   `reduceMotion` est actif. Pas de courbe d'accélération, pas de profils.

## Tâches

### Palier A — Écarts intention ↔ code (aucun choix produit à faire)

1. **Câbler OpenDyslexic.** Mapper `OPEN_DYSLEXIC -> OpenDyslexicFamily`
   dans les **deux** sites (`ThemeColors.kt:67` et `ThemeRenderUtils.kt:18`)
   — un seul site corrigé laisserait la galerie de thèmes mentir sur le
   rendu. Test de non-régression sur `effectiveFontFamily` +
   `toComposeFontFamily` (`ThemeColorTest.kt` existe déjà).
   Commit : `Cable la police OpenDyslexic sur le prereglage accessibilite`.
2. **Unifier le découpage de phrases.** Remplacer les deux regex naïves
   (`PdfTextExtraction.kt:24`, `TxtPublicationParser.kt:39`) par
   `FrenchSentenceSplitter`. Attention aux **offsets** : PDF et TXT
   s'appuient sur les positions de caractères pour le surlignage et
   l'index FTS — vérifier que la substitution conserve des offsets
   absolus cohérents. Tests obligatoires : abréviations françaises
   (`M.`, `Mme`, `Dr.`, `etc.`, `p. ex.`), ellipses, guillemets français.
   Commit : `Unifie le decoupage de phrases sur PDF et TXT`.
3. **Locale de césure.** Porter une locale `fr` (ou la locale du contenu
   si elle est disponible dans les métadonnées de la publication) sur le
   `TextStyle` des deux sites de pagination
   (`ChapterPaginationState.kt`, `ReaderScreen.kt`). Vérification sur
   device : texte justifié français, coupures aux bons endroits.
   Commit : `Porte la locale francaise sur la cesure du texte justifie`.
4. **`reduceMotion` sur les springs de geste.** Faire passer les
   `spring(...)` en dur de `PagedChapterContent.kt` et `ReaderScreen.kt`
   par `Motion.gestureSpring`, qui honore `reduceMotion`. Test : avec
   `reduceMotion = true`, aucun rebond animé.
   Commit : `Applique reduceMotion au rebond du geste de tirage`.

### Palier B — Finitions de sélection et d'annotation (sans migration)

5. **Note de signet.** Proposer une saisie de note optionnelle à la
   création d'un signet ; remplir `Bookmark.note`/`title` depuis
   `ReaderViewModel.toggleBookmarkAtCurrentPosition`. Ne pas transformer
   le geste rapide en dialogue obligatoire : la note reste optionnelle.
   Commit : `Permet d'attacher une note a un signet`.
6. **`paragraphIndex` renseigné.** Le calculer dans
   `AnnotationSelectionHandler.resolveCharRange`. `charOffset` reste
   l'ancre de vérité ; `paragraphIndex` est un renfort, pas un second
   système d'adressage. Test sur une sélection à cheval sur deux blocs.
   Commit : `Renseigne paragraphIndex a la resolution d'une selection`.
7. **Partager depuis la sélection.** Ajouter « Partager » à
   `SelectionActionPopup` (`ACTION_SEND`, texte sélectionné + contexte
   titre/auteur/chapitre). Si le mode `ACTIONS` devient chargé, passer les
   actions secondaires derrière un overflow « … » plutôt que d'élargir la
   barre. Ne pas ajouter « Tout sélectionner ». Ne pas toucher à
   `dismissOnClickOutside = false` ni à la gestion conditionnelle de
   `focusable` — deux corrections de bugs device documentées.
   Commit : `Ajoute le partage du texte selectionne avec son contexte`.

### Palier C — Robustesse et confort (effort faible, périmètre isolé)

8. **Repli gracieux du rendu PDF.** Si `renderPage` échoue (page
   corrompue, OOM), afficher un placeholder explicite et journaliser,
   au lieu de laisser remonter l'échec. Test : page volontairement
   invalide.
   Commit : `Affiche un repli lisible quand une page PDF ne se rend pas`.
9. **Auto-scroll visuel** (décision 3 — dernier palier, sortable du
   périmètre) — défilement continu à vitesse réglable en mode SCROLL,
   arrêt à la première interaction, **désactivé quand `reduceMotion`**. Nouvelle préférence dans
   `UserPreferencesEntity` → migration Room additive + son test
   `MigrationTestHelper` **dans le même commit** (règle K4).
   Commit : `Ajoute le defilement automatique reglable en mode scroll`.
10. **Police de lecture française** (décision 2) — embarquer **Source
    Serif 4** (OFL), l'exposer dans l'enum `FontFamily` du domaine et dans les deux sites de mapping Compose, mettre à jour
    `THIRD_PARTY_NOTICES` et l'écran À propos.
    Commit : `Embarque une police de lecture francaise a empattements`.

---

## Ce qu'on ne fait pas dans ce Lot

- Cache persistant du découpage et des timestamps TTS, pré-analyse
  persistée des chapitres, cache disque des pages PDF → **Lot 22**.
- Souligné / barré, édition depuis `BookmarkPanel`, palette de couleurs
  récemment utilisées → **Lot 22** (migration Room + UI).
- Fusion des `readingSessions`, arbitrage des `PositionConflict` → **Lot 22**.
- Moteur de césure à motifs TeX : **hors périmètre tant que la locale
  corrigée n'a pas été mesurée insuffisante** (le document source le
  conditionne explicitement à un benchmark).
- Tout anti-pattern listé au §10 du document source (MANAGE_EXTERNAL_STORAGE,
  rendu CSS maison, duplication des fichiers, pipeline OpenGL PDF, JS
  embarqué).

---

## Critères de sortie du Lot

- [ ] Le préréglage d'accessibilité affiche **réellement** OpenDyslexic,
      dans le Lecteur **et** dans la galerie de thèmes, vérifié sur device.
- [ ] PDF et TXT passent par `FrenchSentenceSplitter` ; tests
      d'abréviations françaises verts ; surlignage mot-à-mot vérifié sur
      un PDF réel et un TXT réel sur device.
- [ ] Césure française correcte sur un texte justifié, vérifiée sur device.
- [ ] `reduceMotion = true` supprime le rebond du geste de tirage.
- [ ] Un signet peut porter une note ; la note est visible dans
      `BookmarkPanel`.
- [ ] `paragraphIndex` non nul sur les annotations créées après ce Lot ;
      les annotations existantes restent résolvables (`charOffset`).
- [ ] « Partager » fonctionne depuis la sélection, sans régression des
      deux bugs device déjà corrigés dans `SelectionActionPopup`.
- [ ] Une page PDF illisible affiche un repli, ne fait pas tomber l'écran.
- [ ] Auto-scroll réglable, respectant `reduceMotion`, avec migration Room
      testée dans le même commit (ou explicitement sorti du périmètre, et
      dit comme tel).
- [ ] Source Serif 4 sélectionnable et rendue dans le Lecteur comme dans la
      galerie de thèmes ; `THIRD_PARTY_NOTICES` et l'écran À propos à jour.
- [ ] `./gradlew build` vert (inclut `checkArchitectureRules`,
      `check-no-emoji.sh`, `check-no-manage-external-storage.sh`).
