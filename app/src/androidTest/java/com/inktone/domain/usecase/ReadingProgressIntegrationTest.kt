package com.inktone.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.data.database.InkToneDatabase
import com.inktone.data.database.ReadingProgressDao
import com.inktone.data.database.entity.BookEntity
import com.inktone.data.database.entity.ReadingProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test de non-régression pour la tâche 1.8 (plan Top-Tier, Phase 1) : reproduit le scénario
 * exact décrit dans l'audit — ouverture d'un livre → scroll manuel SANS lecture TTS → simulation
 * de fermeture de process (un `SavedStateHandle` neuf n'a plus rien) → réouverture → chapitre et
 * phrase restaurés depuis Room, pas perdus.
 *
 * Base Room réelle en mémoire (pas de mock de DAO) : c'est justement la persistance Room, pas
 * `SavedStateHandle`, qui doit survivre à une vraie fermeture de process — ce test le vérifie
 * au niveau DAO + use cases plutôt qu'au niveau Compose/Activity (infrastructure dédiée prévue
 * en Phase 6.2).
 */
@RunWith(AndroidJUnit4::class)
class ReadingProgressIntegrationTest {

    private lateinit var db: InkToneDatabase
    private lateinit var dao: ReadingProgressDao
    private val resolvePosition = ResolveReadingPositionUseCase()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InkToneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.readingProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scrollManuelSansAudio_survitAUneSimulationDeProcessDeath() = runBlocking {
        db.bookDao().insert(
            BookEntity(
                id = "book-x",
                title = "Livre de test",
                author = "Auteur",
                description = null,
                filePath = "/fake/path.epub",
                coverPath = null,
                totalChapters = 5,
                language = "fr",
                addedAt = System.currentTimeMillis()
            )
        )

        // 1. Ouverture initiale : aucune position en base, SavedStateHandle vide (1er lancement).
        val initial = resolvePosition(
            dbChapterIndex = dao.getProgressForBook("book-x")?.chapterIndex,
            dbSentenceIndex = dao.getProgressForBook("book-x")?.sentenceIndex,
            savedChapterIndex = 0,
            savedSentenceIndex = 0,
            totalChapters = 5
        )
        assertEquals(0, initial.chapterIndex)
        assertEquals(0, initial.sentenceIndex)

        // 2. Scroll manuel SANS lecture TTS — équivalent de ce que
        //    ReaderViewModel.onManualPositionChanged() persiste via CalculateReadingProgressUseCase.
        dao.saveProgress(
            ReadingProgress(
                bookId = "book-x",
                chapterIndex = 2,
                sentenceIndex = 7,
                characterOffset = 150,
                totalProgressFraction = 0.4f,
                updatedAt = System.currentTimeMillis(),
                source = "MANUAL_SCROLL"
            )
        )

        // 3. Simulation de fermeture de process : un nouveau SavedStateHandle n'a plus rien
        //    (contrairement au cas où le process reste vivant en arrière-plan). Seule la base
        //    Room, qui a survécu, peut restaurer la position.
        val afterProcessDeath = resolvePosition(
            dbChapterIndex = dao.getProgressForBook("book-x")?.chapterIndex,
            dbSentenceIndex = dao.getProgressForBook("book-x")?.sentenceIndex,
            savedChapterIndex = 0, // SavedStateHandle neuf, sans valeur restaurée
            savedSentenceIndex = 0,
            totalChapters = 5
        )

        // 4. Réouverture : chapitre 2, phrase 7 — pas le début du livre.
        assertEquals(2, afterProcessDeath.chapterIndex)
        assertEquals(7, afterProcessDeath.sentenceIndex)

        // Le badge % de la bibliothèque (totalProgressFraction) et la source sont eux aussi
        // bien la valeur écrite par le scroll manuel, pas une valeur par défaut/écrasée.
        val persisted = dao.getProgressForBook("book-x")
        assertEquals(0.4f, persisted?.totalProgressFraction ?: -1f, 0.001f)
        assertEquals("MANUAL_SCROLL", persisted?.source)
    }
}
