package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun annotation(id: String, chapterIndex: Int, charOffset: Int, createdAt: Long) = AnnotationEntity(
        id = id, publicationId = "pub-1",
        startResourceHref = "ch$chapterIndex.xhtml", startChapterIndex = chapterIndex,
        startParagraphIndex = null, startCharOffset = charOffset,
        endResourceHref = "ch$chapterIndex.xhtml", endChapterIndex = chapterIndex,
        endParagraphIndex = null, endCharOffset = charOffset + 10,
        color = "YELLOW", content = null, createdAt = createdAt, updatedAt = createdAt,
    )

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
    fun insert_update_delete_fonctionnent() = runTest {
        insertPublication("pub-1")
        db.annotationDao().insert(annotation("a1", 0, 0, 0L))
        db.annotationDao().update(annotation("a1", 0, 0, 0L).copy(content = "note"))
        assertEquals("note", db.annotationDao().observeForPublication("pub-1").first().first().content)
        db.annotationDao().delete("a1")
        assertEquals(0, db.annotationDao().observeForPublication("pub-1").first().size)
    }

    @Test
    fun observeForPublication_trie_par_position_locator_pas_par_createdAt() = runTest {
        insertPublication("pub-1")
        // Insérées dans l'ordre inverse de leur position dans le livre :
        // la plus récente créée (a_late) est la première du chapitre.
        db.annotationDao().insert(annotation("a_late", chapterIndex = 1, charOffset = 500, createdAt = 100L))
        db.annotationDao().insert(annotation("a_early", chapterIndex = 0, charOffset = 10, createdAt = 200L))
        db.annotationDao().insert(annotation("a_middle", chapterIndex = 0, charOffset = 900, createdAt = 300L))

        val ordered = db.annotationDao().observeForPublication("pub-1").first()

        assertEquals(listOf("a_early", "a_middle", "a_late"), ordered.map { it.id })
    }
}
