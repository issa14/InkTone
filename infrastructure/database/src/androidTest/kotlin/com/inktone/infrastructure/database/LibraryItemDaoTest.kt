package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lot 4, tâche 4.5 — vue globale marque-pages + annotations : observation
 * croisée entre les deux sources, désynchronisation de titre, recherche/tri
 * SQL, épinglage. Gabarit de migration séparé dans [DatabaseMigrationTest].
 */
@RunWith(AndroidJUnit4::class)
class LibraryItemDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun insertPublication(id: String, title: String) {
        db.publicationDao().insert(
            PublicationEntity(
                id = id, title = title, subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x/$id", fileHash = "h-$id", fileSize = 1L,
                chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            ),
        )
    }

    private suspend fun insertBookmark(id: String, publicationId: String, createdAt: Long, excerpt: String? = null) {
        db.bookmarkDao().insert(
            BookmarkEntity(
                id = id, publicationId = publicationId, resourceHref = "ch1.xhtml", chapterIndex = 0,
                paragraphIndex = null, charOffset = 0, title = null, note = null, excerpt = excerpt,
                createdAt = createdAt,
            ),
        )
    }

    private suspend fun insertAnnotation(id: String, publicationId: String, createdAt: Long, content: String? = null, excerpt: String? = null) {
        db.annotationDao().insert(
            AnnotationEntity(
                id = id, publicationId = publicationId,
                startResourceHref = "ch1.xhtml", startChapterIndex = 0, startParagraphIndex = null, startCharOffset = 0,
                endResourceHref = "ch1.xhtml", endChapterIndex = 0, endParagraphIndex = null, endCharOffset = 10,
                color = "YELLOW", content = content, excerpt = excerpt,
                createdAt = createdAt, updatedAt = createdAt,
            ),
        )
    }

    @Test
    fun observe_fusionne_les_marque_pages_et_annotations_de_plusieurs_publications_tri_chronologique() = runTest {
        insertPublication("pub-1", "Premier livre")
        insertPublication("pub-2", "Second livre")
        insertBookmark("bm-1", "pub-1", createdAt = 100)
        insertAnnotation("an-1", "pub-2", createdAt = 300)
        insertAnnotation("an-2", "pub-1", createdAt = 200, content = "Une note")

        val items = db.libraryItemDao().observe(typeFilter = null, searchQuery = "", alphabetical = false).first()

        assertEquals(3, items.size)
        assertEquals(listOf("an-1", "an-2", "bm-1"), items.map { it.id }) // ordre chronologique décroissant
    }

    @Test
    fun observe_resout_le_titre_par_jointure_et_reste_a_jour_apres_renommage_sans_cache() = runTest {
        insertPublication("pub-1", "Ancien titre")
        insertBookmark("bm-1", "pub-1", createdAt = 100)

        assertEquals("Ancien titre", db.libraryItemDao().observe(null, "", false).first().single().publicationTitle)

        db.publicationDao().update(
            db.publicationDao().observeAll().first().single().copy(title = "Nouveau titre"),
        )

        assertEquals("Nouveau titre", db.libraryItemDao().observe(null, "", false).first().single().publicationTitle)
    }

    @Test
    fun observe_filtre_par_type_signets_surlignages_notes() = runTest {
        insertPublication("pub-1", "Livre")
        insertBookmark("bm-1", "pub-1", createdAt = 100)
        insertAnnotation("an-1", "pub-1", createdAt = 200) // surlignage sans note
        insertAnnotation("an-2", "pub-1", createdAt = 300, content = "Note") // annotation avec note

        assertEquals(listOf("bm-1"), db.libraryItemDao().observe("BOOKMARK", "", false).first().map { it.id })
        assertEquals(listOf("an-1"), db.libraryItemDao().observe("HIGHLIGHT", "", false).first().map { it.id })
        assertEquals(listOf("an-2"), db.libraryItemDao().observe("NOTE", "", false).first().map { it.id })
        assertEquals(3, db.libraryItemDao().observe(null, "", false).first().size)
    }

    @Test
    fun observe_recherche_sur_l_extrait_la_note_et_le_titre_au_niveau_requete() = runTest {
        insertPublication("pub-1", "Les Misérables")
        insertPublication("pub-2", "Autre ouvrage")
        insertBookmark("bm-1", "pub-1", createdAt = 100, excerpt = "Jean Valjean marchait")
        insertAnnotation("an-1", "pub-2", createdAt = 200, content = "à comparer avec Cosette")
        insertBookmark("bm-2", "pub-2", createdAt = 300, excerpt = "Rien à voir")

        // Un mot présent seulement dans un extrait remonte l'élément.
        assertEquals(listOf("bm-1"), db.libraryItemDao().observe(null, "Valjean", false).first().map { it.id })
        // Un mot présent seulement dans une note remonte l'élément.
        assertEquals(listOf("an-1"), db.libraryItemDao().observe(null, "Cosette", false).first().map { it.id })
        // Un mot présent seulement dans le titre d'ouvrage remonte tous ses éléments.
        assertEquals(setOf("an-1", "bm-2"), db.libraryItemDao().observe(null, "Autre", false).first().map { it.id }.toSet())
    }

    @Test
    fun observe_epingle_remonte_en_tete_quel_que_soit_le_tri() = runTest {
        insertPublication("pub-1", "B livre")
        insertPublication("pub-2", "A livre")
        insertBookmark("bm-1", "pub-1", createdAt = 100)
        insertBookmark("bm-2", "pub-2", createdAt = 200)

        db.bookmarkDao().setPinned("bm-1", true)

        assertEquals("bm-1", db.libraryItemDao().observe(null, "", alphabetical = false).first().first().id)
        assertEquals("bm-1", db.libraryItemDao().observe(null, "", alphabetical = true).first().first().id)
    }

    @Test
    fun observe_tri_alphabetique_reordonne_par_titre_d_ouvrage() = runTest {
        insertPublication("pub-1", "Zorro")
        insertPublication("pub-2", "Alceste")
        insertBookmark("bm-1", "pub-1", createdAt = 100)
        insertBookmark("bm-2", "pub-2", createdAt = 200)

        assertEquals(listOf("bm-2", "bm-1"), db.libraryItemDao().observe(null, "", alphabetical = true).first().map { it.id })
    }
}
