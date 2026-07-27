package com.inktone.infrastructure.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatSamplesToPcm16Test {

    @Test
    fun convertit_silence_en_zeros() {
        val pcm = floatSamplesToPcm16(floatArrayOf(0f, 0f))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), pcm)
    }

    @Test
    fun convertit_amplitude_max_positive_et_negative() {
        val pcm = floatSamplesToPcm16(floatArrayOf(1f, -1f))
        assertEquals(4, pcm.size)
        // 1.0 -> Short.MAX_VALUE (32767), little-endian
        assertEquals(0xFF.toByte(), pcm[0])
        assertEquals(0x7F.toByte(), pcm[1])
        // -1.0 -> -32767, little-endian
        val negativeSample = ((pcm[3].toInt() and 0xFF) shl 8) or (pcm[2].toInt() and 0xFF)
        assertEquals(-32767, negativeSample.toShort().toInt())
    }

    @Test
    fun clampe_les_valeurs_hors_bornes_sans_deborder() {
        val pcm = floatSamplesToPcm16(floatArrayOf(2f, -5f))
        assertEquals(4, pcm.size)
        val first = ((pcm[1].toInt() and 0xFF) shl 8) or (pcm[0].toInt() and 0xFF)
        assertEquals(32767, first.toShort().toInt())
        val second = ((pcm[3].toInt() and 0xFF) shl 8) or (pcm[2].toInt() and 0xFF)
        assertEquals(-32767, second.toShort().toInt())
    }

    @Test
    fun taille_du_tableau_pcm_est_le_double_du_nombre_d_echantillons() {
        val pcm = floatSamplesToPcm16(FloatArray(100) { 0.1f })
        assertEquals(200, pcm.size)
    }
}
