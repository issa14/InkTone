package com.inktone.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste [MIGRATION_16_17] (ajout de la colonne `status` — voir PLAN import EPUB §3) contre une
 * base SQLite brute recréant un schéma `books` minimal en v16, même approche que
 * [InkToneDatabaseMigrationTest] pour [MIGRATION_15_16].
 */
@RunWith(AndroidJUnit4::class)
class InkToneDatabaseMigration16To17Test {

    private val dbName = "migration_16_17_test.db"
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(16) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE books (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL
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
    fun migrate16To17_ajouteLaColonneStatusAvecDefautReady() {
        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO books (id, title) VALUES ('book-A', 'Livre A')")

        MIGRATION_16_17.migrate(db)

        // Une ligne déjà présente avant la migration est forcément déjà terminée.
        db.query("SELECT status FROM books WHERE id = 'book-A'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("READY", c.getString(0))
        }
    }
}
