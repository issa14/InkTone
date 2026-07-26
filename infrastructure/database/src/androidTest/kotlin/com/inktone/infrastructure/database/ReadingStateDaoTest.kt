package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStateDaoTest {

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
    fun save_puis_get_retourne_le_meme_etat() = runTest {
        insertPublication("pub-1")
        db.readingStateDao().save(ReadingStateEntity("pub-1", "ch1.xhtml", 2, null, 150, 100L, null, null, null))
        val state = db.readingStateDao().get("pub-1")
        assertEquals(150, state?.charOffset)
    }

    @Test
    fun save_remplace_l_etat_precedent_meme_publication() = runTest {
        insertPublication("pub-1")
        db.readingStateDao().save(ReadingStateEntity("pub-1", "ch1.xhtml", 0, null, 0, 100L, null, null, null))
        db.readingStateDao().save(ReadingStateEntity("pub-1", "ch2.xhtml", 1, null, 50, 200L, null, null, null))
        assertEquals("ch2.xhtml", db.readingStateDao().get("pub-1")?.resourceHref)
    }

    @Test
    fun observe_reflete_l_etat_courant() = runTest {
        insertPublication("pub-1")
        db.readingStateDao().save(ReadingStateEntity("pub-1", "ch1.xhtml", 0, null, 0, 100L, null, null, null))
        assertEquals(0, db.readingStateDao().observe("pub-1").first()?.chapterIndex)
    }

    @Test
    fun delete_retire_l_etat() = runTest {
        insertPublication("pub-1")
        db.readingStateDao().save(ReadingStateEntity("pub-1", "ch1.xhtml", 0, null, 0, 100L, null, null, null))
        db.readingStateDao().delete("pub-1")
        assertNull(db.readingStateDao().get("pub-1"))
    }
}
