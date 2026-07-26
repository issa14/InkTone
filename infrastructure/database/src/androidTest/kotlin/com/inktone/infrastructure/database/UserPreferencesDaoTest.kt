package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun get_retourne_nul_avant_toute_ecriture() = runTest {
        assertNull(db.userPreferencesDao().get())
    }

    @Test
    fun upsert_puis_get_retourne_les_preferences() = runTest {
        db.userPreferencesDao().upsert(
            UserPreferencesEntity(0, "DARK", 20, "SHERPA_ONNX", false, "fr"),
        )
        assertEquals("DARK", db.userPreferencesDao().get()?.theme)
    }

    @Test
    fun upsert_remplace_la_ligne_unique() = runTest {
        db.userPreferencesDao().upsert(UserPreferencesEntity(0, "LIGHT", 16, "SHERPA_ONNX", false, "fr"))
        db.userPreferencesDao().upsert(UserPreferencesEntity(0, "SEPIA", 22, "PIPER", true, "en"))
        assertEquals("SEPIA", db.userPreferencesDao().observe().first()?.theme)
        assertEquals(22, db.userPreferencesDao().get()?.fontSize)
    }
}
