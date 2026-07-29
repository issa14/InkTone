# Phase 6 — Bibliothèque & import

**Dépend de :** Phase 4 (parser complet, multi-chapitres vérifié sur EPUB réel), Phase 2 (WAL actif, repositories)
**Précède :** Phase 7 — Annotations, signets, recherche
**Référence :** Blueprint InkTone v1.2.2, §11.2 (budgets), K1/K2 (WAL, ouverture ZIP unique)

## Ce qui existe déjà, à ne pas reconstruire

Contrairement aux phases précédentes, une bonne partie des briques de cette phase existe déjà, dispersée dans les phases 1, 2 et 4 :

| Besoin de la Phase 6 | Déjà présent | Depuis |
|---|---|---|
| Calcul de hash SHA-256 d'un fichier | `SafFileStorageService.computeSha256()` | Tâche 2.8 |
| Recherche de doublon par hash | `PublicationRepository.getByFileHash()` | Tâche 1.6/2.6 |
| Lecture SAF `content://` | `ReadiumPublicationParser` (via `Uri.toAbsoluteUrl()`) | Tâche 4.11 (corrigé sur EPUB réel) |
| Détection DRM à l'import | `publication.isProtected` | Tâche 3.2 |
| Extraction multi-chapitres fiable | `DocumentModelExtractor` | Tâche 4.1, corrigée sur Les Misérables (4.11) |
| `ImportPublicationUseCase` (signature) | `TODO()` explicite | Tâche 1.8.2 |
| `ExportLibraryUseCase` (signature) | `TODO()` explicite | Tâche 1.8.2 |

Le travail réel de cette phase est **l'orchestration et l'UI**, pas la reconstruction des primitives.

---

## Tâche 6.0 — Un trou de contrat de domaine, trouvé en planifiant l'export

`FileStorageService` (Tâche 2.0.2) n'a que des méthodes de lecture — `openInputStream`, `computeSha256`, `getFileSize`, `persistReadPermission`. Aucune méthode d'écriture. Sans ça, `ExportLibraryUseCase` (Tâche 6.7) n'a rien vers quoi écrire.

`domain/src/main/kotlin/com/inktone/domain/service/FileStorageService.kt`, ajouter :

```kotlin
/**
 * Écrit vers une destination SAF (Blueprint §10.3 — SAF exclusivement,
 * jamais un chemin fichier brut). Ajout Tâche 6.0 : absent depuis la
 * Phase 2 car rien n'écrivait de fichier utilisateur jusqu'ici.
 */
suspend fun writeToUri(uri: String, sourceFile: java.io.File): Boolean
```

Implémenter dans `SafFileStorageService` (Tâche 2.8) via `resolver.openOutputStream(Uri.parse(uri))`. Test symétrique à `SafFileStorageServiceTest` existant (écrire, relire, comparer le contenu).

**Commit :** `Ajoute la capacite d'ecriture SAF a FileStorageService (necessaire pour l'export, Tache 6.7)`

---

## Tâche 6.1 — `ImportPublicationUseCase` : orchestration, pas reconstruction

**Objectif :** brancher les primitives existantes dans le bon ordre. Lever le `TODO()` de la Tâche 1.8.2.

`domain/src/main/kotlin/com/inktone/domain/usecase/ImportPublicationUseCase.kt`, remplacer le corps :

```kotlin
class ImportPublicationUseCase @Inject constructor(
    private val publicationParser: PublicationParser,
    private val publicationRepository: PublicationRepository,
    private val fileStorageService: FileStorageService,
) {
    suspend operator fun invoke(fileUri: String): ImportResult {
        // 1. Hash AVANT de parser — evite de parser un doublon inutilement
        //    (le parsing d'un gros EPUB n'est pas gratuit, cf. §11.2).
        val hash = fileStorageService.computeSha256(fileUri)
            ?: return ImportResult.Corrupted("Impossible de lire le fichier")

        publicationRepository.getByFileHash(hash)?.let {
            return ImportResult.Duplicate(existingPublicationId = it.id)
        }

        // 2. Parser (gere deja DRM et extraction multi-chapitres, Phases 3/4)
        val parseResult = publicationParser.parse(fileUri)
        val publication = when (parseResult) {
            is ParseResult.DrmProtected -> return ImportResult.DrmProtected(parseResult.message)
            is ParseResult.Corrupted -> return ImportResult.Corrupted(parseResult.message)
            is ParseResult.UnsupportedFormat -> return ImportResult.UnsupportedFormat(parseResult.format)
            is ParseResult.Success -> buildPublication(parseResult, fileUri, hash, fileStorageService)
        }

        // 3. Persistance de la permission SAF AVANT insertion — si l'app est
        //    tuee entre les deux, mieux vaut une permission orpheline
        //    qu'une Publication en base pointant vers un URI inaccessible.
        fileStorageService.persistReadPermission(fileUri)
        publicationRepository.insert(publication)

        return ImportResult.Success(publication)
    }

    private suspend fun buildPublication(
        result: ParseResult.Success, fileUri: String, hash: String, storage: FileStorageService,
    ): Publication {
        val size = storage.getFileSize(fileUri) ?: 0L
        // Metadonnees (titre, auteurs, seriesName...) : le DocumentModel
        // actuel (Tache 3.4/4.1) expose chapters/toc/resources, PAS encore
        // les metadonnees Readium (titre, auteur, belongsTo pour la serie).
        // POINT OUVERT explicite ci-dessous (6.1.1) - ne pas deviner ici.
        TODO("6.1.1 : mapping metadonnees Readium -> Publication, voir ci-dessous")
    }
}
```

