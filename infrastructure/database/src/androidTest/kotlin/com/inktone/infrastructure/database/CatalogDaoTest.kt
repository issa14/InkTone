package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.CatalogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun catalog(id: String, createdAt: Long = 1L) = CatalogEntity(
        id = id, name = "Catalogue $id", rootUrl = "https://example.com/$id.opds",
        searchTemplateUrl = null, createdAt = createdAt,
    )

    @Test
    fun upsert_puis_getById_retrouve_le_catalogue() = runTest {
        db.catalogDao().upsert(catalog("cat-1"))
        assertEquals("Catalogue cat-1", db.catalogDao().getById("cat-1")?.name)
    }

    @Test
    fun observeAll_emet_les_catalogues_tries_par_creation() = runTest {
        db.catalogDao().upsert(catalog("cat-2", createdAt = 2L))
        db.catalogDao().upsert(catalog("cat-1", createdAt = 1L))
        val all = db.catalogDao().observeAll().first()
        assertEquals(listOf("cat-1", "cat-2"), all.map { it.id })
    }

    @Test
    fun upsert_remplace_un_catalogue_existant_meme_id() = runTest {
        db.catalogDao().upsert(catalog("cat-1"))
        db.catalogDao().upsert(catalog("cat-1").copy(searchTemplateUrl = "https://example.com/search?q={searchTerms}"))
        assertEquals(1, db.catalogDao().observeAll().first().size)
        assertEquals("https://example.com/search?q={searchTerms}", db.catalogDao().getById("cat-1")?.searchTemplateUrl)
    }

    @Test
    fun delete_retire_le_catalogue() = runTest {
        db.catalogDao().upsert(catalog("cat-1"))
        db.catalogDao().delete("cat-1")
        assertNull(db.catalogDao().getById("cat-1"))
    }
}
