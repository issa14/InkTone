package com.inktone.core.testing.fake

import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.SyncRemoteFile

class FakeSyncProvider(private var failNextUpload: Boolean = false) : SyncProvider {
    override val id: SyncProviderId = SyncProviderId.GOOGLE_DRIVE
    private val files = mutableMapOf<String, ByteArray>()
    private val versions = mutableMapOf<String, Long>()
    private var clock = 0L

    /** Compteurs pour vérifier qu'une synchronisation sans changement ne transfère rien (audit UX D62). */
    var uploadCount = 0
        private set
    val uploadedNames = mutableListOf<String>()
    val downloadedNames = mutableListOf<String>()
    var downloadCount = 0
        private set

    fun setFailNextUpload(fail: Boolean) {
        failNextUpload = fail
    }

    override suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult {
        if (failNextUpload) {
            failNextUpload = false
            return SyncOperationResult.Failed(SyncFailureReason.NETWORK, "Erreur réseau simulée")
        }
        files[fileName] = bytes
        versions[fileName] = ++clock
        uploadCount++
        uploadedNames += fileName
        return SyncOperationResult.Success
    }

    override suspend fun download(fileName: String): ByteArray? {
        downloadCount++
        downloadedNames += fileName
        return files[fileName]
    }

    override suspend fun list(): List<SyncRemoteFile> =
        files.map { (name, bytes) -> SyncRemoteFile(name, modifiedAt = versions[name] ?: 0L, sizeBytes = bytes.size.toLong()) }

    override suspend fun delete(fileName: String): SyncOperationResult {
        files.remove(fileName)
        return SyncOperationResult.Success
    }
}
