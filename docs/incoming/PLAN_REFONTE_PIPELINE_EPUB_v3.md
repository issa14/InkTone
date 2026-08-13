## Plan v3 : Refonte Pipeline EPUB — Modèle AST + Parsing Jsoup + Rendu Riche

**TL;DR** — Remplacer l'extraction de texte brut par un modèle de document structuré (`BookBlock` sealed + `StyledText` avec `SpanStyles` bitmask) pour afficher les EPUB avec leur formatage réel (gras, italique, images, liens) et corriger le bug des fragments (`#prologue`). Readium = gestionnaire d'archives uniquement (ZIP + manifest + flux). Jsoup = parseur DOM avec normalisation des spans imbriqués. Parsing paresseux (un chapitre à la fois, dispatcher dédié avec annulation explicite). Refonte en 5 paliers additifs — jamais de big bang, build vert à chaque commit. **Ce plan incorpore les 12 corrections de l'audit Google Senior Android Dev du 2026-08-12 PLUS 5 corrections architecturales critiques (concurrence, offsets TTS/UI, pagination par lots, fragment Jsoup, scoping Coil).**

**Portée** : le *rendu enrichi* (`BookBlock`, spans, images) est EPUB uniquement — TXT et PDF continuent d'afficher du texte brut / des pages fixes, aucun changement visuel pour eux. Mais au niveau *domaine*, TXT et PDF ne sont **pas** inchangés : `Chapter.paragraphs` devient `Chapter.content: ChapterContent`, donc `TxtPublicationParser` et `PdfPublicationParser` doivent chacun être adaptés (une ligne : envelopper leur sortie existante dans `ChapterContent.Legacy(paragraphs, structuralBlocks)`) pour continuer à compiler — voir note Palier 1.1 ci-dessous. Le rendu PDF reste sur `FixedPageContent` (bitmap PDFium, ADR-017), totalement découplé de `ChapterTextMeasurer`/`BookBlockItem` — il n'est donc affecté par aucun palier de ce plan au-delà de cette adaptation de compilation.

