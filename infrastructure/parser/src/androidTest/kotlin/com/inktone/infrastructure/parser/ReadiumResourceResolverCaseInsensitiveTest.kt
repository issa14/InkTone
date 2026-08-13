package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bug réel : l'accès aux entrées ZIP est sensible à la casse sur Android
 * (contrairement à Windows/macOS, où l'EPUB a pu être généré ou édité) —
 * le HTML référence p.ex. `Images/Cover.JPG` alors que l'entrée réelle
 * dans l'archive est `images/cover.jpg`. `Publication.linkWithHref`
 * échoue silencieusement dans ce cas.
 *
 * Directive de correction 4 : [ReadiumResourceResolver.openStream] doit
 * retomber sur une recherche d'entrée ZIP insensible à la casse
 * ([ReadiumPublicationRegistry.readAssetIgnoreCase]) quand la résolution
 * normale via le manifeste échoue.
 */
@RunWith(AndroidJUnit4::class)
class ReadiumResourceResolverCaseInsensitiveTest {

    @Test
    fun resout_une_ressource_dont_la_casse_differe_de_l_entree_zip_reelle() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test Casse ZIP</dc:title>
    <dc:identifier id="BookId">urn:uuid:test-casse-zip</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="cover-img" href="Images/Cover.JPG" media-type="image/jpeg"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>"""
        val chapter1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Bonjour.</p></body></html>"""

        val epubFile = TestEpubBuilder.writeToCache(
            context,
            "fixture-casse-zip.epub",
            mapOf(
                "mimetype" to TestEpubBuilder.text(TestEpubBuilder.MIMETYPE),
                "META-INF/container.xml" to TestEpubBuilder.text(TestEpubBuilder.CONTAINER_XML),
                "OEBPS/content.opf" to TestEpubBuilder.text(opf),
                "OEBPS/chapter1.xhtml" to TestEpubBuilder.text(chapter1),
                // Entree ZIP reelle, casse mixte (comme un EPUB genere/edite sous Windows/macOS).
                "OEBPS/Images/Cover.JPG" to TestEpubBuilder.MINIMAL_JPEG_BYTES,
            ),
        )

        val registry = ReadiumPublicationRegistry(context)
        val resolver = ReadiumResourceResolver(registry)
        resolver.open("test-casse-zip", epubFile.absolutePath)

        // Le href resolu correspond deja exactement a l'entree (manifest
        // href == entree ZIP) : sert de temoin que le chemin normal
        // fonctionne toujours.
        val exact = resolver.openStream("test-casse-zip", "OEBPS/Images/Cover.JPG")
        assertNotNull("la resolution exacte doit toujours fonctionner", exact)

        // Href different en casse de l'entree ZIP reelle (simule un href
        // HTML/manifest en casse differente) : linkWithHref echoue, le
        // repli insensible a la casse doit prendre le relais.
        val mismatchedCase = resolver.openStream("test-casse-zip", "OEBPS/images/cover.jpg")
        assertNotNull(
            "la resolution doit reussir via le repli insensible a la casse",
            mismatchedCase,
        )

        // Une ressource qui n'existe vraiment pas ne doit jamais etre
        // trouvee par erreur.
        val trulyMissing = resolver.openStream("test-casse-zip", "OEBPS/images/does-not-exist.png")
        assertNull("une ressource absente doit rester absente", trulyMissing)
    }
}
