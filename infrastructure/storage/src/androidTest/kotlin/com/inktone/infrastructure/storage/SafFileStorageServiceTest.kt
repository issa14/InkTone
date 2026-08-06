package com.inktone.infrastructure.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Teste via une URI file:// vers un fichier temporaire réel, pas une
 * vraie URI SAF content:// (qui exigerait un FileProvider de test ou une
 * interaction utilisateur simulée). ContentResolver.openInputStream gère
 * nativement file:// — ce test valide fidèlement la logique du wrapper
 * pour openInputStream/computeSha256 ; getFileSize utilise sciemment le
 * repli file:// documenté dans SafFileStorageService (voir le commentaire
 * associé).
 */
@RunWith(AndroidJUnit4::class)
class SafFileStorageServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val service = SafFileStorageService(context)

    private fun tempFile(content: String): File =
        File(context.cacheDir, "test-${System.nanoTime()}.txt").apply { writeText(content) }

    @Test
    fun ouvre_et_lit_un_fichier_reel() = runTest {
        val file = tempFile("Bonjour InkTone")
        val content = service.openInputStream(Uri.fromFile(file).toString())
            ?.bufferedReader()?.use { it.readText() }
        assertEquals("Bonjour InkTone", content)
        file.delete()
    }

    @Test
    fun calcule_un_hash_sha256_deterministe() = runTest {
        val file = tempFile("contenu identique")
        val uri = Uri.fromFile(file).toString()
        val hash1 = service.computeSha256(uri)
        val hash2 = service.computeSha256(uri)
        assertNotNull(hash1)
        assertEquals(hash1, hash2)
        file.delete()
    }

    @Test
    fun retourne_la_taille_reelle_du_fichier() = runTest {
        val file = tempFile("12345")
        assertEquals(5L, service.getFileSize(Uri.fromFile(file).toString()))
        file.delete()
    }

    @Test
    fun resout_le_nom_de_fichier_reel() = runTest {
        val file = tempFile("peu importe")
        assertEquals(file.name, service.getFileName(Uri.fromFile(file).toString()))
        file.delete()
    }

    @Test
    fun ecrit_puis_relit_le_meme_contenu() = runTest {
        val source = tempFile("contenu a exporter")
        val destination = File(context.cacheDir, "export-${System.nanoTime()}.txt")

        val success = service.writeToUri(Uri.fromFile(destination).toString(), source)

        assertEquals(true, success)
        assertEquals("contenu a exporter", destination.readText())
        source.delete()
        destination.delete()
    }
}
