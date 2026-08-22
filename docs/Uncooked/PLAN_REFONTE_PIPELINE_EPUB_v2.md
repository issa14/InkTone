## Plan v2: Refonte Pipeline EPUB — Modèle AST + Parsing Jsoup + Rendu Riche

**TL;DR** — Remplacer l'extraction de texte brut par un modèle de document structuré (`BookBlock` sealed + `StyledText` avec `SpanStyles` bitmask) pour afficher les EPUB avec leur formatage réel (gras, italique, images, liens) et corriger le bug des fragments (`#prologue`). Readium = gestionnaire d'archives uniquement (ZIP + manifest + flux). Jsoup = parseur DOM avec normalisation des spans imbriqués. Parsing paresseux (un chapitre à la fois, dispatcher dédié). Refonte en 5 paliers additifs — jamais de big bang, build vert à chaque commit. **Ce plan incorpore les 12 corrections de l'audit Google Senior Android Dev du 2026-08-12.**

**Portée** : EPUB uniquement. TXT et PDF conservent leur pipeline, adaptés pour produire `BookBlock` (compatibilité transparente).

---

## État Actuel vs Cible

| Dimension | Actuel | Cible |
|---|---|---|
| **Modèle** | `Chapter → Paragraph → Sentence(String)` + `structuralBlocks` ancrés par index | `Chapter(ChapterContent.Rich(blocks: List<BookBlock>), sentences: List<Sentence>)` |
| **Texte inline** | `String` brut — formatage perdu | `StyledText(plainText, spans: List<Span(SpanStyles bitmask, start, end))` |
| **Styles imbriqués** | ❌ Impossible | ✅ `SpanStyles` bitmask — `<b><i>texte</i></b>` → `Span(BOLD|ITALIC, …)` après normalisation |
| **Styles bloc** | `ParagraphStyle` enum | `BookBlock` sealed : `ParagraphBlock`, `HeadingBlock(level)`, `ImageBlock`, `SeparatorBlock` |
| **Images** | `StructuralBlock.EpubImage` → placeholder gris | `ImageBlock(href, alt, intrinsicWidth?, intrinsicHeight?)` → `AsyncImage` Coil via `EpubImageKey` |
| **Parseur** | `TextContentTokenizer` Readium | Jsoup (DOM) avec normalisation des spans |
| **Tokenisation** | `TextContentTokenizer(Language("fr"))` | `FrenchSentenceSplitter` (BreakIterator, déjà dans le projet) — spike de compatibilité obligatoire avant adoption |
| **Granularité** | Tout le livre parsé à l'import | Parsing paresseux par chapitre à l'ouverture, préchargement en arrière-plan |
| **Threading** | `Dispatchers.IO` (partagé) | Dispatcher dédié `epub-parser` (2 threads) + `Semaphore(2)` |
| **Cache** | Aucun | `LruCache` par **octets** (5 MB), pas par entrées |
| **Fragments** | Bug `#prologue` (tout le fichier) | Jsoup résout l'ancre DOM → extraction exacte |
| **Accessibilité** | Non traitée | `semantics { heading() }` / `contentDescription` / `invisibleToUser()` par type de bloc |

---

## Décisions d'Architecture

### D1 — `Sentence` survit, aux côtés de `BookBlock`

Le TTS, la recherche FTS, la navigation et les signets dépendent de `Sentence` (texte + offsets). `Sentence` n'est pas supprimé. `Chapter` contient `content: ChapterContent` (pour le rendu) ET `sentences: List<Sentence>` (pour le TTS). Les deux sont produits en une seule passe de parsing. `Sentence.text` est le `StyledText.plainText` concaténé de tous les `ParagraphBlock` du chapitre, tokenisé en phrases. Les offsets sont comptés dans ce texte concaténé.

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

### D5 — `EpubImageKey` Coil (pas d'URI scheme custom)

Pour le chargement d'images, on utilise une `Key` Coil plutôt qu'un schéma d'URI custom (fragile) :

