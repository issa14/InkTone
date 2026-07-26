package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.VoiceProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceProfileDaoTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun profile(id: String, speed: Float = 1.0f) = VoiceProfileEntity(
        id = id, engine = "SHERPA_ONNX", voice = "fr_FR-upmc-medium", language = "fr-FR",
        speed = speed, pitch = 1.0f, volume = 1.0f, style = null,
    )

    @Test
    fun save_puis_getById_retrouve_le_profil() = runTest {
        db.voiceProfileDao().save(profile("vp-1"))
        assertEquals("fr_FR-upmc-medium", db.voiceProfileDao().getById("vp-1")?.voice)
    }

    @Test
    fun save_remplace_un_profil_existant_meme_id() = runTest {
        db.voiceProfileDao().save(profile("vp-1", speed = 1.0f))
        db.voiceProfileDao().save(profile("vp-1", speed = 1.5f))
        assertEquals(1, db.voiceProfileDao().getAll().size)
        assertEquals(1.5f, db.voiceProfileDao().getById("vp-1")?.speed)
    }

    @Test
    fun delete_retire_le_profil() = runTest {
        db.voiceProfileDao().save(profile("vp-1"))
        db.voiceProfileDao().delete("vp-1")
        assertNull(db.voiceProfileDao().getById("vp-1"))
    }
}
