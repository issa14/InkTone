# Lot 12 — Support PDF (affichage seul, ADR-017)

**Base :** `main` à `2c7af4b`. Références : `docs/execution/Plan_integration_PDF.md`
(recherche technique), `ADR-017` (périmètre PDF différé et borné, Blueprint),
`domain/model/Publication.kt` (commentaire `pageCount`), `domain/valueobject/Locator.kt`
(commentaire « jamais de numéro de page »), `infrastructure/parser/CompositePublicationParser.kt`
(point d'extension déjà annoncé : « Étendre cette liste pour PDF »).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu vérifier,
la clôture se fait sur appareil.

## Décisions actées

1. **Deux paliers d'exécution pour l'« affichage seul » d'ADR-017.** Palier 1 (ce plan) :
   fondation données — parser, domaine, persistance, import, couverture. Palier 2 (plan
   séparé, à écrire après validation du Palier 1) : rendu — `FixedPageContent`, zoom,
   thèmes, ouverture du sélecteur SAF. Le TTS sur PDF (second volet d'ADR-017, extraction
   d'ordre de lecture, surlignage synchronisé) est un **lot distinct et ultérieur**, non
   planifié ici.
2. **Moteur retenu : PDFium**, via le binding Kotlin `io.legere:pdfiumandroid:2.0.3`
   (Maven Central, Apache-2.0 ; PDFium lui-même est BSD-3-Clause, projet Google/Chromium).
   Décision révisée en cours de recherche : MuPDF (proposition initiale) est AGPL, et
   Artifex précise explicitement que l'usage AGPL gratuit exige que **tout le code source
   de l'app** soit publié sous licence compatible et **exclut nommément Crashlytics** —
   or `infrastructure/crashreporting` utilise Firebase Crashlytics (K10, CLAUDE.md), déjà
   en place. PDFium (BSD-3) n'a pas cette contrainte. Contrepartie assumée : pas de reflow
   texte natif — sans objet pour un palier « affichage seul, sans reflow ».
   Sources : [MuPDF — Using with Android](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html),
   [ArtifexSoftware/mupdf-android-fitz](https://github.com/ArtifexSoftware/mupdf-android-fitz),
   [io.legere/pdfiumandroid — Maven Central](https://central.sonatype.com/artifact/io.legere/pdfiumandroid),
   [johngray1965/PdfiumAndroidKt](https://github.com/johngray1965/PdfiumAndroidKt).
3. **Deux points non vérifiables depuis la documentation publique** — traités par la
   tâche 12.1 (spike dédiée), pas supposés acquis : (a) les ABI réellement embarquées dans
   l'AAR `pdfiumandroid` (à inspecter directement, alignement `arm64-v8a` obligatoire, voir
   décision 8) ; (b) le comportement exact de la lib face à un PDF protégé par mot de passe
   (code d'erreur exposé côté Kotlin ou exception à intercepter — à établir par un test
   direct avant d'écrire `PdfPublicationParser`, pas par supposition).
4. **`DocumentModel` construit honnêtement, jamais un objet vide de façade.**
   `PdfPublicationParser` produit un `Chapter` par page (`index = pageIndex`), dont les
   `paragraphs` viennent du texte extrait via `PdfTextPage`/`FPDFText_*` — liste vide si la
   page est une image scannée sans texte. Directive Issa : cette extraction texte est
   **indispensable** dès ce palier, pas pour lire à voix haute (TTS reste hors périmètre),
   mais pour que le `Locator` (page = `chapterIndex`, `charOffset` dans le texte de la
   page) soit déjà la structure que Sherpa-ONNX consommera plus tard, sans rétrofit du
   modèle d'adressage.
   Bénéfice collatéral vérifié dans le code réel : `chapterCount` (= nombre de
   `Chapter`, donc nombre de pages pour un PDF) alimente déjà sans changement la requête
   IN_PROGRESS/READ de `PublicationDao` (`rs.chapterIndex >= p.chapterCount - 1`) — un PDF
   « lu » se détecte automatiquement par la logique existante, aucune requête SQL à
   dupliquer.
5. **`resourceHref` du Locator = href réel de la page** (`"page-{index}"`), pas une
   constante document-wide — cohérent avec `chapter.href` déjà utilisé partout côté EPUB
   (`ReaderViewModel.kt`), évite un cas spécial dans tout code consommant `Locator`.
6. **`Publication.pageCount: Int?` reste un champ dédié**, distinct de `chapterCount`,
   conformément au commentaire déjà présent dans `Publication.kt` (« sera introduit avec
   une définition précise… jamais comme champ générique ambigu »). Les deux valeurs
   coïncident numériquement pour un PDF sous ce modèle (page = chapitre), mais portent des
   intentions différentes : `chapterCount` sert la structure de lecture/recherche déjà
   générique, `pageCount` sert l'affichage paginé (« Page 12/240 », branché au Palier 2
   dans `BookActionsSheet.kt`, aujourd'hui figé sur le libellé « Chapitres »).
7. **`Locator.pageOffsetY: Float?` ajouté dès ce palier**, bien que consommé seulement au
   Palier 2 (défilement visuel dans `FixedPageContent`) — même logique que l'ajout
   anticipé de l'identité d'appareil au Lot 11 (tâche 11.2), pour ne pas rouvrir le value
   object deux fois. Convention : `charOffset = 0` pour une page image pure sans texte
   extrait (jamais une valeur indéfinie).
8. **NDK : correction vs la recherche initiale.** Le projet ne cible que `arm64-v8a`
   (vérifié dans `infrastructure/tts/build.gradle.kts` et `app/build.gradle.kts` — pas
   `armeabi-v7a`/`x86_64` comme la recherche le supposait). Le module `infrastructure/parser`
   doit s'aligner sur ce même filtre unique.
9. **Import non exposé à l'utilisateur ce palier.** `ImportPickerButton.kt` (MIME
   `application/epub+zip` + `text/plain`) reste **inchangé**. Le pipeline PDF est livré et
   testé de bout en bout, mais **inatteignable** tant que le Palier 2 n'ouvre pas le filtre
   et l'écran de lecture — même patron que la carte WebDAV grisée du Lot 11 : ne jamais
   exposer un chemin fonctionnel à moitié (ici, une bibliothèque affichant « Chapitres :
   240 » sans écran pour l'ouvrir serait le même défaut).
10. **JS et AcroForms désactivés nativement** à l'ouverture — surface d'attaque et RAM,
    repris tel quel de la recherche initiale, indépendant du choix de moteur.
11. **`ImportPublicationUseCase.formatOf` et `CompositePublicationParser` dupliquent déjà la
    même heuristique par extension** (le commentaire du premier le reconnaît explicitement :
    « coherence du choix de format entre… »). Ce lot ajoute une troisième branche `.pdf` aux
    deux endroits plutôt que d'unifier — écart déclaré, dette pré-existante étendue et non
    corrigée, hors périmètre de ce lot.
12. **`Locator.compareTo` ignore `pageOffsetY`** (il ne compare que `chapterIndex` et
    `charOffset`, tous deux identiques pour deux positions sur la même page PDF à des
    défilements différents). Deux marque-pages sur la même page seraient indiscernables
    pour un tri strict — écart déclaré, sans conséquence tant que les annotations PDF
    restent hors périmètre (décision actée 16 du Palier 2), à revisiter si ce lot futur
    trie des positions intra-page.

## Paliers de ce lot

| Palier | Contenu | Statut |
|---|---|---|
| **1** (ce plan) | Fondation données : binding PDFium, parser, domaine, migration Room, import, couverture | Détaillé ci-dessous |
| **2** | Rendu UI : `FixedPageContent`, zoom par tuiles, thèmes, ouverture du sélecteur SAF, libellé « Pages » | Détaillé ci-dessous |

**Pousser après le palier 1**, ne pas enchaîner sur le palier 2 sans validation device
(leçon du lot 3d, rappelée à chaque lot depuis).

---

# PALIER 1 — Fondation données

## Tâche 12.1 — Vérification technique du binding PDFium

Spike bornée, préalable à toute écriture de `PdfPublicationParser` — les deux inconnues de
la décision actée 3 ne se lèvent pas par lecture de documentation, mais par test direct.

- Créer dès cette tâche (pas en 12.6, trop tard pour ce spike) deux fixtures minimales sous
  `infrastructure/parser/src/androidTest/assets/` : `fixture-valid.pdf` et
  `fixture-password.pdf`. Les trois autres fixtures de la tâche 12.6 peuvent attendre.
- Intégrer `io.legere:pdfiumandroid:2.0.3`, inspecter l'AAR récupéré (`unzip -l`) pour
  lister les ABI natives réellement embarquées (`jni/<abi>/*.so`).
- Restreindre `ndk.abiFilters` du module à `arm64-v8a` uniquement (décision actée 8),
  qu'il y ait ou non d'autres ABI dans l'AAR upstream.
- Ouvrir `fixture-password.pdf` avec l'API de la lib et noter précisément le mécanisme
  d'échec exposé côté Kotlin (exception typée, code retour, valeur nulle) — ce mécanisme
  conditionne l'écriture du `when`/`try` de la tâche 12.2.
- Consigner les deux résultats dans ce document (mise à jour de la décision actée 3) avant
  de poursuivre.

`Verifie le binding PDFium (ABI embarquees, comportement sur PDF protege)`

---

## Tâche 12.2 — Parser PDF et détection de format

- `infrastructure/parser` : nouveau `PdfPublicationParser : PublicationParser`, même
  contrat que `ReadiumPublicationParser`/`TxtPublicationParser` :
  - Détection *Magic Bytes* (`%PDF-` sur les premiers octets) via `FileStorageService`
    (SAF, jamais `java.io.File` — bug déjà corrigé lot 2a pour TXT, ne pas le
    réintroduire) → `ParseResult.UnsupportedFormat` sinon.
  - Ouverture via `FileDescriptor`. Tous les appels natifs isolés dans un
    `safeNativeCall` qui intercepte pointeur nul/erreur native et la convertit en
    `ParseResult.Corrupted` — jamais un `SIGSEGV` qui tue le process.
  - Détection mot de passe selon le mécanisme établi en 12.1 → `ParseResult.DrmProtected`.
    Aucune tentative de déchiffrement (parité avec `DrmDetectionTest` EPUB).
  - `pageCount == 0` ou ouverture impossible → `ParseResult.Corrupted`.
  - Dispatcher dédié à un seul thread (ou `Mutex`) pour sérialiser les accès JNI — le
    contexte natif PDFium n'est pas thread-safe.
  - Désactivation des flags JS et AcroForms si l'API du binding l'expose (décision
    actée 10) ; sinon, consigner l'absence de ce contrôle comme écart déclaré.
- `CompositePublicationParser` : ajouter la branche `.pdf` (le commentaire du fichier
  annonçait déjà ce point d'extension) — résolution par nom de fichier réel via
  `FileStorageService.getFileName`, jamais l'URI SAF opaque.
- `di/ParserModule.kt` : aucun changement du binding Hilt (`PublicationParser` reste lié à
  `CompositePublicationParser`) — seul son constructeur gagne une dépendance.

`Ajoute le parser PDF via PDFium avec detection de mot de passe`

---

## Tâche 12.3 — Construction du DocumentModel, métadonnées et couverture

- Un `Chapter` par page (`index = pageIndex`, `href = "page-{pageIndex}"`), `paragraphs`
  extraits du texte de la page via `PdfTextPage` — liste vide si page image pure (décision
  actée 4).
- `PublicationMetadata` : titre, auteur si présents dans les métadonnées du document ;
  table des matières native si l'API l'expose. Pas de dépendance à Readium. **Pas** de
  champ `pageCount` ajouté ici — voir tâche 12.4, dérivé du `DocumentModel`, jamais de la
  métadonnée.
- **Primitive de rendu bas niveau, à écrire une seule fois et réutiliser au Palier 2** :
  une fonction interne au module (page PDFium → tampon ARGB à une largeur cible) est le
  point commun entre l'extraction de couverture ci-dessous et `PdfPageRendererImpl` de la
  tâche 12.7. Ne pas dupliquer l'appel de rendu PDFium dans les deux tâches — extraire
  cette primitive ici, `PdfPageRendererImpl` l'enveloppera derrière le contrat `domain` au
  Palier 2 sans la réécrire.
- Extraction de couverture : rendu de la page 0 via cette primitive à une résolution basse
  (~300×400), compression WEBP, sauvegarde dans le stockage interne privé de l'app (jamais
  dans l'espace SAF de l'utilisateur), chemin renvoyé via `PublicationMetadata.coverUri`
  (champ déjà existant, aucun changement de contrat).

`Construit le DocumentModel PDF page par page et extrait la couverture`

---

## Tâche 12.4 — Extension du domaine

- `Publication.pageCount: Int?` (défaut `null`), invariant
  `require(pageCount == null || pageCount > 0)`.
- `Locator.pageOffsetY: Float?` (défaut `null`), invariant
  `require(pageOffsetY == null || pageOffsetY in 0f..1f)`.
- `ImportPublicationUseCase.formatOf` : ajouter la branche `.pdf` →
  `PublicationFormat.PDF` (aujourd'hui seul `.txt` est distingué, tout le reste tombe sur
  `EPUB` par défaut — le commentaire du fichier annonce explicitement ce point mort).
- `ImportPublicationUseCase.buildPublication` : peupler
  `pageCount = result.documentModel.chapters.size.takeIf { format == PublicationFormat.PDF }`
  — **jamais** depuis `PublicationMetadata`, qui n'a pas ce champ et n'en gagne pas un
  (bug identifié en relecture : la métadonnée n'a jamais porté cette information, la
  dériver du `DocumentModel` déjà construit est à la fois correct et plus simple).

`Etend Publication et Locator pour l adressage et le comptage PDF`

---

## Tâche 12.5 — Persistance Room

- `PublicationEntity` : colonne `pageCount: Int?`.
- `MIGRATION_24_25` : `ALTER TABLE publications ADD COLUMN pageCount INTEGER DEFAULT NULL`.
  `InkToneDatabase.version = 25`.
- `PublicationMapper` (`toEntity`/`toDomain`) : mapping du nouveau champ, dans les deux
  sens.
- Test `MigrationTestHelper` **dans le même commit** (K4, non négociable) : une ligne
  insérée en v24 sans `pageCount` migre vers v25 avec `pageCount NULL`, sans perte des
  autres colonnes.

`Ajoute la colonne pageCount et sa migration Room testee`

---

## Tâche 12.6 — Tests du palier 1

Fixtures à ajouter sous `infrastructure/parser/src/androidTest/assets/` (même convention
que `fixture-drm.epub`) : `fixture-valid.pdf` (texte vectoriel), `fixture-scanned.pdf`
(image pure, sans texte), `fixture-password.pdf`, `fixture-corrupted.pdf` (tronqué),
`fixture-large.pdf` (≥ 200 pages, pour la mesure de performance).

1. PDF vectoriel valide : `DocumentModel` avec N chapitres = N pages, texte non vide sur
   au moins une page.
2. PDF scanné : N chapitres, `paragraphs` vides sur chaque page — pas de `Corrupted`, pas
   de crash.
3. PDF protégé par mot de passe → `ParseResult.DrmProtected`, aucun déchiffrement tenté
   (miroir direct de `DrmDetectionTest`).
4. Fichier tronqué / `pageCount == 0` → `ParseResult.Corrupted`.
5. Extension `.pdf` usurpée (contenu non-PDF, magic bytes absents) →
   `ParseResult.UnsupportedFormat`, jamais un crash natif.
6. `CompositePublicationParser` route un `.pdf` vers `PdfPublicationParser` en résolvant
   le vrai nom de fichier, pas l'URI SAF.
7. `ImportPublicationUseCase` de bout en bout sur `fixture-valid.pdf` : `Publication`
   insérée avec `format = PDF`, `pageCount` renseigné, `chapterCount == pageCount`,
   `coverUri` non nul.
8. Migration Room 24→25 : `pageCount` absent avant, `NULL` après, aucune perte de données
   existantes.
9. Invariants : `Publication(pageCount = 0)` et `Locator(pageOffsetY = 1.5f)` lèvent
   `IllegalArgumentException`.
10. Un appel natif défaillant (page hors bornes, document déjà fermé) est intercepté par
    `safeNativeCall` et ne crashe jamais le process de test.

`Ajoute les tests du palier 1 support PDF`

---

## Vérifications sur appareil

Ce palier n'a **aucun chemin UI atteignable** (décision actée 9) : le sélecteur SAF ne
propose pas encore les PDF. La vérification device porte sur les tests instrumentés et une
mesure de performance, pas sur une manipulation manuelle du sélecteur de fichiers — écart
assumé par rapport au patron habituel de cette section, à traiter différemment au Palier 2
une fois l'écran de lecture branché.

| # | Attendu |
|---|---|
| 1 | `./gradlew :infrastructure:parser:connectedAndroidTest` passe sur un appareil physique (classe Snapdragon 680 si disponible) — aucun crash natif sur les 5 fixtures |
| 2 | `./gradlew :infrastructure:database:connectedAndroidTest` passe — migration 24→25 validée sur appareil réel, pas seulement Robolectric |
| 3 | Sur `fixture-large.pdf` : temps d'ouverture de l'index **mesuré et consigné** (log/profiler) — traité comme un enregistrement de référence, pas un seuil pass/fail : le chiffre « < 50 ms » de la recherche initiale vient de MuPDF, pas de PDFium (décision actée 19), le vrai seuil se fixe en tâche 12.7 |
| 4 | Import de `fixture-valid.pdf` déclenché depuis un test instrumenté (ou un écran de debug temporaire) confirme la `Publication` insérée avec les bons champs — en l'absence de sélecteur réel à ce palier |

---

## Après le palier 1

- Dette non traitée ici : l'AAR `pdfiumandroid` peut embarquer plus d'ABI que
  `arm64-v8a` (filtré côté Gradle, décision actée 8) — vérifier périodiquement que la
  taille d'APK n'en pâtit pas si la lib change de stratégie de publication.

---

# PALIER 2 — Rendu

## Décisions actées (Palier 2)

11. **Point d'insertion confirmé dans le code réel.** `ReaderViewModel.openPublication`
    (autour de la ligne 327) construit déjà `ReaderUiState` depuis
    `result.documentModel.chapters`/`tableOfContents`, sans branchement par format —
    conséquence directe de la décision actée 4 du Palier 1 (PDF unifié dans le même
    `DocumentModel`, page = chapitre). L'essentiel de l'état se peuple donc **sans
    changement** ; il ne manque qu'un champ de format et un champ de défilement intra-page.
12. **Le contrat de rendu bitmap ne peut pas vivre où le document de recherche initial le
    plaçait.** `ReaderContent`/`PdfPageRenderer` (§C du document de recherche) ne peuvent
    pas référencer directement le binding PDFium d'`infrastructure/parser` depuis
    `feature/reader` : la règle de dépendance est absolue — « Aucun composant de `feature/`
    n'importe quoi que ce soit de `infrastructure/` directement » (Blueprint §4.7, vérifiée
    par `checkArchitectureRules` à chaque build). Il faut donc un **contrat `domain`**,
    symétrique à `PublicationParser`/`TtsEngine` :
    ```kotlin
    // domain/service/FixedPageRenderer.kt
    interface FixedPageRenderer {
        suspend fun open(fileUri: String): FixedPageOpenResult
    }
    sealed interface FixedPageOpenResult {
        data class Success(val document: FixedPageDocument) : FixedPageOpenResult
        data class Failed(val reason: String) : FixedPageOpenResult
    }
    interface FixedPageDocument {
        val pageCount: Int
        suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage?
        fun close()
    }
    // domain/model/RenderedPage.kt
    data class RenderedPage(val widthPx: Int, val heightPx: Int, val pixelsArgb: IntArray)
    ```
    `RenderedPage` transporte un tampon de pixels pur (`IntArray`), **jamais**
    `android.graphics.Bitmap` — le domaine ne dépend jamais d'Android (règle non
    négociable, CLAUDE.md). `feature/reader` convertit via
    `Bitmap.createBitmap(pixelsArgb, widthPx, heightPx, Config.ARGB_8888)`, une seule copie,
    acceptée comme coût de la conformité architecturale plutôt que contournée.
    **Correction de relecture :** la première version de ce contrat renvoyait `null` en
    silence sur `open()` et un `RenderedPage` non-nullable garanti sur `renderPage()` —
    incohérent avec `PublicationParser`/`ParseResult`, conçu précisément pour ne jamais
    échouer sans dire pourquoi (Blueprint §7.11). `open()` distingue maintenant succès et
    raison d'échec (fichier déplacé, permission SAF révoquée, échec natif à l'ouverture) ;
    `renderPage()` reste nullable pour un échec ponctuel en cours de session (page hors
    bornes, erreur native transitoire interceptée par `safeNativeCall`) — `FixedPageContent`
    doit pouvoir afficher un état d'erreur par page plutôt que crasher.
13. **Implémentation dans `infrastructure/parser`, pas un nouveau module.** Le binding
    PDFium (dépendance native, filtre ABI, discipline JNI) y vit déjà depuis le Palier 1 —
    dupliquer la dépendance native dans un second module `infrastructure/` pour une seule
    classe de rendu serait une décoration, pas une nécessité, et ajouterait une entrée à la
    table canonique des modules (Blueprint §5.2) sans justification suffisante.
14. **Cycle de vie du handle de rendu distinct de celui du parsing.** `PublicationParser.parse()`
    ouvre, extrait, ferme — un aller-retour par ouverture de la bibliothèque ou import.
    `FixedPageDocument`, lui, reste **ouvert pour toute la session de lecture** (navigation
    entre pages), fermé explicitement dans `onCleared()` du ViewModel ou à la fermeture du
    lecteur — jamais laissé au ramasse-miettes pour libérer les ressources natives.
15. **`currentChapterIndex` réutilisé tel quel comme index de page** — aucun nouveau champ
    d'index n'est nécessaire, `NextChapter`/`PreviousChapter`/`JumpToChapter`/la table des
    matières fonctionnent sans modification grâce au modèle « page = chapitre » posé au
    Palier 1.
16. **Fonctionnalités explicitement hors périmètre de ce palier**, déclarées plutôt que
    silencieusement absentes (même patron que la carte WebDAV grisée du Lot 11) :
    - **TTS et minuteur de sommeil** — masqués pour `format == PDF` ; le TTS sur PDF est un
      lot distinct et conditionné (ADR-017), le minuteur n'a pas de sens sans lecture audio
      à mettre en pause.
    - **Sélection libre au mot / annotations** — un rendu bitmap sous `Canvas` n'offre pas
      la sélection native de `BasicTextField` ; l'ajouter demanderait un hit-testing dédié
      sur les `BoundingBox` de mots, hors périmètre ici.
    - **Bascule SCROLL/PAGED** (`ToggleReadingMode`) — sans objet, un PDF est nativement
      paginé.
    - **Repos oculaire** — conservé sans changement : indépendant du TTS (simple rappel de
      pause visuelle), fonctionne déjà en lecture purement visuelle.
17. **Marque-pages adaptés, pas dupliqués.** `ToggleBookmarkAtCurrentPosition`/
    `isCurrentPageBookmarked`, aujourd'hui basés sur `currentSentenceIndex` et des offsets
    de phrase, basculent sur `Locator(chapterIndex = currentChapterIndex, charOffset = 0,
    pageOffsetY = pageOffsetY)` quand `publicationFormat == PDF` — même `Locator`, mêmes
    Use Cases (`CreateBookmarkUseCase`), aucune seconde voie de persistance.
18. **Recherche déjà compatible sans changement.** Un résultat de recherche sur le texte
    d'un PDF vectoriel (indexé au Palier 1, décision actée 4) navigue via
    `NavigateToLocator`/`chapterIndex` exactement comme pour un EPUB — bénéfice direct,
    vérifié dans le code, de l'unification du `DocumentModel`.
19. **Chiffres de performance de la recherche initiale non fiables tels quels** — mesurés
    pour MuPDF, pas pour PDFium (moteur changé en cours de Palier 1). À re-mesurer en tâche
    12.7 avant de figer un seuil, pas supposés transférables entre moteurs. S'applique
    aussi rétroactivement à la vérification device du Palier 1 (corrigée : mesure
    consignée, pas seuil pass/fail).
20. **Aucune section `UX_FLOW_DESIGN.md` n'existe pour la lecture PDF.** La discipline
    habituelle (lire le document cible avant la première ligne d'UI) ne peut pas s'appliquer
    ici faute de document. Décision assumée, sur le même modèle que `SyncConflictBottomSheet`
    au Lot 11 : `FixedPageContent` est conçu directement en code (tâche 12.8), sans maquette
    préalable — à consigner dans `UX_FLOW_DESIGN.md` a posteriori (tâche 12.14) plutôt que
    de bloquer le lot sur une maquette qui n'existe pas.
21. **`isCurrentPageBookmarked` change de sémantique de comparaison pour le format PDF.**
    En EPUB, un signet « existe à la position courante » si son `charOffset` tombe dans la
    plage de la phrase courante — notion sans équivalent en lecture PDF page par page (pas
    de « phrase courante » suivie). Pour `publicationFormat == PDF`, la comparaison se
    réduit à l'égalité de page : `bookmark.locator.chapterIndex == currentChapterIndex`,
    en ignorant `charOffset`/`pageOffsetY` — un signet existe « sur cette page », pas « à ce
    défilement précis » (cohérent avec la granularité affichée dans le panneau
    Marque-pages).

---

## Tâche 12.7 — Contrat domaine de rendu et implémentation PDFium

- Mesurer, sur le binding retenu (`io.legere:pdfiumandroid`), le temps de rendu par page et
  l'empreinte mémoire d'un bitmap plein écran non zoomé, sur `fixture-large.pdf` et
  `fixture-scanned.pdf` (fixtures du Palier 1) — remplace les chiffres MuPDF de la
  recherche initiale (décision actée 19), sert de base aux seuils du test de
  performance (tâche 12.13).
- `domain/service/FixedPageRenderer` + `domain/model/RenderedPage` (décision actée 12).
- `infrastructure/parser` : `PdfPageRendererImpl : FixedPageRenderer`, réutilise la
  discipline JNI posée en tâche 12.2 (dispatcher/mutex dédié, `safeNativeCall`). Handle
  ouvert tenu vivant pour la session (décision actée 14).
- Rendu en tuiles pour la zone zoomée : ne jamais rendre un bitmap plein écran à un facteur
  de zoom élevé (risque `OutOfMemoryError` sur cible bas de gamme).
- Liaison Hilt du nouveau contrat (`ParserModule` ou module DI dédié si la distinction
  matériel/texte le justifie à l'usage).

`Ajoute le contrat de rendu fixe et son implementation PDFium`

---

## Tâche 12.8 — Composant `FixedPageContent`

- Nouveau `feature/reader/FixedPageContent.kt` : `HorizontalPager` indexé sur
  `currentChapterIndex` (décision actée 15, aucun nouveau champ d'index). Une page =
  conversion `RenderedPage` → `Bitmap` → `ImageBitmap`, dessinée via `Canvas` Compose.
- Zoom : transformation GPU (`graphicsLayer { scaleX, scaleY }`) pendant le geste de
  pincement ; rasterisation haute définition (nouvel appel à `renderPage` à résolution
  supérieure) uniquement au relâchement du doigt (debounce), jamais à chaque frame du
  geste.
- **Panoramique vertical, absent de la recherche initiale et nécessaire pour `pageOffsetY`** :
  `detectTransformGestures` fournit aussi une translation, pas seulement une échelle —
  `graphicsLayer { translationY }` pendant le geste, convertie en ratio `[0f..1f]` (position
  verticale / hauteur de page rendue) et émise via `UpdatePageOffset` au relâchement. Sans
  ce geste explicite, `pageOffsetY` ne serait jamais mis à jour en pratique — c'est le
  geste qui rend pertinent le champ ajouté au Palier 1 (décision actée 7). Pertinent dès
  qu'une page « ajustée à la largeur » dépasse la hauteur de l'écran, pas seulement au
  zoom.
- Cache `LruCache` limité à 3-5 pages (active, N-1, N+1), recyclage `Bitmap.inBitmap` pour
  éviter les à-coups du ramasse-miettes pendant la navigation séquentielle.
- `ReaderScreen.kt` : branchement `when (state.publicationFormat) { PDF -> FixedPageContent(...); else -> /* rendu existant, inchangé */ }`.
  Le chrome (`ReaderTopBar`, contrôles bas d'écran, `TableOfContentsSheet`) reste **commun
  aux deux formats**, aucune duplication.

`Ajoute FixedPageContent avec zoom par tuiles et cache de pages`

---

## Tâche 12.9 — État, navigation et reprise de lecture

- `ReaderUiState.publicationFormat: PublicationFormat` (nouveau champ), `pageOffsetY: Float = 0f`.
- `ReaderViewModel.openPublication` : peuple `publicationFormat = publication.format`
  (déjà disponible via `publicationRepository.getById`, simplement jamais reporté dans
  l'état jusqu'ici).
- Nouvel intent `UpdatePageOffset(offsetY: Float)`, émis par `FixedPageContent` sur le
  geste de panoramique vertical (tâche 12.8) — miroir de `UpdateScrollPosition`, réservé au
  mode PDF.
- Progression PDF : `ReaderUiState.bookProgression` (propriété déjà existante, déjà lue par
  la barre de progression et le badge de reprise) gagne une branche
  `when (publicationFormat)` — pour `PDF` :
  `(currentChapterIndex + pageOffsetY) / chapters.size`. **Correction de relecture :**
  `state.chapters.size` remplace `publication.pageCount`, qui n'est pas accessible depuis
  `ReaderUiState` (le ViewModel ne stocke jamais l'objet `Publication` complet, seulement
  des champs individuels éclatés à l'ouverture) — `chapters.size` est déjà en mémoire et
  vaut la même valeur par construction (décision actée 4). La branche EPUB/TXT continue
  d'appeler `Locator.computeProgression`, inchangé.
- Marque-pages : écriture décrite en décision actée 17, lecture (`isCurrentPageBookmarked`)
  décrite en décision actée 21.
- Reprise de lecture (`persistPosition`, checkpoint périodique) : chemin d'écriture déjà
  générique (`UpdateReadingStateUseCase`) — porte le `pageOffsetY` sans changement de
  signature, le `Locator` le contient déjà depuis le Palier 1.

`Branche l etat et la reprise de lecture sur le format PDF`

---

## Tâche 12.10 — Désactivation propre des fonctionnalités hors périmètre

Application de la décision actée 16 — chaque désactivation est explicite et visible dans
le code, jamais un bouton qui reste actif sans effet :

- `ReaderTtsPanel`/`TtsPillBar` : masqués si `publicationFormat == PDF`.
- Minuteur de sommeil : masqué pour PDF.
- Sélection libre / menu contextuel d'annotation : non déclenché sur `FixedPageContent`
  (pas de `BasicTextField` sous le rendu bitmap).
- `ToggleReadingMode` : masqué/inerte pour PDF.

`Desactive proprement TTS, selection et bascule de mode pour le format PDF`

---

## Tâche 12.11 — Thèmes et accessibilité visuelle

- `ColorMatrix` d'inversion appliquée uniquement si `chapters[currentChapterIndex].paragraphs.isNotEmpty()`
  (texte vectoriel détecté dès le Palier 1 — pas de nouvelle détection à écrire) : sombre/
  sépia recolorent la page rendue.
- Page scannée (`paragraphs` vide) : rendu original par défaut. Option « Forcer
  l'inversion » dans les réglages (désactivée par défaut), avec atténuation de contraste
  si activée.
- Transitions de page : respect de `reduceMotion` (même garde que le Lot 11, tâche 11.6 —
  ne pas régresser sur ce point déjà posé pour la synchronisation).

`Applique les themes sombre et sepia au rendu PDF vectoriel`

---

## Tâche 12.12 — Ouverture de l'import et libellés

- `ImportPickerButton.kt` : ajout de `"application/pdf"` au tableau MIME du sélecteur SAF —
  le pipeline livré au Palier 1 devient enfin atteignable par un utilisateur réel. Mettre à
  jour le commentaire du fichier (« PDF hors périmètre v1, ADR-017 » n'est plus exact pour
  l'affichage).
- `BookActionsSheet.kt` (ligne du `DetailRow("Chapitres", …)`) : libellé conditionnel selon
  `publication.format` — « Pages » + `publication.pageCount` pour un PDF, « Chapitres » +
  `chapterCount` inchangé pour EPUB/TXT.

`Ouvre l import PDF et adapte le libelle de comptage`

---

## Tâche 12.13 — Tests du palier 2

1. `FixedPageContent` affiche la page bitmap correspondant à `currentChapterIndex`, sans
   reflow du texte sous-jacent.
2. `NextChapter`/`PreviousChapter`/`JumpToChapter` fonctionnent sur un PDF sans code
   spécifique — réutilisation confirmée par le test. Navigation depuis la table des
   matières testée **seulement si** `fixture-valid.pdf` expose une table native (tâche
   12.3) — pas garanti par l'API du binding, à constater plutôt qu'à supposer.
3. Le cache ne dépasse jamais 5 bitmaps pleine résolution simultanés pendant une navigation
   séquentielle rapide.
4. Au-delà d'un seuil de zoom, le rendu bascule en tuiles — pas d'allocation d'un bitmap
   plein écran à facteur de zoom élevé (non-régression mémoire).
5. Fermer puis rouvrir un PDF restaure exactement `chapterIndex` et `pageOffsetY`.
6. Un marque-page posé sur une page PDF réapparaît à la réouverture, avec le bon
   `pageOffsetY`.
7. Un résultat de recherche sur texte PDF vectoriel navigue vers la bonne page.
8. TTS, minuteur de sommeil, sélection libre, bascule SCROLL/PAGED : absents ou inertes en
   mode PDF, aucun crash au clic sur une zone qui les aurait affichés en EPUB.
9. Thème sombre sur PDF vectoriel : couleurs inversées ; sur PDF scanné : image inchangée
   par défaut, inversée seulement si l'option est activée.
10. `ImportPickerButton` propose désormais les PDF ; import de bout en bout (sélecteur →
    bibliothèque → lecteur) réussi sur appareil.
11. `BookActionsSheet` affiche « Pages : N » pour un PDF, « Chapitres : N » pour un EPUB —
    non-régression sur EPUB/TXT.
12. Sur appareil bas de gamme (Snapdragon 680 ou proche) : temps de rendu par page dans les
    fourchettes mesurées en tâche 12.7 (PDFium, pas les chiffres MuPDF de la recherche
    initiale).

`Ajoute les tests du palier 2 rendu PDF`

---

## Tâche 12.14 — Consignation

- Mettre à jour le Blueprint (§16, roadmap) et/ou `ADR-017` : le volet « affichage seul »
  de la v1.x est livré, le second volet (TTS sur PDF) reste conditionné et non planifié.
- Consigner dans `UX_FLOW_DESIGN.md` : absence de maquette préalable pour
  `FixedPageContent` (décision actée 20, exception assumée), et la matrice des
  fonctionnalités désactivées pour ce format (décision actée 16) — pour qu'un lecteur du
  document UX comprenne pourquoi TTS/annotations/bascule de mode sont absents en lecture
  PDF sans devoir relire ce plan.

`Consigne l etat du support PDF affichage dans le blueprint et l UX`

---

## Vérifications sur appareil

| # | Attendu |
|---|---|
| 1 | Importer un PDF réel via le sélecteur : il apparaît dans la bibliothèque avec couverture et « Pages : N » |
| 2 | Ouvrir le PDF : rendu net, sans reflow, navigation page à page fluide |
| 3 | Zoom pincer-écarter : fluide à 60 FPS pendant le geste, netteté haute définition après relâchement |
| 4 | Fermer le lecteur en page 50, rouvrir : retour exact à la page 50, au bon défilement intra-page |
| 5 | Poser un marque-page sur une page, le retrouver dans le panneau Marque-pages, y naviguer |
| 6 | Rechercher un mot présent dans un PDF vectoriel : le résultat ouvre la bonne page |
| 7 | Thème sombre activé : page vectorielle inversée lisiblement ; page scannée inchangée |
| 8 | Aucun bouton TTS, minuteur de sommeil ou bascule de mode visible en lecture PDF |
| 9 | PDF volumineux (≥ 200 pages) : navigation séquentielle rapide sans ralentissement ni crash mémoire observable |
| 10 | Rotation de l'appareil pendant la lecture d'un PDF : pas de perte de position |

---

## Après ce lot

- **Lot distinct et ultérieur, conditionné** (ADR-017) : TTS sur PDF vectoriel — extraction
  de `BoundingBox` par mot via l'API texte de PDFium, surlignage synchronisé, désactivation
  propre du bouton TTS si la page est une image pure sans texte. Les Paliers 1 et 2 posent
  déjà toute la structure nécessaire (`Locator`, `DocumentModel` par page,
  `FixedPageRenderer`) — ce lot futur active des fonctionnalités déjà câblées, il ne
  rouvre pas le modèle de données.
- Dette non traitée : l'AAR `pdfiumandroid` peut embarquer plus d'ABI que `arm64-v8a`
  (filtré côté Gradle) — vérifier périodiquement l'impact sur la taille d'APK.
- Mode paysage / double-page tablette (mentionné dans la recherche initiale, §H) : non
  traité, aucun appareil cible de ce type mentionné dans le Blueprint — à ouvrir seulement
  sur demande explicite plutôt que par anticipation.
