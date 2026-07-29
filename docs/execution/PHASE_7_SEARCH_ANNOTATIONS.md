# Phase 7 — Annotations, signets, recherche

**Dépend de :** Phase 4 (Reader complet), Phase 6 (bibliothèque fonctionnelle)
**Précède :** Phase 8 — Réglages, statistiques, onboarding
**Référence :** Blueprint InkTone v1.2.2, §6.9 (FTS), §7.6 (navigation)

## Ce qui existe déjà, à ne pas reconstruire

| Besoin | Déjà présent | Depuis |
|---|---|---|
| CRUD Annotation (repository + Use Cases) | `AnnotationRepository`, `AddAnnotationUseCase`, `UpdateAnnotationUseCase`, `DeleteAnnotationUseCase` | Tâches 1.6, 1.8, 2.6 |
| CRUD Bookmark | `BookmarkRepository`, `CreateBookmarkUseCase`, `DeleteBookmarkUseCase` | Tâches 1.6, 1.8, 2.6 |
| Validation de plage (`endLocator >= startLocator`) | `Annotation.init` | Tâche 1.4 |
| `SearchService`/`SearchPublicationUseCase` (signature) | Interface + `TODO()` | Tâche 1.7/1.8.2 |

Ce qui manque : **toute l'UI** (sélection de texte, signets, recherche) et **l'implémentation FTS** (jamais construite malgré la table prévue au Blueprint §6.9 depuis la Phase 2).

---

## Tâche 7.0 — Vérification préalable : sélection de texte Compose et rendu virtualisé

**Pourquoi avant tout code :** la documentation officielle de `SelectionContainer` contient un avertissement direct et rarement lu : *« l'usage d'un layout paresseux (LazyColumn) dans un SelectionContainer a un comportement non défini sur les éléments texte qui ne sont pas composés — les textes non composés ne seront pas inclus dans les opérations de copie, et « tout sélectionner » n'étendra pas la sélection pour les inclure »*.

C'est un vrai risque pour nous : `ReaderScreen` (Tâche 4.7) rend le chapitre courant dans un simple `Column`, pas un `LazyColumn` — délibérément, pour éviter ce piège dès le départ, mais jamais vérifié explicitement comme une décision de conception plutôt qu'un hasard de la marche à blanc. Si un futur besoin de performance (chapitre très long) pousse à virtualiser ce rendu, la sélection de texte casse silencieusement pour tout ce qui défile hors écran.

**Action concrète avant 7.1 :**

```kotlin
// Verification empirique - a executer manuellement avant d'ecrire l'UI
// d'annotation complete :
// 1. Construire un ReaderScreen de test avec un chapitre suffisamment
//    long pour depasser l'ecran (Column standard, pas Lazy).
// 2. Envelopper dans SelectionContainer(state = rememberSelectionState()) ou
//    l'API disponible dans la version de Compose Foundation du projet -
//    VERIFIER la signature exacte (state: SelectionState) contre le
//    artifact reellement utilise, cette API a pu evoluer recemment.
// 3. Selectionner du texte qui necessite de faire defiler pendant la
//    selection (glisser la poignee au-dela du bas de l'ecran visible).
// 4. Confirmer que la selection retournee par SelectionState couvre bien
//    tout le texte attendu, pas seulement ce qui etait visible au debut
//    du geste.
```

