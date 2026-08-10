package com.inktone.data.repository

import com.inktone.core.testing.fake.FakeSyncProvider
import com.inktone.domain.model.DeviceFleetEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 11, tâche 11.9, point 1 — deux appareils apparaissent tous deux dans la flotte, sans que l'un efface l'autre. */
class RemoteDeviceFleetRepositoryTest {

    @Test
    fun touchCurrentDevice_ajoute_sans_effacer_les_autres_appareils() = runTest {
        val repository = RemoteDeviceFleetRepository(FakeSyncProvider())

        repository.touchCurrentDevice(DeviceFleetEntry("device-a", "Téléphone A", lastActiveAt = 100L))
        repository.touchCurrentDevice(DeviceFleetEntry("device-b", "Tablette B", lastActiveAt = 200L))

        val devices = repository.listDevices()
        assertEquals(2, devices.size)
        assertTrue(devices.any { it.deviceId == "device-a" })
        assertTrue(devices.any { it.deviceId == "device-b" })
    }

    @Test
    fun touchCurrentDevice_remplace_uniquement_sa_propre_entree() = runTest {
        val repository = RemoteDeviceFleetRepository(FakeSyncProvider())
        repository.touchCurrentDevice(DeviceFleetEntry("device-a", "Téléphone A", lastActiveAt = 100L))
        repository.touchCurrentDevice(DeviceFleetEntry("device-b", "Tablette B", lastActiveAt = 200L))

        repository.touchCurrentDevice(DeviceFleetEntry("device-a", "Téléphone A", lastActiveAt = 999L))

        val devices = repository.listDevices()
        assertEquals(2, devices.size)
        assertEquals(999L, devices.first { it.deviceId == "device-a" }.lastActiveAt)
        assertEquals(200L, devices.first { it.deviceId == "device-b" }.lastActiveAt)
    }

    @Test
    fun removeDevice_ne_retire_que_l_appareil_cible() = runTest {
        val repository = RemoteDeviceFleetRepository(FakeSyncProvider())
        repository.touchCurrentDevice(DeviceFleetEntry("device-a", "Téléphone A", lastActiveAt = 100L))
        repository.touchCurrentDevice(DeviceFleetEntry("device-b", "Tablette B", lastActiveAt = 200L))

        repository.removeDevice("device-a")

        val devices = repository.listDevices()
        assertEquals(1, devices.size)
        assertEquals("device-b", devices.first().deviceId)
    }

    @Test
    fun listDevices_sans_registre_distant_rend_une_liste_vide() = runTest {
        val repository = RemoteDeviceFleetRepository(FakeSyncProvider())

        assertTrue(repository.listDevices().isEmpty())
    }
}
