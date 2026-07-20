package com.inktone.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste [MIGRATION_15_16] (fusion de `progress` et `reading_progress` — architecture.md §11)
 * directement contre une base SQLite brute recréant le schéma v15, sans passer par
 * `MigrationTestHelper` : `exportSchema` était `false` jusqu'à ce changement, donc aucun
 * schéma JSON historique n'existe pour les versions 1 à 15 — `MigrationTestHelper.createDatabase`
 * en aurait besoin. `MIGRATION_15_16.migrate(SupportSQLiteDatabase)` est testable directement,
 * ce qui suffit à couvrir ce qui compte réellement ici : la logique de fusion des données.
 */
@RunWith(AndroidJUnit4::class)
class InkToneDatabaseMigrationTest {

    private val dbName = "migration_15_16_test.db"
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(15) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE books (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE progress (
                        bookId TEXT NOT NULL PRIMARY KEY,
                        currentChapterIndex INTEGER NOT NULL,
                        currentSentenceIndex INTEGER NOT NULL,
                        totalProgressFraction REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    CREATE TABLE reading_progress (
                        bookId TEXT NOT NULL PRIMARY KEY,
                        chapterIndex INTEGER NOT NULL DEFAULT 0,
                        sentenceIndex INTEGER NOT NULL DEFAULT 0,
                        characterOffset INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    @After
    fun tearDown() {
        openHelper.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate15To16_fusionneLesDeuxTablesSansPerte() {
        val db = openHelper.writableDatabase

        db.execSQL("INSERT INTO books (id, title) VALUES ('book-A', 'Livre A')")
        db.execSQL("INSERT INTO books (id, title) VALUES ('book-B', 'Livre B')")
        db.execSQL("INSERT INTO books (id, title) VALUES ('book-C', 'Livre C')")

        // book-A : présent dans les deux anciennes tables, reading_progress plus récent que progress
        db.execSQL("INSERT INTO progress VALUES ('book-A', 2, 10, 0.3, 1000)")
        db.execSQL("INSERT INTO reading_progress VALUES ('book-A', 5, 20, 123, 2000)")

        // book-B : présent uniquement dans l'ancienne table progress (badge % bibliothèque)
        db.execSQL("INSERT INTO progress VALUES ('book-B', 1, 0, 0.1, 500)")

        // book-C : présent uniquement dans l'ancienne table reading_progress (reprise Reader)
        db.execSQL("INSERT INTO reading_progress VALUES ('book-C', 7, 3, 456, 3000)")

        MIGRATION_15_16.migrate(db)

        // Les anciennes tables ont disparu au profit du schéma unifié
        assertFalse(tableExists(db, "progress"))
        assertTrue(tableExists(db, "reading_progress"))

        // book-A : position depuis reading_progress (table la plus récente/lue par le Reader),
        // totalProgressFraction préservée depuis progress plutôt que perdue.
        db.query("SELECT chapterIndex, sentenceIndex, characterOffset, totalProgressFraction, updatedAt, source FROM reading_progress WHERE bookId = 'book-A'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(5, c.getInt(0))
            assertEquals(20, c.getInt(1))
            assertEquals(123, c.getInt(2))
            assertEquals(0.3, c.getDouble(3), 0.001)
            assertEquals(2000L, c.getLong(4))
            assertEquals("TTS", c.getString(5))
        }

        // book-B : pas de ligne reading_progress d'origine → position par défaut (0/0/0),
        // fraction préservée depuis progress. Rien n'est perdu, rien n'est inventé.
        db.query("SELECT chapterIndex, sentenceIndex, characterOffset, totalProgressFraction FROM reading_progress WHERE bookId = 'book-B'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0.1, c.getDouble(3), 0.001)
        }

        // book-C : pas de ligne progress d'origine → fraction par défaut 0.0,
        // position préservée depuis reading_progress.
        db.query("SELECT chapterIndex, sentenceIndex, characterOffset, totalProgressFraction FROM reading_progress WHERE bookId = 'book-C'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(7, c.getInt(0))
            assertEquals(3, c.getInt(1))
            assertEquals(456, c.getInt(2))
            assertEquals(0.0, c.getDouble(3), 0.001)
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { c ->
            return c.count > 0
        }
    }
}
