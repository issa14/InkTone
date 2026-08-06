package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingSessionEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CascadeDeleteTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun supprimer_une_publication_vide_reading_state_et_bookmarks() = runTest {
        val pubId = "pub-1"
        db.publicationDao().insert(
            PublicationEntity(
                id = pubId, title = "Test", subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x", fileHash = "h1", fileSize = 1L,
                chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            )
        )
        db.readingStateDao().save(ReadingStateEntity(pubId, "ch1.xhtml", 0, null, 0, 0L, null, null, null))
        db.bookmarkDao().insert(BookmarkEntity("bm-1", pubId, "ch1.xhtml", 0, null, 0, null, null, 0L))

        db.publicationDao().delete(pubId)

        assertNull(db.readingStateDao().get(pubId))
        assertTrue(db.bookmarkDao().observeForPublication(pubId).first().isEmpty())
    }

    /**
     * Lot 2b.2 — le popup d'actions par livre avertit que « les marque-pages
     * et notes associés seront également supprimés » (texte de confirmation
     * obligatoire, UX §Bibliothèque état peuplé). Vérifié ici pour les
     * quatre entités déclarées `onDelete = ForeignKey.CASCADE` sur
     * `publicationId` — pas seulement supposé exact parce que l'annotation
     * est présente dans le code.
     */
    @Test
    fun supprimer_une_publication_vide_annotations_et_sessions_de_lecture() = runTest {
        val pubId = "pub-2"
        db.publicationDao().insert(
            PublicationEntity(
                id = pubId, title = "Test", subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x", fileHash = "h2", fileSize = 1L,
                chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            )
        )
        db.annotationDao().insert(
            AnnotationEntity(
                id = "an-1", publicationId = pubId,
                startResourceHref = "ch1.xhtml", startChapterIndex = 0, startParagraphIndex = null, startCharOffset = 0,
                endResourceHref = "ch1.xhtml", endChapterIndex = 0, endParagraphIndex = null, endCharOffset = 10,
                color = "YELLOW", content = null, createdAt = 0L, updatedAt = 0L,
            )
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity(
                id = "rs-1", publicationId = pubId, startedAt = 0L, endedAt = null,
                mode = "SCROLL", sentencesRead = 5, durationMs = 1000L,
            )
        )

        db.publicationDao().delete(pubId)

        assertTrue(db.annotationDao().observeForPublication(pubId).first().isEmpty())
        assertTrue(db.readingSessionDao().getAllForPublication(pubId).isEmpty())
    }
}
