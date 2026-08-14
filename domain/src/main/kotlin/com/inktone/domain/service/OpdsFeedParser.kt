package com.inktone.domain.service

import com.inktone.domain.model.OpdsFeed

/** Résultat du parsing d'un flux Atom/OPDS (Lot 13, tâche 13.2.2). */
sealed interface OpdsParseResult {
    data class Success(val feed: OpdsFeed) : OpdsParseResult
    data class Failure(val reason: OpdsFailureReason, val message: String) : OpdsParseResult
}

/**
 * Parseur de flux OPDS 1.2/Atom (Lot 13, ADR-023) — abstraction du
 * domaine sur `XmlPullParser`, pour que l'implémentation reste dans
 * `infrastructure:opds`. [baseUrl] est l'URL du flux consulté, contre
 * laquelle les hrefs relatifs sont résolus via `java.net.URI.resolve`
 * (mécanisme dédié — jamais `JsoupChapterParser.resolveHref`, qui est
 * propre aux chemins internes d'un ZIP EPUB).
 */
interface OpdsFeedParser {
    fun parse(xml: String, baseUrl: String): OpdsParseResult
}
