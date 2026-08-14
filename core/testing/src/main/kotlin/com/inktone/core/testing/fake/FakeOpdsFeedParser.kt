package com.inktone.core.testing.fake

import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsParseResult

/** Fake du parseur OPDS — renvoie un flux vide par défaut, configurable. */
class FakeOpdsFeedParser(
    var onParse: (xml: String, baseUrl: String) -> OpdsParseResult = { _, _ ->
        OpdsParseResult.Success(
            OpdsFeed(title = "t", items = emptyList(), nextPageUrl = null, searchTemplateUrl = null),
        )
    },
) : OpdsFeedParser {
    override fun parse(xml: String, baseUrl: String): OpdsParseResult = onParse(xml, baseUrl)
}
