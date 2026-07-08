package com.readflow.ui.screen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.readflow.service.onnx.OnnxInferenceService
import com.readflow.service.onnx.SynthesisResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran de test TTS — temporaire (Phase 0.6).
 * Permet de valider Sherpa-ONNX sur un device Android.
 */
@Composable
fun TtsTestScreen() {
    val context = LocalContext.current

    // Récupération du service Hilt
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnnxInferenceServiceEntryPoint::class.java
        )
    }
    val inferenceService = remember { entryPoint.onnxInferenceService() }

    var texte by remember { mutableStateOf("Bonjour, bienvenue dans ReadFlow.") }
    var vitesse by remember { mutableFloatStateOf(1.0f) }
    var voixIndex by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SynthesisResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎤 Test TTS — ReadFlow", style = MaterialTheme.typography.headlineSmall)

        // Initialisation
        Button(
            onClick = {
                loading = true
                error = null
                try {
                    inferenceService.initialize()
                    initialized = true
                } catch (e: Exception) {
                    error = e.message
                }
                loading = false
            },
            enabled = !initialized && !loading
        ) {
            Text(if (loading) "Chargement..." else "1. Initialiser le moteur")
        }

        if (initialized) {
            Text("✅ Moteur prêt — 2 voix disponibles", color = MaterialTheme.colorScheme.primary)

            // Choix de la voix
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voix : ")
                OnnxInferenceService.Voice.entries.forEach { voice ->
                    FilterChip(
                        selected = voixIndex == voice.ordinal,
                        onClick = { voixIndex = voice.ordinal },
                        label = { Text(voice.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // Vitesse
            Text("Vitesse : ${"%.1f".format(vitesse)}x")
            Slider(
                value = vitesse,
                onValueChange = { vitesse = it },
                valueRange = 0.5f..2.0f,
                steps = 5
            )

            // Texte
            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it },
                label = { Text("Texte à synthétiser") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Synthétiser + Lire
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val r = withContext(Dispatchers.IO) {
                                inferenceService.synthesize(
                                    text = texte,
                                    voice = OnnxInferenceService.Voice.entries[voixIndex],
                                    speed = vitesse
                                )
                            }
                            result = r
                            // Jouer l'audio
                            playing = true
                            withContext(Dispatchers.IO) {
                                playPcm(r.samples, r.sampleRate)
                            }
                            playing = false
                        } catch (e: Exception) {
                            error = e.message
                        }
                        loading = false
                    }
                },
                enabled = !loading && texte.isNotBlank()
            ) {
                Text(if (playing) "🔊 Lecture..." else if (loading) "Synthèse..." else "2. Parler")
            }

            // Résultat
            result?.let { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📊 Résultat", style = MaterialTheme.typography.titleSmall)
                        Text("• Échantillons : ${r.samples.size}")
                        Text("• Fréquence : ${r.sampleRate} Hz")
                        Text("• Durée audio : ${r.audioDurationMs} ms")
                        Text("• Temps synthèse : ${r.synthesisTimeMs} ms")
                        Text("• RTF : ${"%.2f".format(r.realTimeFactor)} ${if (r.realTimeFactor < 1f) "✅" else "⚠️"}")
                        Text("• Voix : ${r.voiceLabel}")
                    }
                }
            }
        }

        // Erreurs
        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "❌ $it",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/** Point d'entrée Hilt pour injecter OnnxInferenceService dans un contexte Compose */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnnxInferenceServiceEntryPoint {
    fun onnxInferenceService(): OnnxInferenceService
}

/** Joue des échantillons PCM float via AudioTrack. */
private fun playPcm(samples: FloatArray, sampleRate: Int) {
    val bufferSize = maxOf(
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
        samples.size * 4 // float = 4 bytes
    )

    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    track.play()
    // Écriture par blocs pour éviter de dépasser le buffer
    val chunkSize = bufferSize / 4
    var offset = 0
    while (offset < samples.size) {
        val len = minOf(chunkSize, samples.size - offset)
        track.write(samples, offset, len, AudioTrack.WRITE_BLOCKING)
        offset += len
    }
    // Attendre la fin
    Thread.sleep((samples.size.toLong() * 1000 / sampleRate) + 200)
    track.stop()
    track.release()
}
