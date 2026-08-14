package com.inktone.infrastructure.media

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Tests JVM de [GaplessPlaybackCore] (Tâche 1.3) : machine d'états, file
 * non-bloquante et synchronisation anti-SIGSEGV, sans `AudioTrack`.
 *
 * NB : ces tests ne prouvent PAS l'absence de SIGSEGV — la preuve du
 * non-crash est instrumentée (Tâche 2.1), sur device. Ici on vérifie
 * uniquement la discipline du verrou : jamais de libération pendant une
 * écriture.
 */
class GaplessPlaybackCoreTest {

    private fun segment(bytes: ByteArray, sampleRate: Int = 22_050): AudioSegment =
        AudioSegment(
            audioData = bytes,
            durationMs = 0L,
            wordTimestamps = emptyList(),
            sampleRate = sampleRate,
        )

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            delay(10)
        }
        assertTrue("condition non atteinte en $timeoutMs ms", condition())
    }

    @Test
    fun enqueue_nonBloquant_et_neDemarrePasLaLecture() = runBlocking {
        val sink = FakePcmSink()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 50)
            repeat(100) { core.enqueue(segment(ByteArray(1024))) }
            assertEquals(100, core.pendingCount)
            assertEquals(PlayerState.Idle, core.state.value)
            assertEquals(0, sink.writtenBytes.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun transitionsDetat_nominales() = runBlocking {
        val sink = FakePcmSink()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 50)
            assertEquals(PlayerState.Idle, core.state.value)

            core.play()
            assertEquals(PlayerState.Playing, core.state.value)
            core.play() // idempotent
            assertEquals(PlayerState.Playing, core.state.value)

            core.pause()
            assertEquals(PlayerState.Paused, core.state.value)
            assertEquals(1, sink.paused.get())

            core.resume()
            assertEquals(PlayerState.Playing, core.state.value)
            assertEquals(1, sink.resumed.get())

            core.stop()
            assertEquals(PlayerState.Stopped, core.state.value)
            assertEquals(1, sink.released.get())

            core.release()
            assertEquals(PlayerState.Idle, core.state.value)
            assertEquals(2, sink.released.get()) // release ré-arrête (idempotent)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun segmentsEcritsDansLordreFifo_etTrackCreeAuSampleRateConfigure() = runBlocking {
        val sink = FakePcmSink()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 20)
            core.enqueue(segment(ByteArray(4) { 0x01 }))
            core.enqueue(segment(ByteArray(4) { 0x02 }))
            core.play()

            awaitUntil { core.pendingCount == 0 && sink.writtenBytes.size == 8 }
            assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2), sink.writtenBytes.map { it.toInt() and 0xFF })
            // ensureTrack est re-vérifié avant CHAQUE segment (re-validation du
            // sampleRate configuré : c'est ce qui rend le sampleRate dynamique).
            assertEquals(listOf(22_050, 22_050), sink.ensuredSampleRates)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun changementDeSampleRate_estTransmisAuSink() = runBlocking {
        val sink = FakePcmSink()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 20)
            core.sampleRate = 24_000
            core.enqueue(segment(ByteArray(4)))
            core.play()

            awaitUntil { sink.writtenBytes.size == 4 }
            assertEquals(listOf(24_000), sink.ensuredSampleRates)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stop_videLaFile() = runBlocking {
        val sink = FakePcmSink()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 50)
            core.enqueue(segment(ByteArray(8)))
            core.enqueue(segment(ByteArray(8)))
            assertEquals(2, core.pendingCount)

            core.stop()
            assertEquals(0, core.pendingCount)
            assertEquals(PlayerState.Stopped, core.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stopPendantEcriture_attendLaFinPuisLibere() = runBlocking {
        val sink = FakePcmSink()
        sink.blockWrites = true
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val core = GaplessPlaybackCore(sink, scope, pollTimeoutMs = 50)
            core.enqueue(segment(ByteArray(64)))
            core.play()

            assertTrue("l'écriture doit démarrer", sink.writeEntered.await(2, TimeUnit.SECONDS))

            val stopThread = thread(name = "stop-test") { core.stop() }
            Thread.sleep(150)
            assertTrue("stop doit être bloqué pendant l'écriture", stopThread.isAlive)
            assertEquals("aucune libération pendant l'écriture", 0, sink.released.get())

            sink.allowWriteToFinish.countDown()
            stopThread.join(2_000)
            assertFalse("stop doit se terminer après la fin de l'écriture", stopThread.isAlive)
            assertEquals(1, sink.released.get())
            assertEquals(PlayerState.Stopped, core.state.value)
        } finally {
            scope.cancel()
        }
    }

    private class FakePcmSink : GaplessPlaybackCore.PcmSink {
        val ensuredSampleRates = CopyOnWriteArrayList<Int>()
        val writtenBytes = CopyOnWriteArrayList<Byte>()
        val released = AtomicInteger(0)
        val paused = AtomicInteger(0)
        val resumed = AtomicInteger(0)

        @Volatile
        var blockWrites = false
        val writeEntered = CountDownLatch(1)
        val allowWriteToFinish = CountDownLatch(1)

        override fun ensureTrack(sampleRate: Int) {
            ensuredSampleRates.add(sampleRate)
        }

        override fun write(data: ByteArray, offset: Int, length: Int): Int {
            if (blockWrites) {
                writeEntered.countDown()
                allowWriteToFinish.await()
            }
            for (i in offset until offset + length) writtenBytes.add(data[i])
            return length
        }

        override fun pauseTrack() {
            paused.incrementAndGet()
        }

        override fun resumeTrack() {
            resumed.incrementAndGet()
        }

        override fun stopAndReleaseTrack() {
            released.incrementAndGet()
        }

        override fun setTrackVolume(volume: Float) = Unit
    }
}
