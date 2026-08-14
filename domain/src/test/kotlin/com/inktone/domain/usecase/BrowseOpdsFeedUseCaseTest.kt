package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeOpdsCatalogRepository
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsFetchResult
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.domain.service.OpdsParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 13, tâche 13.2.8 — non-régression du garde-fou anti-boucle de pagination (décision actée §14). */
class BrowseOpdsFeedUseCaseTest {

    private class FakeHttpClient : OpdsHttpClient {
        override suspend fun fetch(url: String, catalogId: String?): OpdsFetchResult =
            OpdsFetchResult.Success("<feed/>", url)

        override suspend fun download(url: String, catalogId: String?): OpdsDownloadResult =
            OpdsDownloadResult.Success(ByteArray(0))
    }

    private class FakeParser : OpdsFeedParser {
        override fun parse(xml: String, baseUrl: String): OpdsParseResult =
            OpdsParseResult.Success(
                OpdsFeed(title = "t", items = emptyList(), nextPageUrl = null, searchTemplateUrl = null),
            )
    }

    @Test
    fun loadNextPage_sur_une_url_deja_visitee_interrompt_la_pagination() = runTest {
        val useCase = BrowseOpdsFeedUseCase(FakeHttpClient(), FakeParser(), FakeOpdsCatalogRepository())
        useCase.resetSession()

        val first = useCase("https://ex.com/a", null)
        assertTrue(first is OpdsBrowseResult.Success)

        val loop = useCase.loadNextPage("https://ex.com/a", null)
        assertEquals(OpdsBrowseResult.LoopDetected, loop)
    }

    @Test
    fun la_navigation_manuelle_peut_revisiter_une_url_sans_etre_bloquee() = runTest {
        val useCase = BrowseOpdsFeedUseCase(FakeHttpClient(), FakeParser(), FakeOpdsCatalogRepository())
        useCase.resetSession()

        assertTrue(useCase("https://ex.com/a", null) is OpdsBrowseResult.Success)
        assertTrue(useCase("https://ex.com/a", null) is OpdsBrowseResult.Success)
    }
}
