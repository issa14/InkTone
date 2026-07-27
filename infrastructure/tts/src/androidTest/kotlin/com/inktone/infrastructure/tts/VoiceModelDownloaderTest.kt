package com.inktone.infrastructure.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Utilise une URL `file://` locale comme source de téléchargement — pas
 * de réseau réel dans ce test, déterministe et rapide, mais exerce le
 * vrai chemin `java.net.URL(...).openConnection()` (Tâche 5.6).
 */
@RunWith(AndroidJUnit4::class)
class VoiceModelDownloaderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createSourceFile(content: ByteArray): File {
        val source = File(context.cacheDir, "source-${System.nanoTime()}.bin")
        source.writeBytes(content)
        return source
    }

    @Test
    fun telecharge_et_verifie_un_modele_avec_la_bonne_empreinte() = runTest {
        val content = "contenu factice de modele vocal".toByteArray()
        val source = createSourceFile(content)
        val fileName = "voice-${System.nanoTime()}.bin"

        val downloader = VoiceModelDownloader(context)
        val events = downloader.downloadVoiceModel(
            url = source.toURI().toString(),
            expectedSha256 = sha256(content),
            fileName = fileName,
        ).toList()

        val last = events.last()
        assertTrue("le dernier evenement doit etre Complete, obtenu: $last", last is DownloadProgress.Complete)
        val modelFile = (last as DownloadProgress.Complete).modelFile
        assertTrue(modelFile.exists())
        assertEquals(content.toList(), modelFile.readBytes().toList())
    }

    @Test
    fun rejette_et_supprime_un_modele_dont_l_empreinte_est_fausse() = runTest {
        val content = "contenu factice de modele vocal".toByteArray()
        val source = createSourceFile(content)
        val fileName = "voice-bad-${System.nanoTime()}.bin"
        val wrongHash = sha256("autre contenu".toByteArray())

        val downloader = VoiceModelDownloader(context)
        val events = downloader.downloadVoiceModel(
            url = source.toURI().toString(),
            expectedSha256 = wrongHash,
            fileName = fileName,
        ).toList()

        val last = events.last()
        assertTrue("le dernier evenement doit etre VerificationFailed, obtenu: $last", last is DownloadProgress.VerificationFailed)
        val modelFile = File(context.filesDir, "voices/$fileName")
        assertFalse("le fichier a l'empreinte invalide ne doit pas etre conserve", modelFile.exists())
    }

    @Test
    fun ne_retelecharge_pas_si_le_fichier_local_a_deja_la_bonne_empreinte() = runTest {
        val content = "contenu deja present".toByteArray()
        val fileName = "voice-cached-${System.nanoTime()}.bin"
        val existing = File(context.filesDir, "voices/$fileName")
        existing.parentFile?.mkdirs()
        existing.writeBytes(content)

        // URL volontairement invalide : si le telechargement etait
        // tente, ce test echouerait avec une IOException plutot que de
        // recevoir Complete directement depuis le cache local.
        val downloader = VoiceModelDownloader(context)
        val events = downloader.downloadVoiceModel(
            url = "file:///chemin/qui/n/existe/pas",
            expectedSha256 = sha256(content),
            fileName = fileName,
        ).toList()

        assertEquals(1, events.size)
        assertTrue(events.first() is DownloadProgress.Complete)
    }
}
