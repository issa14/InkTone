package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPublicationUseCaseTest {

    /**
     * Progression : les longueurs de TOUS les chapitres sont posées dès
     * l'import, à partir des chapitres déjà parsés pour l'indexation — c'est
     * ce qui permet au Lecteur et à la Bibliothèque d'afficher une
     * progression juste sans avoir chargé le livre entier.
     */
    /**
     * Bug réel corrigé (audit UX D2) : l'index de recherche reprenait
     * l'index du parseur, qui ignore la couverture ajoutée en tête ; un
     * résultat désignait alors le chapitre précédent. [FakeChapterParser]
     * renvoie toujours l'index 0, comme ce défaut.
     */
    @Test
    fun `l'index de recherche numerote les chapitres dans l'ordre de lecture`() = runTest {
        val searchService = FakeSearchService()
        val shells = listOf("cover.xhtml", "ch1.xhtml", "ch2.xhtml").map { href ->
            Chapter(index = 0, href = href, title = null, content = ChapterContent.Rich(emptyList()))
        }
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(
                result = ParseResult.Success(
                    documentModel = DocumentModel(chapters = shells, tableOfContents = emptyList(), resources = emptyList()),
                    isDrmProtected = false,
                    metadata = PublicationMetadata(title = "Titre"),
                ),
            ),
            publicationRepository = FakePublicationRepository(),
            fileStorageService = FakeFileStorageService(),
            searchService = searchService,
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
        )

        importPublication("content://fake/livre.epub")

        assertEquals(
            listOf(0 to "cover.xhtml", 1 to "ch1.xhtml", 2 to "ch2.xhtml"),
            searchService.indexedChapters,
        )
    }

    @Test
    fun `l'import pose les longueurs de tous les chapitres`() = runTest {
        val publicationRepository = FakePublicationRepository()
        val shells = listOf("cover.xhtml", "ch1.xhtml", "ch2.xhtml").map { href ->
            Chapter(index = 0, href = href, title = null, content = ChapterContent.Rich(emptyList()))
        }
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(
                result = ParseResult.Success(
                    documentModel = DocumentModel(chapters = shells, tableOfContents = emptyList(), resources = emptyList()),
                    isDrmProtected = false,
                    metadata = PublicationMetadata(title = "Titre"),
                ),
            ),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
        )

        val result = importPublication("content://fake/livre.epub")

        check(result is ImportResult.Success)
        val stored = publicationRepository.getById(result.publication.id)
        assertEquals("une longueur par chapitre, couverture comprise", 3, stored?.chapterLengths?.values?.size)
    }

    @Test
    fun `importer deux fois le meme fichier ne cree pas de doublon`() = runTest {
        val publicationRepository = FakePublicationRepository()
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
        )
        val fixtureUri = "content://fake/fixture-minimal.epub"

        val first = importPublication(fixtureUri)
        val second = importPublication(fixtureUri)

        check(first is ImportResult.Success)
        check(second is ImportResult.Duplicate)
        assertEquals(first.publication.id, second.existingPublicationId)

        // Le vrai test : un seul enregistrement en base, pas deux.
        val all = publicationRepository.observeAll().first()
        assertEquals(1, all.size)
    }

    @Test
    fun `deux imports concurrents du meme fichier ne creent pas de doublon (Tache 6_3)`() = runTest {
        val publicationRepository = FakePublicationRepository()
        // delayMs > 0 elargit deliberement la fenetre entre la verification
        // hors verrou et la reverification sous mutex - sans ca, le fake
        // n'a aucune IO reelle et les deux coroutines ne s'entrelaceraient
        // jamais (reproduirait un faux negatif, pas une preuve d'absence
        // de course - la meme discipline que K2 avec le legacy).
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(delayMs = 10),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
        )
        val fixtureUri = "content://fake/fixture-minimal.epub"

        val results = listOf(
            async { importPublication(fixtureUri) },
            async { importPublication(fixtureUri) },
        ).awaitAll()

        assertEquals(1, results.count { it is ImportResult.Success })
        assertEquals(1, results.count { it is ImportResult.Duplicate })
        assertEquals(1, publicationRepository.observeAll().first().size)
    }

    /**
     * Audit UX D56 : un chapitre illisible interrompait l'indexation, la
     * pré-analyse et le calcul des longueurs pour tout le livre.
     */
    @Test
    fun `un chapitre illisible n'interrompt pas l'import du reste du livre`() = runTest {
        val searchService = FakeSearchService()
        val publicationRepository = FakePublicationRepository()
        val preAnalysisStore = FakePreAnalysisStore()
        val shells = listOf("ch1.xhtml", "casse.xhtml", "ch3.xhtml").map { href ->
            Chapter(index = 0, href = href, title = null, content = ChapterContent.Rich(emptyList()))
        }
        val parser = object : com.inktone.domain.service.ChapterParser {
            override fun registerPublication(publicationId: String, fileUri: String) = Unit
            override suspend fun parseChapter(publicationId: String, chapterHref: String, fragment: String?): Chapter {
                if (chapterHref == "casse.xhtml") error("chapitre illisible")
                return Chapter(
                    index = 0, href = chapterHref, title = null, content = ChapterContent.Rich(emptyList()),
                    sentences = listOf(com.inktone.domain.model.Sentence(0, "Une phrase.", 0, 11)),
                )
            }
            override fun preload(publicationId: String, chapterHref: String, scope: kotlinx.coroutines.CoroutineScope) =
                kotlinx.coroutines.Job().apply { complete() }
            override fun invalidate(publicationId: String) = Unit
        }
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(
                result = ParseResult.Success(
                    documentModel = DocumentModel(chapters = shells, tableOfContents = emptyList(), resources = emptyList()),
                    isDrmProtected = false,
                    metadata = PublicationMetadata(title = "Titre"),
                ),
            ),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = searchService,
            chapterParser = parser,
            preAnalysisStore = preAnalysisStore,
        )

        val publication = (importPublication("content://fake/livre.epub") as ImportResult.Success).publication

        // Le chapitre après le chapitre cassé est indexé, à sa position.
        assertEquals(listOf(0 to "ch1.xhtml", 2 to "ch3.xhtml"), searchService.indexedChapters)
        // Pré-analyse sans le chapitre cassé, que le Lecteur retentera.
        assertEquals(listOf("ch1.xhtml", "ch3.xhtml"), preAnalysisStore.savedChapters(publication.id)?.map { it.href })
        // Longueurs alignées sur les positions : 0 pour le chapitre cassé.
        val lengths = publicationRepository.getById(publication.id)?.chapterLengths
        assertEquals(listOf(11, 0, 11), lengths?.values)
    }
}
