package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DrmDetectionTest {

    @Test
    fun detecte_un_epub_protege_sans_crash_et_sans_dechiffrement() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-drm.epub").apply {
            context.assets.open("fixture-drm.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)

        // Le parsing doit reussir (ouverture des metadonnees), meme si le
        // contenu est protege — la detection n'est pas un dechiffrement
        // (hors perimetre v1, Blueprint §7.11).
        check(result is ParseResult.Success)
        assertTrue("le fixture DRM doit etre detecte comme protege", result.isDrmProtected)
    }
}