**Décision à documenter selon le résultat** : si `Column` simple fonctionne proprement avec la sélection (attendu, confirmé par l'avertissement qui ne s'applique qu'aux layouts paresseux), acter que **le rendu du chapitre reste volontairement non virtualisé tant que la sélection de texte est une fonctionnalité active** — un compromis performance/fonctionnalité explicite, pas un oubli. Si un besoin de performance futur l'exige, la sélection devra être repensée en même temps (ex. désactivée pendant le défilement rapide), pas ajoutée après coup.

**Commit :** `Verifie et documente la compatibilite selection de texte / rendu non virtualise`

---

## Tâche 7.1 — UI annotation (sélection → `Locator` de début/fin)

**Objectif :** transformer une sélection de texte Compose en `Annotation` avec `startLocator`/`endLocator` réels.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/AnnotationSelectionHandler.kt` :

```kotlin
package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.valueobject.Locator

/**
 * Convertit une selection de texte (offset dans le texte CONCATENE du
 * chapitre affiche, cf. convention posee en Tache 3.4/4.1 -
 * DocumentModelExtractor) en Locator de debut et de fin.
 *
 * Le point delicat : la selection Compose donne un offset dans le texte
 * RENDU (un Sentence par Text composable, Tache 4.7), pas directement
 * dans notre modele de Sentence indexees. Il faut retrouver quelle(s)
 * Sentence(s) la selection traverse et calculer l'offset RELATIF a
 * chacune, pas juste utiliser l'offset global tel quel.
 */
class AnnotationSelectionHandler {

    fun resolveSelection(
        sentences: List<Sentence>,
        selectionStart: Int, // offset absolu dans le texte concatene affiche
        selectionEnd: Int,
        chapterIndex: Int,
        resourceHref: String,
    ): Pair<Locator, Locator>? {
        val startSentence = findSentenceContaining(sentences, selectionStart) ?: return null
        val endSentence = findSentenceContaining(sentences, selectionEnd) ?: return null

        val startLocator = Locator(
            resourceHref = resourceHref, chapterIndex = chapterIndex,
            charOffset = selectionStart - startSentence.startOffset + startSentence.startOffset,
            // NOTE : selectionStart est deja dans le referentiel absolu du
            // chapitre si la convention de concatenation (Tache 3.4) est
            // respectee jusqu'ici - a VERIFIER par un test avec une
            // selection multi-phrases avant de faire confiance a ce calcul,
            // pas suppose correct par construction.
        )
        val endLocator = Locator(
            resourceHref = resourceHref, chapterIndex = chapterIndex,
            charOffset = selectionEnd,
        )

        return if (endLocator >= startLocator) startLocator to endLocator else null
    }

    private fun findSentenceContaining(sentences: List<Sentence>, offset: Int): Sentence? =
        sentences.firstOrNull { offset in it.startOffset..it.endOffset }
}
```

**Point non résolu, à trancher en écrivant le test, pas deviné ici** : la relation exacte entre l'offset retourné par `SelectionState` de Compose (Tâche 7.0) et l'`offset` global de nos `Sentence` dépend de si Compose expose un offset par `Text` individuel ou un offset global sur tout le `SelectionContainer` — **vérifié empiriquement en 7.0, à réutiliser ici**, pas supposé deux fois.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/AnnotationColorPicker.kt` — UI triviale (Material 3 `FilterChip` par `AnnotationColor`), non détaillée ici, même pattern que les autres écrans MVI du projet.

**Test JVM pur** (logique de résolution, indépendante de Compose) :

```kotlin
class AnnotationSelectionHandlerTest {
    @Test
    fun `selection a l'interieur d'une seule phrase produit un locator coherent`() {
        val sentences = listOf(
            Sentence(0, "Bonjour le monde.", startOffset = 0, endOffset = 18),
            Sentence(1, "Ceci est un test.", startOffset = 19, endOffset = 37),
        )
        val result = AnnotationSelectionHandler().resolveSelection(
            sentences, selectionStart = 8, selectionEnd = 12, chapterIndex = 0, resourceHref = "ch1.xhtml",
        )
        assertNotNull(result)
    }

    @Test
    fun `selection traversant deux phrases est geree, pas rejetee silencieusement`() {
        val sentences = listOf(
            Sentence(0, "Bonjour le monde.", startOffset = 0, endOffset = 18),
            Sentence(1, "Ceci est un test.", startOffset = 19, endOffset = 37),
        )
        val result = AnnotationSelectionHandler().resolveSelection(
            sentences, selectionStart = 10, selectionEnd = 25, chapterIndex = 0, resourceHref = "ch1.xhtml",
        )
        assertNotNull("une selection multi-phrases doit produire une annotation, pas null", result)
    }
}
```

**Critère de validation avant/après :** sélectionner du texte réel dans l'app (device), créer une annotation, la fermer et la rouvrir — le surlignage doit réapparaître exactement sur le texte sélectionné à l'origine, pas décalé.

**Commit :** `Ajoute la selection de texte et le mapping vers Locator pour les annotations`

---

## Tâche 7.2 — UI signets

**Objectif :** plus simple que 7.1 — capture la position courante, pas de plage à résoudre.

```kotlin
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val createBookmark: CreateBookmarkUseCase, // deja complet, Phase 1
    private val bookmarkRepository: BookmarkRepository,
) : ViewModel() {

    fun createBookmarkAtCurrentPosition(publicationId: String, currentLocator: Locator) {
        viewModelScope.launch {
            createBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    locator = currentLocator, // reutilise le Locator deja tenu par ReaderViewModel (Tache 4.5/3.6)
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun observeBookmarks(publicationId: String) = bookmarkRepository.observeForPublication(publicationId)
}
```

`BookmarkListSheet` — liste virtualisée (`LazyColumn`, sans risque cette fois : pas de sélection de texte impliquée, l'avertissement de la Tâche 7.0 ne s'applique pas), un item par signet avec titre/note optionnels, navigation au clic (`navigateToChapter` + positionnement sur `locator.charOffset`, réutilise Tâche 4.5).

**Commit :** `Ajoute la creation et la navigation par signets`

---

## Tâche 7.3 — `SearchService` : implémentation FTS4

**Objectif :** rien n'existe encore malgré la mention au Blueprint depuis la Phase 2 — construction complète.

### 7.3.1 — Table FTS et migration

**Point critique, à ne jamais oublier (K4, Blueprint §14.5)** : la base est en version 1 (Phase 2). Ajouter une table FTS est un changement de schéma → **migration explicite + test de migration obligatoires**, pas une exception à la règle posée depuis la Phase 2.

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/SentenceFtsEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Index FTS4 sur le texte des phrases (Blueprint §6.9). Une ligne par
 * Sentence extraite — pas par Chapter entier, pour permettre un extrait
 * (snippet) precis autour du terme trouve, et un Locator exact vers le
 * resultat (chapterIndex + charOffset de la Sentence, pas juste "quelque
 * part dans ce chapitre").
 */
@Fts4
@Entity(tableName = "sentence_fts")
data class SentenceFtsEntity(
    val publicationId: String,
    val chapterIndex: Int,
    val resourceHref: String,
    val charOffset: Int,
    val text: String, // colonne indexee par FTS4
)
```

**Migration 1→2** (`infrastructure/database/src/main/kotlin/.../Migrations.kt`) :

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS sentence_fts USING fts4(
                publicationId, chapterIndex, resourceHref, charOffset, text
            )
        """.trimIndent())
    }
}
```

Enregistrer dans `DatabaseModule` (Tâche 2.3) : `.addMigrations(MIGRATION_1_2)`, bumper `InkToneDatabase` à `version = 2`, réexporter le schéma (`exportSchema`, dossier `schemas/`, committé — même discipline que la Tâche 2.4).

**Test de migration obligatoire** (même gabarit que `DatabaseMigrationTest`, Tâche 2.4) : créer en v1, insérer des `Publication`/`ReadingState` représentatifs, migrer vers v2, vérifier que les données v1 survivent ET que la table FTS existe et accepte une insertion.

### 7.3.2 — Peuplement de l'index

**Question non résolue par le plan, à trancher avant de coder** : l'index FTS se peuple-t-il **à l'import** (Phase 6, `ImportPublicationUseCase`) en insérant chaque `Sentence` extraite, ou **paresseusement** à la première recherche ? Peupler à l'import est plus simple et cohérent avec « le contenu est déjà extrait à ce moment-là » (`DocumentModelExtractor`, Tâche 3.4/4.1) — recommandé par défaut, mais à confirmer : ça ajoute du temps à l'import (budget 500 EPUB ≤ 5 min, Tâche 6.9, déjà mesuré à 66,7s de marge confortable — probablement absorbable, mais **à remesurer**, pas supposé gratuit).

```kotlin
// Dans ImportPublicationUseCase (Tache 6.1), apres l'insertion de la
// Publication : peupler l'index FTS a partir du DocumentModel deja
// extrait par le parsing - pas de second passage de parsing.
sentenceFtsDao.insertAll(
    documentModel.chapters.flatMap { chapter ->
        chapter.paragraphs.flatMap { it.sentences }.map { sentence ->
            SentenceFtsEntity(
                publicationId = publication.id, chapterIndex = chapter.index,
                resourceHref = chapter.href, charOffset = sentence.startOffset, text = sentence.text,
            )
        }
    },
)
```

### 7.3.3 — `SearchServiceImpl`

```kotlin
class RoomSearchService @Inject constructor(
    private val sentenceFtsDao: SentenceFtsDao,
) : SearchService {
    override suspend fun search(query: String, publicationId: String?): List<SearchResult> {
        val sanitized = sanitizeFtsQuery(query) // echapper les caracteres speciaux FTS4 (", *, -)
        val rows = if (publicationId != null) {
            sentenceFtsDao.searchInPublication(sanitized, publicationId)
        } else {
            sentenceFtsDao.searchAll(sanitized)
        }
        return rows.map {
            SearchResult(
                publicationId = it.publicationId,
                locator = Locator(it.resourceHref, it.chapterIndex, charOffset = it.charOffset),
                snippet = buildSnippet(it.text, sanitized),
            )
        }
    }
}
```

**Point d'attention explicite** : `sanitizeFtsQuery` n'est **pas montrée ici en détail** — l'échappement correct des caractères spéciaux FTS4 (`"`, `*`, `-`, `NEAR`) est une source classique de bugs de recherche silencieux (requête qui ne plante pas mais ne retourne jamais rien, ou pire, interprète mal l'intention de l'utilisateur). À écrire avec des tests explicites couvrant chaque caractère spécial, pas improvisée en une ligne.

**Commit :** `Implemente SearchService via FTS4, migration 1-2, peuplement a l'import`

---

## Tâche 7.4 — `SearchPublicationUseCase` complété

**Objectif :** lever le dernier `TODO()` de la Tâche 1.8.2.

```kotlin
class SearchPublicationUseCase @Inject constructor(
    private val searchService: SearchService,
) {
    suspend operator fun invoke(query: String, publicationId: String? = null): List<SearchResult> {
        if (query.isBlank()) return emptyList() // pas de requete vide vers FTS
        return searchService.search(query.trim(), publicationId)
    }
}
```

**Commit :** `Complete SearchPublicationUseCase`

---

## Tâche 7.5 — UI recherche

**Objectif :** parcours manuel complet — champ de recherche, résultats avec extrait, navigation.

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column {
        TextField(
            value = state.query,
            onValueChange = { viewModel.onIntent(SearchIntent.QueryChanged(it)) },
            placeholder = { Text("Rechercher...") },
        )
        LazyColumn {
            items(state.results, key = { it.locator.hashCode() }) { result ->
                SearchResultItem(result, onClick = {
                    viewModel.onIntent(SearchIntent.NavigateToResult(result))
                })
            }
        }
    }
}
```

**Point de conception** : débouncer la saisie (`debounce(300)` sur le `Flow` de requête côté ViewModel) — une recherche FTS à chaque frappe sur une bibliothèque large serait coûteuse et inutile avant que l'utilisateur ait fini de taper.

**Commit :** `Ajoute l'UI de recherche avec debounce`

---

## Tâche 7.6 — Tests FTS (pertinence, performance)

**Objectif :** rapidité même sur bibliothèque large — ne pas supposer que FTS4 « est rapide par nature » sans le mesurer sur notre volume réel.

```kotlin
@Test
fun recherche_reste_rapide_sur_corpus_volumineux() = runTest {
    // Reutilise le corpus du benchmark d'import (Tache 6.9, 500 EPUB
    // dupliques a hash distincts) plutot que d'en construire un nouveau -
    // meme discipline d'honnetete sur les fixtures de volume.
    val elapsed = measureTimeMillis {
        searchService.search("test")
    }
    assertTrue("recherche sous 200ms attendue, mesure: ${elapsed}ms", elapsed < 200)
}

@Test
fun echappement_des_caracteres_speciaux_fts4() {
    listOf("test\"citation", "mot*", "a-b", "NEAR", "").forEach { query ->
        // Ne doit jamais lever d'exception SQLite, quel que soit l'entree
        assertDoesNotThrow { runBlocking { searchService.search(query) } }
    }
}
```

**Commit :** `Ajoute les tests de performance et d'echappement FTS`

---

## Checklist finale de sortie de Phase 7

| # | Critère | Vérification |
|---|---|---|
| 1 | Sélection de texte vérifiée compatible avec le rendu non virtualisé | Tâche 7.0, décision documentée |
| 2 | Annotations créées avec `Locator` réel, testées sur sélection multi-phrases | Tâche 7.1 |
| 3 | Signets fonctionnels | Tâche 7.2 |
| 4 | Migration 1→2 (table FTS) testée, schéma exporté et committé | Tâche 7.3.1 |
| 5 | Décision de peuplement de l'index actée (à l'import), remesurée sur le budget 500 EPUB | Tâche 7.3.2 |
| 6 | `SearchService`/`SearchPublicationUseCase` complets | Tâches 7.3.3, 7.4 |
| 7 | UI recherche avec debounce | Tâche 7.5 |
| 8 | Performance FTS mesurée sur corpus volumineux, pas supposée | Tâche 7.6 |
| 9 | Échappement des caractères spéciaux FTS4 testé | Tâche 7.6 |

Une fois les 9 critères vérifiés, Phase 7 est close. Étape suivante : **Phase 8 — Réglages, statistiques, onboarding**.
