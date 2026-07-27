# Phase 3 — Tâche 3.8 : lecture audio réelle (clôture propre de la Tâche 3.7)

**Objectif :** la Tâche 3.7 a laissé le point 5 (qualité vocale du Palier 1) explicitement non évaluable — aucune lecture audio réelle n'était câblée. Cette tâche corrige ça, pour qu'Issa puisse juger la voix par lui-même avant de trancher Palier 1 seul vs. Palier 1+2.

**Portée volontairement limitée :** un `AudioTrack` minimal pour entendre le résultat — pas `AudioPlaybackService`, pas de gestion de file d'attente multi-phrases, pas de lecture en arrière-plan. Ça reste la Phase 5.

---

## Tâche 3.8.0 — Correctif de contrat de domaine : `sampleRate` manquant

**Le trou :** `AudioSegment.audioData` est du PCM16 brut, sans en-tête (confirmé dans le code mergé de la Tâche 3.1). Sans le sample rate, aucune lecture correcte n'est possible — ni `AudioTrack` ni aucun autre mécanisme ne peut deviner à quelle fréquence rejouer les échantillons. Ce n'était pas visible tant que personne n'essayait réellement de jouer l'audio.

`domain/src/main/kotlin/com/inktone/domain/service/TtsEngine.kt`, modifier `AudioSegment` :

```kotlin
/**
 * Segment audio synthétisé. `wordTimestamps` est vide si
 * [TtsCapabilities.wordTimestamps] est faux (§8.9, ADR-013/021).
 *
 * `sampleRate` et `channelCount`/`bitsPerSample` sont nécessaires pour
 * toute lecture réelle du PCM brut — ajout Tâche 3.8, absent depuis la
 * Phase 1 car aucun code ne jouait encore l'audio à ce moment-là.
 * Hypothèse posée ici, à documenter si un moteur futur la viole : PCM16
 * signé, mono. Si un moteur produit un format différent (stéréo,
 * flottant), cette classe devra être étendue explicitement — jamais
 * une supposition silencieuse côté lecteur.
 */
class AudioSegment(
    val audioData: ByteArray,
    val durationMs: Long,
    val wordTimestamps: List<WordTimestamp>,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSegment) return false
        return audioData.contentEquals(other.audioData) &&
            durationMs == other.durationMs &&
            wordTimestamps == other.wordTimestamps &&
            sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + wordTimestamps.hashCode()
        result = 31 * result + sampleRate
        return result
    }
}
```

**Mettre à jour `AndroidNativeTtsEngine.onDone`** (Tâche 3.1) pour passer `sampleRate = sampleRate` au constructeur — le sample rate est déjà extrait par `readWavPcmAndSampleRate`, juste jamais propagé jusqu'ici.

**Mettre à jour `AudioSegmentTest`** (Tâche 1.7) : les deux tests existants doivent maintenant fournir un `sampleRate` (ex. `22050`) — sans quoi le module ne compile plus. Ajouter un troisième cas :

```kotlin
@Test
fun `deux segments avec un sample rate different ne sont pas egaux`() {
    val a = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 22050)
    val b = AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 500L, wordTimestamps = emptyList(), sampleRate = 16000)
    assertNotEquals(a, b)
}
```

**Commit :** `Ajoute sampleRate au contrat AudioSegment, absent depuis la Phase 1`

---

## Tâche 3.8.1 — Lecture réelle via `AudioTrack`

**Objectif :** jouer le PCM brut directement — `AudioTrack` est fait pour ça, pas besoin de reconstruire un en-tête WAV ni de passer par `MediaPlayer`.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/AudioSegmentPlayer.kt` :

```kotlin
package com.inktone.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.inktone.domain.service.AudioSegment
import javax.inject.Inject

/**
 * Lecture minimale pour la marche à blanc (Tâche 3.8) — un AudioTrack
 * par segment, aucune file d'attente, aucune lecture en arrière-plan.
 * AudioPlaybackService (Phase 5) remplacera ceci pour l'usage réel.
 */
class AudioSegmentPlayer @Inject constructor() {

    fun play(segment: AudioSegment) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            segment.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(segment.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(minBufferSize, segment.audioData.size),
            AudioTrack.MODE_STATIC,
            AudioTrack.SESSION_ID_GENERATE,
        )
        audioTrack.write(segment.audioData, 0, segment.audioData.size)
        audioTrack.play()

        // Liberation differee — laisse le temps a la lecture MODE_STATIC
        // de se terminer avant de liberer le AudioTrack. Approche
        // volontairement simple (Thread + sleep) pour cette tache de
        // marche a blanc uniquement ; AudioPlaybackService (Phase 5)
        // gerera ca correctement via des callbacks/coroutines.
        Thread {
            Thread.sleep(segment.durationMs + 200)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}
```

**Brancher dans `ReaderViewModel.playCurrentSentence()`** (Tâche 3.5), en parallèle du minuteur de surlignage existant — les deux partent du même instant, les deux dérivent leur timing du même événement de synthèse réel :

```kotlin
val segment = ttsEngine.synthesize(sentence, voiceProfile)
audioSegmentPlayer.play(segment) // NOUVEAU — démarre en parallèle du surlignage ci-dessous

segment.wordTimestamps.forEach { wt ->
    _state.value = _state.value.copy(highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length))
    delay(wt.endMs - wt.startMs)
}
```

**Commit :** `Ajoute la lecture audio reelle via AudioTrack pour la marche a blanc`

---

## Tâche 3.8.2 — Vérification manuelle (par toi, pas par Claude Code)

**Objectif :** entendre la voix, juger sa qualité — c'est un jugement subjectif, personne ne peut le faire à ta place.

**Procédure :**
1. Installer l'app avec les changements de 3.8.0/3.8.1.
2. Appuyer sur « Lire ».
3. Écouter — vérifier que le son sort à la bonne vitesse/hauteur (si ça sonne étrangement aigu ou lent, `sampleRate` est probablement mal propagé quelque part — signal d'un bug, pas une caractéristique de la voix).
4. Juger la qualité perçue de la voix native Android sur ce device.

**Ce que je ne peux pas faire à ta place :** évaluer si cette qualité suffit pour la v1. Une fois que tu as écouté, la question posée en fin de Tâche 3.7 (Palier 1 seul vs. Palier 1+2) redevient tranchable.

---

## Mise à jour de la checklist de sortie Phase 3

Ajouter à `docs/execution/PHASE_3_MARCHE_A_BLANC.md`, section « Ce qui n'est PAS validé » :

```
- [MISE A JOUR] Lecture audio reelle cablee (Tache 3.8, AudioTrack sur
  PCM brut). Qualite vocale a evaluer par Issa - voir Tache 3.8.2.
```

Ne pas remplir la case toi-même avec une conclusion sur la qualité — laisser le champ pour qu'Issa y reporte son propre jugement après écoute.
