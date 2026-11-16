package com.inktone.infrastructure.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstallMarkerTest {
    private fun tempDir(): File = File.createTempFile("marker", "test").apply { delete(); mkdirs() }

    @Test
    fun sans_marqueur_l_installation_n_est_pas_complete() {
        assertFalse(InstallMarker.matches(tempDir(), "abc"))
    }

    @Test
    fun le_marqueur_vaut_pour_l_archive_installee_seulement() {
        val dir = tempDir()
        InstallMarker.write(dir, "abc")

        assertTrue(InstallMarker.matches(dir, "abc"))
        assertFalse("une autre archive n'est pas celle qui est installée", InstallMarker.matches(dir, "def"))
    }

    @Test
    fun un_repertoire_inexistant_n_est_pas_installe() {
        assertFalse(InstallMarker.matches(File("/chemin/inexistant"), "abc"))
    }
}