### 6.1.1 — Point ouvert, pas encore résolu par les phases précédentes

Aucune tâche antérieure n'a construit le mapping **métadonnées Readium → champs `Publication`** (titre, auteurs, `seriesName`/`seriesIndex` via `belongsTo`, `subjects`). `ReadiumPublicationParser.parse()` (Tâche 3.2/4.x) ne retourne que le `DocumentModel` (contenu) et `isDrmProtected` — jamais `publication.metadata`. C'est un vrai manque, pas un oubli mineur : sans lui, chaque livre importé aurait un titre vide.

**À vérifier avant d'écrire le mapping** (même discipline que Readium en Phase 3/4) : la forme exacte de `Publication.metadata` dans Readium 3.0.0 (champs disponibles pour `title`, `author`, `belongsTo` pour la série) — ne pas supposer identique à la documentation générique déjà lue, vérifier contre les sources comme pour `Content`/`ContentTokenizer`.

```kotlin
// A COMPLETER apres verification :
// ParseResult.Success devra probablement porter aussi les metadonnees
// (pas seulement documentModel + isDrmProtected) - extension du contrat
// domaine (Tache 1.7), non cassante si les nouveaux champs sont ajoutes
// avec des valeurs par defaut.
```

**Commit (une fois 6.1.1 résolu) :** `Complete ImportPublicationUseCase et le mapping des metadonnees Readium`

---

## Tâche 6.2 — Import en tâche WorkManager

