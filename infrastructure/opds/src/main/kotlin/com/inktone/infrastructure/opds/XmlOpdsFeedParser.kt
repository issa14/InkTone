package com.inktone.infrastructure.opds

import android.util.Xml
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsParseResult
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject

/**
 * Parseur OPDS 1.2/Atom via `XmlPullParser` (Lot 13, tâche 13.2.2) —
 * zéro dépendance nouvelle, même sobriété que le client sync du Lot 11
 * (pas de TikXml/Retrofit). Distingue les liens de navigation
 * (`rel="subsection"`/`collection`) des liens d'acquisition
 * (`rel` commençant par `http://opds-spec.org/acquisition`) ; les hrefs
 * relatifs sont résolus via `java.net.URI.resolve` contre l'URL du flux
 * consulté (mécanisme dédié, jamais `JsoupChapterParser.resolveHref`).
 *
 * Un flux malformé produit [OpdsFailureReason.MALFORMED_FEED] — jamais
 * un feed vide silencieux.
 */
class XmlOpdsFeedParser @Inject constructor() : OpdsFeedParser {

    override fun parse(xml: String, baseUrl: String): OpdsParseResult = try {
        OpdsParseResult.Success(parseFeed(xml, baseUrl))
    } catch (e: Exception) {
        OpdsParseResult.Failure(OpdsFailureReason.MALFORMED_FEED, e.message ?: "Flux OPDS malformé")
    }

    private fun parseFeed(xml: String, baseUrl: String): OpdsFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var feedTitle = ""
        var nextPageUrl: String? = null
        var searchTemplateUrl: String? = null
        val items = mutableListOf<OpdsItem>()

        var inEntry = false
        var inAuthor = false
        var entryTitle: String? = null
        val entryAuthors = mutableListOf<String>()
        var entryCoverUrl: String? = null
        var entryNavHref: String? = null
        var entryAcqHref: String? = null
        var entryAcqType: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        inAuthor = false
                        entryTitle = null
                        entryAuthors.clear()
                        entryCoverUrl = null
                        entryNavHref = null
                        entryAcqHref = null
                        entryAcqType = null
                    }
                    "author" -> inAuthor = true
                    "title" -> {
                        val text = readText(parser)
                        if (inEntry) entryTitle = text else feedTitle = text
                    }
                    "name" -> {
                        if (inAuthor) {
                            val text = readText(parser)
                            if (text.isNotBlank()) entryAuthors += text
                        }
                    }
                    // dc:creator (Dublin Core) en secours d'<author><name>.
                    "creator" -> {
                        if (inEntry) {
                            val text = readText(parser)
                            if (text.isNotBlank() && text !in entryAuthors) entryAuthors += text
                        }
                    }
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        val type = parser.getAttributeValue(null, "type")
                        val template = parser.getAttributeValue(null, "template")
                        val resolved = href?.let { resolve(baseUrl, it) }
                        when {
                            rel == null || resolved == null -> Unit
                            inEntry -> when {
                                rel == "subsection" || rel == "collection" || rel == "http://opds-spec.org/group" -> {
                                    if (entryNavHref == null) entryNavHref = resolved
                                }
                                rel.startsWith("http://opds-spec.org/acquisition") -> {
                                    // Préfère le lien EPUB direct s'il y a plusieurs liens d'acquisition.
                                    if (entryAcqHref == null || (isEpub(type) && !isEpub(entryAcqType))) {
                                        entryAcqHref = resolved
                                        entryAcqType = type
                                    }
                                }
                                rel.startsWith("http://opds-spec.org/image") || rel == "http://opds-spec.org/cover" -> {
                                    // Préfère la vignette (`.../thumbnail`) à l'image pleine.
                                    if (entryCoverUrl == null || rel.endsWith("/thumbnail")) entryCoverUrl = resolved
                                }
                            }
                            rel == "next" -> nextPageUrl = resolved
                            rel == "search" -> searchTemplateUrl = searchTemplate(template, resolved)
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        if (inEntry) {
                            buildItem(
                                title = entryTitle, authors = entryAuthors, coverUrl = entryCoverUrl,
                                navHref = entryNavHref, acqHref = entryAcqHref, acqType = entryAcqType,
                            )?.let(items::add)
                            inEntry = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = feedTitle,
            items = items,
            nextPageUrl = nextPageUrl,
            searchTemplateUrl = searchTemplateUrl,
        )
    }

    private fun buildItem(
        title: String?,
        authors: List<String>,
        coverUrl: String?,
        navHref: String?,
        acqHref: String?,
        acqType: String?,
    ): OpdsItem? {
        val itemTitle = title ?: return null
        return when {
            navHref != null -> OpdsItem.Navigation(title = itemTitle, href = navHref)
            acqHref != null -> OpdsItem.Book(
                title = itemTitle,
                authors = authors,
                coverUrl = coverUrl,
                acquisitionHref = acqHref,
                mimeType = acqType ?: "",
            )
            else -> null
        }
    }

    private fun searchTemplate(template: String?, resolvedHref: String?): String? = when {
        !template.isNullOrBlank() -> template
        resolvedHref != null && resolvedHref.contains("{searchTerms}") -> resolvedHref
        // rel="search" pointant vers un document opensearchdescription.xml
        // (sans {searchTerms} inline) n'est pas résolu ici : pas de fetch
        // dédié, la loupe reste masquée pour ce flux.
        else -> null
    }

    private fun isEpub(type: String?): Boolean = type?.contains("epub", ignoreCase = true) == true

    private fun resolve(baseUrl: String, href: String): String = try {
        java.net.URI.create(baseUrl).resolve(href).toString()
    } catch (e: Exception) {
        href // best-effort : href brut si la résolution échoue
    }

    /** Lit le texte d'un élément : on est sur START_TAG, on avance jusqu'au texte et on s'arrête sur END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        var event = parser.next()
        var text = ""
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) text += parser.text
            event = parser.next()
        }
        return text.trim()
    }
}