```kotlin
data class EpubImageKey(
    val publicationId: String,
    val resourceHref: String,
) : Key {
    override val cacheKey: String get() = "$publicationId:$resourceHref"
}
```

L'`ImageLoader` est instancié dans `ReaderScreen` (`remember`) avec un `Fetcher.Factory` dédié — pas de pollution globale. Le `Fetcher` résout via `EpubResourceResolver` (interface domaine → implémentation Readium).

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

### D8 — Les offsets caractère restent la source de vérité

`Locator(chapterIndex, charOffset)` inchangé. Les `Span` sont purement décoratifs et n'affectent jamais les offsets. TTS, signets et annotations fonctionnent sur les offsets du `StyledText.plainText` concaténé.

### D9 — Dispatcher dédié pour le parsing

```kotlin
private val parserDispatcher = newFixedThreadPoolContext(2, "epub-parser")
private val parseSemaphore = Semaphore(2)
```

Évite la contention avec `Dispatchers.Default` (utilisé par Compose pour la mesure/layout). La lecture d'`InputStream` depuis le ZIP reste sur `Dispatchers.IO`.

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

### Palier 0 — Spike bloquant : Compatibilité des tokenizers

*Dépend de rien. Bloque le Palier 1.3. Ne produit pas de code de production.*

#### 0.1 — Comparer `FrenchSentenceSplitter` vs `TextContentTokenizer` Readium

Prendre un EPUB réel (Les Misérables Tome I, fixture existante), extraire les phrases avec les deux tokenizers, comparer offset par offset.

**Livrable** : `docs/spikes/sentence-tokenizer-comparison.md` contenant :
- Tableau comparatif : nombre de phrases, offsets de début/fin pour les 50 premières phrases
- Cas de divergence (abréviations, ponctuation, tirets) avec explication
- Décision : utiliser `FrenchSentenceSplitter` comme source unique, ou conserver `TextContentTokenizer` pour la tokenisation
- Si divergence > 0 et `FrenchSentenceSplitter` retenu : liste des corrections à appliquer au `FrenchSentenceSplitter`

**Critère de succès** : divergence ≤ 2 caractères par phrase sur 95% des phrases.

---

### Palier 1 — Fondation : Nouveau modèle domaine + Nouveau parseur Jsoup

*Dépend du Palier 0. Aucun consommateur migré. Build vert.*

#### 1.1 — Ajouter les nouveaux types au domaine (naming sémantique)

**Fichiers à créer :**
- `domain/src/main/kotlin/com/inktone/domain/model/BookBlock.kt`
  ```kotlin
  sealed class BookBlock {
      abstract val approxByteSize: Int
      data class ParagraphBlock(val richText: StyledText) : BookBlock()
      data class HeadingBlock(val level: Int, val richText: StyledText) : BookBlock()
      data class ImageBlock(val href: String, val alt: String?, val intrinsicWidth: Int? = null, val intrinsicHeight: Int? = null) : BookBlock()
      data class SeparatorBlock : BookBlock()
  }
  ```
- `domain/src/main/kotlin/com/inktone/domain/model/StyledText.kt`
  ```kotlin
  data class StyledText(val plainText: String, val spans: List<Span>) {
      init {
          require(plainText.isNotEmpty() || spans.isEmpty())
          require(spans.all { it.start >= 0 && it.end <= plainText.length && it.end > it.start })
          // Vérifier que les spans ne se chevauchent PAS (normalisés par JsoupChapterParser)
          // Vérifier que les spans sont triés par start ASC
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
  - `Chapter.paragraphs` et `structuralBlocks` restent mais dépréciés (accédés via `content` quand c'est `Legacy`)

**Tests :**
- `domain/src/test/kotlin/com/inktone/domain/model/StyledTextTest.kt` — invariants, fusion de spans
- `domain/src/test/kotlin/com/inktone/domain/model/SpanStylesTest.kt` — opérateurs bitmask, combinaisons
- `domain/src/test/kotlin/com/inktone/domain/model/BookBlockTest.kt` — approxByteSize

**Commit** : `Ajoute StyledText, SpanStyles, Span et BookBlock au modèle domaine (naming sémantique)`

#### 1.2 — Ajouter la dépendance Jsoup

- `gradle/libs.versions.toml` : `jsoup = "1.18.1"`, `jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }`
- `infrastructure/parser/build.gradle.kts` : `implementation(libs.jsoup)`

**Commit** : `Ajoute la dépendance Jsoup 1.18.1`

#### 1.3 — Créer le parseur Jsoup avec normalisation des spans

**Fichier à créer :**
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/JsoupChapterParser.kt`

