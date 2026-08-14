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
        // Certains serveurs non conformes émettent des entités HTML
        // (`&nbsp;`, `&eacute;`, …) non déclarées dans le flux —
        // `XmlPullParser` les refuse (« Undetermined entity ref »). On les
        // décode en amont pour ne pas planter (Lot 13, retour device).
        OpdsParseResult.Success(parseFeed(decodeHtmlEntities(xml), baseUrl))
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
                            rel == null && resolved == null -> Unit
                            inEntry -> {
                                // Acquisition : rel explicite OU lien EPUB direct.
                                // Certains flux (ebooksgratuits) émettent le lien
                                // EPUB SANS rel="...acquisition", seulement
                                // type="application/epub+zip".
                                val isAcquisition = rel?.startsWith("http://opds-spec.org/acquisition") == true ||
                                    type?.contains("epub", ignoreCase = true) == true
                                val isCover = rel?.startsWith("http://opds-spec.org/image") == true ||
                                    rel == "http://opds-spec.org/cover" ||
                                    type?.startsWith("image/", ignoreCase = true) == true
                                // Navigation : rel explicite OU lien vers un flux Atom/OPDS.
                                // Le repli sur le type couvre les flux non conformes
                                // (ebooksgratuits, unglue.it) qui n'émettent pas de
                                // rel="subsection".
                                val isNavigation = rel == "subsection" || rel == "collection" ||
                                    rel == "http://opds-spec.org/group" ||
                                    type?.contains("atom", ignoreCase = true) == true ||
                                    type?.contains("opds", ignoreCase = true) == true
                                when {
                                    isAcquisition -> {
                                        // Préfère le lien EPUB direct s'il y a plusieurs liens d'acquisition.
                                        if (entryAcqHref == null || (isEpub(type) && !isEpub(entryAcqType))) {
                                            entryAcqHref = resolved
                                            entryAcqType = type
                                        }
                                    }
                                    isCover -> {
                                        // Préfère la vignette (`.../thumbnail`) à l'image pleine.
                                        if (entryCoverUrl == null || rel.endsWith("/thumbnail")) entryCoverUrl = resolved
                                    }
                                    isNavigation -> {
                                        if (entryNavHref == null) entryNavHref = resolved
                                    }
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
            // L'acquisition prime sur la navigation : une entrée avec un lien
            // EPUB ET un lien atom est un livre, pas un dossier.
            acqHref != null -> OpdsItem.Book(
                title = itemTitle,
                authors = authors,
                coverUrl = coverUrl,
                acquisitionHref = acqHref,
                mimeType = acqType ?: "",
            )
            navHref != null -> OpdsItem.Navigation(title = itemTitle, href = navHref)
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

    /**
     * Décode les entités HTML nommées que `XmlPullParser` ne connaît pas.
     * Les 5 entités XML (`amp`, `lt`, `gt`, `quot`, `apos`) et les
     * références numériques (`&#…;`) sont laissées intactes — le parseur
     * les résout nativement. Une entité nommée inconnue est retirée plutôt
     * que de faire planter le parseur.
     */
    private fun decodeHtmlEntities(xml: String): String =
        ENTITY_REGEX.replace(xml) { match ->
            val name = match.groupValues[1]
            when {
                name in XML_ENTITIES -> match.value
                HTML_ENTITIES.containsKey(name) -> HTML_ENTITIES.getValue(name)
                else -> ""
            }
        }

    private companion object {
        val ENTITY_REGEX = Regex("&([a-zA-Z][a-zA-Z0-9]{0,31});")
        val XML_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos")

        val HTML_ENTITIES = mapOf(
            "nbsp" to "\u00A0",
            "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë",
            "agrave" to "à", "acirc" to "â", "auml" to "ä",
            "ccedil" to "ç", "ocirc" to "ô", "oelig" to "œ",
            "icirc" to "î", "iuml" to "ï", "ucirc" to "û", "uuml" to "ü",
            "Eacute" to "É", "Egrave" to "È", "Ecirc" to "Ê",
            "Agrave" to "À", "Ccedil" to "Ç", "OElig" to "Œ",
            "laquo" to "«", "raquo" to "»",
            "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
            "hellip" to "…", "mdash" to "—", "ndash" to "–",
            "copy" to "©", "reg" to "®", "trade" to "™",
            "deg" to "°", "middot" to "·", "bull" to "•",
        )
    }
}
