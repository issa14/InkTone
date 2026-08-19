package com.inktone.infrastructure.sync

import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.core.testing.fake.FakeSyncProvider
import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Lot 19 — le routeur décide vers quel cloud partent les données : sans compte → Drive, WEBDAV → WebDAV, Drive → Drive. */
class SyncProviderRouterTest {

    @Test
    fun compte_absent_route_vers_drive() = runTest {
        val drive = FakeSyncProvider()
        val webdav = FakeSyncProvider()
        val router = SyncProviderRouter(drive, webdav, FakeSyncAccountRepository())

        router.upload("f.json", byteArrayOf(1))

        assertNotNull(drive.download("f.json"))
        assertNull(webdav.download("f.json"))
    }

    @Test
    fun compte_webdav_route_vers_webdav() = runTest {
        val drive = FakeSyncProvider()
        val webdav = FakeSyncProvider()
        val repository = FakeSyncAccountRepository()
        repository.save(SyncAccount(SyncProviderId.WEBDAV, "https://exemple.tld", linkedAt = 0L))
        val router = SyncProviderRouter(drive, webdav, repository)

        router.upload("f.json", byteArrayOf(1))

        assertNotNull(webdav.download("f.json"))
        assertNull(drive.download("f.json"))
    }

    @Test
    fun compte_drive_route_vers_drive() = runTest {
        val drive = FakeSyncProvider()
        val webdav = FakeSyncProvider()
        val repository = FakeSyncAccountRepository()
        repository.save(SyncAccount(SyncProviderId.GOOGLE_DRIVE, "issa@example.com", linkedAt = 0L))
        val router = SyncProviderRouter(drive, webdav, repository)

        router.upload("f.json", byteArrayOf(1))

        assertNotNull(drive.download("f.json"))
        assertNull(webdav.download("f.json"))
    }
}
