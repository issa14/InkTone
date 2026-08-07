package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun insertPublication(id: String) {
        db.publicationDao().insert(
            PublicationEntity(
                id = id, title = "Titre", subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x/$id", fileHash = "h-$id", fileSize = 1L,
                chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            )
        )
    }

    @Test
    fun insert_puis_observeForPublication_retrouve_le_signet() = runTest {
        insertPublication("pub-1")
        db.bookmarkDao().insert(BookmarkEntity("bm-1", "pub-1", "ch1.xhtml", 0, null, 10, null, null, createdAt = 0L))
        assertEquals(1, db.bookmarkDao().observeForPublication("pub-1").first().size)
    }

    @Test
    fun delete_retire_le_signet() = runTest {
        insertPublication("pub-1")
        db.bookmarkDao().insert(BookmarkEntity("bm-1", "pub-1", "ch1.xhtml", 0, null, 10, null, null, createdAt = 0L))
        db.bookmarkDao().delete("bm-1")
        assertTrue(db.bookmarkDao().observeForPublication("pub-1").first().isEmpty())
    }
}
