# Phase 3 — Tâche 3.1 (reconstruite) : adaptateur `TextToSpeech` natif (Palier 1, ADR-021)

**Contexte :** la Tâche 3.1 d'origine (intégration Readium) est repoussée en 3.2. Le spike de vérification Sherpa-ONNX prévu en 3.4/3.5 de l'ancien plan est **déjà fait** — empiriquement, par inspection directe des bindings (voir ADR-021). Cette tâche construit maintenant le Palier 1 en premier : c'est le chemin le moins coûteux pour valider toute la chaîne Locator → surlignage → reprise de la marche à blanc, avant d'attaquer Readium ou l'alignement forcé.

## ⚠️ Une inconnue à vérifier avant d'écrire l'adaptateur complet

Notre contrat de domaine (`TtsEngine.synthesize(sentence, voiceProfile): AudioSegment`, Tâche 1.7) suppose un modèle **« synthétiser d'abord, jouer ensuite »** : on obtient un buffer audio + ses timestamps, qu'on joue via notre propre pipeline (`AudioPlaybackService`, Phase 5).

Mais la documentation d'`onRangeStart` la décrit comme déclenchée *« quand l'audio est sur le point de démarrer sur le haut-parleur »* — ce qui suggère qu'elle pourrait n'être émise que pendant une lecture live via `speak()`, pas pendant une synthèse vers fichier via `synthesizeToFile()` (aucun haut-parleur impliqué). Si c'est le cas, notre modèle « buffer d'abord » ne fonctionne pas pour ce moteur : il faudrait laisser Android piloter la lecture réelle et se contenter d'écouter ses événements de progression, plutôt que de pré-synthétiser un `AudioSegment`.

**Personne n'a vérifié ce point empiriquement** — ni la recherche précédente (qui portait sur la fiabilité globale d'`onRangeStart`, pas sur cette nuance précise), ni moi (ça exige un vrai appareil/émulateur Android, hors de portée de mon bac à sable). C'est la toute première chose à faire.

### 3.1.0 — Vérification empirique : `onRangeStart` fonctionne-t-il avec `synthesizeToFile` ?

```kotlin
package com.inktone.infrastructure.tts.spike

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

/**
 * SPIKE — à exécuter manuellement sur émulateur/device avant d'écrire
 * AndroidNativeTtsEngine. Log chaque callback onRangeStart pendant un
 * synthesizeToFile(). Si le Logcat affiche des lignes "onRangeStart"
 * pendant la génération, Plan A (Tâche 3.1.1) s'applique. Si onRangeStart
 * ne se déclenche JAMAIS (mais onDone oui), Plan B (Tâche 3.1.2) s'applique.
 */
class OnRangeStartFileSynthesisSpike(private val context: Context) {

    fun run(onResult: (fired: Boolean) -> Unit) {
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                android.util.Log.e("Spike", "Échec init TextToSpeech")
                onResult(false)
                return@TextToSpeech
            }
            tts.language = Locale.FRENCH
            var rangeStartFired = false

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    android.util.Log.d("Spike", "onStart: $utteranceId")
                }
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    rangeStartFired = true
                    android.util.Log.d("Spike", "onRangeStart: [$start,$end) frame=$frame")
                }
                override fun onDone(utteranceId: String?) {
                    android.util.Log.d("Spike", "onDone: $utteranceId — rangeStartFired=$rangeStartFired")
                    onResult(rangeStartFired)
                }
                override fun onError(utteranceId: String?) {
                    android.util.Log.e("Spike", "onError: $utteranceId")
                    onResult(false)
                }
            })

            val outputFile = File(context.cacheDir, "spike-onrangestart-test.wav")
            val bundle = Bundle()
            tts.synthesizeToFile(
                "Bonjour, ceci est un test de synchronisation.",
                bundle,
                outputFile,
                "spike-utterance-1",
            )
        }
    }
}
```

**Procédure :** exécuter ce spike sur un émulateur avec Google TTS installé (ou un device réel), observer Logcat. Rapporter le résultat avant de continuer.

