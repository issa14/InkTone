package com.inktone.infrastructure.parser

import android.content.Context
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * Repli EPUB2 pour identifier la couverture via `<guide><reference
 * type="cover" href="…"/></guide>`, quand elle n'est exposée ni via
 * `properties="cover-image"` du manifeste ni via `<meta name="cover">` —
 * ces deux derniers sont déjà résolus par Readium (`ResourceAdapter`
 * calcule un rel `"cover"` à partir d'eux, accessible via
 * `Publication.linkWithRel("cover")`).
 *
 * Readium 3.0.0 n'analyse PAS `<guide>` du tout (vérifié par décompilation
 * du jar `readium-streamer-3.0.0` : aucune classe `Guide`/`Reference`
 * dans `org.readium.r2.streamer.parser.epub`) — sans ce repli, un EPUB2
 * dont la couverture n'est identifiable QUE via `<guide>` est invisible à
 * l'app, écran noir, `Chapitre 1 (1/1)`, `0,0%` (le seul item du spine
 * réellement lu est une page de titre quasi vide).
 *
 * Lecture ZIP directe via [EpubZipAccess] : l'OPF n'est pas un item de
 * contenu du manifeste WebPub, donc pas accessible via `Publication.get()`.
 */
internal object EpubGuideCoverResolver {

    /**
     * @return le href de la couverture, résolu relativement à la racine
     *   de l'archive (même référentiel que `Link.href` de Readium — K6),
     *   ou `null` si l'OPF/le guide est illisible ou n'a pas de référence
     *   `type="cover"`.
     */
    fun findCoverHref(context: Context, fileUri: String): String? {
        return try {
            val containerXml = EpubZipAccess.readEntryBytes(context, fileUri, "META-INF/container.xml")
                ?: return null
            val opfPath = extractOpfPath(containerXml.decodeToString()) ?: return null

            val opfText = EpubZipAccess.readEntryBytes(context, fileUri, opfPath)
                ?.decodeToString()
                ?: return null
            val coverHrefRelativeToOpf = extractGuideCoverHref(opfText) ?: return null

            resolveRelative(opfPath, coverHrefRelativeToOpf)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractOpfPath(containerXml: String): String? {
        val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        return doc.selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
    }

    private fun extractGuideCoverHref(opfText: String): String? {
        val doc = Jsoup.parse(opfText, "", Parser.xmlParser())
        val reference = doc.select("guide > reference").firstOrNull { element ->
            element.attr("type").equals("cover", ignoreCase = true)
        }
        return reference?.attr("href")?.takeIf { it.isNotBlank() }
    }

    /**
     * Résout [relativeHref] (relatif au RÉPERTOIRE de l'OPF, convention
     * OPF standard) contre [opfPath], par segments de chemin — même
     * logique que `JsoupChapterParser.resolveHref`, dupliquée ici car
     * cette classe n'a pas accès au href d'un chapitre courant, juste à
     * l'OPF lui-même.
     */
    private fun resolveRelative(opfPath: String, relativeHref: String): String {
        if (relativeHref.contains("://") || relativeHref.startsWith("data:")) return relativeHref
        val baseDir = opfPath.substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) relativeHref else "$baseDir/$relativeHref"

        val resolved = mutableListOf<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(segment)
            }
        }
        return resolved.joinToString("/")
    }
}
