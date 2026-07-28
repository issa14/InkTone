# Phase 5 — TTS Engine complet

**Dépend de :** Phase 4 (close, validée contre un EPUB réel)
**Précède :** Phase 6 — Bibliothèque & import
**Référence :** Blueprint InkTone v1.2.2, §8 (TTS Engine), ADR-021 (architecture à paliers), §11.2 (budgets)
**Sortie de phase :** voir Checklist finale en fin de document.

## Niveau de certitude, à lire avant de commencer

Contrairement à Readium (dépendance Maven propre, vérifiée par la lecture directe des sources aux Tâches 3.2/4.x), **l'intégration Android de Sherpa-ONNX n'est pas un `implementation(...)` simple** : elle exige de placer des bibliothèques natives (`libonnxruntime.so`, `libsherpa-onnx-jni.so`) dans `jniLibs/`, et de recopier les fichiers Kotlin de l'API directement dans le projet (pas un artefact publié séparément). C'est confirmé par la documentation officielle de build Android du projet et par le dépôt d'exemples `k2-fsa/sherpa-onnx/android/`.

**Conséquence pour ce document :** les Tâches 5.1 et 5.2 sont structurées « vérifier et prototyper d'abord », comme le spike Sherpa-ONNX de la Phase 3 — pas du code de production affirmé avec la même confiance que Readium. Les Tâches 5.3 à 5.10 suivent des patterns Android standards, plus proches en certitude de ce qui a été fait en Phase 4.

---

## Tâche 5.1 — Adaptateur Sherpa-ONNX (synthèse neuronale, Palier 2)

### 5.1.0 — Vérification et vendoring, avant tout code d'adaptateur

**Ce qui est confirmé** (documentation officielle de build, `k2-fsa.github.io/sherpa`) :

1. Télécharger les bibliothèques natives prébuilt depuis les releases GitHub — jamais compilées depuis les sources par nous (build C++/NDK hors périmètre) :
   ```bash
   wget https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-v1.13.4-android.tar.bz2
   ```
   **Vérifier le numéro de version le plus récent avant d'exécuter** — celui-ci date de la recherche de ce document, pas une constante figée.

2. Extraire et placer par ABI :
   ```bash
   infrastructure/tts/src/main/jniLibs/arm64-v8a/libonnxruntime.so
   infrastructure/tts/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
   # repeter pour armeabi-v7a si le parc d'appareils cible l'exige (Snapdragon 680 = arm64-v8a uniquement normalement — VERIFIER, ne pas supposer)
   ```

3. Copier les fichiers Kotlin de l'API officielle (`sherpa-onnx/kotlin-api/*.kt` du dépôt `k2-fsa/sherpa-onnx`, pas réécrits à la main) dans `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/sherpa/` — **vendoring délibéré**, pas un raccourci : ces fichiers ne sont publiés nulle part ailleurs (confirmé Phase 3).

**Ce qui N'EST PAS vérifié, à faire avant d'écrire l'adaptateur final :**
- La structure exacte du tarball 1.13.4+ (les noms de fichiers ont pu changer depuis les exemples vus).
- Si `arm64-v8a` seul suffit pour le parc cible (Snapdragon 680 est arm64, mais vérifier qu'aucun appareil de test n'est en 32 bits).
- La taille exacte du binaire natif ajouté à l'APK (impact sur le budget §11.2, ≤ 60 Mo hors modèles de voix).

