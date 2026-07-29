package com.inktone.infrastructure.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Garde-fou de régression (Tâche 7.1bis) — même esprit que les garde-fous
 * K3/K6/K7 (Blueprint §14.6, Tâche 4.10) : un conflit d'id sur
 * `publications` doit échouer bruyamment, jamais effacer silencieusement
 * `reading_states`/`bookmarks`/`annotations` via `ON DELETE CASCADE`.
 *
 * Bug réel trouvé Tâche 7.1 : `PublicationDao.insert()` utilisait
 * `OnConflictStrategy.REPLACE`, qui fait un DELETE+INSERT sur conflit —
 * `bootstrapAndOpenFixture` (scaffolding, `MainActivity`) réinsère le même
 * id à chaque lancement, effaçant silencieusement les annotations créées
 * entre-temps. Découvert par inspection directe de la base SQLite (pas
 * un test qui aurait dû l'attraper avant — d'où ce garde-fou).
 */
@RunWith(AndroidJUnit4::class)
class PublicationConflictRegressionTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun publication(id: String) = PublicationEntity(
        id = id, title = "Titre $id", subtitle = null, authors = emptyList(),
        publisher = null, language = null, description = null, coverUri = null,
        format = "EPUB", fileUri = "content://x/$id", fileHash = "hash-$id", fileSize = 1L,
        chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
        subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
    )

    @Test
    fun reinserer_le_meme_id_echoue_et_ne_supprime_pas_les_enfants() = runTest {
        db.publicationDao().insert(publication("pub-1"))
        db.readingStateDao().save(
            ReadingStateEntity(
                publicationId = "pub-1", resourceHref = "ch1.xhtml", chapterIndex = 0,
                paragraphIndex = null, charOffset = 0, lastReadAt = 0L, voiceProfileId = null,
                overrideTheme = null, overrideFontSize = null,
            ),
        )
        db.bookmarkDao().insert(
            BookmarkEntity(
                id = "bm-1", publicationId = "pub-1", resourceHref = "ch1.xhtml",
                chapterIndex = 0, paragraphIndex = null, charOffset = 0, title = null,
                note = null, createdAt = 0L,
            ),
        )
        db.annotationDao().insert(
            AnnotationEntity(
                id = "an-1", publicationId = "pub-1",
                startResourceHref = "ch1.xhtml", startChapterIndex = 0, startParagraphIndex = null, startCharOffset = 0,
                endResourceHref = "ch1.xhtml", endChapterIndex = 0, endParagraphIndex = null, endCharOffset = 10,
                color = "YELLOW", content = null, createdAt = 0L, updatedAt = 0L,
            ),
        )

        var thrown: Throwable? = null
        try {
            db.publicationDao().insert(publication("pub-1"))
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull("un conflit d'id doit lever une exception, pas s'executer silencieusement", thrown)

        // Le vrai test : les enfants doivent avoir survécu à la tentative
        // ratée, pas avoir été effacés silencieusement avant l'échec.
        assertEquals("pub-1", db.readingStateDao().get("pub-1")?.publicationId)
        assertEquals(1, db.bookmarkDao().observeForPublication("pub-1").first().size)
        assertEquals(1, db.annotationDao().observeForPublication("pub-1").first().size)
    }
}