**Objectif :** l'import survit à la mise en arrière-plan de l'app — obligatoire pour un import de plusieurs centaines de fichiers (§11.2 : 500 EPUB ≤ 5 min, personne ne garde l'app au premier plan tout ce temps).

`infrastructure/worker/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
}

android { namespace = "com.inktone.infrastructure.worker" }

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}
```

`infrastructure/worker/src/main/kotlin/com/inktone/infrastructure/worker/ImportWorker.kt` :

```kotlin
package com.inktone.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.inktone.domain.usecase.ImportPublicationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importPublication: ImportPublicationUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS) ?: return Result.failure()

        // Sequentiel pour l'instant — la parallelisation est la Tache 6.3,
        // DELIBEREMENT separee (K1 avant K2 : WAL d'abord, deja actif
        // depuis la Phase 2, mais la sequence de verification reste la
        // meme discipline que le legacy a apprise a ses depens).
        var successCount = 0
        var duplicateCount = 0
        var failureCount = 0

        uris.forEachIndexed { index, uri ->
            setProgressAsync(Data.Builder()
                .putInt(KEY_PROGRESS_CURRENT, index + 1)
                .putInt(KEY_PROGRESS_TOTAL, uris.size)
                .build())

            when (importPublication(uri)) {
                is com.inktone.domain.usecase.ImportResult.Success -> successCount++
                is com.inktone.domain.usecase.ImportResult.Duplicate -> duplicateCount++
                else -> failureCount++
            }
        }

        val output = Data.Builder()
            .putInt(KEY_RESULT_SUCCESS, successCount)
            .putInt(KEY_RESULT_DUPLICATE, duplicateCount)
            .putInt(KEY_RESULT_FAILURE, failureCount)
            .build()
        return Result.success(output)
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_DUPLICATE = "result_duplicate"
        const val KEY_RESULT_FAILURE = "result_failure"
    }
}
```

**Point d'attention :** `WorkManager` sérialise ses `Data` (limite ~10 Ko par `Data` объект) — une liste de 500 URI SAF peut dépasser cette limite selon leur longueur. **À vérifier avant de considérer cette tâche terminée**, pas supposé : si la limite est atteinte, découper l'input en plusieurs `WorkRequest` chaînées plutôt qu'un seul `Data` géant.

`infrastructure/worker/src/androidTest/kotlin/com/inktone/infrastructure/worker/ImportWorkerTest.kt` :

```kotlin
package com.inktone.infrastructure.worker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportWorkerTest {

    @Test
    fun import_avec_fixtures_valides_retourne_succes() = runTest {
        val worker = TestListenableWorkerBuilder<ImportWorker>(
            ApplicationProvider.getApplicationContext(),
        ).setInputData(
            workDataOf(ImportWorker.KEY_URIS to arrayOf(/* fixture URIs */)),
        ).build()

        val result = worker.doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
    }
}
```

**Commit :** `Ajoute ImportWorker (WorkManager, survit a la mise en arriere-plan)`

---

## Tâche 6.3 — Parallélisation (K2)

**Objectif :** le legacy a appris cette leçon à ses dépens — paralléliser seulement après confirmation que WAL absorbe la charge concurrente (déjà actif, Tâche 2.3), et **une seule ouverture ZIP par fichier** (l'erreur legacy exacte, K2).

```kotlin
override suspend fun doWork(): Result = coroutineScope {
    val uris = inputData.getStringArray(KEY_URIS) ?: return@coroutineScope Result.failure()

    // Bornee au nombre de coeurs — pas une parallelisation illimitee qui
    // saturerait la memoire sur un import de 500 fichiers (Snapdragon 680,
    // budget memoire §11.2).
    val semaphore = Semaphore(permits = 4)
    val results = uris.mapIndexed { index, uri ->
        async {
            semaphore.withPermit {
                // Chaque appel importPublication() -> publicationParser.parse()
                // ouvre le ZIP UNE FOIS (deja garanti par ReadiumPublicationParser,
                // Tache 3.2 — AssetRetriever.retrieve() une seule fois par appel,
                // pas de reouverture repetee comme le legacy le faisait).
                importPublication(uri)
            }
        }
    }.awaitAll()

    // Agregation identique a la version sequentielle (6.2).
    ...
}
```

**Critère de validation avant/après :** benchmark comparatif séquentiel vs parallèle (Tâche 6.9) — la parallélisation doit démontrer un gain réel, pas juste ajouter de la complexité. Si le gain est marginal (I/O-bound plutôt que CPU-bound), documenter pourquoi plutôt que de la garder par principe.

**Commit :** `Parallelise l'import (K2, borne a 4 permits, apres confirmation WAL)`

---

## Tâche 6.4 — Détection de doublons, test de bout en bout

**Objectif :** la logique existe déjà dans `ImportPublicationUseCase` (Tâche 6.1). Cette tâche prouve qu'elle fonctionne réellement à l'import, pas en isolation.

```kotlin
@Test
fun importer_deux_fois_le_meme_fichier_ne_cree_pas_de_doublon() = runTest {
    val fixtureUri = copyFixtureToTestUri("fixture-minimal.epub")

    val first = importPublicationUseCase(fixtureUri)
    val second = importPublicationUseCase(fixtureUri)

    check(first is ImportResult.Success)
    check(second is ImportResult.Duplicate)
    assertEquals(first.publication.id, second.existingPublicationId)

    // Le vrai test : un seul enregistrement en base, pas deux.
    val all = publicationRepository.observeAll().first()
    assertEquals(1, all.size)
}
```

**Commit :** `Verifie la detection de doublons de bout en bout a l'import`

---

## Tâche 6.5 — Filtres réels

**Objectif :** lever les no-ops posés en Phase 1 (`BY_AUTHOR`, `IN_PROGRESS`, `READ`, `UNREAD` → `true`).

### 6.5.1 — Favoris, séries, tags : triviaux, déjà tout le nécessaire

```kotlin
FilterMode.FAVORITES -> publication.isFavorite
FilterMode.SERIES -> publication.seriesName == targetSeriesName
FilterMode.TAG -> targetTag in publication.subjects
FilterMode.BY_AUTHOR -> targetAuthor in publication.authors
```

### 6.5.2 — Statut de lecture : question de produit non résolue, à trancher avant de coder

Le domaine n'a **aucun concept explicite de « terminé »** — seulement un `Locator`/une `progression` dérivée (Blueprint §3.2). Trois définitions possibles pour `READ`, aucune évidente :

1. **Seuil de progression** (ex. `progression >= 0.95`) — simple, mais arbitraire et jamais validé produit.
2. **Marquage explicite utilisateur** (« marquer comme terminé ») — plus fiable, mais exige un nouveau champ domaine (`Publication.isFinished` ou équivalent) et une action UI dédiée, hors périmètre annoncé de cette tâche.
3. **Dernier chapitre atteint** — évite le seuil arbitraire sur la progression globale, mais ignore les livres à chapitre unique ou à structure inhabituelle.

**Ne pas choisir silencieusement.** Cette décision doit remonter avant d'écrire le filtre — proposer l'option 1 comme heuristique v1 (la plus simple), avec l'option 2 notée comme évolution naturelle (Blueprint §16.4).

```kotlin
// PLACEHOLDER assume jusqu'a decision produit — seuil arbitraire a 0.95,
// A REVISER explicitement, pas une verite du domaine :
FilterMode.READ -> (readingState?.let { computeProgression(it.locator, ...) } ?: 0f) >= 0.95f
FilterMode.UNREAD -> readingState == null
FilterMode.IN_PROGRESS -> readingState != null && !isRead(readingState)
```

### 6.5.3 — Performance de la jointure Publication ↔ ReadingState

Pour `IN_PROGRESS`/`READ`/`UNREAD`, filtrer nécessite de connaître l'état de lecture de **chaque** publication — un filtrage en mémoire après deux requêtes séparées serait un anti-pattern sur 1000+ livres (§11.2). Préférer une requête SQL avec jointure :

```kotlin
@Query("""
    SELECT p.* FROM publications p
    LEFT JOIN reading_states rs ON p.id = rs.publicationId
    WHERE (:filterUnread = 0 OR rs.publicationId IS NULL)
    ORDER BY p.lastOpened DESC
""")
fun observeFilteredByReadStatus(filterUnread: Boolean): Flow<List<PublicationEntity>>
```

**Commit :** `Implemente les filtres reels, statut de lecture par seuil de progression (a reviser produit)`

---

## Tâche 6.6 — UI bibliothèque (scroll à 1000+ livres)

**Objectif :** grille/liste performante — pas de dégradation à grande échelle.

`feature/library/src/main/kotlin/com/inktone/feature/library/LibraryScreen.kt` — pattern MVI identique à `ReaderScreen`/`PlayerScreen` (états immuables, intents explicites) :

```kotlin
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        // cle stable = id, jamais l'index — meme lecon que la TOC (Tache 4.11,
        // le crash LazyColumn a cle non unique)
    ) {
        items(state.publications, key = { it.id }) { publication ->
            PublicationCard(publication, onClick = { viewModel.onIntent(LibraryIntent.OpenPublication(publication.id)) })
        }
    }
}
```

**Points de performance, à vérifier empiriquement (Tâche 6.9), pas supposés :**
- Pagination (`Paging3` ou requête fenêtrée) plutôt que charger 1000+ `Publication` d'un coup en mémoire.
- Chargement des couvertures : `AsyncImage`/Coil avec cache disque, jamais un décodage bitmap synchrone sur le thread principal.

**Commit :** `Ajoute LibraryScreen avec grille virtualisee et pagination`

---

## Tâche 6.7 — `ExportLibraryUseCase`

**Objectif :** lever le second `TODO()` de la Tâche 1.8.2, maintenant que `FileStorageService.writeToUri` existe (Tâche 6.0).

```kotlin
class ExportLibraryUseCase @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val fileStorageService: FileStorageService,
) {
    suspend operator fun invoke(publicationId: String, destinationUri: String): ExportResult {
        val publication = publicationRepository.getById(publicationId)
            ?: return ExportResult.NotFound

        val sourceFile = File(publication.fileUri) // NOTE: fileUri est un URI SAF,
        // pas un chemin fichier local direct — le vrai export doit lire via
        // fileStorageService.openInputStream(publication.fileUri) puis ecrire
        // vers destinationUri, PAS instancier File() directement sur un URI
        // content:// (erreur de type a corriger avant de considerer ce code fini).

        val success = fileStorageService.writeToUri(destinationUri, sourceFile)
        return if (success) ExportResult.Success else ExportResult.Failed
    }
}

sealed interface ExportResult {
    object Success : ExportResult
    object NotFound : ExportResult
    object Failed : ExportResult
}
```

**Erreur signalée délibérément dans le code ci-dessus** : `File(publication.fileUri)` est faux pour une URI `content://` — laissé visible pour que Claude Code corrige en écrivant, pas caché dans un exemple qui semblerait fonctionner.

**Commit :** `Complete ExportLibraryUseCase`

---

### 6.7.1 — Comparaison ZIP / arborescence SAF / partage OS, refaite après revue

**Constat honnête sur la décision d'origine** : au moment d'écrire `ExportLibraryUseCase`, le choix de l'archive ZIP a été posé via une question à trois options (ZIP recommandé / export livre par livre / arborescence SAF) — mais la justification donnée pour ZIP à ce moment-là était en bonne partie une justification d'*implémentation* : « respecte le contrat existant sans étendre `FileStorageService`, simple et testable ». Ce n'est pas une comparaison de mérite entre alternatives, c'est une comparaison de coût d'implémentation compte tenu de l'interface déjà posée à la Tâche 6.0 (ma propre décision) — exactement le biais qu'il ne fallait pas avoir. Par ailleurs, une troisième alternative réelle — le partage via `ACTION_SEND_MULTIPLE` (feuille de partage du système) — n'avait **jamais été mise sur la table**, ni dans la question posée ni dans le code. La comparaison n'a donc pas vraiment eu lieu ; elle est refaite ici avec les trois options réelles, sans supposer que `writeToUri` doive rester la contrainte.

| | **ZIP (retenu)** | **Arborescence SAF** (`DocumentsContract.createDocument` sous un URI d'arbre) | **Partage OS** (`Intent.ACTION_SEND_MULTIPLE`) |
|---|---|---|---|
| **Nature du geste** | Choisir *un fichier* de destination (`ACTION_CREATE_DOCUMENT`) | Choisir *un dossier* de destination (`ACTION_OPEN_DOCUMENT_TREE`) | Choisir *une app cible* (Gmail, Drive, Bluetooth…) via la feuille système |
| **Résultat pour l'utilisateur** | Une archive, à décompresser pour retrouver les EPUB individuels | Les EPUB restent des fichiers individuels, immédiatement ouvrables par une autre app | Les EPUB arrivent chez le destinataire, sans dossier de sauvegarde local |
| **Atomicité** | Oui — l'archive est assemblée entièrement en local avant l'unique écriture SAF ; échec = rien n'est écrit, jamais d'état à moitié exporté | Non par nature — un échec à mi-parcours laisse un dossier partiellement rempli (nécessiterait une logique de reprise/nettoyage explicite, un état partiel implicite que le reste du projet évite systématiquement, K3/K4) | Non pertinent — pas une opération de sauvegarde, juste un envoi ponctuel |
| **Coût disque pendant l'export** | Double temporairement (sources + archive temporaire) | Aucun surcoût — chaque fichier est écrit puis libéré | Aucun (pas de fichier de destination local) |
| **Changement de contrat requis** | Aucun — `writeToUri(uri, file)` suffit (Tâche 6.0) | Oui — `destinationUri` (Phase 1, `invoke(destinationUri)`) devrait devenir une URI d'arbre, avec un picker différent (`OpenDocumentTree` vs `CreateDocument`) et une nouvelle capacité `FileStorageService.createChildDocument(treeUri, name)` | Oui — nécessite d'exposer les fichiers SAF via un `FileProvider` de l'app (les URI SAF de la bibliothèque ne sont pas directement partageables telles quelles), plus la construction d'un `ArrayList<Uri>` avec permissions accordées |
| **Passage à l'échelle (des centaines de livres)** | Correct — une seule écriture, taille de l'archive proportionnelle à la bibliothèque | Correct — un fichier à la fois, pas de limite connue | **Rédhibitoire** — `ACTION_SEND_MULTIPLE` transporte la liste d'URI dans les extras de l'Intent (`ClipData`), soumis à la même limite de taille de transaction Binder que `WorkManager.Data` (déjà rencontrée et documentée à la Tâche 6.2/6.2bis, ~1 Mo, dépassée bien avant 500 entrées) ; en pratique, la plupart des apps réceptrices (Gmail, WhatsApp…) plafonnent aussi le nombre de pièces jointes bien en-dessous de la taille d'une bibliothèque |
| **Adéquation avec l'objectif de la Tâche 6.7** (« exporte **toute la bibliothèque** ») | Bonne — sémantique de sauvegarde/transfert d'un tout | Bonne, voire meilleure si l'usage réel est « déplacer sa bibliothèque vers une autre app lecteur » | Mauvaise — `ACTION_SEND_MULTIPLE` est conçu pour partager une poignée de fichiers choisis, pas exporter une bibliothèque entière ; adapté à une fonctionnalité différente (« partager ces livres-ci »), hors périmètre de cette tâche |

**Verdict** : `ACTION_SEND_MULTIPLE` est éliminé sur un critère technique dur, pas une préférence — la même classe de problème que la limite `Data` de WorkManager (Tâche 6.2), déjà rencontrée dans cette phase. Entre ZIP et l'arborescence SAF, le compromis est réel (l'arborescence évite l'étape de décompression et le surcoût disque), mais ZIP l'emporte pour **cette tâche précise** sur deux critères qui ne sont pas des critères de facilité d'implémentation : l'atomicité (aucun état partiel possible, cohérent avec la discipline K3/K4 du reste du projet) et la stabilité du contrat `invoke(destinationUri)` déjà fixé en Phase 1 (que l'arborescence SAF casserait). Si un besoin futur de « déplacer sa bibliothèque vers une autre app lecteur, fichier par fichier » émerge, l'arborescence SAF redevient le bon choix — mais comme fonctionnalité distincte, pas comme remplacement de cet export de sauvegarde.

---

## Tâche 6.8 — Bannière de progression, badges

**Objectif :** import visible, UI jamais gelée (Blueprint §6.8 module Import).

```kotlin
@Composable
fun ImportProgressBanner(current: Int, total: Int) {
    if (total == 0) return
    LinearProgressIndicator(progress = current.toFloat() / total)
    Text("Import : $current / $total")
}
```

Observer la progression via `WorkManager.getWorkInfoByIdFlow()` (Tâche 6.2, `KEY_PROGRESS_CURRENT`/`KEY_PROGRESS_TOTAL`) — pas de polling manuel.

**Commit :** `Ajoute la banniere de progression d'import`

---

## Tâche 6.9 — Benchmark import (§11.2 : 500 EPUB ≤ 5 min)

**Objectif :** mesurer, comme pour chaque budget précédent — pas supposer que la parallélisation (6.3) suffit.

Étendre le module `benchmark` (Tâches 4.9/5.9) :

```kotlin
@Test
fun import500Epub() = benchmarkRule.measureRepeated(
    packageName = "com.inktone.app",
    metrics = listOf(TraceSectionMetric("import_batch")),
    iterations = 1, // un seul run complet, pas 5 — 500 EPUB x 5 iterations serait deraisonnable
) {
    // Necessite un corpus de 500 fixtures — a generer (contenu original,
    // pas de contenu tiers en volume) ou reutiliser un sous-ensemble
    // duplique avec des hash differents artificiellement pour le volume,
    // en notant explicitement que ce n'est pas 500 livres distincts reels.
}
```

**Honnêteté sur cette tâche** (même principe que 4.9/5.9) : générer 500 EPUB de contenu original distinct dépasse le raisonnable pour un test de performance — utiliser un sous-ensemble de fixtures dupliquées avec des hash forcés différents (pour ne pas déclencher la détection de doublons pendant le benchmark) est une simplification à documenter, pas à cacher.

**Commit :** `Ajoute le benchmark d'import 500 EPUB`

---

## Checklist finale de sortie de Phase 6

| # | Critère | Vérification |
|---|---|---|
| 1 | `FileStorageService.writeToUri` ajouté et testé | Tâche 6.0 |
| 2 | `ImportPublicationUseCase` complet, métadonnées Readium mappées | Tâche 6.1, point 6.1.1 résolu (pas laissé en `TODO()`) |
| 3 | Import survit à la mise en arrière-plan | Tâche 6.2, limite `Data` WorkManager vérifiée |
| 4 | Parallélisation avec gain mesuré (pas supposé) | Tâche 6.3, benchmark comparatif |
| 5 | Doublons détectés de bout en bout | Tâche 6.4 |
| 6 | Filtres réels, statut de lecture avec décision produit actée | Tâche 6.5, seuil documenté ou révisé |
| 7 | Bibliothèque à 1000+ livres, 60 fps | Tâche 6.6, pagination confirmée |
| 8 | Export fonctionnel (bug SAF corrigé, pas laissé tel quel) | Tâche 6.7 |
| 9 | Progression d'import visible, UI non bloquée | Tâche 6.8 |
| 10 | Budget import 500 EPUB ≤ 5 min mesuré | Tâche 6.9 |

Une fois les 10 critères vérifiés, Phase 6 est close. Étape suivante : **Phase 7 — Annotations, signets, recherche**.