**Action concrète, avant 5.1.1 :** cloner `k2-fsa/sherpa-onnx`, ouvrir `android/SherpaOnnxTtsEngine/` (exemple officiel le plus proche de notre cas d'usage — il enveloppe Sherpa-ONNX derrière `android.speech.tts.TextToSpeechService`, structure différente de la nôtre mais référence fiable pour le câblage Gradle/jniLibs réel), et **confirmer par la pratique** — build local du projet d'exemple avant de reproduire quoi que ce soit dans InkTone.

### 5.1.1 — Modèle vocal : Kokoro via Sherpa-ONNX

**Rappel Tâche 3.5/ADR-021 :** Kokoro n'est pas un moteur séparé — c'est un choix de modèle *dans* Sherpa-ONNX (`OfflineTtsKokoroModelConfig`, confirmé Phase 3). Télécharger le modèle français depuis les releases `tts-models` du même dépôt (URL exacte à confirmer au moment de l'implémentation — le catalogue de modèles évolue).

`domain/src/main/kotlin/com/inktone/domain/model/VoiceProfile.kt` — aucune modification nécessaire, `TtsEngineId.SHERPA_ONNX` existe déjà depuis la Phase 1.

### 5.1.2 — Structure de l'adaptateur (squelette, à compléter après 5.1.0)

`infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/SherpaOnnxTtsEngine.kt` :

```kotlin
package com.inktone.infrastructure.tts

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.awaitClose
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Palier 2 (ADR-021) — qualite vocale superieure au Palier 1, PAS de
 * timestamps mot natifs (confirme empiriquement Phase 3 : GeneratedAudio
 * = samples + sampleRate uniquement, quel que soit le modele charge).
 * Les WordTimestamp de cet adaptateur proviennent du passage
 * d'alignement CTC (Tache 5.2), jamais directement de Sherpa-ONNX.
 *
 * CORPS NON IMPLEMENTE ICI — depend du vendoring reel (5.1.0) et de la
 * forme exacte de l'API Kotlin copiee depuis le depot officiel. Ecrire
 * ce corps seulement apres avoir confirme par la pratique (build de
 * l'exemple officiel) la signature reelle de OfflineTts.generate() en
 * Kotlin (pas seulement en Python, verifie Phase 3).
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    // Dependances reelles (chemins de modeles, config native) a preciser
    // une fois 5.1.0 termine — pas de placeholder invente ici.
) : TtsEngine {

    override val id = TtsEngineId.SHERPA_ONNX

    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = true, // vrai UNIQUEMENT parce que 5.2 (alignement CTC) complete cet adaptateur — jamais Sherpa-ONNX seul
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = false, // A CONFIRMER contre l'API reelle — Kokoro/VITS n'exposent pas forcement un controle de hauteur independant
        modelSizeMb = 0, // A mesurer reellement une fois le modele Kokoro fr telecharge (5.1.1)
        license = "Apache-2.0 (Kokoro)",
    )

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        TODO("Implemente apres 5.1.0 (vendoring confirme) et 5.2 (alignement CTC pour peupler wordTimestamps)")
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow { awaitClose { } }
}
```

**Critère de validation avant/après (5.1 dans son ensemble) :**
- Avant : aucune bibliothèque native Sherpa-ONNX dans le projet, aucune preuve que le vendoring fonctionne sur ce projet spécifiquement.
- Après 5.1.0 : le projet d'exemple officiel compile et tourne en local (preuve que l'environnement de build gère les `.so`) — **avant** de toucher au code InkTone.
- Après 5.1.2 complété (pas montré ici, dépend de 5.1.0) : `synthesize()` produit un `AudioSegment` réel sur une phrase de test, `wordTimestamps` vide à ce stade (rempli seulement après 5.2).

**Commit (5.1.0 seul, le reste dépend de la vérification) :** `Vendoring Sherpa-ONNX (bibliotheques natives + API Kotlin officielle)`

---

## Tâche 5.2 — Alignement forcé CTC (timestamps réels du Palier 2)

**Niveau de certitude : le plus bas de toute la Phase 5.** La recherche de la Phase 3 a trouvé un précédent réel (`react-native-sherpa-onnx`, mode `timingMode: 'aligned'`, wav2vec2 CTC, entièrement hors ligne) — mais aucune implémentation Kotlin/Android native n'a été vérifiée directement dans ce fil. Cette tâche est un prototypage encadré, pas une implémentation à exécuter en confiance.

### 5.2.0 — Étudier la référence avant d'écrire quoi que ce soit

**Action concrète, avant tout code :** cloner `react-native-sherpa-onnx` (ou le module `alignment` qu'il référence), lire son implémentation réelle de l'alignement forcé — quel modèle CTC exact, quelle bibliothèque de décodage Viterbi, quel format d'entrée/sortie. **Ne pas réinventer l'algorithme de zéro sans avoir regardé comment un projet réel l'a déjà résolu.**

### 5.2.1 — Choix du modèle CTC français

Le Blueprint (§8.9 révisé, patch ADR-021) mentionne des modèles CTC multilingues disponibles dans l'écosystème Sherpa-ONNX (Omnilingual ASR, NeMo/Kroko). **À trancher par une comparaison réelle**, pas une supposition :
- Taille du modèle (impact APK/téléchargement, §11.2)
- Qualité d'alignement sur du français avec liaisons/élisions (« l'homme », « peut-être ») — cas connu comme délicat (recherche Phase 3)
- Latence sur Snapdragon 680 pour une phrase de quelques secondes

### 5.2.2 — Principe de l'algorithme (structure, pas encore de code final)

```kotlin
package com.inktone.infrastructure.tts.alignment

/**
 * Alignement force : etant donne un audio ET le texte QUI A ETE UTILISE
 * POUR LE GENERER (pas une transcription a deviner), determiner le
 * timing de chaque mot. Plus simple qu'une reconnaissance vocale
 * ouverte car le texte cible est deja connu — c'est une contrainte, pas
 * une recherche.
 *
 * ETAPES (structure a valider contre 5.2.0, pas encore une implementation
 * testee) :
 * 1. Faire passer l'audio genere (Tache 5.1) dans le modele CTC ->
 *    posteriors par frame (probabilite de chaque token du vocabulaire a
 *    chaque instant).
 * 2. Decodage de Viterbi CONTRAINT par la sequence de tokens attendue
 *    (celle du texte de la Sentence) -> chemin optimal alignant chaque
 *    token a une plage de frames.
 * 3. Regrouper les tokens par mot (le texte de la Sentence donne les
 *    frontieres de mots) -> WordTimestamp avec startMs/endMs reels.
 */
interface ForcedAligner {
    suspend fun align(audioSamples: ByteArray, sampleRate: Int, expectedText: String): List<com.inktone.domain.service.WordTimestamp>
}
```

**Ce document s'arrête délibérément ici pour la Tâche 5.2.** Écrire l'implémentation complète de `ForcedAligner` sans avoir fait 5.2.0 serait produire du code qui semble complet mais n'a jamais été mis à l'épreuve — exactement le risque que toute la discipline de ce projet cherche à éviter. **Revenir sur cette tâche avec le détail complet une fois 5.2.0 fait.**

**Critère de sortie de 5.2.0 (ce qui doit exister avant de continuer) :** un rapport court (`docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`) documentant le modèle choisi, sa taille, sa latence mesurée sur device réel pour une phrase de test, et sa précision approximative sur un échantillon de phrases françaises avec liaisons — avant tout code de production.

---

## Tâche 5.3 — Buffer/préchargement adaptatif

**Objectif :** silence inter-phrases ≤ 150 ms (§11.2) — la phrase n+1 se synthétise pendant la lecture de la phrase n.

`feature/reader/src/main/kotlin/com/inktone/feature/reader/SentenceAudioBuffer.kt` :

```kotlin
package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import javax.inject.Inject

/**
 * Precharge la synthese de la phrase suivante pendant la lecture de la
 * phrase courante (Blueprint §8.7). Un seul slot d'avance — pas de file
 * profonde, la lecture est sequentielle par nature (une phrase a la
 * fois), inutile de precharger plus loin qu'une phrase.
 */
class SentenceAudioBuffer(private val scope: CoroutineScope, private val ttsEngine: TtsEngine) {

    private var preloadedNext: Deferred<AudioSegment>? = null
    private var preloadedForSentenceIndex: Int? = null

    fun preloadNext(sentence: Sentence, voiceProfile: VoiceProfile) {
        if (preloadedForSentenceIndex == sentence.index) return // deja en cours ou fait
        preloadedNext = scope.async { ttsEngine.synthesize(sentence, voiceProfile) }
        preloadedForSentenceIndex = sentence.index
    }

    /** Consomme le segment preload s'il correspond, sinon synthetise a la volee (repli, ex. saut manuel). */
    suspend fun get(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        if (preloadedForSentenceIndex == sentence.index) {
            val segment = preloadedNext!!.await()
            preloadedNext = null
            preloadedForSentenceIndex = null
            return segment
        }
        return ttsEngine.synthesize(sentence, voiceProfile)
    }
}
```

**Critère de validation :** silence mesuré entre deux phrases consécutives ≤ 150 ms sur device réel (chronométrage manuel ou log horodaté, benchmark formel en Tâche 5.9).

**Commit :** `Ajoute le buffer de prechargement audio (phrase n+1 pendant lecture de n)`

---

## Tâche 5.4 — MediaSession et lecture en arrière-plan

**Objectif :** la lecture continue écran éteint, contrôlable depuis la notification/écran verrouillé.

`infrastructure/media/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
}

android { namespace = "com.inktone.infrastructure.media" }

dependencies {
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
}
```

`infrastructure/media/src/main/kotlin/com/inktone/infrastructure/media/AudioPlaybackService.kt` :

```kotlin
package com.inktone.infrastructure.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Remplace la lecture ad hoc de la marche a blanc (AudioSegmentPlayer,
 * Tache 3.8 — AudioTrack jetable) par un vrai service de lecture,
 * survivant a la mise en arriere-plan. AudioSegmentPlayer reste utilise
 * tel quel uniquement si un jour un besoin de preview tres court
 * (aperçu d'une voix dans les reglages) justifie un chemin plus leger —
 * pas pour la lecture de publication.
 */
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }
}
```

**Point d'attention, pas encore résolu ici :** `ExoPlayer` attend un flux audio (fichier, stream), pas un `ByteArray` PCM brut en mémoire comme le produit `AudioSegment` (Tâche 3.8). Deux options à trancher en écrivant le code complet : écrire chaque `AudioSegment` dans un fichier temporaire WAV avant de le donner à ExoPlayer (simple, un peu de latence disque), ou implémenter un `MediaSource` custom lisant directement le buffer mémoire (plus rapide, plus de code). **Ne pas décider silencieusement — documenter le choix retenu dans le commit.**

`app/src/main/AndroidManifest.xml`, ajouter le service :

```xml
<service
    android:name="com.inktone.infrastructure.media.AudioPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

**Commit :** `Ajoute AudioPlaybackService (Media3, lecture arriere-plan)`

---

## Tâche 5.5 — Contrôles de lecture complets

**Objectif :** play/pause/stop/vitesse/voix à chaud, exposés via `MediaSession` (donc disponibles depuis la notification et les boutons matériels).

`feature/player/src/main/kotlin/com/inktone/feature/player/PlayerViewModel.kt` :

```kotlin
package com.inktone.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val currentVoiceProfileId: String? = null,
)

