package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingSessionDaoTest {

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
    fun insert_puis_getAllForPublication_retrouve_la_session() = runTest {
        insertPublication("pub-1")
        db.readingSessionDao().insert(
            ReadingSessionEntity("s1", "pub-1", 0L, 100L, "AUDIO", 5, 100L),
        )
        assertEquals(1, db.readingSessionDao().getAllForPublication("pub-1").size)
    }

    @Test
    fun getAllForPublication_ignore_les_sessions_d_une_autre_publication() = runTest {
        insertPublication("pub-1")
        insertPublication("pub-2")
        db.readingSessionDao().insert(ReadingSessionEntity("s1", "pub-1", 0L, 100L, "AUDIO", 5, 100L))
        db.readingSessionDao().insert(ReadingSessionEntity("s2", "pub-2", 0L, 100L, "VISUAL", 3, 50L))
        assertEquals(1, db.readingSessionDao().getAllForPublication("pub-1").size)
    }

    @Test
    fun getAll_retourne_toutes_les_sessions() = runTest {
        insertPublication("pub-1")
        db.readingSessionDao().insert(ReadingSessionEntity("s1", "pub-1", 0L, 100L, "AUDIO", 5, 100L))
        db.readingSessionDao().insert(ReadingSessionEntity("s2", "pub-1", 100L, 200L, "VISUAL", 2, 100L))
        assertEquals(2, db.readingSessionDao().getAll().size)
    }
}
