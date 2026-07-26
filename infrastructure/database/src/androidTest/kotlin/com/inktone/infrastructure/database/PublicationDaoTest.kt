package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicationDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun publication(id: String, hash: String, favorite: Boolean = false) = PublicationEntity(
        id = id, title = "Titre $id", subtitle = null, authors = emptyList(),
        publisher = null, language = null, description = null, coverUri = null,
        format = "EPUB", fileUri = "content://x/$id", fileHash = hash, fileSize = 1L,
        chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = favorite,
        subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
    )

    @Test
    fun insere_puis_retrouve_par_id() = runTest {
        db.publicationDao().insert(publication("pub-1", "hash-1"))
        assertEquals("Titre pub-1", db.publicationDao().getById("pub-1")?.title)
    }

    @Test
    fun retrouve_par_fileHash_indexe() = runTest {
        db.publicationDao().insert(publication("pub-1", "hash-unique"))
        assertEquals("pub-1", db.publicationDao().getByFileHash("hash-unique")?.id)
    }

    @Test
    fun met_a_jour_le_favori() = runTest {
        db.publicationDao().insert(publication("pub-1", "hash-1"))
        db.publicationDao().setFavorite("pub-1", true)
        assertTrue(db.publicationDao().getById("pub-1")!!.isFavorite)
    }

    @Test
    fun supprime_une_publication() = runTest {
        db.publicationDao().insert(publication("pub-1", "hash-1"))
        db.publicationDao().delete("pub-1")
        assertNull(db.publicationDao().getById("pub-1"))
    }

    @Test
    fun observeAll_reflete_les_insertions() = runTest {
        db.publicationDao().insert(publication("pub-1", "hash-1"))
        db.publicationDao().insert(publication("pub-2", "hash-2"))
        assertEquals(2, db.publicationDao().observeAll().first().size)
    }
}