**Algorithme de normalisation des spans** (documenté dans le KDoc) :
1. Collecter tous les spans bruts depuis l'arbre DOM : `List<RawSpan(styles, start, end)>`
2. Collecter tous les points de transition : `Set<Int>` = toutes les valeurs de `start` et `end`
3. Trier les points de transition
4. Pour chaque segment `[transition[i], transition[i+1])`, calculer le masque de styles actif = OR de tous les `RawSpan` qui couvrent ce segment
5. Émettre `Span(styles, transition[i], transition[i+1])` si `styles != NONE`

**Méthodes :**
- `parse(inputStream: InputStream, baseUrl: String, fragment: String? = null): ChapterData`
- `extractRichText(node: Node): StyledText` — récursif, accumule texte + spans bruts → normalisation

**Tests (JVM pur, pas androidTest — Jsoup n'a pas besoin d'Android) :**
- `infrastructure/parser/src/test/kotlin/com/inktone/infrastructure/parser/JsoupChapterParserTest.kt`
  - Test 1 : `<p>Le <b>Petit</b> <i>Prince</i></p>` → `StyledText("Le Petit Prince", [Span(STRONG,3,8), Span(EMPHASIS,9,15)])`
  - Test 2 : `<b>bold <i>bold-italic</i></b>` → `[Span(STRONG,0,5), Span(STRONG|EMPHASIS,5,15)]` ← **test critique de normalisation**
  - Test 3 : `<h1>Titre</h1><p>Texte</p>` → `[HeadingBlock(1,…), ParagraphBlock(…)]`
  - Test 4 : `<img src="foo.png" alt="Illustration" width="200" height="100"/>` → `ImageBlock(href, "Illustration", 200, 100)`
  - Test 5 : fragment `#prologue` → extraction partielle correcte
  - Test 6 : `<blockquote>Citation</blockquote>` → `ParagraphBlock`
  - Test 7 : `<sup>haut</sup>` / `<sub>bas</sub>` → `Span(SUPERSCRIPT)` / `Span(SUBSCRIPT)`
  - Test 8 : `<a href="ch2.xhtml">lien</a>` → `Span(REFERENCE, href="ch2.xhtml")`
  - Test 9 : EPUB réel (fixture) → cohérence offsets, aucun span hors bornes
  - Test 10 : `<p><b>A</b><i>B</i><u>C</u></p>` → 3 spans adjacents, pas de chevauchement

**Commit** : `Ajoute JsoupChapterParser avec normalisation des spans imbriqués (bitmask)`

#### 1.4 — Créer l'interface `ChapterParser` dans le domaine

- `domain/src/main/kotlin/com/inktone/domain/service/ChapterParser.kt`

```kotlin
interface ChapterParser {
    suspend fun parseChapter(publicationId: String, chapterHref: String, fragment: String? = null): ChapterData
    fun preload(publicationId: String, chapterHref: String)
    fun invalidate(publicationId: String)
}
```

**Commit** : `Ajoute l'interface ChapterParser au domaine`

#### 1.5 — Créer le DSL de test

- `core/testing/src/main/kotlin/com/inktone/core/testing/fixture/BookBlockFixtures.kt`

Fonctions `chap()`, `p()`, `h()`, `img()`, `styledText()`, `s()`, extension `withStyle`, constantes `B`, `I`, `U`, `S`.

