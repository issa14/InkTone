package com.inktone.infrastructure.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Test INSTRUMENTE, pas JVM (écart par rapport au plan d'origine, qui le
 * plaçait en test unitaire pur) : `Url(String)` de Readium délègue à
 * `android.net.Uri.parse`, indisponible (RuntimeException "not mocked")
 * dans un test JVM sans Robolectric. Vérifié empiriquement avant de
 * fermer cette tâche — voir CLAUDE.md, "le code fait foi".
 */
@RunWith(AndroidJUnit4::class)
class ReadiumLocatorMapperTest {

    @Test
    fun construit_un_locator_readium_minimal_a_partir_du_href_domaine() {
        val domainLocator = Locator(resourceHref = "OEBPS/chapter1.xhtml", chapterIndex = 0, charOffset = 42)
        val readiumLocator = domainLocator.toMinimalReadiumLocator(MediaType.XHTML)
        assertEquals("OEBPS/chapter1.xhtml", readiumLocator.href.toString())
    }
}
