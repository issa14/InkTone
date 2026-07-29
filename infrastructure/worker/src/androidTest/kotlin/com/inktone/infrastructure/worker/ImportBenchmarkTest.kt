package com.inktone.infrastructure.worker

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.data.repository.RoomPublicationRepository
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ImportResult
import com.inktone.infrastructure.database.InkToneDatabase
import com.inktone.infrastructure.database.search.RoomSearchService
import com.inktone.infrastructure.parser.ReadiumPublicationParser
import com.inktone.infrastructure.storage.SafFileStorageService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis

/**
 * Budget §11.2 : 500 EPUB importés en ≤ 5 minutes. Mesure le pipeline
 * réel de bout en bout — `ReadiumPublicationParser`, `SafFileStorageService`,
 * `RoomPublicationRepository` sur une base **fichier** (pas en mémoire)
 * en WAL (K1, comme `DatabaseModule` en production) — avec exactement la
 * même politique de concurrence qu'`ImportWorker` (Tâche 6.3 :
 * `Semaphore(4)`), plutôt qu'une mesure macrobenchmark en boîte noire
 * (`:benchmark`) qui exigerait d'automatiser un sélecteur SAF système —
 * peu fiable et hors de portée raisonnable pour ce budget interne.
 *
 * **Honnêteté sur le corpus (même principe que 4.9/5.9)** : générer 500
 * EPUB distincts de contenu original à la main est déraisonnable ; générer
 * 500 EPUB *réels* de bibliothèque personnelle l'est encore moins — un
 * dépôt Git n'est pas un endroit pour distribuer du contenu sous droit
 * d'auteur, quelle que soit la taille. Ce test duplique le fixture
 * original déjà présent dans le dépôt
 * (`infrastructure/parser/.../fixture-multi-chapitre.epub`, contenu
 * généré, réutilisé ici sous `fixture-benchmark-base.epub`) en réécrivant
 * le contenu de chaque chapitre avec du texte généré unique par copie —
 * à la fois un hash distinct (évite la détection de doublons, K7, qui
 * fausserait la mesure en sautant le parsing réel) et une taille
 * sensiblement plus réaliste qu'un fixture de test brut (~30-40 Ko/fichier
 * une fois compressé, pas 500 exemplaires identiques de 2 Ko) — plus
 * petit qu'un roman EPUB moyen (souvent 0,5-3 Mo), documenté comme tel :
 * un facteur de marge supplémentaire existe donc entre ce résultat et un
 * corpus de vrais romans, dans le sens le plus défavorable (plus gros
 * fichiers, plus de texte à tokeniser).
 */
@RunWith(AndroidJUnit4::class)
class ImportBenchmarkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: InkToneDatabase
    private lateinit var corpusDir: File

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, InkToneDatabase::class.java, DB_NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // K1, meme config que DatabaseModule
            .build()
        corpusDir = File(context.cacheDir, "import-benchmark-corpus").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        corpusDir.deleteRecursively()
    }

    @Test
    fun import500EpubSousLeBudgetDe5Minutes() = runBlocking {
        val uris = generateCorpus(count = CORPUS_SIZE)

        val importPublication = ImportPublicationUseCase(
            publicationParser = ReadiumPublicationParser(context),
            publicationRepository = RoomPublicationRepository(db.publicationDao()),
            fileStorageService = SafFileStorageService(context),
            // Tache 7.3 : indexation FTS reelle a l'import - le benchmark
            // doit refleter le cout ajoute, pas le contourner avec un fake.
            searchService = RoomSearchService(db.sentenceFtsDao()),
        )

        var successCount = 0
        val elapsedMs = measureTimeMillis {
            coroutineScope {
                // Meme borne qu'ImportWorker (Tache 6.3, K2) - ne pas
                // mesurer une politique de concurrence differente de celle
                // reellement executee en production.
                val semaphore = Semaphore(permits = 4)
                val results = uris.map { uri ->
                    async { semaphore.withPermit { importPublication(uri) } }
                }.awaitAll()
                successCount = results.count { it is ImportResult.Success }
            }
        }

        val budgetMs = BUDGET_MINUTES * 60_000L
        println(
            "[Tache 6.9] Import de ${uris.size} EPUB (dont $successCount reussis) en ${elapsedMs}ms " +
                "(${elapsedMs / uris.size.toDouble()}ms/fichier en moyenne) - budget $budgetMs" + "ms.",
        )

        assertTrue("$successCount/${uris.size} imports reussis - un echec fausserait la mesure", successCount == uris.size)
        assertTrue(
            "Import de ${uris.size} EPUB a pris ${elapsedMs}ms, au-dela du budget de ${budgetMs}ms (Blueprint §11.2)",
            elapsedMs <= budgetMs,
        )
    }

    /** Génère [count] EPUB de contenu distinct — voir la KDoc de la classe. */
    private fun generateCorpus(count: Int): List<String> {
        // ZipFile exige un fichier reel, pas un flux - un seul fichier
        // gabarit temporaire reutilise en lecture pour les `count` copies
        // (pas un fichier temporaire par copie).
        val templateFile = File.createTempFile("bench-template-", ".epub", corpusDir).apply {
            context.assets.open(BASE_FIXTURE_ASSET).use { input -> outputStream().use { input.copyTo(it) } }
        }
        return (0 until count).map { index ->
            val file = File(corpusDir, "bench-$index.epub")
            writeVariant(templateFile, index, file)
            Uri.fromFile(file).toString()
        }
    }

    private fun writeVariant(templateFile: File, index: Int, output: File) {
        ZipFile(templateFile).use { zip ->
            ZipOutputStream(output.outputStream()).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name == "mimetype") {
                        // Doit rester STORED (non compresse) - convention EPUB.
                        out.putNextEntry(
                            ZipEntry(entry.name).apply {
                                method = ZipEntry.STORED
                                size = bytes.size.toLong()
                                crc = CRC32().apply { update(bytes) }.value
                            },
                        )
                        out.write(bytes)
                        out.closeEntry()
                    } else {
                        val content = if (entry.name.endsWith(".xhtml") && !entry.isDirectory) {
                            injectUniqueParagraphs(String(bytes, Charsets.UTF_8), index)
                        } else {
                            String(bytes, Charsets.UTF_8)
                        }
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Ajoute [PARAGRAPHS_PER_CHAPTER] paragraphes de texte généré (unique
     * par [index]) avant `</body>` — gonfle chaque chapitre à une taille
     * réaliste et garantit un hash de fichier distinct par copie (évite
     * K7/déduplication pendant la mesure, qui sauterait le parsing réel).
     */
    private fun injectUniqueParagraphs(xhtml: String, index: Int): String {
        val filler = (1..PARAGRAPHS_PER_CHAPTER).joinToString("\n") { p ->
            "<p>Paragraphe genere $p pour la copie de banc d'essai $index. " +
                "Texte de remplissage original, repete pour simuler la taille " +
                "d'un chapitre de roman reel (Tache 6.9, contenu non protege).</p>"
        }
        return if ("</body>" in xhtml) xhtml.replace("</body>", "$filler</body>") else xhtml + filler
    }

    private companion object {
        const val BASE_FIXTURE_ASSET = "fixture-benchmark-base.epub"
        const val DB_NAME = "inktone-benchmark-import.db"
        const val CORPUS_SIZE = 500
        const val PARAGRAPHS_PER_CHAPTER = 120 // 3 chapitres * 120 paragraphes ~= 30-40 Ko compresse
        const val BUDGET_MINUTES = 5
    }
}
