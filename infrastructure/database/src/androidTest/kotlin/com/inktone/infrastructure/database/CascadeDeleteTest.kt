package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
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
}
