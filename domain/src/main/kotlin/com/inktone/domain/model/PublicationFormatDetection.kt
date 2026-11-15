package com.inktone.domain.model

/** Extension de fichier propre à ce format. */
val PublicationFormat.fileExtension: String
    get() = when (this) {
        PublicationFormat.EPUB -> "epub"
        PublicationFormat.TXT -> "txt"
        PublicationFormat.PDF -> "pdf"
    }

/**
 * Format d'un fichier : son extension quand elle est connue, sinon son contenu
 * (début du fichier, [header]). Un fichier texte accepté par le sélecteur
 * (`text/plain`) sans extension `.txt` était traité comme un EPUB puis rejeté
 * comme corrompu (audit UX D23).
 *
 * Par contenu : `%PDF` est un PDF, une archive ZIP (`PK`) un EPUB, un texte sans
 * octet nul un TXT. Dans le doute, EPUB : le choix historique.
 */
fun detectPublicationFormat(fileName: String, header: ByteArray): PublicationFormat {
    val name = fileName.lowercase()
    return when {
        name.endsWith(".txt") -> PublicationFormat.TXT
        name.endsWith(".pdf") -> PublicationFormat.PDF
        name.endsWith(".epub") -> PublicationFormat.EPUB
        header.startsWith("%PDF") -> PublicationFormat.PDF
        header.startsWith("PK") -> PublicationFormat.EPUB
        header.isNotEmpty() && header.none { it == 0.toByte() } -> PublicationFormat.TXT
        else -> PublicationFormat.EPUB
    }
}

private fun ByteArray.startsWith(prefix: String): Boolean =
    size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }
