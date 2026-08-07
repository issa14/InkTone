package com.inktone.core.testing.fake

import com.inktone.domain.service.ImportResultEntry
import com.inktone.domain.service.ImportResultsStore
import com.inktone.domain.usecase.ImportResult

class FakeImportResultsStore : ImportResultsStore {
    var startedSessionId: String? = null
    val entries = mutableListOf<ImportResultEntry>()
    val recordedSessionIds = mutableListOf<String>()
    var clearedSessionId: String? = null

    override suspend fun beginSession(sessionId: String) {
        startedSessionId = sessionId
        entries.clear()
    }

    override suspend fun recordResult(sessionId: String, fileName: String, result: ImportResult) {
        recordedSessionIds += sessionId
        entries += ImportResultEntry(
            fileName = fileName,
            resultType = when (result) {
                is ImportResult.Success -> "success"
                is ImportResult.Duplicate -> "duplicate"
                is ImportResult.DrmProtected -> "drm_protected"
                is ImportResult.Corrupted -> "corrupted"
                is ImportResult.UnsupportedFormat -> "unsupported_format"
            },
            message = (result as? ImportResult.Corrupted)?.message ?: (result as? ImportResult.DrmProtected)?.message,
            existingPublicationId = (result as? ImportResult.Duplicate)?.existingPublicationId,
        )
    }

    override suspend fun getResults(sessionId: String): List<ImportResultEntry> = entries.toList()

    override suspend fun clearSession(sessionId: String) {
        clearedSessionId = sessionId
        entries.clear()
    }
}
