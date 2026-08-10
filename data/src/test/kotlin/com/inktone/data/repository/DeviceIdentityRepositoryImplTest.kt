package com.inktone.data.repository

import com.inktone.core.testing.fake.FakePreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lot 11, tâche 11.3, point 4 — l'identifiant d'appareil est stable entre deux « lancements » (deux instances pointant vers la même persistance). */
class DeviceIdentityRepositoryImplTest {

    @Test
    fun getOrCreate_genere_un_identifiant_stable_entre_deux_appels() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val repository = DeviceIdentityRepositoryImpl(preferencesRepository)

        val first = repository.getOrCreate()
        val second = repository.getOrCreate()

        assertEquals(first.id, second.id)
        assertEquals(first.displayName, second.displayName)
    }

    @Test
    fun getOrCreate_reste_stable_meme_apres_un_nouveau_lancement_simule() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val firstLaunch = DeviceIdentityRepositoryImpl(preferencesRepository).getOrCreate()

        // Nouvelle instance sur la même persistance — simule un redémarrage de l'app.
        val secondLaunch = DeviceIdentityRepositoryImpl(preferencesRepository).getOrCreate()

        assertEquals(firstLaunch.id, secondLaunch.id)
    }
}
