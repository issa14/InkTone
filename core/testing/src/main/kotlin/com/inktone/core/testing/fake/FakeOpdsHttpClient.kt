package com.inktone.core.testing.fake

import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsFetchResult
import com.inktone.domain.service.OpdsHttpClient

/** Fake du client HTTP OPDS — réponse configurable, jamais de vrai réseau. */
class FakeOpdsHttpClient(
    var onFetch: (url: String, catalogId: String?) -> OpdsFetchResult = { url, _ ->
        OpdsFetchResult.Success("<feed/>", url)
    },
) : OpdsHttpClient {
    override suspend fun fetch(url: String, catalogId: String?): OpdsFetchResult = onFetch(url, catalogId)

    override suspend fun download(url: String, catalogId: String?): OpdsDownloadResult =
        OpdsDownloadResult.Success(ByteArray(0))
}
