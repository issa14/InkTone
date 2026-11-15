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

/** Audit UX D61 : une suppression écrit sa tombe, la suppression d'un livre n'en écrit aucune. */
@RunWith(AndroidJUnit4::class)
class SyncTombstoneDaoTest {

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
            ),
        )
    }

    private suspend fun insertBookmark(id: String) =
        db.bookmarkDao().insert(BookmarkEntity(id, "pub-1", "ch1.xhtml", 0, null, 10, null, null, createdAt = 1L))

    @Test
    fun supprimer_un_signet_le_retire_et_ecrit_sa_tombe() = runTest {
        insertPublication("pub-1")
        insertBookmark("bm-1")

        db.syncTombstoneDao().deleteBookmark("bm-1", deletedAt = 5000L)

        assertTrue(db.bookmarkDao().observeForPublication("pub-1").first().isEmpty())
        val tombstone = db.syncTombstoneDao().getAll().single()
        assertEquals("bm-1", tombstone.id)
        assertEquals("BOOKMARK", tombstone.kind)
        assertEquals(5000L, tombstone.deletedAt)
    }

    @Test
    fun supprimer_un_livre_n_ecrit_aucune_tombe_pour_ses_signets() = runTest {
        insertPublication("pub-1")
        insertBookmark("bm-1")

        db.publicationDao().delete("pub-1")

        assertTrue("la suppression en cascade reste locale", db.syncTombstoneDao().getAll().isEmpty())
    }

    @Test
    fun une_tombe_se_remplace_se_retire_et_se_purge_par_anciennete() = runTest {
        val dao = db.syncTombstoneDao()
        dao.upsert(com.inktone.infrastructure.database.entity.SyncTombstoneEntity("a", "BOOKMARK", 100L))
        dao.upsert(com.inktone.infrastructure.database.entity.SyncTombstoneEntity("a", "BOOKMARK", 300L))
        dao.upsert(com.inktone.infrastructure.database.entity.SyncTombstoneEntity("b", "ANNOTATION", 50L))

        assertEquals(300L, dao.getAll().first { it.id == "a" }.deletedAt)

        dao.purgeBefore(200L)
        assertEquals(listOf("a"), dao.getAll().map { it.id })

        dao.remove("a")
        assertTrue(dao.getAll().isEmpty())
    }
}
