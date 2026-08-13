package com.inktone.infrastructure.parser

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Accès ZIP brut à un EPUB, indépendant de Readium — deux besoins que
 * `Publication`/`Manifest` (Readium) ne couvrent pas :
 *
 * 1. Lire un fichier qui n'est PAS un item de contenu du manifeste WebPub
 *    (ex. `META-INF/container.xml`, l'OPF lui-même) — Readium ne l'expose
 *    pas via `Publication.get()`, qui ne connaît que les ressources déjà
 *    listées dans le manifeste.
 * 2. Repli insensible à la casse (bug réel Android : l'accès aux entrées
 *    ZIP est sensible à la casse, contrairement à un système de fichiers
 *    Windows/macOS typique — un EPUB généré/édité sous ces OS peut référencer
 *    `Images/Cover.JPG` en HTML alors que l'entrée réelle est
 *    `images/cover.jpg`) quand `Publication.linkWithHref` échoue.
 *
 * Ouverture SAF-compatible (K5, CLAUDE.md) : `ContentResolver` pour un
 * `content://`, `File` sinon — même distinction que
 * [ReadiumPublicationRegistry.openPublication].
 */
internal object EpubZipAccess {

    /** Lit les octets d'une entrée ZIP par chemin exact ou insensible à la casse. */
    fun readEntryBytes(
        context: Context,
        fileUri: String,
        entryPath: String,
        ignoreCase: Boolean = false,
    ): ByteArray? {
        val target = entryPath.removePrefix("/")
        openRawStream(context, fileUri).use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("/")
                    val matches = if (ignoreCase) name.equals(target, ignoreCase = true) else name == target
                    if (matches) return zis.readBytes()
                    entry = zis.nextEntry
                }
            }
        }
        return null
    }

    private fun openRawStream(context: Context, fileUri: String): InputStream {
        return if (fileUri.contains("://") && !fileUri.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(fileUri))
                ?: throw FileNotFoundException(fileUri)
        } else {
            File(fileUri.removePrefix("file://")).inputStream()
        }
    }
}