sealed interface PlayerIntent {
    object PlayPause : PlayerIntent
    object Stop : PlayerIntent
    data class ChangeSpeed(val speed: Float) : PlayerIntent
    data class ChangeVoice(val voiceProfileId: String) : PlayerIntent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    // MediaController connecte au AudioPlaybackService (Tache 5.4) —
    // injection exacte a preciser une fois 5.4 stabilise (le controller
    // se construit de maniere asynchrone via un ListenableFuture, pas
    // directement injectable comme un objet simple).
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.PlayPause -> togglePlayPause()
            is PlayerIntent.Stop -> stop()
            is PlayerIntent.ChangeSpeed -> changeSpeed(intent.speed)
            is PlayerIntent.ChangeVoice -> changeVoice(intent.voiceProfileId)
        }
    }

    private fun togglePlayPause() { /* deleguer au MediaController — a completer avec 5.4 */ }
    private fun stop() { /* idem */ }
    private fun changeSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        // Rappel Blueprint §8.9 : un changement de vitesse doit recalculer
        // les timestamps EXACTEMENT, pas les approximer — pour le Palier
        // 2 (Sherpa-ONNX), cela signifie probablement resynthetiser plutot
        // que d'accelerer l'audio existant (l'acceleration naive degrade
        // la prosodie). A trancher explicitement, pas par defaut.
    }
    private fun changeVoice(voiceProfileId: String) {
        _state.value = _state.value.copy(currentVoiceProfileId = voiceProfileId)
    }
}
```

**Commit :** `Ajoute les controles de lecture (structure ViewModel, cablage MediaController a completer avec 5.4)`

---

## Tâche 5.6 — Téléchargement de voix à la demande (ADR-018)

**Objectif :** APK sans modèles embarqués ; téléchargement au premier lancement, vérifié par empreinte, utilisable hors ligne ensuite.

`infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/VoiceModelDownloader.kt` :

```kotlin
package com.inktone.infrastructure.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress
    data class VerificationFailed(val expectedHash: String, val actualHash: String) : DownloadProgress
    data class Complete(val modelFile: File) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

