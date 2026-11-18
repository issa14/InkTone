package com.inktone.infrastructure.parser

import android.graphics.BitmapFactory

import android.content.Context
import android.net.Uri
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

    /**
     * Octets de l'IMAGE de couverture, quand Readium n'a rien pu déduire
     * (`Publication.cover()` retourne `null`).
     *
     * Bug réel trouvé sur appareil (éditions Calibre EPUB2 — les sept
     * tomes de Harry Potter, 13 titres sur 481 dans la bibliothèque de
     * test) : le seul marqueur de couverture de l'OPF est
     * `<guide><reference type="cover" href="titlepage.xhtml"/>`. Il n'y a
     * ni `<meta name="cover">` ni `properties="cover-image"`, donc
     * `Publication.linkWithRel("cover")` — et avec lui
     * `Publication.cover()` — ne trouve rien : la vignette de la
     * bibliothèque restait vide alors que le livre s'ouvrait normalement
     * (le repli [findCoverHref] plaçait bien la page de couverture en
     * tête des chapitres, mais il n'alimentait QUE cette liste, jamais
     * `metadata.coverUri`).
     *
     * Le href du guide désigne le plus souvent une PAGE XHTML, pas
     * l'image : il faut alors en extraire le `<img src>` (ou le
     * `<svg><image xlink:href>` des exports InDesign/Sigil) et résoudre
     * ce chemin relativement au répertoire de cette page.
     *
     * @return les octets bruts de l'image (à décoder par l'appelant), ou
     *   `null` si l'archive, le guide ou l'image sont introuvables.
     */
    fun findCoverImageBytes(context: Context, fileUri: String): ByteArray? {
        return try {
            val coverHref = findCoverHref(context, fileUri) ?: return null
            val imageHref = if (isImageHref(coverHref)) {
                coverHref
            } else {
                val pageBytes = readEntry(context, fileUri, coverHref) ?: return null
                val embedded = extractFirstImageHref(pageBytes.decodeToString()) ?: return null
                resolveRelative(coverHref, embedded)
            }
            readEntry(context, fileUri, imageHref)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dernier repli : l'image qui ouvre la PREMIÈRE page du livre, quand l'OPF
     * ne déclare aucune couverture (ni métadonnée, ni `<guide>`). Cas réels
     * (19 livres sur 533 d'une bibliothèque) : pages `Couverture.xhtml` de
     * Sigil, portrait de l'auteur des éditions Ebooks libres et gratuits
     * (« La Guerre des mondes », 223 × 261), couverture en tête d'un livre
     * d'un seul fichier — l'image que le livre montre en premier, celle
     * qu'affichent les autres liseuses.
     *
     * Retenue si presque aucun texte ne la précède et si elle est en
     * portrait et pas minuscule : un logo d'éditeur en bandeau (Feedbooks)
     * ou une illustration après la page de titre ne sont pas une couverture.
     */
    fun findFirstPageCoverImageBytes(context: Context, fileUri: String): ByteArray? {
        return try {
            val containerXml = EpubZipAccess.readEntryBytes(context, fileUri, "META-INF/container.xml")
                ?: return null
            val opfPath = extractOpfPath(containerXml.decodeToString()) ?: return null
            val opfText = EpubZipAccess.readEntryBytes(context, fileUri, opfPath)?.decodeToString() ?: return null
            val firstPageHref = resolveRelative(opfPath, extractFirstSpineHref(opfText) ?: return null)
            val pageText = readEntry(context, fileUri, firstPageHref)?.decodeToString() ?: return null
            val leadingText = textBeforeFirstImage(pageText) ?: return null
            if (leadingText.length > MAX_TEXT_BEFORE_COVER) return null
            val imageHref = resolveRelative(firstPageHref, extractFirstImageHref(pageText) ?: return null)
            val bytes = readEntry(context, fileUri, imageHref) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            bytes.takeIf { isCoverLikeImage(bounds.outWidth, bounds.outHeight) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Texte visible avant la première image de la page (`<img>` ou `<svg><image>`),
     * `null` si la page n'a pas d'image. L'en-tête HTML n'est pas du texte visible.
     */
    internal fun textBeforeFirstImage(page: String): String? {
        val body = page.replace(Regex("(?is)<(head|style|script)\\b.*?</\\1>"), "")
        val imageStart = Regex("(?i)<(img|image)\\b").find(body)?.range?.first ?: return null
        return Jsoup.parse(body.substring(0, imageStart)).text().trim()
    }

    /** Format de couverture : portrait, et pas une icône. */
    internal fun isCoverLikeImage(width: Int, height: Int): Boolean =
        width > 0 && height >= MIN_COVER_HEIGHT_PX && height >= width

    private fun extractFirstSpineHref(opfText: String): String? {
        val doc = Jsoup.parse(opfText, "", Parser.xmlParser())
        val idref = doc.selectFirst("spine > itemref")?.attr("idref")?.takeIf { it.isNotBlank() } ?: return null
        return doc.select("manifest > item").firstOrNull { it.attr("id") == idref }
            ?.attr("href")?.takeIf { it.isNotBlank() }
    }

    /**
     * Lecture d'une entrée ZIP tolérante aux deux écarts déjà connus du
     * projet : href percent-encodé (K6) et casse divergente entre le
     * href HTML et le nom réel de l'entrée (voir [EpubZipAccess]).
     */
    private fun readEntry(context: Context, fileUri: String, href: String): ByteArray? {
        val decoded = Uri.decode(href.substringBefore('#'))
        return EpubZipAccess.readEntryBytes(context, fileUri, decoded)
            ?: EpubZipAccess.readEntryBytes(context, fileUri, decoded, ignoreCase = true)
    }

    private fun isImageHref(href: String): Boolean {
        val path = href.substringBefore('#').lowercase()
        return IMAGE_EXTENSIONS.any { path.endsWith(it) }
    }

    /**
     * Premier `<img src>` ou `<svg><image xlink:href|href>` de la page —
     * les deux formes utilisées en pratique par les pages de couverture.
     * Parseur XML (et non HTML) : une page de couverture est du XHTML,
     * et `xlink:href` ne survit pas au parseur HTML de Jsoup.
     */
    private fun extractFirstImageHref(pageXhtml: String): String? {
        val doc = Jsoup.parse(pageXhtml, "", Parser.xmlParser())
        doc.selectFirst("img[src]")?.attr("src")?.takeIf { it.isNotBlank() }?.let { return it }
        val svgImage = doc.selectFirst("image") ?: return null
        return listOf("xlink:href", "href")
            .map { svgImage.attr(it) }
            .firstOrNull { it.isNotBlank() }
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

    // `.svg` volontairement absent : un SVG n'est pas décodable par
    // BitmapFactory, et une couverture SVG enveloppe de toute façon un
    // bitmap dans un `<image>` — la laisser passer par la branche XHTML
    // ci-dessus extrait justement ce bitmap.
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")

    private const val MAX_TEXT_BEFORE_COVER = 40
    private const val MIN_COVER_HEIGHT_PX = 150
}