**Note (2026-08-12, post-vérification code)** : `docs/execution/LOT_12_SUPPORT_PDF.md` est mergé sur `main` (PR #64) au moment de l'écriture de cette note. `PdfPublicationParser.kt` (180 lignes, `infrastructure/parser`) construit déjà directement `Chapter/Paragraph/Sentence` — c'est donc bien le pipeline existant décrit dans la colonne « Actuel » du tableau ci-dessous, sans ambiguïté de séquencement avec un lot encore en chantier.

---

## État Actuel vs Cible

| Dimension | Actuel | Cible |
|---|---|---|
| **Modèle** | `Chapter → Paragraph → Sentence(String)` + `structuralBlocks` ancrés par index | `Chapter(ChapterContent.Rich(blocks: List<BookBlock>), sentences: List<Sentence>)` |
| **Texte inline** | `String` brut — formatage perdu | `StyledText(plainText, spans: List<Span(SpanStyles bitmask, start, end))` |
| **Styles imbriqués** | ❌ Impossible | ✅ `SpanStyles` bitmask — `<b><i>texte</i></b>` → `Span(STRONG\|EMPHASIS, …)` après normalisation |
| **Styles bloc** | `ParagraphStyle` enum | `BookBlock` sealed : `ParagraphBlock`, `HeadingBlock(level)`, `ImageBlock`, `SeparatorBlock` |
| **Images** | `StructuralBlock.EpubImage` → placeholder gris | `ImageBlock(href, alt, intrinsicWidth?, intrinsicHeight?)` → `AsyncImage` Coil via `EpubImageKey` |
| **Parseur** | `TextContentTokenizer` Readium | Jsoup (DOM, charge tout l'arbre mais extrait sélectivement) avec normalisation des spans |
| **Tokenisation** | `TextContentTokenizer(Language("fr"))` | `FrenchSentenceSplitter` (BreakIterator, déjà dans le projet) — spike de compatibilité obligatoire avant adoption |
| **Granularité** | Tout le livre parsé à l'import | Parsing paresseux par chapitre à l'ouverture, préchargement annulable en arrière-plan |
| **Threading** | `Dispatchers.IO` (partagé) | Dispatcher dédié `epub-parser` (2 threads) — `preload()` retourne `Job`, annulé au changement de chapitre |
| **Cache** | Aucun | `LruCache` par **octets** (5 MB), pas par entrées |
| **Fragments** | Bug `#prologue` (tout le fichier) | Jsoup charge tout l'arbre DOM (coût I/O complet) mais extrait sélectivement le sous-arbre du fragment — corrige le bug sans magie |
| **Pagination** | `AnnotatedString` unique par chapitre | Mesure par **lots** (batching, ~10 000 caractères par lot) pour éviter les crashs de texture Compose sur les longs chapitres |
| **Lien TTS ↔ UI** | Offsets globaux uniquement | `Sentence.blockIndex` + `ParagraphBlock.globalOffsetRange` → recherche dichotomique O(log n) pour le surlignage mot-à-mot |
| **Accessibilité** | Non traitée | `semantics { heading() }` / `contentDescription` / `invisibleToUser()` par type de bloc |

---

## Décisions d'Architecture

### D1 — `Sentence` survit, avec lien explicite vers son bloc parent

Le TTS, la recherche FTS, la navigation et les signets dépendent de `Sentence` (texte + offsets). `Sentence` n'est pas supprimé. `Chapter` contient `content: ChapterContent` (pour le rendu) ET `sentences: List<Sentence>` (pour le TTS). Les deux sont produits en une seule passe de parsing.

**Nouveau (v3)** : chaque `Sentence` porte un champ `val blockIndex: Int = -1` qui référence l'index du `BookBlock` parent dans `Chapter.blocks` quand `content` est `Rich` (`-1` pour `Legacy` — PDF/TXT, voir correction Palier 1.1). Chaque `ParagraphBlock` expose `val globalOffsetRange: IntRange` (offsets début/fin dans le texte concaténé du chapitre). Ce double indexage permet une **recherche dichotomique O(log n)** : quand le TTS surligne un mot à l'offset global `C`, on trouve le bloc contenant `C` via `blocks.binarySearch { it.globalOffsetRange.start.compareTo(C) }`, puis on calcule l'offset local au bloc pour le rendu — sans parcourir tous les blocs linéairement.

### D2 — Parsing paresseux avec cache par octets

`DocumentModel` est un conteneur léger (métadonnées + TOC). Les chapitres sont extraits à la demande via `ChapterParser.parseChapter(href)`. Cache `LruCache` avec `maxSize` en **octets** (5 MB), éviction basée sur `ChapterData.approxByteSize`. Préchargement asynchrone des chapitres N+1 et N-1 sur le dispatcher dédié.

### D3 — Readium = gestionnaire d'archives UNIQUEMENT

Readium ouvre le ZIP, lit le manifest, extrait les métadonnées, détecte les DRM, fournit la couverture, et expose les `InputStream` des ressources du spine. Il ne fait **plus** l'extraction de contenu (`HtmlResourceContentIterator`) ni la tokenisation (`TextContentTokenizer`).

### D4 — Jsoup avec normalisation des spans

Jsoup parse le HTML (y compris malformé, courant dans les EPUB). L'extraction des spans inline utilise un algorithme de **normalisation par split aux frontières** :

```
<b>bold <i>bold-italic</i></b>
→ Span(STRONG, 0, 5), Span(STRONG|EMPHASIS, 5, 15)
```

Algorithme : collecter tous les spans bruts (début, fin, style), trier les points de transition, créer des segments atomiques entre deux transitions consécutives, assigner à chaque segment le masque de styles actif. Ceci garantit qu'aucun span ne se chevauche et que toutes les combinaisons sont représentées.

### D5 — `EpubImageKey` Coil (pas d'URI scheme custom, pas de singleton)

Pour le chargement d'images, on utilise une `Key` Coil plutôt qu'un schéma d'URI custom (fragile) :

```kotlin
data class EpubImageKey(
    val publicationId: String,
    val resourceHref: String,
) : Key {
    override val cacheKey: String get() = "$publicationId:$resourceHref"
}
```

**Nouveau (v3)** : l'instance de `EpubResourceResolver` **n'est pas** obtenue via un singleton global. Elle est fournie par Hilt au `ReaderViewModel`, qui la transmet à `ReaderScreen` en paramètre de composable. `ReaderScreen` construit l'`ImageLoader` Coil dans un `remember` et injecte le resolver dans la `Factory` du `EpubResourceFetcher`. Le `DisposableEffect` de `ReaderScreen` ferme le resolver quand l'écran quitte la composition — aucune instance Readium ne fuit globalement. Clean Architecture respectée : le domaine ne connaît ni Coil ni Readium.

### D6 — `ChapterContent` sealed wrapper (pas de champs parallèles)

Pour une transition compiler-checked :

```kotlin
data class Chapter(
    val index: Int, val href: String, val title: String?,
    val content: ChapterContent,
    val sentences: List<Sentence>,
)

sealed class ChapterContent {
    data class Legacy(val paragraphs: List<Paragraph>, val structuralBlocks: List<StructuralBlock>) : ChapterContent()
    data class Rich(val blocks: List<BookBlock>) : ChapterContent()
}
```

Dans `ReaderScreen`, un `when (chapter.content)` sélectionne le chemin de rendu. Au Palier 5, `Legacy` est supprimé.

### D7 — Stratégie de migration : 5 paliers additifs, build vert à chaque commit

Aucun commit ne casse `./gradlew build`. Les nouveaux types sont créés dans le domaine sans supprimer les anciens. Les consommateurs migrent progressivement. L'ancien modèle est retiré UNIQUEMENT au Palier 5.

### D8 — Les offsets caractère restent la source de vérité, avec pont TTS↔UI

`Locator(chapterIndex, charOffset)` inchangé. Les `Span` sont purement décoratifs et n'affectent jamais les offsets. TTS, signets et annotations fonctionnent sur les offsets du `StyledText.plainText` concaténé.

**Nouveau (v3)** : le pont entre offset global et bloc de rendu est assuré par `Sentence.blockIndex` + `ParagraphBlock.globalOffsetRange`. Quand le TTS signale un mot à l'offset `charOffset`, l'UI trouve le bloc en O(log n) via recherche dichotomique sur `blocks.map { it.globalOffsetRange.start }`, puis soustrait `block.globalOffsetRange.start` pour obtenir l'offset local dans le `BasicTextField` du bloc. Algorithme documenté dans `BookBlockItem.kt`.

### D9 — Dispatcher dédié avec annulation explicite des préchargements

```kotlin
private val parserDispatcher = newFixedThreadPoolContext(2, "epub-parser")
```

Évite la contention avec `Dispatchers.Default` (utilisé par Compose pour la mesure/layout). La lecture d'`InputStream` depuis le ZIP reste sur `Dispatchers.IO`.

**Nouveau (v3)** : `ChapterParser.preload()` retourne un `Job` (ou accepte un `CoroutineScope` parent). Le `ReaderViewModel` conserve une référence `var preloadJob: Job?` et appelle `preloadJob?.cancel()` **avant** de lancer un nouveau préchargement lors d'un changement de chapitre. Ceci garantit que si l'utilisateur zappe rapidement 5 chapitres, seuls les préchargements du chapitre final survivent — pas d'empilement, pas de starvation des threads. Le `Semaphore` interne du parser reste pour limiter le parallélisme (max 2 parses simultanés).

### D10 — Dimensions d'image intrinsèques

`ImageBlock` inclut `intrinsicWidth: Int?` et `intrinsicHeight: Int?` extraits des attributs `width`/`height` du `<img>`. Le placeholder `AsyncImage` utilise ces dimensions pour conserver le ratio d'aspect — zéro layout shift au chargement.

### D11 — Accessibilité TalkBack

Chaque `BookBlockItem` déclare sa sémantique Compose : `HeadingBlock → semantics { heading() }`, `ParagraphBlock → semantics { contentDescription = plainText }`, `ImageBlock → contentDescription = alt`, `SeparatorBlock → semantics { invisibleToUser() }`. Conforme au Blueprint §1.4 (« Accessible from Day One »).

### D12 — DSL de test pour les fixtures

`core/testing/src/main/kotlin/com/inktone/core/testing/fixture/BookBlockFixtures.kt` :

```kotlin
fun chap(index: Int = 0, vararg blocks: BookBlock) = Chapter(index, "ch$index.xhtml", null, ChapterContent.Rich(blocks.toList()), emptyList())
fun p(vararg segments: Pair<String, Int>) = ParagraphBlock(styledText(*segments))
fun h(level: Int, text: String) = HeadingBlock(level, StyledText(text, emptyList()))
fun img(src: String, alt: String? = null) = ImageBlock(src, alt)
infix fun String.withStyle(mask: Int) = this to mask
const val B = SpanStyles.STRONG
const val I = SpanStyles.EMPHASIS
// Usage: chap(0, h(1, "Titre"), p("Le " withStyle B, "Petit Prince" withStyle (B or I)))
```

---

## Limites connues (documentées, non régressives)

- **Sélection inter-bloc impossible** : chaque `ParagraphBlock` est rendu dans son propre `BasicTextField`. Un appui long + glissement ne peut pas traverser la frontière entre deux paragraphes. C'est la **même limite qu'aujourd'hui** (un `BasicTextField` par `ParagraphText`). La solution v2 consiste à rendre TOUS les blocs de texte dans UN SEUL `BasicTextField` avec des `Placeholder` intercalés dans l'`AnnotatedString` pour les blocs non-texte — chantier distinct, hors scope de cette refonte.
- **Images en mode PAGED** : pas d'images dans le `HorizontalPager` (écart assumé, déjà présent aujourd'hui).
- **Cache mémoire uniquement** : un redémarrage = re-parse. Acceptable car Jsoup parse un chapitre en ~200ms.

---

## Phases et Étapes

### Palier 0 — Spike bloquant : Créer et valider un tokenizer de phrases unifié

*Dépend de rien. Bloque le Palier 1.3. Ne produit pas de code de production (hors le splitter lui-même, en `core` ou `domain`, testé mais pas encore branché).*

**Correction (2026-08-12, post-vérification code)** : ce palier supposait à tort qu'un `FrenchSentenceSplitter` existe déjà dans le projet (« déjà dans le projet »). Vérification faite : aucun fichier, classe ou fonction de ce nom ni d'un équivalent (`SentenceSplitter`, `sentenceSplit`) n'existe dans le repo. La tokenisation actuelle est assurée par deux mécanismes différents et non partagés : `TextContentTokenizer(Language("fr"))` de Readium pour l'EPUB (`DocumentModelExtractor.kt`), et une regex naïve `Regex("""(?<=[.!?])\s+""")` pour PDF/TXT. Le Palier 0 doit donc **créer** ce splitter avant de pouvoir le comparer — la portée du palier change en conséquence.

#### 0.1 — Créer `FrenchSentenceSplitter` (BreakIterator) et le comparer à `TextContentTokenizer` Readium

Implémenter un splitter de phrases basé sur `java.text.BreakIterator` (locale `fr`) — candidat à terme pour unifier la tokenisation EPUB/PDF/TXT sous un seul algorithme testé, au lieu de la regex naïve actuelle pour PDF/TXT et du tokenizer Readium pour l'EPUB. Puis, sur un EPUB réel (Les Misérables Tome I, fixture existante), extraire les phrases avec les deux tokenizers (le nouveau splitter et `TextContentTokenizer`) et comparer offset par offset.

**Livrable** : `docs/spikes/sentence-tokenizer-comparison.md` contenant :
- Tableau comparatif : nombre de phrases, offsets de début/fin pour les 50 premières phrases
- Cas de divergence (abréviations, ponctuation, tirets) avec explication
- Décision : utiliser `FrenchSentenceSplitter` comme source unique (EPUB **et** PDF/TXT, remplaçant la regex naïve), ou conserver `TextContentTokenizer` pour l'EPUB et ne pas toucher au pipeline PDF/TXT dans ce lot
- Si divergence > 0 et `FrenchSentenceSplitter` retenu : liste des corrections à appliquer au `FrenchSentenceSplitter`

**Critère de succès** : divergence ≤ 2 caractères par phrase sur 95% des phrases.

---

### Palier 1 — Fondation : Nouveau modèle domaine + Nouveau parseur Jsoup

*Dépend du Palier 0. Aucun consommateur migré. Build vert.*

#### 1.1 — Ajouter les nouveaux types au domaine (naming sémantique + pont TTS↔UI)

**Fichiers à créer :**
- `domain/src/main/kotlin/com/inktone/domain/model/BookBlock.kt`
  ```kotlin
  sealed class BookBlock {
      abstract val approxByteSize: Int
      /** Offsets [début, fin[ dans le texte concaténé du chapitre — null pour les blocs non-texte. */
      abstract val globalOffsetRange: IntRange?
      data class ParagraphBlock(
          val richText: StyledText,
          override val globalOffsetRange: IntRange,
      ) : BookBlock()
      data class HeadingBlock(
          val level: Int,
          val richText: StyledText,
          override val globalOffsetRange: IntRange,
      ) : BookBlock()
      data class ImageBlock(
          val href: String, val alt: String?,
          val intrinsicWidth: Int? = null, val intrinsicHeight: Int? = null,
      ) : BookBlock() { override val globalOffsetRange: IntRange? = null }
      data class SeparatorBlock : BookBlock() { override val globalOffsetRange: IntRange? = null }
  }
  ```

- `domain/src/main/kotlin/com/inktone/domain/model/StyledText.kt`
  ```kotlin
  data class StyledText(val plainText: String, val spans: List<Span>) {
      init {
          require(plainText.isNotEmpty() || spans.isEmpty())
          require(spans.all { it.start >= 0 && it.end <= plainText.length && it.end > it.start })
      }
      val approxByteSize: Int get() = plainText.length * 2 + spans.size * 20
  }
  ```

- `domain/src/main/kotlin/com/inktone/domain/model/Span.kt`
  ```kotlin
  data class Span(val styles: SpanStyles, val start: Int, val end: Int, val href: String? = null)
  ```

- `domain/src/main/kotlin/com/inktone/domain/model/SpanStyles.kt`
  ```kotlin
  @JvmInline value class SpanStyles(val mask: Int) {
      companion object {
          val NONE = SpanStyles(0)
          val STRONG = SpanStyles(1 shl 0)    // <strong>, <b>
          val EMPHASIS = SpanStyles(1 shl 1)  // <em>, <i>
          val INSERTED = SpanStyles(1 shl 2)  // <ins>, <u>
          val DELETED = SpanStyles(1 shl 3)   // <del>, <s>
          val SUPERSCRIPT = SpanStyles(1 shl 4)
          val SUBSCRIPT = SpanStyles(1 shl 5)
          val REFERENCE = SpanStyles(1 shl 6) // <a href>
      }
      operator fun plus(other: SpanStyles) = SpanStyles(mask or other.mask)
      operator fun contains(other: SpanStyles) = (mask and other.mask) == other.mask
  }
  ```

**Modifier :**
- `domain/src/main/kotlin/com/inktone/domain/model/DocumentModel.kt`
  - Ajouter `ChapterContent` sealed class et `Chapter.content: ChapterContent`
  - `Chapter` conserve `index`, `href`, `title`, `sentences`
  - **Nouveau (v3)** : `Sentence` gagne `val blockIndex: Int = -1` — référence l'index du `BookBlock` contenant cette phrase dans `Chapter.blocks` **quand `content` est `Rich`**. Valeur par défaut `-1` = « pas de bloc » pour les `Sentence` produites par un `ChapterContent.Legacy` (PDF/TXT — voir correction ci-dessous). Invariant : `blockIndex >= -1`, jamais `>= 0` seul (sinon la construction PDF/TXT ne compile plus).
  - `Chapter.paragraphs` et `structuralBlocks` restent mais dépréciés (accédés via `content` quand c'est `Legacy`)

**Correction (2026-08-12)** — impact sur les parseurs non-EPUB : dès que `Chapter.paragraphs`/`structuralBlocks` sont remplacés par `Chapter.content: ChapterContent`, `PdfPublicationParser.kt` et `TxtPublicationParser.kt` (qui construisent aujourd'hui `Chapter(paragraphs = …)` directement) cessent de compiler. Ce même commit doit donc les adapter pour émettre `Chapter(content = ChapterContent.Legacy(paragraphs, structuralBlocks), …)` — changement mécanique, sans modification de logique de parsing PDF/TXT. À lister explicitement comme sous-tâche 1.1 bis pour ne pas la découvrir en cours de build. **Sentence** : ces deux parseurs construisent aussi `Sentence(index=…, text=…, startOffset=…, endOffset=…)` (`PdfPublicationParser.kt:141`, `TxtPublicationParser.kt:48`) sans jamais avoir de `BookBlock` — le défaut `blockIndex = -1` ci-dessus leur évite toute modification ; ils compilent tels quels.

**Tests :**
- `domain/src/test/kotlin/com/inktone/domain/model/StyledTextTest.kt` — invariants, fusion de spans
- `domain/src/test/kotlin/com/inktone/domain/model/SpanStylesTest.kt` — opérateurs bitmask, combinaisons
- `domain/src/test/kotlin/com/inktone/domain/model/BookBlockTest.kt` — approxByteSize, globalOffsetRange cohérent, recherche dichotomique
- `domain/src/test/kotlin/com/inktone/domain/model/SentenceBlockIndexTest.kt` — blockIndex cohérent avec le bloc parent

**Commit** : `Ajoute StyledText, SpanStyles, Span, BookBlock (avec globalOffsetRange), Sentence.blockIndex au modèle domaine`

#### 1.2 — Ajouter la dépendance Jsoup

- `gradle/libs.versions.toml` : `jsoup = "1.18.1"`, `jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }`
- `infrastructure/parser/build.gradle.kts` : `implementation(libs.jsoup)`

**Commit** : `Ajoute la dépendance Jsoup 1.18.1`

#### 1.3 — Créer le parseur Jsoup avec normalisation des spans et extraction sélective par fragment

**Fichier à créer :**
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/JsoupChapterParser.kt`

**Clarification technique (v3) — Coût I/O du fragment** : `Jsoup.parse(inputStream, "UTF-8", baseUrl)` charge **tout le fichier XHTML en mémoire** — le coût I/O est complet, qu'il y ait un fragment ou non (le flux est décompressé et parsé intégralement). L'optimisation ne porte **pas** sur le temps de parsing HTML mais sur l'extraction de l'AST `BookBlock` : quand un fragment est spécifié (`#prologue`), `document.selectFirst("[id=prologue]")` ou `document.selectFirst("a[name=prologue]")` localise l'élément cible, puis l'algorithme ne parcourt que le sous-arbre DOM à partir de ce nœud pour produire les `BookBlock`. Ceci **corrige le bug du Prologue** (l'en-tête du document parent n'est pas inclus) sans prétendre réduire le coût I/O — qui reste O(taille du fichier).

**Algorithme de normalisation des spans** (documenté dans le KDoc) :
1. Collecter tous les spans bruts depuis l'arbre DOM : `List<RawSpan(styles, start, end)>`
2. Collecter tous les points de transition : `Set<Int>` = toutes les valeurs de `start` et `end`
3. Trier les points de transition
4. Pour chaque segment `[transition[i], transition[i+1])`, calculer le masque de styles actif = OR de tous les `RawSpan` qui couvrent ce segment
5. Émettre `Span(styles, transition[i], transition[i+1])` si `styles != NONE`

**Construction des offsets globaux et `Sentence.blockIndex`** :
- Un `runningOffset` global est maintenu pendant l'extraction des blocs
- Chaque `ParagraphBlock` et `HeadingBlock` reçoit `globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length)`
- Les `Sentence` sont tokenisées APRÈS extraction de tous les blocs (concaténation des `plainText`). Chaque `Sentence` reçoit `blockIndex = index du bloc contenant sentence.startOffset` (trouvé par recherche dichotomique sur les `globalOffsetRange`)

**Méthodes :**
- `parse(inputStream: InputStream, baseUrl: String, fragment: String? = null): ChapterData`
- `extractRichText(node: Node): StyledText` — récursif, accumule texte + spans bruts → normalisation
- `extractBlocks(body: Element, fragment: String?): List<BookBlock>` — traverse le DOM, applique le filtre de fragment si présent

**Tests (JVM pur, pas androidTest — Jsoup n'a pas besoin d'Android) :**
- `infrastructure/parser/src/test/kotlin/com/inktone/infrastructure/parser/JsoupChapterParserTest.kt`
  - Test 1 : `<p>Le <b>Petit</b> <i>Prince</i></p>` → `StyledText("Le Petit Prince", [Span(STRONG,3,8), Span(EMPHASIS,9,15)])`
  - Test 2 : `<b>bold <i>bold-italic</i></b>` → `[Span(STRONG,0,5), Span(STRONG|EMPHASIS,5,15)]` ← **test critique de normalisation**
  - Test 3 : `<h1>Titre</h1><p>Texte</p>` → `[HeadingBlock(1,…), ParagraphBlock(…)]` avec `globalOffsetRange` corrects
  - Test 4 : `<img src="foo.png" alt="Illustration" width="200" height="100"/>` → `ImageBlock(href, "Illustration", 200, 100)`
  - Test 5 : fragment `#prologue` → extraction partielle correcte (l'en-tête du document parent absent)
  - Test 6 : `<blockquote>Citation</blockquote>` → `ParagraphBlock`
  - Test 7 : `<sup>haut</sup>` / `<sub>bas</sub>` → `Span(SUPERSCRIPT)` / `Span(SUBSCRIPT)`
  - Test 8 : `<a href="ch2.xhtml">lien</a>` → `Span(REFERENCE, href="ch2.xhtml")`
  - Test 9 : EPUB réel (fixture) → cohérence offsets, aucun span hors bornes
  - Test 10 : `<p><b>A</b><i>B</i><u>C</u></p>` → 3 spans adjacents, pas de chevauchement
  - **Test 11 (v3)** : chapitre multi-blocs → `Sentence.blockIndex` pointe vers le bon `ParagraphBlock`, `globalOffsetRange` de chaque bloc ne se chevauche pas
  - **Test 12 (v3)** : fragment `#prologue` sur un fichier avec `<div id="header">` avant l'ancre → le `<div id="header">` n'apparaît PAS dans les `BookBlock` extraits

**Commit** : `Ajoute JsoupChapterParser avec normalisation des spans, extraction sélective par fragment, globalOffsetRange et blockIndex`

#### 1.4 — Créer l'interface `ChapterParser` dans le domaine (avec support d'annulation)

- `domain/src/main/kotlin/com/inktone/domain/service/ChapterParser.kt`

```kotlin
interface ChapterParser {
    suspend fun parseChapter(publicationId: String, chapterHref: String, fragment: String? = null): ChapterData
    /** Lance le préchargement asynchrone. Retourne un [Job] que l'appelant DOIT annuler
     *  ([Job.cancel]) si le chapitre n'est plus pertinent (changement de chapitre, fermeture). */
    fun preload(publicationId: String, chapterHref: String, scope: CoroutineScope): Job
    fun invalidate(publicationId: String)
}
```

Le `Job` retourné par `preload()` permet au `ReaderViewModel` d'annuler les préchargements obsolètes. Voir Palier 4.1 pour le contrat d'annulation.

**Commit** : `Ajoute l'interface ChapterParser avec Job d'annulation au domaine`

#### 1.5 — Créer le DSL de test

- `core/testing/src/main/kotlin/com/inktone/core/testing/fixture/BookBlockFixtures.kt`

Fonctions `chap()`, `p()`, `h()`, `img()`, `styledText()`, `s()`, extension `withStyle`, constantes `B`, `I`, `U`, `S`. Les fixtures `p()` et `h()` calculent automatiquement `globalOffsetRange` cohérent quand plusieurs blocs sont passés à `chap()`.

**Commit** : `Ajoute BookBlockFixtures DSL pour les tests`

---

### Palier 2 — Intégration : Wiring du nouveau parseur

*Dépend du Palier 1. L'ancien pipeline continue de fonctionner.*

#### 2.1 — Implémenter `EpubChapterParser` avec cache par octets, dispatcher dédié, et annulation

**Fichier à créer :**
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/EpubChapterParser.kt`

Combine `ReadiumPublicationParser` (accès aux ressources) et `JsoupChapterParser` (parsing DOM).
- Cache `LruCache<String, ChapterData>` avec `maxSize = 5 * 1024 * 1024` (5 MB), `safeSizeOf { _, value -> value.approxByteSize }`
- Dispatcher dédié `newFixedThreadPoolContext(2, "epub-parser")`
- `Semaphore(2)` pour limiter le parallélisme
- `preload()` lance `scope.launch(parserDispatcher) { … }` et retourne le `Job`

**Commit** : `Ajoute EpubChapterParser avec cache LRU (5 MB), dispatcher dédié, et preload annulable`

#### 2.2 — Enregistrer `ChapterParser` dans le module Hilt

- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/di/ParserModule.kt` : `@Binds fun bindChapterParser(impl: EpubChapterParser): ChapterParser`

**Commit** : `Enregistre ChapterParser dans le module Hilt parser`

#### 2.3 — Adapter `ReadiumPublicationParser` pour le parsing lazy

Ajouter `parseLazy(publicationId: String): LazyDocumentModel`. L'ancien `parse()` continue de fonctionner (backward compat).

**Commit** : `Ajoute le parsing paresseux par chapitre à ReadiumPublicationParser`

#### 2.4 — Adapter `SearchService` pour l'indexation depuis `BookBlock`

Ajouter une surcharge `indexPublication(publicationId, blocks: List<BookBlock>, sentences: List<Sentence>)`. L'indexation FTS reste identique (elle indexe `Sentence.text`).

**Fichiers** : `domain/.../SearchService.kt`, `infrastructure/database/.../RoomSearchService.kt`

**Commit** : `Adapte SearchService pour l'indexation depuis BookBlock`

---

### Palier 3 — Rendu : Lecteur basé sur `BookBlock` + Images Coil

*Dépend du Palier 2.*

#### 3.1 — Créer le mapper `BookBlockStyleMapper` (mapping sémantique → visuel)

**Fichier à créer :**
- `feature/reader/src/main/kotlin/com/inktone/feature/reader/rendering/BookBlockStyleMapper.kt`

Mapping `SpanStyles` → `SpanStyle` (SEUL endroit où la sémantique devient du visuel) :
- `STRONG` → `FontWeight.Bold`
- `EMPHASIS` → `FontStyle.Italic`
- `INSERTED` → `TextDecoration.Underline`
- `DELETED` → `TextDecoration.LineThrough`
- `SUPERSCRIPT` → `BaselineShift.Superscript` + `FontSize(0.7.em)`
- `SUBSCRIPT` → `BaselineShift.Subscript` + `FontSize(0.7.em)`
- `REFERENCE` → `Color(…link…)` + `TextDecoration.Underline`

Mapping `BookBlock` → `TextStyle` de bloc :
- `ParagraphBlock` → style de base
- `HeadingBlock(1)` → `FontSize(1.5.em)`, `FontWeight.Bold`
- `HeadingBlock(2)` → `FontSize(1.25.em)`, `FontWeight.Bold`

**Commit** : `Ajoute BookBlockStyleMapper (sémantique → visuel, couche présentation uniquement)`

#### 3.2 — Créer `BookBlockItem` (avec accessibilité + recherche dichotomique TTS)

**Fichier à créer :**
- `feature/reader/src/main/kotlin/com/inktone/feature/reader/rendering/BookBlockItem.kt`

Rend un `BookBlock` :
- `ParagraphBlock` → `BasicTextField` avec sélection libre (réutilise `ParagraphTextMapping`, adapté aux offsets locaux du bloc)
- `HeadingBlock` → `Text` + `Modifier.semantics { heading() }`
- `ImageBlock` → `AsyncImage(model = EpubImageKey(…), contentDescription = alt)` avec placeholder dimensionné
- `SeparatorBlock` → `HorizontalDivider` + `Modifier.semantics { invisibleToUser() }`

**Nouveau (v3) — Pont TTS↔UI documenté dans le KDoc de `BookBlockItem`** :
```kotlin
/**
 * SUR LIGNAGE MOT-À-MOT TTS — ALGORITHME DE CORRESPONDANCE OFFSET → BLOC :
 *
 * 1. Le TTS produit un offset global [charOffset] (espace du texte concaténé du chapitre).
 * 2. Trouver le bloc contenant cet offset :
 *    val blockIndex = chapter.blocks.binarySearch { it.globalOffsetRange?.start?.compareTo(charOffset) ?: -1 }
 *    if (blockIndex < 0) blockIndex = (-blockIndex - 2).coerceAtLeast(0)
 * 3. Valider que charOffset est dans [block.globalOffsetRange].
 * 4. Offset local = charOffset - block.globalOffsetRange.start.
 * 5. Appliquer le surlignage dans le BasicTextField de CE bloc uniquement.
 *
 * Complexité : O(log n) en nombre de blocs, contre O(n) avec un parcours linéaire.
 */
```

**Commit** : `Ajoute BookBlockItem avec rendu par type de bloc, accessibilité TalkBack, et pont TTS↔UI O(log n)`

#### 3.3 — Créer `EpubResourceFetcher` Coil (Key-based, injecté via paramètre de composable)

**Fichiers à créer :**
- `domain/src/main/kotlin/com/inktone/domain/service/EpubResourceResolver.kt` — interface
  ```kotlin
  interface EpubResourceResolver {
      suspend fun openStream(publicationId: String, resourceHref: String): InputStream?
      fun close()
  }
  ```
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/ReadiumResourceResolver.kt` — implémentation via Readium, scopée à une `Publication`
- `feature/reader/src/main/kotlin/com/inktone/feature/reader/rendering/EpubResourceFetcher.kt` — `Fetcher` + `Factory` Coil basé sur `EpubImageKey`

**Nouveau (v3) — Chaîne d'injection Hilt → Composable (pas de singleton global)** :

```
Hilt (ReaderViewModel)
  └─ @Inject constructor(..., epubResourceResolver: EpubResourceResolver)  // via ParserModule
       └─ ReaderUiState contient epubResourceResolver
            └─ ReaderScreen(resolver: EpubResourceResolver, ...)  // paramètre explicite
                 └─ val imageLoader = remember(resolver) {
                        ImageLoader.Builder(context)
                            .components { add(EpubResourceFetcher.Factory(resolver)) }
                            .build()
                    }
                 └─ DisposableEffect(resolver) { onDispose { resolver.close() } }
```

Le `ReadiumResourceResolver` est instancié par Hilt (`@ActivityScoped` ou `@ViewModelScoped`) et détruit avec le ViewModel. **Aucune** instance Readium ne survit au `ReaderScreen` — pas de fuite globale, pas de violation Clean Architecture (le domaine ne connaît ni Coil, ni Readium, ni Hilt).

**Ajouter Coil à `feature/reader` :**
- `feature/reader/build.gradle.kts` : `implementation(libs.coil.compose)`

**Commit** : `Ajoute EpubResourceFetcher Coil (Key-based, injecté via paramètre de composable, pas de singleton)`

#### 3.4 — Basculer `ReaderScreen` mode SCROLL sur `BookBlock`

`when (chapter.content) { is ChapterContent.Rich → LazyColumn(items = blocks) { BookBlockItem(it) }; is ChapterContent.Legacy → ancien chemin }`.

Supprimer `imagesByParagraph` (les images sont dans le flux naturel).

**Commit** : `Bascule ReaderScreen mode SCROLL sur le rendu BookBlock (fallback Legacy conservé)`

#### 3.5 — Adapter la pagination pour `BookBlock` avec MESURE PAR LOTS (batching)

`ChapterTextMeasurer` NE concatène PAS tous les blocs en un seul `AnnotatedString` géant — cela ferait crasher Compose sur les longs chapitres (dépassement de la taille maximale de texture de rendu GPU, typiquement 4096×4096 ou 8192×8192 pixels de hauteur de layout).

**Algorithme de mesure par lots (batching, v3)** :

1. **Découpage en lots** : les `ParagraphBlock` et `HeadingBlock` sont concaténés par lots de **10 000 caractères** maximum. Un lot = un `AnnotatedString` produit par `buildAnnotatedText(lots[i])`. Les frontières de lot tombent TOUJOURS entre deux blocs (jamais au milieu d'un bloc) pour ne pas fragmenter un `SpanStyle`.

2. **Mesure séquentielle** : chaque lot est mesuré indépendamment via `TextMeasurer.measure()`. Les `LineGeometry` de tous les lots sont accumulées dans une liste globale, avec `top` ajusté (décalage vertical cumulatif).

3. **`sentenceStartOffsets` globaux** : les offsets de début de phrase sont comptés dans l'espace du texte concaténé GLOBAL (somme des longueurs de tous les lots). Cet espace est purement logique — il n'existe pas sous forme d'`AnnotatedString` unique. `VirtualPaginationEngine` reçoit ces offsets globaux et les `LineGeometry` cumulées, et fonctionne exactement comme avant.

4. **Rendu d'une page** : quand `PagedChapterContent` demande la tranche `[pageStartOffset, pageEndOffset[`, on localise le(s) lot(s) concerné(s) par recherche dichotomique sur les offsets cumulés, on extrait la sous-chaîne de chaque lot, et on assemble l'`AnnotatedString` de la page — qui, elle, est de taille raisonnable (~taille d'un viewport).

**Contrat de l'API `ChapterTextMeasurer`** (inchangé pour l'appelant) :
```kotlin
fun measure(chapter: Chapter, baseStyle: TextStyle, maxWidthPx: Int): ChapterMeasurement
fun measureFirstPage(chapter: Chapter, baseStyle: TextStyle, maxWidthPx: Int, prefixCharBudget: Int = 6000): ChapterMeasurement
```

`ChapterMeasurement` est inchangé (`annotatedString`, `lines`, `sentenceStartOffsets`) — le batching est un détail d'implémentation interne, transparent pour `ChapterPaginationState` et `PagedChapterContent`.

**Fichier** : `feature/reader/.../pagination/ChapterTextMeasurer.kt`

**Commit** : `Adapte ChapterTextMeasurer pour le batching (lots de 10k chars) — évite les crashs de texture Compose`

#### 3.6 — Adapter `ReaderUiState` et `ReaderViewModel`

- `ReaderUiState` : `currentChapterContent: ChapterContent?` et `currentChapterBlocks: List<BookBlock>?` (dérivé)
- `ReaderViewModel.openPublication()` : appelle `chapterParser.parseChapter()` pour le chapitre courant
- Navigation, TTS, `persistPosition` : inchangés (utilisent `Sentence` + `Locator`)
- **Nouveau (v3)** : `ReaderViewModel` utilise `Sentence.blockIndex` + `ParagraphBlock.globalOffsetRange` pour convertir un offset TTS global → bloc de rendu local

**Commit** : `Intègre ChapterParser dans ReaderViewModel avec pont TTS↔UI par blockIndex/globalOffsetRange`

---

### Palier 4 — Optimisations : Préchargement annulable et performances

*Dépend du Palier 3.*

#### 4.1 — Préchargement asynchrone DES chapitres adjacents avec ANNULATION EXPLICITE

Dans `ReaderViewModel` :

```kotlin
private var preloadJob: Job? = null

private fun onChapterChanged(newChapterIndex: Int) {
    // 1. ANNULER tous les préchargements en cours — l'utilisateur a changé
    //    de chapitre, les anciens préchargements sont obsolètes.
    preloadJob?.cancel()
    preloadJob = null

    // 2. Lancer les nouveaux préchargements pour N-1 et N+1
    preloadJob = viewModelScope.launch {
        val jobPrev = chapterParser.preload(publicationId, chapterHref(newChapterIndex - 1), this)
        val jobNext = chapterParser.preload(publicationId, chapterHref(newChapterIndex + 1), this)
        // Attendre les deux (non bloquant — les préchargements sont asynchrones)
        joinAll(jobPrev, jobNext)
    }
}
```

**Contrat d'annulation** : quand `preloadJob.cancel()` est appelé, le `CoroutineScope` passé à `preload()` est annulé, ce qui annule récursivement tous les `Job` lancés par les `preload()` enfants. Le `Semaphore(2)` interne d'`EpubChapterParser` est libéré automatiquement (annulation → finally { semaphore.release() }).

**Garantie anti-starvation** : si l'utilisateur zappe 5 chapitres en 2 secondes, seuls les préchargements du chapitre final survivent. Les 4 précédents sont annulés avant même d'avoir acquis le `Semaphore`. Le thread `epub-parser` n'est jamais saturé par des préchargements zombies.

**Commit** : `Ajoute le préchargement asynchrone annulable des chapitres adjacents (anti-starvation)`

#### 4.2 — Vérification device + bench

Protocole device InkTone (Snapdragon 680 V2206) :
- EPUB complexe (images + formatage) : ouverture < 500ms, défilement > 55 FPS
- Gras/italique/liens visibles et corrects
- Images réelles (pas de placeholder), pas de layout shift
- TTS + surlignage mot-à-mot fonctionnel (vérifier le pont `Sentence.blockIndex` → `globalOffsetRange`)
- Fragments TOC (`#section`) : navigation exacte (l'en-tête du document parent absent)
- Recherche FTS fonctionnelle
- Signets/annotations préservés
- TalkBack annonce correctement les titres/paragraphes/images
- **Nouveau (v3)** : zapping rapide de 10 chapitres → pas de ralentissement, pas de crash OOM
- **Nouveau (v3)** : chapitre de >100 000 caractères → pas de crash de texture, pagination fluide

**Commit** : N/A (vérification manuelle, rapport dans `docs/device-verification/pipeline-ast-v3.md`)

---

### Palier 5 — Nettoyage : Suppression de l'ancien modèle

*Dépend du Palier 4. Plus rien ne référence l'ancien modèle.*

#### 5.1 — Supprimer `ChapterContent.Legacy` et les types associés

- Supprimer `Paragraph`, `ParagraphStyle`, `StructuralBlock`, `EpubImage`, `SectionBreak`
- `ChapterContent` devient `data class RichContent(val blocks: List<BookBlock>)` directement dans `Chapter`
- `Chapter.blocks` devient obligatoire

**Commit** : `Supprime l'ancien modèle Legacy (Paragraph, ParagraphStyle, StructuralBlock)`

#### 5.2 — Nettoyer les consommateurs

- `ReaderScreen.kt` : supprimer le `when`, ne garder que le chemin `Rich`
- `ParagraphTextStyle.kt` → **supprimer**
- `DocumentModelExtractor.kt` → **supprimer**
- `AnnotationSelectionHandler.kt` → simplifier
- `RoomSearchService.kt` → nettoyer l'ancienne surcharge
- `ReadiumPublicationParser.kt` → supprimer l'instanciation de `DocumentModelExtractor`

**Commit** : `Nettoie les références à l'ancien modèle de document`

#### 5.3 — Migrer les ~15 fichiers de test

Utiliser le DSL `BookBlockFixtures` pour des fixtures lisibles. Chaque test qui construisait `Paragraph(…)` devient `p(…)` ou `chap(…)`.

**Commit** : `Migre tous les tests vers le nouveau modèle BookBlock + DSL`

---

## Fichiers Clés — Résumé

| Fichier | Palier | Action |
|---|---|---|
| `domain/.../model/BookBlock.kt` | 1.1 | **Créer** — sealed class avec `globalOffsetRange` |
| `domain/.../model/StyledText.kt` | 1.1 | **Créer** — data class |
| `domain/.../model/Span.kt` | 1.1 | **Créer** — data class |
| `domain/.../model/SpanStyles.kt` | 1.1 | **Créer** — inline value class bitmask |
| `domain/.../model/DocumentModel.kt` | 1.1, 5.1 | **Modifier** — ChapterContent sealed + `Sentence.blockIndex` (P1), supprimer Legacy (P5) |
| `domain/.../service/ChapterParser.kt` | 1.4 | **Créer** — interface avec `preload()` → `Job` |
| `domain/.../service/EpubResourceResolver.kt` | 3.3 | **Créer** — interface avec `close()` |
| `core/testing/.../fixture/BookBlockFixtures.kt` | 1.5 | **Créer** — DSL de test (calcule `globalOffsetRange` automatiquement) |
| `gradle/libs.versions.toml` | 1.2 | **Modifier** — ajouter Jsoup |
| `infrastructure/parser/.../JsoupChapterParser.kt` | 1.3 | **Créer** — parseur DOM + normalisation spans + extraction fragment + `globalOffsetRange` + `blockIndex` |
| `infrastructure/parser/.../EpubChapterParser.kt` | 2.1 | **Créer** — cache LRU (5 MB) + dispatcher + `preload()` annulable |
| `infrastructure/parser/.../ReadiumResourceResolver.kt` | 3.3 | **Créer** — implémentation EpubResourceResolver scopée |
| `infrastructure/parser/.../ReadiumPublicationParser.kt` | 2.3, 5.2 | **Modifier** — parseLazy(), supprimer DocumentModelExtractor |
| `infrastructure/parser/.../DocumentModelExtractor.kt` | 5.2 | **Supprimer** |
| `infrastructure/parser/.../di/ParserModule.kt` | 2.2 | **Modifier** — binder ChapterParser + EpubResourceResolver |
| `infrastructure/database/.../RoomSearchService.kt` | 2.4, 5.2 | **Modifier** — adapter indexation |
| `feature/reader/.../rendering/BookBlockStyleMapper.kt` | 3.1 | **Créer** — sémantique → visuel |
| `feature/reader/.../rendering/BookBlockItem.kt` | 3.2 | **Créer** — composable + accessibilité + pont TTS↔UI O(log n) |
| `feature/reader/.../rendering/EpubResourceFetcher.kt` | 3.3 | **Créer** — Key-based Coil fetcher (reçoit le resolver en paramètre) |
| `feature/reader/.../pagination/ParagraphTextStyle.kt` | 5.2 | **Supprimer** |
| `feature/reader/.../ReaderScreen.kt` | 3.4, 5.2 | **Modifier** — when(content) → BookBlockItem, ImageLoader scopé, nettoyer legacy |
| `feature/reader/.../pagination/ChapterTextMeasurer.kt` | 3.5, 5.2 | **Modifier** — batching (lots de 10k chars), `globalOffsetRange` pour les offsets |
| `feature/reader/.../ReaderUiState.kt` | 3.6 | **Modifier** — ChapterContent + EpubResourceResolver |
| `feature/reader/.../ReaderViewModel.kt` | 3.6, 4.1 | **Modifier** — ChapterParser, pont TTS↔UI, `preloadJob?.cancel()` |
| `feature/reader/build.gradle.kts` | 3.3 | **Modifier** — ajouter coil-compose |
| `docs/spikes/sentence-tokenizer-comparison.md` | 0.1 | **Créer** — rapport de spike |
| ~15 fichiers de test | 5.3 | **Modifier** — migrer vers DSL BookBlockFixtures |

---

## Vérification

1. **`./gradlew :domain:test`** — invariants `StyledText`/`SpanStyles`/`BookBlock`/`Sentence.blockIndex` OK, recherche dichotomique OK
2. **`./gradlew :infrastructure:parser:test`** — tests Jsoup en JVM pur OK (y compris fragmentation et blockIndex)
3. **`./gradlew :infrastructure:parser:connectedCheck`** — tests Readium intégrés OK
4. **`./gradlew :feature:reader:test`** — tests unitaires reader avec DSL OK
5. **`./gradlew :feature:reader:connectedCheck`** — tests instrumentés reader OK, batching pagination vérifié
6. **`./gradlew build`** — build complet vert avant chaque merge de palier
7. **`./gradlew :app:checkArchitectureRules`** — domaine sans dépendance Android/Jsoup/Readium/Coil/Hilt
8. **Device Snapdragon 680** — protocole complet :
   - Ouverture < 500ms, défilement > 55 FPS
   - Pas de crash de texture sur chapitre de >100 000 caractères
   - TTS + surlignage mot-à-mot correct (pont `blockIndex`/`binarySearch` vérifié)
   - Zapping rapide 10 chapitres → pas de ralentissement, pas d'OOM
   - Fragments TOC (`#section`) : navigation exacte, pas d'en-tête parasite
   - TalkBack annonce correctement
9. **`bash scripts/check-no-emoji.sh`** — K12
10. **`bash scripts/check-no-manage-external-storage.sh`** — K5

---

## Exclusions conscientes

- **Liens cliquables** : `Span(REFERENCE)` affiché en bleu souligné, pas de navigation au clic (v2)
- **Tableaux** : aplanis en `ParagraphBlock` (v2)
- **Images en mode PAGED** : ignorées dans `HorizontalPager` (écart assumé, déjà présent)
- **Polices embarquées** : non chargées (v2)
- **MathML/SVG** : ignorés
- **PDF/TXT** : adaptés pour produire `BookBlock` mais pas de parsing Jsoup (pas de HTML)
- **Cache disque** : cache mémoire uniquement
- **Sélection inter-bloc** : documentée comme limite connue, piste v2 tracée

---

## Questions résolues

1. ~~`FrenchSentenceSplitter` vs `TextContentTokenizer`~~ → **Spike Palier 0** obligatoire avant adoption. Critère : ≤ 2 chars d'écart sur 95% des phrases.

2. ~~Performance Jsoup sur gros chapitres~~ → Dispatcher dédié `epub-parser` (2 threads) + `preload()` annulable. Si >500ms, parsing « first page » comme `measureFirstPage()`.

3. ~~Coil et cycle de vie `Publication`~~ → `EpubImageKey` (pas d'URI scheme). `EpubResourceResolver` injecté via Hilt → ViewModel → paramètre `ReaderScreen` → `remember ImageLoader`. `DisposableEffect` ferme le resolver. Aucun singleton, aucune fuite.

4. ~~Spans imbriqués~~ → Bitmask `SpanStyles` + normalisation par split aux frontières dans `JsoupChapterParser`. Test critique de `<b><i>texte</i></b>`.

5. ~~Sélection inter-bloc~~ → Documenté comme limite connue (identique à l'actuel). Piste v2 : `BasicTextField` unique avec `Placeholder` dans l'`AnnotatedString`.

6. ~~Cache LRU en entrées~~ → `LruCache` par octets (5 MB), `safeSizeOf { _, value -> value.approxByteSize }`.

7. ~~`Dispatchers.Default` partagé~~ → Dispatcher dédié `epub-parser` (2 threads) + `preload()` → `Job` annulable. Anti-starvation : `preloadJob?.cancel()` au changement de chapitre.

8. ~~Layout shifts images~~ → `ImageBlock(intrinsicWidth, intrinsicHeight)` extraits du `<img>`.

9. ~~Accessibilité absente~~ → `semantics { heading() }` / `contentDescription` / `invisibleToUser()` par `BookBlockItem`.

10. ~~Champs parallèles `blocks` + `paragraphs`~~ → `ChapterContent` sealed wrapper, transition compiler-checked.

11. ~~Nommage rendu dans le domaine~~ → `STRONG`/`EMPHASIS`/`INSERTED`/`DELETED`/`REFERENCE`. Mapping visuel UNIQUEMENT dans `BookBlockStyleMapper`.

12. ~~Verbosité des fixtures~~ → DSL `BookBlockFixtures.kt` avec `chap()`, `p()`, `h()`, `img()`, `withStyle`, constantes `B`, `I`, `U`, `S`.

13. ~~Désynchronisation offsets TTS vs UI~~ → `Sentence.blockIndex` + `ParagraphBlock.globalOffsetRange` + recherche dichotomique O(log n) documentée dans `BookBlockItem.kt`.

14. ~~Crash TextMeasurer sur longs chapitres~~ → Mesure par lots (batching) de 10 000 caractères. `ChapterTextMeasurer` découpe en `AnnotatedString` multiples, accumule les `LineGeometry`. Transparent pour l'appelant.

15. ~~Préchargements zombies (starvation)~~ → `ChapterParser.preload()` retourne `Job`. `ReaderViewModel` conserve `var preloadJob: Job?` et appelle `.cancel()` au changement de chapitre. `Semaphore` libéré automatiquement.

16. ~~Illusion du fragment Jsoup~~ → Documenté : Jsoup charge TOUT le XHTML en mémoire (coût I/O complet). L'optimisation porte sur l'extraction sélective de l'AST : seuls les nœuds DOM à partir du fragment sont convertis en `BookBlock`.

17. ~~Coil DI hors-scope (singleton Readium)~~ → `EpubResourceResolver` injecté via Hilt → ViewModel → paramètre explicite `ReaderScreen`. `ImageLoader` dans `remember`, fermé dans `DisposableEffect`. Aucune instance Readium globale.
