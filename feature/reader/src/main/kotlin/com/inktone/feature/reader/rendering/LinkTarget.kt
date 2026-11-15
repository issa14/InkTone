package com.inktone.feature.reader.rendering

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder

/** Étiquette de l'annotation portant l'adresse d'un lien dans un [AnnotatedString] (audit UX D15). */
internal const val LINK_ANNOTATION_TAG = "href"

/** Adresse du lien posé au caractère [offset] du texte, ou null. */
internal fun AnnotatedString.linkAt(offset: Int): String? =
    getStringAnnotations(LINK_ANNOTATION_TAG, offset, offset).firstOrNull()?.item

/** Ajoute [text] en l'annotant de l'adresse [href] quand elle existe. */
internal inline fun Builder.appendLinkable(href: String?, block: () -> Unit) {
    if (href == null) {
        block()
        return
    }
    pushStringAnnotation(LINK_ANNOTATION_TAG, href)
    try {
        block()
    } finally {
        pop()
    }
}

/** Action d'un tap sur un lien ; fournie par l'écran du Lecteur, sans effet par défaut. */
val LocalLinkTapHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Cible d'un lien : un chapitre du livre, une adresse extérieure, ou rien de résoluble. */
sealed interface LinkTarget {
    data class Chapter(val index: Int) : LinkTarget
    data class External(val url: String) : LinkTarget

    /** Ressource du livre hors de la liste des chapitres (une note de bas de page hors lecture linéaire). */
    data class Resource(val path: String, val fragment: String) : LinkTarget
    data object None : LinkTarget
}

/**
 * Résout [href] (tel qu'écrit dans le livre) par rapport au chapitre courant. Les adresses
 * `http`, `https` et `mailto` sont extérieures ; les autres sont des chemins relatifs à
 * [currentChapterHref], comparés aux chapitres sans fragment ni encodage pour cent. Le fragment
 * n'est pas suivi : on arrive au début du chapitre.
 */
fun resolveLinkTarget(href: String, currentChapterHref: String, chapterHrefs: List<String>): LinkTarget {
    val raw = href.trim()
    if (raw.isEmpty() || raw.startsWith("#")) return LinkTarget.None
    val lower = raw.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
        return LinkTarget.External(raw)
    }
    if (raw.contains("://")) return LinkTarget.None
    val path = percentDecode(raw.substringBefore('#'))
    val baseDir = percentDecode(currentChapterHref).substringBeforeLast('/', "")
    val combined = if (baseDir.isEmpty() || path.startsWith("/")) path.removePrefix("/") else "$baseDir/$path"
    val resolved = mutableListOf<String>()
    for (segment in combined.split('/')) {
        when (segment) {
            "", "." -> {}
            ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
            else -> resolved.add(segment)
        }
    }
    val target = resolved.joinToString("/")
    val index = chapterHrefs.indexOfFirst { percentDecode(it).substringBefore('#').trimStart('/') == target }
    if (index >= 0) return LinkTarget.Chapter(index)
    // Hors des chapitres (ex. `footnotes.xml` en `linear="no"`) : une note ciblée par un fragment
    // s'affiche dans une fenêtre, sans changer de chapitre (audit UX D15).
    val fragment = percentDecode(raw.substringAfter('#', ""))
    return if (fragment.isNotEmpty() && target.isNotEmpty()) LinkTarget.Resource(target, fragment) else LinkTarget.None
}

private fun percentDecode(value: String): String =
    runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
