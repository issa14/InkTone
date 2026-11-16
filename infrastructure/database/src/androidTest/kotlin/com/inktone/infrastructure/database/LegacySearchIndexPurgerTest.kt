package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySearchIndexPurgerTest {
    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun legacyTables(): Int =
        db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master WHERE name LIKE 'sentence_fts%'").use {
            it.moveToFirst(); it.getInt(0)
        }

    @Test
    fun sans_ancien_index_il_ne_fait_rien() {
        assertTrue(LegacySearchIndexPurger.purge(db.openHelper.writableDatabase))
    }

    @Test
    fun un_ancien_index_volumineux_est_vide_par_lots_puis_supprime() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE VIRTUAL TABLE sentence_fts USING FTS4(publicationId, chapterIndex, resourceHref, charOffset, text)")
        sqlite.beginTransaction()
        try {
            repeat(2_000) { i ->
                sqlite.execSQL(
                    "INSERT INTO sentence_fts (publicationId, chapterIndex, resourceHref, charOffset, text) VALUES (?, ?, ?, ?, ?)",
                    arrayOf("pub-${i % 7}", i % 10, "ch${i % 10}.xhtml", i, "Phrase numéro $i d'un roman imaginaire."),
                )
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
        assertEquals(true, legacyTables() > 0)

        assertTrue(LegacySearchIndexPurger.purge(sqlite))
        assertEquals(0, legacyTables())
    }

    @Test
    fun interrompu_il_reprend_ou_il_s_est_arrete() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE VIRTUAL TABLE sentence_fts USING FTS4(publicationId, chapterIndex, resourceHref, charOffset, text)")
        repeat(50) { i ->
            sqlite.execSQL(
                "INSERT INTO sentence_fts (publicationId, chapterIndex, resourceHref, charOffset, text) VALUES ('p', 0, 'c.xhtml', $i, 'texte $i')",
            )
        }

        assertEquals("arrêté d'emblée : rien n'est fini", false, LegacySearchIndexPurger.purge(sqlite, shouldContinue = { false }))
        assertTrue("repris : tout est nettoyé", LegacySearchIndexPurger.purge(sqlite))
        assertEquals(0, legacyTables())
    }
}
