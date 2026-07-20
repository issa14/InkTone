package com.inktone.domain.usecase

import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TocEntry
import com.inktone.domain.repository.BookRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalculateReadingProgressUseCaseTest {

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private lateinit var useCase: CalculateReadingProgressUseCase

    private val testBook = Book(
        id = "book-1",
        title = "Test",
        author = "Auteur",
        description = null,
        totalChapters = 10,
        language = "fr",
        addedAt = 0
    )

    @BeforeEach
    fun setUp() {
        useCase = CalculateReadingProgressUseCase(bookRepository)
    }

    @Test
    fun `début du livre retourne 0`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 0, sentenceIndex = 0, totalSentences = 100)
        assertEquals(0f, fraction)
    }

    @Test
    fun `fin du livre retourne environ 1`() = runTest {
        // Dernier chapitre, dernière phrase
        val fraction = useCase(
            testBook,
            chapterIndex = 9, // dernier chapitre (0-based, 10 total)
            sentenceIndex = 99, // dernière phrase (0-based, 100 total)
            totalSentences = 100
        )
        assertTrue(fraction >= 0.99f, "La fraction en fin de livre doit être proche de 1 (trouvé: $fraction)")
        assertTrue(fraction <= 1f, "La fraction ne doit pas dépasser 1 (trouvé: $fraction)")
    }

    @Test
    fun `milieu du livre retourne environ 0_5`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 4, sentenceIndex = 50, totalSentences = 100)
        assertEquals(0.45f, fraction, 0.01f)
    }

    @Test
    fun `totalSentences à 0 ne cause pas de division par zéro`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 5, sentenceIndex = 0, totalSentences = 0)
        assertTrue(fraction in 0f..1f, "La fraction doit être dans [0,1], trouvé: $fraction")
    }

    @Test
    fun `totalChapters à 0 ne cause pas de division par zéro`() = runTest {
        val emptyBook = testBook.copy(totalChapters = 0)
        val fraction = useCase(emptyBook, chapterIndex = 0, sentenceIndex = 0, totalSentences = 10)
        assertTrue(fraction in 0f..1f, "La fraction doit être dans [0,1], trouvé: $fraction")
    }

    @Test
    fun `fraction toujours clampée entre 0 et 1`() = runTest {
        // Cas extrême : chapterIndex > totalChapters
        val fraction = useCase(testBook, chapterIndex = 100, sentenceIndex = 100, totalSentences = 1)
        assertEquals(1f, fraction)
    }

    // ── Pondération par longueur réelle de chapitre (tâche 1.3) ────

    /**
     * Livre à chapitres très inégaux : une préface de 200 caractères suivie d'un
     * chapitre de 50 000 caractères. Un livre "normal" en aurait bien plus, mais 2
     * chapitres suffisent à distinguer la pondération par longueur de la position TOC.
     */
    private val unevenBook = testBook.copy(
        totalChapters = 2,
        tocEntries = listOf(
            TocEntry(index = 0, title = "Préface", charCount = 200),
            TocEntry(index = 1, title = "Chapitre 1", charCount = 50_000)
        )
    )

    @Test
    fun `pondère par longueur réelle, pas par position dans la TOC`() = runTest {
        // Position TOC naïve : chapitre 1 sur 2 → 50% attendu par une formule non pondérée.
        // Pondéré par longueur réelle : 200 + 0 caractères lus sur 50200 → ~0,4%, pas 50%.
        val fraction = useCase(
            unevenBook, chapterIndex = 1, sentenceIndex = 0, totalSentences = 500, characterOffset = 0
        )
        assertTrue(fraction < 0.01f, "La pondération par longueur doit donner un pourcentage proche de 0, pas 50% (trouvé: $fraction)")
    }

    @Test
    fun `pondère correctement à mi-parcours d'un long chapitre`() = runTest {
        // Milieu du chapitre de 50 000 caractères : (200 + 25000) / 50200 ≈ 0,502
        val fraction = useCase(
            unevenBook, chapterIndex = 1, sentenceIndex = 250, totalSentences = 500, characterOffset = 25_000
        )
        assertEquals(0.502f, fraction, 0.005f)
    }

    @Test
    fun `livre sans charCount dégrade vers la formule non pondérée`() = runTest {
        // testBook.tocEntries est vide (livre importé avant l'ajout de charCount) → dégradation
        // gracieuse vers l'ancienne formule, pas de division par zéro ni de blocage.
        val fraction = useCase(testBook, chapterIndex = 4, sentenceIndex = 50, totalSentences = 100)
        assertEquals(0.45f, fraction, 0.01f)
    }

    @Test
    fun `le progrès est bien persisté via le repository`() = runTest {
        val savedProgress = slot<ReadingProgress>()
        coEvery { bookRepository.saveProgress(capture(savedProgress)) } just Runs

        useCase(testBook, chapterIndex = 3, sentenceIndex = 42, totalSentences = 100)

        coVerify(exactly = 1) { bookRepository.saveProgress(any()) }
        assertEquals("book-1", savedProgress.captured.bookId)
        assertEquals(3, savedProgress.captured.chapterIndex)
        assertEquals(42, savedProgress.captured.sentenceIndex)
    }
}