- **Si `onRangeStart` se déclenche pendant `synthesizeToFile`** → Tâche 3.1.1 (Plan A, buffer d'abord — cohérent avec notre contrat existant).
- **Si seul `onDone` se déclenche, jamais `onRangeStart`** → Tâche 3.1.2 (Plan B, lecture live pilotée par Android).

---

### 3.1.1 — Plan A : `onRangeStart` fonctionne en synthèse fichier

`infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/AndroidNativeTtsEngine.kt` :

```kotlin
package com.inktone.infrastructure.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AndroidNativeTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val id = TtsEngineId.ANDROID_NATIVE

    // wordTimestamps=true CONDITIONNÉ à la confirmation du spike 3.1.0.
    // Si un jour le moteur OS actif ne le supporte pas (constructeur non
    // Google), le détecter au runtime et exposer une instance de
    // capacités différente — pas un mensonge statique ici.
    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = true,
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = true,
        modelSizeMb = 0,
        license = "Système Android (moteur OS installé)",
    )

    private var tts: TextToSpeech? = null

    private suspend fun ensureInitialized(): TextToSpeech = tts ?: suspendCancellableCoroutine { cont ->
        val instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                cont.resume(tts!!)
            } else {
                cont.resumeWithException(IllegalStateException("Échec d'initialisation TextToSpeech: $status"))
            }
        }
        instance.language = Locale.FRENCH
        tts = instance
    }

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        val engine = ensureInitialized()
        val utteranceId = UUID.randomUUID().toString()
        val outputFile = File(context.cacheDir, "tts-$utteranceId.wav")
        val wordTimestamps = mutableListOf<WordTimestamp>()

        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    if (id != utteranceId) return
                    val word = sentence.text.substring(start, end)
                    // frame est en échantillons audio ; conversion en ms
                    // nécessite le sample rate — TextToSpeech ne l'expose
                    // pas directement avant lecture du WAV : approximé
                    // ici via le ratio frame/sampleRate lu du fichier une
                    // fois la synthèse terminée (voir onDone ci-dessous).
                    wordTimestamps += WordTimestamp(
                        word = word, startMs = 0L, endMs = 0L, charOffset = start,
                    )
                }

                override fun onDone(id: String?) {
                    if (id != utteranceId) return
                    val (bytes, sampleRate) = readWavPcmAndSampleRate(outputFile)
                    val corrected = reconcileTimestampsWithSampleRate(wordTimestamps, sentence, sampleRate)
                    val durationMs = (bytes.size.toLong() * 1000L) / (sampleRate * 2L) // PCM16 mono
                    outputFile.delete()
                    cont.resume(AudioSegment(audioData = bytes, durationMs = durationMs, wordTimestamps = corrected))
                }

                override fun onError(id: String?) {
                    if (id != utteranceId) return
                    cont.resumeWithException(IllegalStateException("Échec de synthèse TTS pour l'utterance $utteranceId"))
                }
            })

            val bundle = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceProfile.volume)
            }
            engine.setSpeechRate(voiceProfile.speed)
            engine.synthesizeToFile(sentence.text, bundle, outputFile, utteranceId)
        }
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow {
        // Ce moteur produit ses événements de mot PENDANT synthesize(),
        // pas pendant la lecture (qui passe par notre AudioPlaybackService
        // une fois l'AudioSegment obtenu) — contrairement à un moteur
        // neuronal où la lecture et le timing sont découplés de la
        // synthèse. Ce flow reste donc vide pour ce moteur : les
        // WordTimestamp sont déjà dans l'AudioSegment retourné.
        awaitClose { }
    }
}
```

**Point d'attention explicite dans le code ci-dessus :** `frame` dans `onRangeStart` est en échantillons, pas en millisecondes — la conversion exacte exige le sample rate du WAV produit, connu seulement après écriture du fichier. La fonction `reconcileTimestampsWithSampleRate` (à écrire, triviale : relire le WAV, extraire `sampleRate` de son en-tête, recalculer `startMs = frame * 1000 / sampleRate` pour chaque timestamp) doit être implémentée avant que ce code compile réellement — non montrée ici pour rester focalisé sur la structure, mais **obligatoire**, pas optionnelle.

---

### 3.1.2 — Plan B : `onRangeStart` ne fonctionne qu'en lecture live

Si le spike 3.1.0 confirme que `synthesizeToFile` ne déclenche jamais `onRangeStart` :

**Changement d'architecture pour ce moteur uniquement :** `AndroidNativeTtsEngine` ne produit pas de buffer réutilisable. Il pilote lui-même la lecture (via `speak()`, en direct) et émet ses événements de mot via `observePlaybackEvents()` **pendant** la lecture réelle — pas via `synthesize()` qui retournerait un `AudioSegment` vide ou fictif.

```kotlin
override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
    // Pas de buffer pour ce moteur en Plan B — la lecture ET le timing
    // sont couplés côté OS. Retourne un AudioSegment vide ; le vrai
    // travail se passe dans observePlaybackEvents() + une méthode
    // dédiée playLive(sentence, voiceProfile) à ajouter à ce moteur
    // spécifiquement (hors de l'interface TtsEngine commune — nécessite
    // une réflexion d'architecture avant la Phase 5, pas une improvisation
    // ici).
    return AudioSegment(audioData = ByteArray(0), durationMs = 0L, wordTimestamps = emptyList())
}
```

**Ce plan B n'est volontairement pas développé plus loin ici.** S'il se confirme, c'est un signal qu'il faut réviser l'interface `TtsEngine` elle-même (Tâche 1.7) pour accueillir un mode « lecture pilotée par le moteur » à côté du mode « buffer préconstruit » — une décision d'architecture, pas un correctif ponctuel. Retour à cette discussion avant d'aller plus loin si le spike tombe sur ce cas.

---

## Critère de validation avant/après (Tâche 3.1 dans son ensemble)

- **Avant :** aucune confirmation empirique du comportement d'`onRangeStart` en synthèse fichier ; contrat `TtsEngine` non testé contre un moteur réel.
- **Après 3.1.0 :** Logcat capturé et rapporté, tranchant Plan A vs Plan B.
- **Après 3.1.1 (si Plan A) :** `AndroidNativeTtsEngine.synthesize()` sur la phrase de test renvoie un `AudioSegment` avec au moins un `WordTimestamp` par mot de la phrase, `charOffset` correspondant exactement aux limites de mots du texte source, `startMs` croissants et cohérents avec `durationMs`.

**Commit :** `Ajoute l'adaptateur TextToSpeech natif (Palier 1, ADR-021) apres verification empirique d'onRangeStart`

---

## Reste de la Phase 3 — séquence révisée (aperçu, détail à venir après validation de 3.1)

| # | Tâche | Note |
|---|---|---|
| 3.0 | `TtsEngineId.ANDROID_NATIVE` + fixes éventuels | Voir patch Blueprint §7 |
| **3.1** | **Adaptateur `TextToSpeech` natif (ce document)** | Fait en premier — le moins coûteux |
| 3.2 | Intégration Readium | Inchangé par rapport au plan d'origine |
| 3.3 | Mapping Locator Readium ↔ domaine | Inchangé |
| 3.4 | Document Model minimal | Inchangé |
| 3.5 | `feature/reader` squelette MVI, surlignage piloté par le Palier 1 | Le moteur neuronal (Palier 2, alignement CTC) est repoussé à la Phase 5 — la marche à blanc n'a plus besoin de l'attendre |
| 3.6 | Persistance `ReadingState` (K3) + reprise après relance | Inchangé |
| 3.7 | Test de bout en bout + décision : le Palier 1 seul suffit-il pour la v1, ou le Palier 2 reste-t-il nécessaire ? | Décision informée par l'usage réel, pas anticipée |
