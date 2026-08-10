package com.inktone.core.testing.fake

import com.inktone.domain.model.DeviceIdentity
import com.inktone.domain.repository.DeviceIdentityRepository

class FakeDeviceIdentityRepository(
    private val identity: DeviceIdentity = DeviceIdentity(id = "device-test", displayName = "Appareil de test"),
) : DeviceIdentityRepository {
    override suspend fun getOrCreate(): DeviceIdentity = identity
}
