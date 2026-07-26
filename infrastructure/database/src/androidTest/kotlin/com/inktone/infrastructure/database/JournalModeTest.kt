package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie K1 sur une base RÉELLEMENT fichier — un test sur base en
 * mémoire ne le peut pas : SQLite ignore WAL pour `:memory:` et utilise
 * toujours MEMORY, quel que soit `setJournalMode`. C'est pourquoi ce
 * test, seul de la suite Phase 2, construit la base exactement comme
 * DatabaseModule le fait en production, plutôt que via
 * `inMemoryDatabaseBuilder` comme les autres tests de cette phase.
 */
@RunWith(AndroidJUnit4::class)
class JournalModeTest {

    @Test
    fun la_base_de_production_utilise_wal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "journal-mode-test.db"
        val db = Room.databaseBuilder(context, InkToneDatabase::class.java, dbName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

        db.openHelper.writableDatabase.query("PRAGMA journal_mode", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            assertEquals("wal", cursor.getString(0).lowercase())
        }

        db.close()
        context.deleteDatabase(dbName)
    }
}
