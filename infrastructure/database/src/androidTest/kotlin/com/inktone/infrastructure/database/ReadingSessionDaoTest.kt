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
import org.junit.Assert.assertTrue
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

    // Lot Statistiques Palier 1 — tests des nouvelles requêtes SQL-first

    @Test
    fun getTotalStats_agrege_visuel_tts_et_publications_distinctes() = runTest {
        insertPublication("pub-1")
        insertPublication("pub-2")
        db.readingSessionDao().insert(
            ReadingSessionEntity("s1", "pub-1", 0L, 100L, "VISUAL", 0, 0, visualDurationMs = 500, ttsDurationMs = 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s2", "pub-1", 0L, 100L, "AUDIO", 0, 0, visualDurationMs = 0, ttsDurationMs = 300),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s3", "pub-2", 0L, 100L, "VISUAL", 0, 0, visualDurationMs = 200, ttsDurationMs = 100),
        )

        val stats = db.readingSessionDao().getTotalStats()
        assertEquals(700L, stats.totalVisualMs)
        assertEquals(400L, stats.totalTtsMs)
        assertEquals(2, stats.booksInteracted)
    }

    @Test
    fun getDistinctReadingDays_retourne_les_jours_tries_decroissants() = runTest {
        insertPublication("pub-1")
        // Deux sessions sur le même jour, une sur un jour différent
        val day1 = 1690000000000L // un lundi
        val day2 = day1 + 86400000L  // le lendemain
        db.readingSessionDao().insert(
            ReadingSessionEntity("s1", "pub-1", day1, day1 + 60000, "AUDIO", 0, 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s2", "pub-1", day1 + 3600000, day1 + 7200000, "VISUAL", 0, 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s3", "pub-1", day2, day2 + 60000, "AUDIO", 0, 0),
        )

        val days = db.readingSessionDao().getDistinctReadingDays()
        assertEquals(2, days.size)
        // Tri décroissant
        assertTrue(days[0] > days[1])
    }

    @Test
    fun getLastReadPublicationId_retourne_la_publication_la_plus_recente() = runTest {
        insertPublication("pub-a")
        insertPublication("pub-b")
        db.readingSessionDao().insert(
            ReadingSessionEntity("s1", "pub-a", 0L, 100L, "AUDIO", 0, 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s2", "pub-b", 0L, 200L, "VISUAL", 0, 0),
        )

        assertEquals("pub-b", db.readingSessionDao().getLastReadPublicationId())
    }

    @Test
    fun getByPublicationId_retourne_les_sessions_d_un_livre_triees_par_date() = runTest {
        insertPublication("pub-1")
        db.readingSessionDao().insert(
            ReadingSessionEntity("s1", "pub-1", 0L, 100L, "AUDIO", 0, 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s2", "pub-1", 200L, 300L, "VISUAL", 0, 0),
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity("s3", "pub-2", 0L, 100L, "AUDIO", 0, 0),
        )

        val sessions = db.readingSessionDao().getByPublicationId("pub-1")
        assertEquals(2, sessions.size)
        // Tri décroissant par startedAt
        assertEquals("s2", sessions.first().id)
    }
}