@Singleton
class VoiceModelDownloader @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * URL et hash SHA-256 attendu A CONFIRMER une fois le modele Kokoro
     * francais choisi (Tache 5.1.1) — pas invente ici.
     */
    fun downloadVoiceModel(url: String, expectedSha256: String, fileName: String): Flow<DownloadProgress> = flow {
        val targetFile = File(context.filesDir, "voices/$fileName")
        if (targetFile.exists() && verifyHash(targetFile, expectedSha256)) {
            emit(DownloadProgress.Complete(targetFile))
            return@flow
        }
        targetFile.parentFile?.mkdirs()

        withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection()
            val totalBytes = connection.contentLengthLong
            var downloaded = 0L
            connection.getInputStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        emit(DownloadProgress.InProgress(downloaded, totalBytes))
                        read = input.read(buffer)
                    }
                }
            }
        }

        if (!verifyHash(targetFile, expectedSha256)) {
            val actual = computeHash(targetFile)
            targetFile.delete() // ne jamais garder un modele dont l'empreinte est fausse
            emit(DownloadProgress.VerificationFailed(expectedSha256, actual))
            return@flow
        }

        emit(DownloadProgress.Complete(targetFile))
    }.flowOn(Dispatchers.IO)

    private fun verifyHash(file: File, expectedSha256: String) = computeHash(file) == expectedSha256

    private fun computeHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read >= 0) { digest.update(buffer, 0, read); read = input.read(buffer) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

**Commit :** `Ajoute le telechargement de modele vocal avec verification d'empreinte (ADR-018)`

---

## Tâche 5.7 — `feature/player` UI complète

**Objectif :** parcours manuel complet — play/pause, vitesse, sélection de voix, barre de progression.

Composant standard Compose (Material 3), consommant `PlayerViewModel` (Tâche 5.5) et `VoiceModelDownloader` (Tâche 5.6) pour l'état de téléchargement. **Pas détaillé pixel par pixel ici** — le pattern est identique à `ReaderScreen` (Tâche 4.7) : état immuable, intents explicites, aucune logique métier dans le Composable.

**Commit :** `Ajoute l'UI feature-player complete`

---

## Tâche 5.8 — Gestion d'erreurs TTS

**Objectif :** voix indisponible, modèle corrompu, erreur de synthèse — jamais d'interruption brutale (Blueprint §8.12).

Étendre `PlaybackEvent.Error` (déjà défini domaine Phase 1) pour couvrir : échec de téléchargement de modèle, empreinte invalide (Tâche 5.6), échec JNI natif (Tâche 5.1). Chaque cas propose un repli explicite (basculer au Palier 1 automatiquement si le Palier 2 échoue en cours de lecture — cohérent avec la détection au runtime déjà actée par ADR-021).

**Commit :** `Ajoute la gestion d'erreurs TTS avec repli automatique vers le Palier 1`

---

## Tâche 5.9 — Benchmarks TTS

**Objectif :** mesurer réellement les budgets §11.2 — latence premier audio, silence inter-phrases, précision du surlignage.

Étendre le module `benchmark` (Tâche 4.9) avec un scénario TTS. **Le budget de précision (±120 ms) ne peut être mesuré qu'une fois 5.2 (alignement CTC) terminé** — noter explicitement toute mesure faite avant cela comme partielle (Palier 1 seul).

**Commit :** `Ajoute les benchmarks TTS (latence, silence inter-phrases, precision surlignage)`

---

## Tâche 5.10 — Tests par capacité

**Objectif :** chaque adaptateur (Palier 1, Palier 2) vérifié contre son propre `TtsCapabilities` déclaré — aucun adaptateur ne prétend une capacité qu'il n'a pas (§8.4).

```kotlin
// Pattern : pour chaque TtsEngine enregistre, verifier que
// capabilities.wordTimestamps == true implique reellement des
// WordTimestamp non vides dans AudioSegment, et que wordTimestamps ==
// false ne produit jamais de WordTimestamp invente.
```

**Commit :** `Ajoute les tests de coherence capacite/comportement par adaptateur TTS`

---

## Checklist finale de sortie de Phase 5

**Mise à jour du 2026-07-28** — case par case, d'après les mesures réelles
de `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §1-10 (Kokoro et
l'alignement CTC sont désormais assemblés en un pipeline réel, pas
seulement prototypés séparément).

| # | Critère | État réel | Vérification |
|---|---|---|---|
| 1 | Vendoring Sherpa-ONNX confirmé par la pratique (projet officiel buildé localement) | ✅ Fait — et Kokoro (remplace VITS) vendoré et branché en production | 5.1.0, `PROTOTYPE_ALIGNEMENT_CTC.md` §8 |
| 2 | Adaptateur Sherpa-ONNX produit un `AudioSegment` réel | ✅ Fait — avec de vrais `WordTimestamp`, pas seulement l'audio | 5.1.2, §9.4 |
| 3 | Alignement CTC prototypé et mesuré avant code de production | ✅ Fait, et **branché en production** (dépasse le critère d'origine) | `PROTOTYPE_ALIGNEMENT_CTC.md` §1-9 |
| 4 | Silence inter-phrases ≤ 150 ms | ❌ **Échec mesuré** — de l'ordre de 25-30 s, pas 150 ms (synthèse Kokoro ~28-34 s pour ~4,8 s d'audio) | §10.1-10.2 |
| 5 | Lecture continue écran éteint | ⬜ Non commencé (5.4) — inchangé par cette tâche | — |
| 6 | Contrôles complets fonctionnels | ⬜ Non commencé (5.5) — inchangé | — |
| 7 | Téléchargement de voix vérifié par empreinte | ⬜ Non commencé (5.6) — placement manuel des modèles toujours nécessaire | — |
| 8 | UI `feature/player` complète | ⬜ Non commencé (5.7) — inchangé | — |
| 9 | Repli automatique Palier 2 → Palier 1 sur erreur | ⬜ Non fait (5.8) — **devient critique** : sans lui, sélectionner Palier 2 aujourd'hui expose l'utilisateur à ~30 s de silence par phrase | — |
| 10 | Budgets TTS §11.2 mesurés (précision ±120ms incluse) | ⚠️ **Mesurés pour de vrai, et en échec** sur 2 des 3 budgets TTS (tap→premier audio, silence inter-phrases) ; précision du surlignage (±120ms) dans le budget | §10.1 |
| 11 | Aucun adaptateur ne ment sur ses capacités | ✅ Toujours vrai — `wordTimestamps=true` est honnête et vérifié (`TtsCapabilityConsistencyTest`), mais l'honnêteté des capacités ne dit rien de la viabilité en production (item 10) | §9.4 |

**Verdict explicite** : cette phase n'est **pas prête à être close**. Les
items 1-3 et 11 sont réellement acquis (pas juste déclarés). Les items
4 et 10 ne sont pas simplement « pas encore faits » — ils ont été
**mesurés et ont échoué**, ce qui rend l'item 9 (repli automatique)
nécessaire avant tout déploiement de Palier 2, pas optionnel. Un ADR est
nécessaire avant de continuer : soit une piste de réduction de latence
Kokoro fonctionne (profilage, threads, modèle plus léger, accélération
matérielle), soit le budget §11.2 est révisé, soit Palier 2 reste
désactivé par défaut derrière le repli Palier 1 en attendant.

**Deux points de décision explicites avant de considérer cette phase close**, cohérents avec la discipline du reste du projet : le choix ExoPlayer/fichier temporaire vs `MediaSource` custom (Tâche 5.4), et la validation empirique de l'alignement CTC (5.2.0) — ni l'un ni l'autre ne doit être tranché silencieusement en cours d'implémentation. **Un troisième point rejoint ces deux-là** : la viabilité de latence de Palier 2 (Kokoro), mesurée et en échec (§10), doit être explicitement arbitrée — pas contournée silencieusement par un futur commit qui changerait les seuils ou désactiverait les tests sans discussion.
