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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requête jointe `PublicationDao.observeFiltered` (Tâche 6.5, §6.5.3) —
 * teste la jointure `reading_states` réelle, pas une simulation en
 * mémoire (contrairement à `FakePublicationRepository`, réservée aux
 * tests de use case).
 */
@RunWith(AndroidJUnit4::class)
class PublicationDaoFilterTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun publication(id: String, chapterCount: Int = 3, favorite: Boolean = false, seriesName: String? = null) =
        PublicationEntity(
            id = id, title = "Titre $id", subtitle = null, authors = emptyList(),
            publisher = null, language = null, description = null, coverUri = null,
            format = "EPUB", fileUri = "content://x/$id", fileHash = "hash-$id", fileSize = 1L,
            chapterCount = chapterCount, seriesName = seriesName, seriesIndex = null, isFavorite = favorite,
            subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
        )

    private fun readingState(publicationId: String, chapterIndex: Int) = ReadingStateEntity(
        publicationId = publicationId, resourceHref = "chap.xhtml", chapterIndex = chapterIndex,
        paragraphIndex = null, charOffset = 0, lastReadAt = 0L, voiceProfileId = null,
        overrideTheme = null, overrideFontSize = null,
    )

    @Test
    fun favoris_filtre_uniquement_les_favoris() = runTest {
        db.publicationDao().insert(publication("pub-1", favorite = true))
        db.publicationDao().insert(publication("pub-2", favorite = false))

        val result = db.publicationDao().observeFiltered("FAVORITES", null).first()

        assertEquals(listOf("pub-1"), result.map { it.id })
    }

    @Test
    fun serie_filtre_par_nom_exact() = runTest {
        db.publicationDao().insert(publication("pub-1", seriesName = "Les Rougon-Macquart"))
        db.publicationDao().insert(publication("pub-2", seriesName = "Autre serie"))

        val result = db.publicationDao().observeFiltered("SERIES", "Les Rougon-Macquart").first()

        assertEquals(listOf("pub-1"), result.map { it.id })
    }

    @Test
    fun non_lu_exclut_les_publications_avec_reading_state() = runTest {
        db.publicationDao().insert(publication("pub-1"))
        db.publicationDao().insert(publication("pub-2"))
        db.readingStateDao().save(readingState("pub-1", chapterIndex = 0))

        val result = db.publicationDao().observeFiltered("UNREAD", null).first()

        assertEquals(listOf("pub-2"), result.map { it.id })
    }

    @Test
    fun en_cours_exclut_le_dernier_chapitre() = runTest {
        db.publicationDao().insert(publication("pub-1", chapterCount = 5))
        db.publicationDao().insert(publication("pub-2", chapterCount = 5))
        db.readingStateDao().save(readingState("pub-1", chapterIndex = 2)) // en cours
        db.readingStateDao().save(readingState("pub-2", chapterIndex = 4)) // dernier chapitre (index 4 = chapterCount-1)

        val inProgress = db.publicationDao().observeFiltered("IN_PROGRESS", null).first()
        val read = db.publicationDao().observeFiltered("READ", null).first()

        assertEquals(listOf("pub-1"), inProgress.map { it.id })
        assertEquals(listOf("pub-2"), read.map { it.id })
    }
}
