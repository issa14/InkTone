package com.inktone.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioSegmentTest {

    @Test
    fun `deux segments avec le meme contenu binaire sont egaux`() {
        val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
        val b = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
        assertEquals(a, b) // échouerait avec l'égalité par défaut d'une data class sur ByteArray
    }

    @Test
    fun `deux segments avec un contenu binaire different ne sont pas egaux`() {
        val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
        val b = AudioSegment(audioData = byteArrayOf(9, 9, 9), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
        assertNotEquals(a, b)
    }

    @Test
    fun `deux segments avec un sample rate different ne sont pas egaux`() {
        val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
        val b = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 16000)
        assertNotEquals(a, b)
    }
}