**Commit** : `Ajoute BookBlockFixtures DSL pour les tests`

---

### Palier 2 — Intégration : Wiring du nouveau parseur

*Dépend du Palier 1. L'ancien pipeline continue de fonctionner.*

#### 2.1 — Implémenter `EpubChapterParser` avec cache par octets et dispatcher dédié

**Fichier à créer :**
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/EpubChapterParser.kt`

Combine `ReadiumPublicationParser` (accès aux ressources) et `JsoupChapterParser` (parsing DOM).
- Cache `LruCache<String, ChapterData>` avec `maxSize = 5 * 1024 * 1024` (5 MB), `safeSizeOf { _, value -> value.approxByteSize }`
- Dispatcher dédié `newFixedThreadPoolContext(2, "epub-parser")` + `Semaphore(2)`
- Implémente `ChapterParser`

**Commit** : `Ajoute EpubChapterParser avec cache LRU (5 MB) et dispatcher dédié`

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

#### 3.2 — Créer `BookBlockItem` (avec accessibilité)

**Fichier à créer :**
- `feature/reader/src/main/kotlin/com/inktone/feature/reader/rendering/BookBlockItem.kt`

Rend un `BookBlock` :
- `ParagraphBlock` → `BasicTextField` avec sélection libre (réutilise `ParagraphTextMapping`, adapté)
- `HeadingBlock` → `Text` + `Modifier.semantics { heading() }`
- `ImageBlock` → `AsyncImage(model = EpubImageKey(…), contentDescription = alt)` avec placeholder dimensionné
- `SeparatorBlock` → `HorizontalDivider` + `Modifier.semantics { invisibleToUser() }`

**Commit** : `Ajoute BookBlockItem avec rendu par type de bloc et accessibilité TalkBack`

#### 3.3 — Créer `EpubResourceFetcher` Coil (basé sur `Key`, pas sur URI scheme)

**Fichiers à créer :**
- `domain/src/main/kotlin/com/inktone/domain/service/EpubResourceResolver.kt` — interface
- `infrastructure/parser/src/main/kotlin/com/inktone/infrastructure/parser/ReadiumResourceResolver.kt` — implémentation via Readium
- `feature/reader/src/main/kotlin/com/inktone/feature/reader/rendering/EpubResourceFetcher.kt` — `Fetcher` + `Factory` Coil basé sur `EpubImageKey`

**Ajouter Coil à `feature/reader` :**
- `feature/reader/build.gradle.kts` : `implementation(libs.coil.compose)`

**Commit** : `Ajoute EpubResourceFetcher Coil (Key-based, pas d'URI scheme custom)`

#### 3.4 — Basculer `ReaderScreen` mode SCROLL sur `BookBlock`

`when (chapter.content) { is ChapterContent.Rich → LazyColumn(items = blocks) { BookBlockItem(it) }; is ChapterContent.Legacy → ancien chemin }`.

Supprimer `imagesByParagraph` (les images sont dans le flux naturel).

**Commit** : `Bascule ReaderScreen mode SCROLL sur le rendu BookBlock (fallback Legacy conservé)`

#### 3.5 — Adapter la pagination pour `BookBlock`

`ChapterTextMeasurer.buildAnnotatedText()` concatène tous les `ParagraphBlock.richText` et `HeadingBlock.richText` en UN SEUL `AnnotatedString` (séparés par `\n`), avec conversion des `SpanStyles` → `SpanStyle`. Les `sentenceStartOffsets` sont comptés dans CET `AnnotatedString` global. `ImageBlock`/`SeparatorBlock` sont ignorés (pas de texte).

**Fichier** : `feature/reader/.../pagination/ChapterTextMeasurer.kt`

**Commit** : `Adapte ChapterTextMeasurer pour le rendu paginé depuis BookBlock`

#### 3.6 — Adapter `ReaderUiState` et `ReaderViewModel`

- `ReaderUiState` : `currentChapterContent: ChapterContent?` et `currentChapterBlocks: List<BookBlock>?` (dérivé)
- `ReaderViewModel.openPublication()` : appelle `chapterParser.parseChapter()` pour le chapitre courant
- Navigation, TTS, `persistPosition` : inchangés (utilisent `Sentence` + `Locator`)

**Commit** : `Intègre ChapterParser dans ReaderViewModel pour les chapitres AST`

---

### Palier 4 — Optimisations : Préchargement et performances

*Dépend du Palier 3.*

#### 4.1 — Préchargement asynchrone des chapitres adjacents

Dans `ReaderViewModel.openPublication()`, après affichage du chapitre courant, lancer `chapterParser.preload()` pour N-1 et N+1 sur le dispatcher dédié `epub-parser`.

**Commit** : `Ajoute le préchargement asynchrone des chapitres adjacents`

#### 4.2 — Vérification device + bench

Protocole device InkTone (Snapdragon 680 V2206) :
- EPUB complexe (images + formatage) : ouverture < 500ms, défilement > 55 FPS
- Gras/italique/liens visibles et corrects
- Images réelles (pas de placeholder), pas de layout shift
- TTS + surlignage mot-à-mot fonctionnel
- Fragments TOC (`#section`) : navigation exacte
- Recherche FTS fonctionnelle
- Signets/annotations préservés
- TalkBack annonce correctement les titres/paragraphes/images

**Commit** : N/A (vérification manuelle, rapport dans `docs/device-verification/pipeline-ast.md`)

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
| `domain/.../model/BookBlock.kt` | 1.1 | **Créer** — sealed class |
| `domain/.../model/StyledText.kt` | 1.1 | **Créer** — data class |
| `domain/.../model/Span.kt` | 1.1 | **Créer** — data class |
| `domain/.../model/SpanStyles.kt` | 1.1 | **Créer** — inline value class bitmask |
| `domain/.../model/DocumentModel.kt` | 1.1, 5.1 | **Modifier** — ChapterContent sealed (P1), supprimer Legacy (P5) |
| `domain/.../service/ChapterParser.kt` | 1.4 | **Créer** — interface |
| `domain/.../service/EpubResourceResolver.kt` | 3.3 | **Créer** — interface |
| `core/testing/.../fixture/BookBlockFixtures.kt` | 1.5 | **Créer** — DSL de test |
| `gradle/libs.versions.toml` | 1.2 | **Modifier** — ajouter Jsoup |
| `infrastructure/parser/.../JsoupChapterParser.kt` | 1.3 | **Créer** — parseur DOM + normalisation spans |
| `infrastructure/parser/.../EpubChapterParser.kt` | 2.1 | **Créer** — cache LRU (5 MB) + dispatcher dédié |
| `infrastructure/parser/.../ReadiumResourceResolver.kt` | 3.3 | **Créer** — implémentation EpubResourceResolver |
| `infrastructure/parser/.../ReadiumPublicationParser.kt` | 2.3, 5.2 | **Modifier** — parseLazy(), supprimer DocumentModelExtractor |
| `infrastructure/parser/.../DocumentModelExtractor.kt` | 5.2 | **Supprimer** |
| `infrastructure/parser/.../di/ParserModule.kt` | 2.2 | **Modifier** — binder ChapterParser |
| `infrastructure/database/.../RoomSearchService.kt` | 2.4, 5.2 | **Modifier** — adapter indexation |
| `feature/reader/.../rendering/BookBlockStyleMapper.kt` | 3.1 | **Créer** — sémantique → visuel |
| `feature/reader/.../rendering/BookBlockItem.kt` | 3.2 | **Créer** — composable avec accessibilité |
| `feature/reader/.../rendering/EpubResourceFetcher.kt` | 3.3 | **Créer** — Key-based Coil fetcher |
| `feature/reader/.../pagination/ParagraphTextStyle.kt` | 5.2 | **Supprimer** |
| `feature/reader/.../ReaderScreen.kt` | 3.4, 5.2 | **Modifier** — when(content) → BookBlockItem, nettoyer legacy |
| `feature/reader/.../pagination/ChapterTextMeasurer.kt` | 3.5, 5.2 | **Modifier** — concaténer BookBlock → AnnotatedString |
| `feature/reader/.../ReaderUiState.kt` | 3.6 | **Modifier** — ChapterContent |
| `feature/reader/.../ReaderViewModel.kt` | 3.6, 4.1 | **Modifier** — ChapterParser, préchargement |
| `feature/reader/build.gradle.kts` | 3.3 | **Modifier** — ajouter coil-compose |
| `docs/spikes/sentence-tokenizer-comparison.md` | 0.1 | **Créer** — rapport de spike |
| ~15 fichiers de test | 5.3 | **Modifier** — migrer vers DSL BookBlockFixtures |

---

## Vérification

1. **`./gradlew :domain:test`** — invariants `StyledText`/`SpanStyles`/`BookBlock` OK
2. **`./gradlew :infrastructure:parser:test`** — tests Jsoup en JVM pur OK
3. **`./gradlew :infrastructure:parser:connectedCheck`** — tests Readium intégrés OK
4. **`./gradlew :feature:reader:test`** — tests unitaires reader avec DSL OK
5. **`./gradlew :feature:reader:connectedCheck`** — tests instrumentés reader OK
6. **`./gradlew build`** — build complet vert avant chaque merge de palier
7. **`./gradlew :app:checkArchitectureRules`** — domaine sans dépendance Android/Jsoup/Readium
8. **Device Snapdragon 680** — protocole complet (ouverture < 500ms, pas de layout shift, TTS OK, fragments OK, TalkBack OK)
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

## Questions résolues par l'audit

1. ~~`FrenchSentenceSplitter` vs `TextContentTokenizer`~~ → **Spike Palier 0** obligatoire avant adoption. Critère : ≤ 2 chars d'écart sur 95% des phrases.

2. ~~Performance Jsoup sur gros chapitres~~ → Dispatcher dédié `epub-parser` (2 threads) + `Semaphore(2)`. Si >500ms, parsing « first page » comme `measureFirstPage()`.

3. ~~Coil et cycle de vie `Publication`~~ → `EpubImageKey` (pas d'URI scheme). `ImageLoader` scopé à `ReaderScreen` (`remember`). Fermeture dans `DisposableEffect` → pas de fuite.

4. ~~Spans imbriqués~~ → Bitmask `SpanStyles` + normalisation par split aux frontières dans `JsoupChapterParser`. Test critique de `<b><i>texte</i></b>`.

5. ~~Sélection inter-bloc~~ → Documenté comme limite connue (identique à l'actuel). Piste v2 : `BasicTextField` unique avec `Placeholder` dans l'`AnnotatedString`.

6. ~~Cache LRU en entrées~~ → `LruCache` par octets (5 MB), `safeSizeOf { _, value -> value.approxByteSize }`.

7. ~~`Dispatchers.Default` partagé~~ → Dispatcher dédié `epub-parser` (2 threads).

8. ~~Layout shifts images~~ → `ImageBlock(intrinsicWidth, intrinsicHeight)` extraits du `<img>`.

9. ~~Accessibilité absente~~ → `semantics { heading() }` / `contentDescription` / `invisibleToUser()` par `BookBlockItem`.

10. ~~Champs parallèles `blocks` + `paragraphs`~~ → `ChapterContent` sealed wrapper, transition compiler-checked.

11. ~~Nommage rendu dans le domaine~~ → `STRONG`/`EMPHASIS`/`INSERTED`/`DELETED`/`REFERENCE`. Mapping visuel UNIQUEMENT dans `BookBlockStyleMapper`.

12. ~~Verbosité des fixtures~~ → DSL `BookBlockFixtures.kt` avec `chap()`, `p()`, `h()`, `img()`, `withStyle`, constantes `B`, `I`, `U`, `S`.
