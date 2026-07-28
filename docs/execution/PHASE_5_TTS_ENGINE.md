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

## Diagnostic de la latence Kokoro (RTF ~6-7×) — avant conclusion architecturale

**2026-07-28.** `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §10 mesurait
un RTF Kokoro de ~6-7× sur le V2206 (Snapdragon 680) et concluait à une
latence bloquante pour le budget §11.2. Avant d'écrire un ADR sur cette
base, cette section élimine méthodiquement les causes de configuration
évidentes — même discipline que le reste de cette phase : ne pas
supposer une limite matérielle avant d'avoir vérifié les leviers logiciels
triviaux.

Phrase de test inchangée : « Bonjour le monde. Ceci est un test pour
vérifier l'alignement. » (~4,8 s d'audio Kokoro produit). Toutes les
mesures ci-dessous sont des médianes sur 5 exécutions à chaud (modèle déjà
chargé), sur le même device physique.

### 1. `numThreads` — avant/après, avec le RTF de chaque valeur

**Avant** : `numThreads = 2` (valeur non justifiée, héritée du prototype
Kokoro d'origine). Mesuré (`PROTOTYPE_ALIGNEMENT_CTC.md` §10) :
synthèse Kokoro seule ~28 500 – 33 800 ms → **RTF ≈ 5,9 – 7,1×**.

Cœurs réellement disponibles, vérifiés sur le device (pas supposés) :

```
$ adb shell cat /sys/devices/system/cpu/cpu{0..7}/cpufreq/cpuinfo_max_freq
cpu0-3: 1 900 800 Hz (cœurs efficience)
cpu4-7: 2 400 000 Hz (cœurs performance)
```

→ 4 cœurs performants, pas 2. **Après**, `numThreads = 4` :

| Run | Kokoro synth (ms) | RTF |
|---|---|---|
| 1 (provider cpu explicite) | 22 476,51 | 4,69× |
| 2 (provider xnnpack demandé, voir §2) | 22 793,06 | 4,76× |
| 3 (provider nnapi demandé, voir §2) | 22 750,03 | 4,75× |

**Amélioration réelle mais partielle** : ~20-25 % de latence en moins
(28-34 s → ~22,5-22,8 s), RTF passe de ~6-7× à **~4,7×**. Une
amélioration, pas une résolution — toujours très loin du budget.

### 2. Execution provider — vérifié explicitement, pas supposé

`debug = true` (temporaire, pour ce diagnostic) fait apparaître les logs
internes `sherpa-onnx`/ONNX Runtime. Deux fournisseurs demandés
explicitement, chacun avec un message de repli clair :

```
provider = "xnnpack" →
  Available providers: NnapiExecutionProvider, CPUExecutionProvider, . Fallback to cpu!

provider = "nnapi" →
  Android NNAPI requires API level >= 27. Current API level 21 Fallback to cpu!
```

**Deux causes distinctes, toutes deux vérifiées dans le code source de
sherpa-onnx (`session.cc`), pas devinées :**

- **XNNPACK** : absent de la liste `Ort::GetAvailableProviders()` — le
  `.so` vendoré (sherpa-onnx v1.13.4, déjà documenté
  `PROTOTYPE_ALIGNEMENT_CTC.md` §8.1) n'a simplement pas été compilé avec
  le support XNNPACK.
- **NNAPI** : présent dans la liste des providers disponibles, mais le
  code qui l'active est protégé par une macro de compilation
  `#elif defined(__ANDROID_API__)` avec le message « requires API level
  >= 27 » — `__ANDROID_API__` est ici **la valeur au moment de la
  compilation du binaire** (celui-ci cible l'API 21, cohérent avec
  `libsherpa-onnx-jni.so` « built for Android 21 » déjà noté en §8.1),
  **pas** le niveau d'API réel du device (34, largement suffisant pour
  NNAPI). Le device supporterait NNAPI ; c'est le binaire prébuilt qui ne
  l'expose pas pour cette cible de compilation.

**Conclusion de cette étape** : `provider = "cpu"` n'est pas un défaut
non exploré — c'est la **seule option réellement disponible** dans le
binaire vendoré aujourd'hui. Aucune accélération matérielle accessible
sans reconstruire sherpa-onnx depuis les sources avec un NDK ciblant une
API plus récente (option non retenue : contredirait la décision de
Phase 5.1.0 de ne pas compiler sherpa-onnx soi-même).

### 3. Modèle int8 — confirmé réellement quantifié, pas un mixup fp32

Fichier chargé : `model.int8.onnx`, 114 298 054 octets (~109 Mo) — taille
cohérente avec un modèle quantifié (le Kokoro fp32 officiel fait ~310 Mo,
un facteur ~2,7× plus gros ; un mixup de chemin vers le fp32 aurait été
immédiatement visible à la taille). Inspection directe du graphe ONNX
(`onnx.load` + comptage des types de tenseurs et d'opérateurs, pas une
supposition) :

```
Tenseurs (poids)   : FLOAT=449, UINT8=354, INT64=81, INT8=24
Operateurs quantifies presents : DynamicQuantizeLinear=98, MatMulInteger=83,
                                  ConvInteger=90, DynamicQuantizeLSTM=6
```

**Confirmé : quantification dynamique int8 réellement active**
(`DynamicQuantizeLinear`/`MatMulInteger`/`ConvInteger`, pattern standard
d'ONNX Runtime `quantize_dynamic`, pas une quantification statique
pré-calculée mais un calcul de quantification à l'exécution). Aucun
avertissement « falling back to float » dans les logs `sherpa-onnx` sur
l'ensemble des runs de ce diagnostic. Point notable, pas une certitude
absolue : la présence de `DynamicQuantizeLSTM` (6 occurrences) suggère un
composant LSTM dans l'architecture Kokoro (cohérent avec StyleTTS2, dont
Kokoro-82M dérive) — une récurrence LSTM est intrinsèquement séquentielle
dans le temps, ce qui limiterait le gain de plus de threads même avec un
provider optimisé, cohérent avec le gain partiel (pas linéaire) observé
en passant de 2 à 4 threads en étape 1. Hypothèse plausible, pas vérifiée
plus avant (nécessiterait un profilage nœud-par-nœud, hors périmètre de
ce diagnostic).

### 4. G2P vs inférence neuronale — isolé par horodatage des logs internes

À partir des logs `sherpa-onnx` horodatés à la milliseconde pour un run :

| Étape | Début | Fin | Durée |
|---|---|---|---|
| G2P (conversion texte → tokens, lexique + règles) | 10:32:14.543 | 10:32:14.754 | **~211 ms** |
| Inférence neuronale (2 lots, jusqu'au retour de `Generate`) | 10:32:14.755 | 10:32:41.484 | **~26 700 ms** |

**Sans ambiguïté : le G2P (phonémisation espeak-ng/lexique) ne représente
qu'environ 0,8 % du temps total.** La quasi-totalité (~99 %) est le
passage avant du réseau de neurones lui-même. Une piste de cache de
phonémisation (envisagée dans la consigne de ce diagnostic) **n'aurait
qu'un effet négligeable** — ce n'est pas là que se trouve le coût.

### 5. Verdict — signal architectural confirmé, pas une négligence de configuration

Trajectoire complète du RTF à travers ce diagnostic :

| Étape | RTF Kokoro |
|---|---|
| Avant diagnostic (`numThreads=2`, provider cpu implicite) | ~5,9 – 7,1× |
| Après `numThreads=4` | ~4,7× |
| `numThreads=4` + xnnpack demandé (retombe sur cpu, `.so` vendoré) | ~4,7× (inchangé) |
| `numThreads=4` + nnapi demandé (retombe sur cpu, `.so` vendoré) | ~4,7× (inchangé) |
| `numThreads=4` + nnapi recompilé et réellement actif (voir section dédiée) | ~5,4× — plus lent |
| `numThreads=4` + **xnnpack recompilé et réellement actif** (voir section dédiée) | **~5,2× — plus lent** |

Le RTF reste dans le même ordre de grandeur (~4,7× à ~7×) après avoir
épuisé les quatre leviers de configuration listés dans cette tâche :
threads corrigés (+20-25 %, gain réel mais insuffisant), aucun provider
accéléré disponible dans le binaire vendoré, modèle authentiquement
int8, G2P négligeable (~0,2 %). **Ce n'est donc pas une négligence de
configuration — c'est un signal architectural réel**, conforme au critère
explicitement posé pour cette tâche : un RTF qui reste 5-7× après ce
diagnostic justifie un ADR plutôt que de le rendre prématuré.

**Pistes restantes, non explorées ici (hors périmètre de ce diagnostic,
matière pour l'ADR)** : reconstruire sherpa-onnx depuis les sources avec
NNAPI/XNNPACK compilés (contredit la décision 5.1.0) ; un modèle Kokoro
plus petit ou une architecture non-LSTM ; profilage nœud-par-nœud pour
confirmer/infirmer l'hypothèse LSTM de l'étape 3 ; révision du budget
§11.2 par ADR si aucune piste technique ne suffit.

**Mise à jour du code** : `numThreads = 4` et `provider = "cpu"`
(explicite, documenté comme seule option disponible plutôt que laissé
implicite) conservés dans `SherpaOnnxTtsEngine.kt` — c'est une
amélioration réelle mesurée, même si elle ne résout pas le problème de
fond.

---

## NNAPI recompilé depuis les sources — vérifié réellement disponible, pas juste absent du prebuilt

**2026-07-28, suite du diagnostic ci-dessus.** Le point 2 du diagnostic
précédent identifiait une **contrainte de build** du binaire prébuilt
(NNAPI compilé hors-jeu car `ANDROID_PLATFORM=android-21`), pas une
limite prouvée du device. Avant d'écrire l'ADR sur cette base, cette
section vérifie si NNAPI apporte un gain réel une fois activé pour de
vrai — pas supposé à partir d'un message de repli.

### 1. Recherche d'un prebuilt existant avec NNAPI — aucun trouvé

185 releases GitHub `k2-fsa/sherpa-onnx` inspectées via l'API (pas une
recherche manuelle partielle) : seules trois variantes de tarball Android
existent par version (`android`, `android-rknn`, `android-static-link-onnxruntime`)
— **aucune variante NNAPI dédiée**. Le script de build officiel
(`build-android-arm64-v8a.sh`) documente lui-même la marche à suivre :

```
# Please use -DANDROID_PLATFORM=android-27 if you want to use Android NNAPI
```

mais le défaut du script est `android-21`, et le workflow CI officiel
(`.github/workflows/android.yaml`) appelle le script **sans** surcharger
cette variable — confirmé en lisant le YAML, pas supposé. Aucun raccourci
légitime : il faut recompiler.

### 2. Coût réel de la compilation — chiffré, pas juste tenté

NDK 27.2.12479018 et CMake 3.22.1 déjà installés (Tâche 5.2.0),
réutilisés tels quels. Seul ajustement nécessaire : `cmake` n'était pas
sur le `PATH` (premier essai échoué en 58 s, `cmake: command not found` —
corrigé en ajoutant `/home/majeur/Android/Sdk/cmake/3.22.1/bin` au
`PATH`).

```bash
export ANDROID_NDK=.../ndk/27.2.12479018
export SHERPA_ONNX_ANDROID_PLATFORM=android-27
export SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF  # hors perimetre, reduit le temps de build
bash build-android-arm64-v8a.sh
```

**Temps réel mesuré (`time`, pas estimé) : 9 min 45 s** — nettement
moins que redouté (la machine de build n'a que 1,7 Go de RAM libre au
moment du build). Explication : `libonnxruntime.so` n'est pas recompilé
par ce script — il est téléchargé prébuilt (`csukuangfj/onnxruntime-libs`
v1.27.0), **confirmé identique bit à bit** (`sha256sum`) à celui déjà
vendoré (Tâche 5.1.0/5.2). Seul `libsherpa-onnx-jni.so` (le code
sherpa-onnx lui-même, plus petit que l'intégralité d'ONNX Runtime) est
réellement recompilé — d'où le temps raisonnable.

### 3. RTF mesuré avec NNAPI réellement actif — comparaison directe

`libsherpa-onnx-jni.so` recompilé substitué temporairement (même
protocole de test que le diagnostic précédent : même phrase, même
device V2206, `numThreads=4` inchangé, `provider="nnapi"`,
`debug=true`) :

```
07-28 11:20:46 W sherpa-onnx: Use nnapi
```

**Aucun message de repli** (`Failed to enable NNAPI`/`Fallback to cpu`)
— NNAPI s'active réellement cette fois, confirmé par le log, pas supposé.

| Configuration | Synthèse Kokoro (médiane, ms) | RTF | Provider confirmé |
|---|---|---|---|
| `numThreads=2`, cpu (baseline initiale) | ~28 500 – 33 800 | ~5,9 – 7,1× | cpu (implicite) |
| `numThreads=4`, cpu | ~22 476 – 22 793 | ~4,7× | cpu (confirmé, seul dispo dans le prebuilt) |
| `numThreads=4`, **nnapi (recompilé, réellement actif)** | **25 714** | **~5,4×** | **nnapi (confirmé « Use nnapi », pas de repli)** |

**NNAPI, une fois réellement activé, est plus lent que CPU** — pas
seulement indisponible, réellement contre-productif sur ce modèle/device.
Le premier appel (froid) avec NNAPI grimpe même à ~42,4 s (contre
~31-39 s en cpu), cohérent avec le coût de compilation/partitionnement de
graphe propre à NNAPI au premier appel.

**Hypothèse plausible pour expliquer cette contre-performance**, cohérente
avec l'inspection du graphe ONNX de la section précédente
(`DynamicQuantizeLinear`, `MatMulInteger`, `ConvInteger`,
`DynamicQuantizeLSTM`) : ces opérateurs de quantification dynamique et la
composante LSTM ne sont probablement pas supportés nativement par le
pilote NNAPI de ce device (Snapdragon 680, milieu de gamme, sans NPU
dédié documenté) — NNAPI délèguerait alors une grande partie du graphe à
un repli CPU interne à son propre HAL, avec en plus la surcharge de
partitionnement/synchronisation entre les deux chemins d'exécution.
Hypothèse plausible, pas vérifiée plus avant (nécessiterait un accès aux
logs internes du HAL NNAPI du device, hors périmètre).

### 4. Décision — binaire NNAPI non intégré

Le `.so` recompilé (API 27) a été retiré après mesure — restauré à
l'original vendoré (Tâche 5.1.0/5.2, API 21, `sha256` revérifié
identique après restauration). Pas de bénéfice mesuré, donc pas de raison
d'introduire la complexité supplémentaire (maintenance d'un fork de build
sherpa-onnx, `minSdk` à relever à 27 pour ce module — actuellement 26 —
avec la perte de compatibilité associée) pour un résultat pire.

### 5. Verdict final — les leviers de configuration sont épuisés

Cinquième donnée ajoutée aux quatre du diagnostic précédent : **NNAPI
recompilé avec succès, réellement activé, mesuré plus lent que CPU.** Ce
n'est pas un échec de compilation qui aurait laissé la question ouverte —
c'est une mesure complète et négative. Combinée aux quatre vérifications
précédentes (threads, provider, int8, G2P), **tous les leviers de
configuration identifiables ont été essayés et chiffrés** :

| # | Levier | Résultat |
|---|---|---|
| 1 | `numThreads` 2→4 | +20-25 %, insuffisant |
| 2a | XNNPACK | Absent du binaire, non activable sans recompilation |
| 2b | NNAPI (indisponibilité initiale) | Contrainte de build, pas de device |
| 3 | Modèle int8 | Authentiquement quantifié, pas un mixup |
| 4 | G2P vs inférence | G2P négligeable (~0,8 %) |
| 5 | **NNAPI recompilé et réellement testé** | **Plus lent que CPU (~5,4× contre ~4,7×)** |

**L'ADR est maintenant pleinement justifié** — pas seulement parce
qu'un levier était indisponible, mais parce que le seul levier
d'accélération matérielle réellement accessible sur ce device, une fois
compilé et vérifié actif, dégrade la performance au lieu de l'améliorer.
Le RTF ~4,7× (meilleure configuration mesurée à ce jour, CPU + 4 threads)
reste la référence à confronter au budget §11.2 dans l'ADR à venir.

---

## `generateWithConfigAndCallback` livre-t-il vraiment l'audio en incrémental ? — vérifié avant compromis produit

**2026-07-28, suite du diagnostic ci-dessus.** Avant de conclure que la
latence Kokoro impose un compromis produit (attendre la phrase entière),
cette section vérifie un point non testé jusqu'ici : `OfflineTts`
expose `generateWithConfigAndCallback` (`Tts.kt:189-195`), qui accepte un
callback invoqué pendant la génération — mais rien ne garantissait que
ce callback se déclenche plus d'une fois. Une API de callback qui ne
callback qu'à la toute fin serait un streaming illusoire.

### Protocole

Test instrumenté dédié
(`SherpaOnnxCallbackStreamingTest.trajectoire_des_appels_du_callback`,
`infrastructure/tts/src/androidTest/`), même phrase et même config que le
diagnostic RTF ci-dessus (« Bonjour le monde. Ceci est un test pour
vérifier l'alignement. », `numThreads=4`, `provider=cpu`, device V2206) :
chaque appel du callback est horodaté individuellement par rapport au
`t0` du run (pas seulement la valeur de retour finale de
`generateWithConfigAndCallback`), sur 1 run à froid + 3 runs à chaud.

**Contournement nécessaire, en soi un résultat notable** : la syntaxe
lambda trainante Kotlin (`{ samples -> ... }`) fait planter le process en
`SIGABRT`/`JNI DETECTED ERROR` (`NoSuchMethodError` sur
`invoke([F)Ljava/lang/Integer;`) — le lambda généré par
`invokedynamic` (comportement par défaut du compilateur Kotlin utilisé
ici) n'expose pas la classe concrète que l'appel JNI réflexif de
`generateWithConfigImpl` recherche. Remplacé par un
`object : Function1<FloatArray, Int>` explicite (classe concrète, pas de
métafactory `indy`), qui fonctionne sans erreur. Point à retenir si cette
API est un jour branchée en production : la lambda trainante n'est pas
un choix neutre ici.

### Résultat : le callback se déclenche bien plusieurs fois — pas un streaming illusoire

```
[RUN 0] nb_appels_callback=2 total_ms=29901.30 audio_samples=114903 (froid)
[RUN 0][CALLBACK 0] elapsed_ms=12038.87 samples=29777
[RUN 0][CALLBACK 1] elapsed_ms=29899.98 samples=85126

[RUN 1] nb_appels_callback=2 total_ms=23548.42 audio_samples=114916 (chaud)
[RUN 1][CALLBACK 0] elapsed_ms=7272.60  samples=29780
[RUN 1][CALLBACK 1] elapsed_ms=23547.92 samples=85136

[RUN 2] nb_appels_callback=2 total_ms=22899.70 audio_samples=115088 (chaud)
[RUN 2][CALLBACK 0] elapsed_ms=7240.77  samples=29880
[RUN 2][CALLBACK 1] elapsed_ms=22899.17 samples=85208

[RUN 3] nb_appels_callback=2 total_ms=22824.17 audio_samples=114927 (chaud)
[RUN 3][CALLBACK 0] elapsed_ms=7149.25  samples=29785
[RUN 3][CALLBACK 1] elapsed_ms=22823.64 samples=85142
```

**Le callback se déclenche 2 fois, pas 1** — confirmé sur les 4 runs,
pas un artefact isolé. **2, pas un nombre arbitraire** : la phrase de
test contient exactement 2 phrases au sens Kokoro (« Bonjour le monde. »
et « Ceci est un test pour vérifier l'alignement. ») et
`OfflineTtsConfig.maxNumSentences = 1` (valeur par défaut, jamais changée
dans `SherpaOnnxTtsEngine`) — chaque appel du callback correspond à une
phrase individuelle entièrement synthétisée, pas à un flux continu de
petits blocs pendant l'inférence d'une seule phrase. C'est un streaming
réel au niveau **phrase**, pas au niveau **frame audio** — une
granularité plus grossière que ce qu'« incrémental » pourrait laisser
supposer, mais ce n'est pas une fausse promesse d'API : le premier appel
correspond bien à un audio partiel réellement disponible avant la fin de
la génération complète (29 777-29 880 échantillons ≈ 1,24 s d'audio sur
un total ≈ 4,79-4,80 s, cohérent avec la phrase de test déjà caractérisée).

### Le temps jusqu'au PREMIER appel — le vrai candidat pour le budget tap-premier-audio

| Run | 1er appel (ms) | Dernier appel (ms) | Total (ms) | Audio 1re phrase |
|---|---|---|---|---|
| 0 (froid) | **12 038,87** | 29 899,98 | 29 901,30 | ≈1,24 s |
| 1 (chaud) | **7 272,60** | 23 547,92 | 23 548,42 | ≈1,24 s |
| 2 (chaud) | **7 240,77** | 22 899,17 | 22 899,70 | ≈1,24 s |
| 3 (chaud) | **7 149,25** | 22 823,64 | 22 824,17 | ≈1,24 s |

Le premier appel (médiane à chaud ≈ **7 240 ms**) réduit l'attente
d'environ **15,6-15,7 s** par rapport au dernier appel (~22,8-23,5 s) —
un gain réel, pas négligeable. Mais **7,2 s reste ~4,8× au-dessus du
budget §11.2 (≤ 1 500 ms)**, pas sous le seuil. Le RTF de la première
phrase isolée (≈1,24 s d'audio produit en ≈7,2 s) est ≈5,8×, dans le même
ordre de grandeur que le RTF déjà mesuré pour la phrase complète
(~4,7-7×, sections ci-dessus) — cohérent avec l'hypothèse que le
goulot est l'inférence neuronale par phrase, pas un coût fixe de
préparation qui s'amortirait sur une phrase plus courte.

### Ce que ça impliquerait pour l'architecture — décision à prendre ensemble, pas exécutée ici

Le callback est un streaming réel (pas illusoire), au grain phrase.
Techniquement, `TtsEngine.synthesize()` pourrait retourner un flux de
`AudioSegment` (un par phrase Kokoro) plutôt qu'un `AudioSegment` unique
— un changement de **contrat domaine** (`domain/service/TtsEngine.kt`),
pas seulement d'implémentation infrastructure, avec un impact sur
`AudioSegmentPlayer` et l'alignement CTC (qui devrait s'exécuter par
segment, pas sur l'audio complet). **Ce changement n'est pas fait ici** —
il est mentionné parce que la mesure ci-dessus change la question posée
à l'ADR : ce n'est plus « attendre 22-30 s avant le premier son » mais
« attendre ~7,2 s avant le premier son, si le texte lu est découpé en
phrases courtes ». Cela **ne résout pas** le dépassement du budget
§11.2 (7,2 s reste ~4,8× la limite), mais réduit l'écart d'un facteur
~3-4× — une donnée nouvelle et pertinente pour la décision d'ADR
(scinder le budget par phrase, réviser le budget, ou changer de moteur),
pas un problème résolu.

**Verdict de ce diagnostic** : streaming confirmé réel, pas une
API de callback à un seul appel — mais le grain (phrase entière) et le
RTF par phrase (~5,8×, cohérent avec le reste des mesures) font que même
le meilleur candidat mesuré pour le budget tap-premier-audio (~7,2 s)
dépasse toujours ce budget de façon significative. Signal architectural
inchangé sur le fond ; nuance réelle sur l'ampleur exacte de l'écart et
sur où il faudrait couper (par phrase, pas par segment de lecture entier).

---

## Réévaluation du modèle legacy `vits-piper-fr_FR-upmc-medium` sur le pipeline actuel — RTF ~0,33 confirmé, pas une affirmation de commentaire

**2026-07-28.** `PROJECT_STATUS.md`/`architecture.md` du monolithe archivé
(`legacy/monolith`) citaient un RTF ~0,33 pour ce modèle VITS/Piper — mais
c'était une mesure de l'**implémentation legacy** (`PiperTtsProvider.kt`,
archivé), jamais rejouée sur le pipeline actuel (`OfflineTts` de la même
dépendance `sherpa-onnx` que Kokoro, câblée dans `SherpaOnnxTtsEngine`).
Avant de la citer dans l'ADR comme alternative crédible au RTF Kokoro
(~4,7-7×), cette section la revérifie par une mesure réelle, même
protocole que les diagnostics ci-dessus.

### 1. Récupération du modèle — déjà présent localement, pas retéléchargé

Trouvé sur disque, `app/src/main/assets/models/vits-piper-fr_FR-upmc-medium/`
(module `app/`, gitignoré — `.gitignore:32` — jamais commité, laissé sur
le poste depuis le monolithe) : `fr_FR-upmc-medium.onnx` (76 682 529
octets, ~73 Mo — cohérent avec la taille citée dans l'ancienne doc),
`fr_FR-upmc-medium.onnx.json` (métadonnées), `tokens.txt`,
`espeak-ng-data/`, `MODEL_CARD`. Fichiers intacts, pas besoin de
retélécharger depuis Hugging Face (`rhasspy/piper-voices`). Métadonnées
lues directement (`speaker_id_map`), pas devinées : `{"jessica": 0,
"pierre": 1}`, `sample_rate: 22050`, `phoneme_type: espeak`.

### 2. Branché sur le pipeline actuel — même `OfflineTts`, modèle différent

`SherpaOnnxTtsEngine.tts` (le `lazy` de production) est câblé
spécifiquement sur les chemins Kokoro (`SherpaOnnxModelPaths`) — pas
modifié pour ce diagnostic, pour ne pas perturber le code de production
en cours de mesure. Un test instrumenté dédié
(`SherpaOnnxPiperUpmcLatencyTest`, `infrastructure/tts/src/androidTest/`)
construit `OfflineTts` directement avec une `OfflineTtsVitsModelConfig`
(`model`, `tokens`, `dataDir` — pas de `lexicon`, phonémisation espeak
pure, même convention que l'exemple officiel `NonStreamingTtsPiperEn.java`)
— **même classe `OfflineTts`, même `.so` vendoré** que Kokoro, seul le
modèle chargé change. Même réglage déjà confirmé optimal sur ce device
(`numThreads=4`, `provider=cpu`), même phrase de test, même device V2206,
même discipline de mesure (1 run froid + 5 répétitions, pas
`measureRepeated`).

### 3. RTF mesuré — confirmé, pas juste dans le bon ordre de grandeur

```
[RTF] premier appel (froid) = 1909.03 ms, audio_duration_ms=3599, RTF=0.530
[RTF] repetitions (ms) = 1191.89,1173.42,1191.75,1214.84,1288.37
[RTF] mediane_ms=1191.89 RTF_median=0.331
```

| Modèle | RTF (médiane, à chaud) | Audio produit (même phrase) | Sample rate |
|---|---|---|---|
| Kokoro (`SherpaOnnxTtsEngine` actuel) | **~4,7×** (meilleure config mesurée, `numThreads=4`/cpu) | ≈4,79-4,80 s | 24 000 Hz |
| **Piper VITS `fr_FR-upmc-medium`** | **~0,33×** (mesuré ici, pas cité) | ≈3,60 s (texte identique, voix plus rapide/courte) | 22 050 Hz |

**Le RTF ~0,33 de la doc legacy est confirmé, pas approximativement — au
centième près** (0,331 mesuré ici contre 0,33 cité) sur le pipeline
actuel, pas seulement sur l'ancienne implémentation. **Sous le budget
§11.2** (≤ 1 500 ms pour un tap-premier-audio) dans l'absolu : à ce RTF,
même une phrase de plusieurs secondes se synthétise en une fraction de
sa durée réelle — inverse complet du problème Kokoro (RTF > 1×, plus
lent que le temps réel).

### 4. Qualité vocale — échantillons produits, écoute non faite par ce diagnostic

Les deux voix (`sid=0` Jessica, `sid=1` Pierre — confirmé via
`speaker_id_map` du JSON, pas deviné) ont été synthétisées sur la même
phrase de test et exportées en WAV
(`SherpaOnnxPiperUpmcLatencyTest.exporte_echantillons_wav_pour_ecoute`) :

Pulls depuis le device (`SherpaOnnxPiperUpmcLatencyTest.exporte_echantillons_wav_pour_ecoute`,
répertoire externe de l'apk de test) vers le scratchpad de session —
chemin propre à cette session, non versionné, à régénérer si besoin pour
une écoute future :

```
piper_upmc_samples/jessica.wav
piper_upmc_samples/pierre.wav
```

**Non évalué par ce diagnostic** : contrairement au RTF ou à la
compatibilité CTC, la qualité perçue d'une voix ne peut pas être mesurée
par un test automatisé — elle nécessite une écoute humaine, avec le même
étalon déjà appliqué à Kokoro (barre 8/10). Les fichiers sont prêts ;
l'évaluation reste à faire par écoute avant de trancher l'ADR sur cette
base. Point de vigilance déjà documenté dans les métadonnées du modèle,
indépendant de la qualité perçue : VITS/Piper `medium` est une
architecture et une résolution de sortie antérieures à Kokoro (22 050 Hz
contre 24 000 Hz, pas de composante de style/prosodie équivalente à
StyleTTS2) — à confirmer ou infirmer par l'écoute, pas par supposition.

### 5. Licence — vérifiée précisément, pas supposée identique à Kokoro

Deux niveaux de licence distincts, **pas un seul comme pour Kokoro**
(Apache-2.0, propre) :

- **Dépôt `rhasspy/piper-voices` (Hugging Face)** : tag de licence
  `mit` au niveau du dépôt.
- **`MODEL_CARD` de la voix `fr_FR-upmc-medium` elle-même** (fichier
  vérifié identique en local et sur Hugging Face, contenu confirmé
  octet pour octet) :

  ```
  ## Dataset
  * URL: https://github.com/marytts/upmc-pierre-data
  * License: http://creativecommons.org/licenses/by-sa/4.0/
  ```

  Le **jeu de données d'entraînement** (`upmc-pierre-data`, MaryTTS) est
  sous **CC-BY-SA 4.0** — licence à clause de partage à l'identique
  (ShareAlike) et d'attribution, distincte du tag MIT du dépôt qui
  héberge le poids exporté. **Nuance réelle à trancher pour l'ADR, pas à
  ignorer** : contrairement à Kokoro (Apache-2.0 propre, sans clause
  ShareAlike sur les données amont), une attribution du dataset
  d'origine (et potentiellement une clause de partage à l'identique
  selon l'interprétation retenue pour un modèle dérivé d'un jeu de
  données CC-BY-SA) serait nécessaire si cette voix est distribuée en
  production — pas un bloqueur en soi, mais une obligation différente de
  ce qui a déjà été vérifié pour Kokoro.

### 6. Compatibilité avec le pipeline d'alignement CTC — vérifiée par un vrai run, pas supposée

`CtcForcedAligner.align()` appelé sur l'audio **réellement produit** par
Piper (22 050 Hz, pas 24 000 Hz comme Kokoro) — le resampling interne
(`AudioResampler`, déjà conçu pour ne jamais supposer un taux fixe côté
moteur appelant) prend le taux réel rapporté par `GeneratedAudio`,
vérifié ici avec une valeur différente de celle utilisée pour tous les
diagnostics précédents :

```
[CTC] align_ms=5176.34 nb_mots=10
[CTC][WORD] start=0    end=320  word=bonjour
[CTC][WORD] start=560  end=640  word=le
[CTC][WORD] start=640  end=960  word=monde
[CTC][WORD] start=1040 end=1360 word=ceci
[CTC][WORD] start=1520 end=1600 word=est
[CTC][WORD] start=1680 end=1760 word=un
[CTC][WORD] start=1760 end=2000 word=test
[CTC][WORD] start=2160 end=2240 word=pour
[CTC][WORD] start=2240 end=2800 word=vérifier
[CTC][WORD] start=2800 end=3440 word=l'alignement
```

**10 mots alignés sur 10 attendus, timestamps cohérents avec l'audio
produit (aucun mot manquant ni dupliqué)** — confirmé : le pipeline CTC
est bien indifférent au moteur TTS source, comme attendu (il ne consomme
que `audioSamples`/`sampleRate`), mais désormais **vérifié par un
run réel sur un second moteur**, pas seulement déduit de la conception.
`align_ms` ici (~5,18 s) est un appel à froid (session ONNX du modèle CTC
pas encore chargée dans ce test, contrairement aux mesures Kokoro qui
isolaient déjà froid/chaud) — pas comparable directement au ~540 ms déjà
mesuré à chaud pour le CTC seul (`PROTOTYPE_ALIGNEMENT_CTC.md` §7.4),
mais ce n'est pas ce que cette étape vérifie : elle confirme la
compatibilité fonctionnelle, pas une nouvelle mesure de latence CTC.

### Verdict de cette réévaluation

Le RTF ~0,33 n'est plus une affirmation de commentaire — **mesuré sur le
pipeline actuel, confirmé au centième près**, et sous le budget §11.2
dans l'absolu (contrairement à Kokoro). Compatible avec le pipeline CTC
déjà prouvé, sans modification. Deux réserves réelles à trancher avant
de l'adopter pour l'ADR, pas des détails : (1) qualité vocale non encore
évaluée par écoute (échantillons prêts, `jessica.wav`/`pierre.wav`),
architecture plus ancienne que Kokoro — la barre 8/10 pourrait ne pas
être atteinte ; (2) licence à deux niveaux, avec une obligation
d'attribution/ShareAlike sur le dataset d'entraînement à clarifier,
distincte du Apache-2.0 propre déjà vérifié pour Kokoro. Deux locuteurs
seulement (Jessica, Pierre) contre le catalogue plus large visé
initialement — à évaluer si suffisant pour une v1, pas un point technique.

---

## Recherche d'un candidat Piper à provenance de licence plus propre que `upmc-medium`

**2026-07-28.** Avant de s'engager sur `upmc-medium` ou de chercher un
avis juridique sur sa clause CC-BY-SA 4.0/ShareAlike (section
ci-dessus), cette section examine **toutes** les voix françaises du
catalogue `rhasspy/piper-voices` — pas seulement `upmc` — pour chercher
un candidat entraîné *from scratch* sur un dataset à la licence claire,
sans chaîne de provenance passant par une voix anglaise de base dont la
licence des données d'entraînement d'origine n'est pas aussi bien
documentée.

### 1. Inventaire complet — 7 voix françaises, une seule sans chaîne de provenance trouble

Catalogue interrogé directement (API Hugging Face, `tree/main/fr/fr_FR`
puis chaque sous-répertoire) — 7 voix au total, chaque `MODEL_CARD` lu
individuellement (pas seulement `upmc`) :

| Voix | Qualité | Locuteurs | Dataset | Licence dataset | Voix de base (finetune) | Provenance |
|---|---|---|---|---|---|---|
| `gilles` | low (16 kHz) | 1 | French Single Speaker Speech Dataset (Kaggle, bryanpark) | **CC0** | EN `ryan` (low) | Trouble — chaîne via voix EN |
| **`mls`** | **medium (22 050 Hz)** | **125** | **Multilingual LibriSpeech, OpenSLR-94** | **CC-BY 4.0** | **Aucune — entraîné from scratch** | **Propre — aucune chaîne** |
| `mls_1840` | low (16 kHz) | 1 | Multilingual LibriSpeech, OpenSLR-94 (même dataset que `mls`, locuteur unique "1840" extrait) | CC-BY 4.0 | EN `ryan` (low) | Trouble — chaîne via voix EN, **alors que la variante `medium` du même dataset est from scratch** (incohérence de pipeline d'entraînement entre les deux variantes, notable en soi) |
| `siwis` | low (16 kHz) | 1 | SIWIS (University of Edinburgh, datashare) | CC-BY 4.0 | EN `ryan` (low) | Trouble — chaîne via voix EN |
| `siwis` | medium (22 050 Hz) | 1 | SIWIS (University of Edinburgh, datashare) | CC-BY 4.0 | EN `lessac` (medium) | Trouble — même chaîne que `upmc` (voix `fr_FR-siwis-medium`, déjà mentionnée comme remplacée par Kokoro dans le KDoc de `SherpaOnnxModelPaths.kt`, Tâche 5.1.1) |
| `tom` | medium (44 100 Hz) | 1 | dépôt `git.bksp.space/Tjiho/French-tts-model-piper` | **AGPLv3** | Non précisé (« See URL ») | Trouble différemment — licence copyleft virale, pas une provenance floue mais une obligation de publication du code dérivé peu compatible avec une app propriétaire |
| `upmc` | medium (22 050 Hz) | 2 | upmc-pierre-data (MaryTTS, GitHub) | CC-BY-SA 4.0 | EN `lessac` (medium) | Trouble — déjà documenté ci-dessus |

**Un seul candidat réellement propre : `fr_FR-mls-medium`** — c'est le
seul des 7 dont le `MODEL_CARD` indique « Trained from scratch », sans
aucune ligne « Finetuned from … ». Tous les autres, y compris ceux dont
le *dataset* est en licence permissive (CC0 pour `gilles`, CC-BY 4.0
pour `siwis`/`mls_1840`), héritent d'une chaîne de provenance via une
voix anglaise de base (`ryan` ou `lessac`) dont la licence des données
d'entraînement d'origine n'est pas documentée avec la même précision
dans ce catalogue — même défaut structurel que celui identifié pour
`upmc`, pas un cas isolé. `tom` est un cas à part : licence très
précisément documentée (AGPLv3), mais une obligation de copyleft fort,
pas une provenance floue.

### 2. RTF mesuré pour le candidat propre — même protocole exact

Le `.onnx` brut téléchargé depuis Hugging Face **n'est pas directement
utilisable** par `sherpa-onnx` : il lui manque les métadonnées ONNX
(`model_type`/`comment`/`n_speakers`/`sample_rate`) que le script
officiel `scripts/piper/add_meta_data.py` du dépôt `sherpa-onnx` injecte
avant publication des modèles déjà "vendorés" — confirmé en comparant
aux métadonnées réellement présentes dans le `.onnx` d'`upmc-medium` déjà
utilisé en production (`model_type=vits`, `comment=piper`, etc., lues
directement via `onnx.load()`, pas supposées). Régénérées ici hors
device (mêmes clés, mêmes valeurs que le script officiel) avant de
pousser le modèle sur le device — tokens.txt dérivé du
`phoneme_id_map` du `.onnx.json` (159 entrées), même procédure que le
script officiel.

Test dédié (`SherpaOnnxPiperMlsLatencyTest`,
`infrastructure/tts/src/androidTest/`), même `OfflineTts`/`.so` vendoré,
même device V2206, même phrase de test, `numThreads=4`/`provider=cpu`,
1 run froid + 5 répétitions :

```
[RTF] premier appel (froid) = 3062.28 ms, audio_duration_ms=6648, RTF=0.461
[RTF] repetitions (ms) = 2491.96,2799.88,3567.59,2330.00,1840.72
[RTF] mediane_ms=2491.96 RTF_median=0.375
```

| Voix | RTF médian (chaud) | Audio produit (même phrase) | Sample rate | Licence |
|---|---|---|---|---|
| Kokoro (`SherpaOnnxTtsEngine` actuel) | ~4,7× | ≈4,79-4,80 s | 24 000 Hz | Apache-2.0, propre |
| Piper `upmc-medium` | 0,331× | ≈3,60 s | 22 050 Hz | CC-BY-SA 4.0 (dataset) + chaîne EN `lessac` trouble |
| **Piper `mls-medium`** | **0,375×** | **≈6,65 s** (voix plus lente/débit différent, même texte) | 22 050 Hz | **CC-BY 4.0, from scratch, provenance propre** |

**`mls-medium` est performant ET propre.** RTF 0,375× — même ordre de
grandeur que `upmc-medium` (0,331×), très largement sous le budget
§11.2, sans la réserve de licence à deux niveaux. La phrase de test dure
≈6,65 s de synthèse (contre ≈3,60 s pour `upmc` et ≈4,79 s pour Kokoro) —
débit propre à cette voix particulière (parmi les 125 locuteurs
disponibles, le locuteur `sid=0` correspond à l'identifiant MLS anonymisé
« 1840 », lu dans `speaker_id_map`, pas deviné — c'est d'ailleurs le même
locuteur que la voix séparée `fr_FR-mls_1840-low` du catalogue), sans
lien avec le RTF (le RTF rapporte le coût de calcul à la seconde d'audio
produite, indépendamment de la longueur de cet audio).

### 3. Qualité vocale — échantillon produit, écoute non faite par ce diagnostic

`SherpaOnnxPiperMlsLatencyTest` exporte un échantillon
(`mls_sid0.wav`, locuteur `sid=0`/« 1840 ») sur la même phrase de test,
même limite que pour `upmc` : l'évaluation perceptuelle (barre 8/10 déjà
appliquée à Kokoro) reste à faire par écoute humaine, pas par ce test.
Réserve supplémentaire propre à ce candidat, à vérifier par l'écoute :
125 locuteurs anonymisés issus d'un corpus de livres audio (Multilingual
LibriSpeech) n'offrent pas la sélection curatée d'une voix dédiée comme
`upmc` (Jessica/Pierre, noms et genres identifiés) — la qualité peut
varier fortement d'un des 125 identifiants à l'autre, un seul échantillon
(`sid=0`) ne suffit pas à caractériser l'ensemble du catalogue de voix
disponibles dans ce modèle.

### Verdict de cette recherche

**Un candidat à la fois performant et à la licence propre existe :
`fr_FR-mls-medium`.** RTF mesuré 0,375× (comparable à `upmc-medium`,
largement sous le budget), dataset CC-BY 4.0 pur, entraîné from scratch
— sans la réserve de provenance trouble qui s'applique à `upmc` et à 5
des 6 autres voix françaises du catalogue. Ce n'est **pas** une
résolution automatique par défaut vers ce candidat : la qualité vocale
n'est pas encore évaluée par écoute (échantillon `mls_sid0.wav` prêt),
et la nature du corpus (125 locuteurs anonymisés de livres audio,
pas une voix dédiée) est une différence qualitative réelle par rapport à
`upmc` à peser dans la décision d'ADR, pas seulement un chiffre de RTF.
Les compromis précis pour la décision :

| Option | Licence | RTF | Réserve principale |
|---|---|---|---|
| `upmc-medium` | Trouble (CC-BY-SA 4.0 + chaîne EN `lessac`) | 0,331× | Attribution/ShareAlike à clarifier ; seulement 2 locuteurs mais nommés/identifiés |
| `mls-medium` | **Propre (CC-BY 4.0, from scratch)** | 0,375× | Qualité non évaluée ; 125 locuteurs anonymisés, pas de sélection curatée |
| Kokoro (actuel) | Propre (Apache-2.0) | ~4,7× | RTF ~14× au-dessus du budget §11.2 — déjà disqualifié par les diagnostics précédents |

---

## XNNPACK recompilé depuis les sources — dernier levier non testé, mesuré et écarté

**2026-07-28.** Dernier levier de configuration/matériel identifié mais
jamais réellement testé : XNNPACK. À la différence de NNAPI (déjà
recompilé et mesuré, section dédiée ci-dessus — disponible dans le
binaire mais désactivé par un guard de compilation `__ANDROID_API__`),
XNNPACK n'a **jamais** été activé pour de vrai jusqu'ici : le `.so`
vendoré ne l'a simplement pas compilé (`Ort::GetAvailableProviders()`
listait `NnapiExecutionProvider, CPUExecutionProvider` — pas de
`XnnpackExecutionProvider`). Un test borné, une seule mesure — pas une
nouvelle série de diagnostic.

### Correction avant de commencer : ce n'est pas un flag sherpa-onnx

La consigne de départ supposait un flag de build sherpa-onnx dédié,
comme `SHERPA_ONNX_ANDROID_PLATFORM` pour NNAPI. Vérifié faux avant de
lancer quoi que ce soit (`sherpa-onnx/csrc/session.cc:243-254`,
`sherpa-onnx/csrc/provider.cc:24-25`, tous les fichiers
`cmake/onnxruntime*.cmake`) : sherpa-onnx demande simplement le provider
`"XNNPACK"` à ONNX Runtime au runtime — s'il n'est pas dans
`Ort::GetAvailableProviders()`, repli silencieux sur CPU, sans aucun
flag de compilation sherpa-onnx pour en décider. XNNPACK doit être
compilé **dans ONNX Runtime lui-même** (`--use_xnnpack`), pas dans
`libsherpa-onnx-jni.so`. Aucun prebuilt XNNPACK n'existe non plus côté
`csukuangfj/onnxruntime-libs` (release v1.27.0 inspectée via l'API
GitHub : `onnxruntime-android-1.27.0.zip` et 3 variantes statiques,
aucune dédiée XNNPACK). Chantier plus lourd que la recompilation NNAPI
(qui ne touchait que le JNI, ~10 min) : reconstruction complète d'ONNX
Runtime depuis les sources.

### Build — chiffré, un seul essai, avec l'incident réel qui l'a interrompu

ONNX Runtime v1.27.0 (même version que le binaire déjà vendoré, pour
rester comparable) cloné avec ses sous-modules, cross-compilé pour
`arm64-v8a`/API 21 avec `tools/ci_build/build.py --use_xnnpack`
(NDK 27.2.12479018, CMake 4.4.0 — le CMake du NDK, 3.22.1, s'est révélé
trop ancien pour ONNX Runtime v1.27.0, qui exige ≥ 3.28 ; corrigé avec
un CMake récent installé via `pip`). Le build a rempli le disque de la
machine host (« Disk quota exceeded » sur le dernier binaire de test
avant la fin — `--skip_tests` évite d'*exécuter* les tests mais pas de
les *compiler*) : incident réel, pas masqué. **`libonnxruntime.so`
lui-même avait déjà fini de compiler avant l'incident** — seuls des
binaires de test superflus restaient en cours ; espace disque libéré,
build non relancé (déjà exploitable en l'état).

`libsherpa-onnx-jni.so` ensuite relié contre ce nouveau
`libonnxruntime.so` (mêmes headers publics C que le binaire officiel
vendoré, `ANDROID_PLATFORM=android-21`, script officiel
`build-android-arm64-v8a.sh` avec `SHERPA_ONNXRUNTIME_LIB_DIR`/
`SHERPA_ONNXRUNTIME_INCLUDE_DIR` pointés vers le build XNNPACK) —
**temps réel mesuré (`time`) : 7 min 55 s**, comparable au temps du
diagnostic NNAPI. Présence du symbole `XnnpackExecutionProvider`
confirmée dans le nouveau `libonnxruntime.so` (`strings` + grep, pas
supposée).

### RTF mesuré — XNNPACK réellement actif, pas un repli silencieux

`.so` de production remplacés **temporairement** (les deux :
`libonnxruntime.so` et `libsherpa-onnx-jni.so`, puisque XNNPACK vit dans
le premier) par les binaires recompilés, `provider="xnnpack"`,
`debug=true`, même protocole exact que tous les diagnostics précédents
(même phrase, device V2206, `numThreads=4`) :

```
[RTF] premier appel (froid) = 25918.67 ms, audio_duration_ms=4793, RTF=5.408
[RTF] repetitions (ms) = 25142.84,25369.64,24999.80,24835.47,25054.80
[RTF] mediane_ms=25054.80 RTF_median=5.227
```

**Aucun message « Fallback to cpu! »** dans les logs `sherpa-onnx` (ce
message n'apparaît, par construction du code, que si
`XnnpackExecutionProvider` est absent de la liste des providers
disponibles — confirmé absent ici, donc XNNPACK bien accepté par
`AppendExecutionProvider`, pas un repli déguisé) : XNNPACK s'active
réellement, comme NNAPI avant lui.

`.so` de production restaurés immédiatement après la mesure — **sha256
revérifié identique aux fichiers d'origine avant remplacement** (pas
seulement après une supposition de restauration correcte) :
`libsherpa-onnx-jni.so` et `libonnxruntime.so` tous deux confirmés
bit-à-bit identiques à l'état pré-diagnostic. `git status` sur
`infrastructure/tts/src/main/jniLibs/` propre après restauration.

### Verdict — XNNPACK réellement actif, mesuré plus lent que CPU

**RTF 5,227× (médian, à chaud) — plus lent que CPU+4threads (~4,7×),
proche du RTF NNAPI (~5,4×), pas sous 0,8×.** Chiffre rapporté tel quel,
sans nouvelle exploration : la consigne de cette tâche était un test
borné, pas une nouvelle série de diagnostic si XNNPACK ne suffit pas
seul. Cohérent avec l'hypothèse déjà posée pour NNAPI (section
dédiée, §3) — les opérateurs de quantification dynamique
(`DynamicQuantizeLinear`/`MatMulInteger`/`ConvInteger`) et la composante
LSTM (`DynamicQuantizeLSTM`) du graphe Kokoro ne bénéficient
probablement pas des noyaux optimisés XNNPACK (conçus avant tout pour
la convolution/matmul denses en float ou quantifiés statiquement), avec
en plus une surcharge de partitionnement de graphe entre CPU et XNNPACK
pour les opérateurs non supportés. Hypothèse plausible, pas vérifiée
plus avant (hors périmètre de ce test borné).

Avec XNNPACK, **les six leviers de configuration/matériel identifiables
sur ce device sont maintenant épuisés et chiffrés** (threads, NNAPI
compilé et testé, int8 authentique, G2P négligeable, streaming par
segment mesuré, XNNPACK compilé et testé) — aucun ne fait passer le RTF
sous ~4,7×, la meilleure configuration mesurée reste CPU+4 threads.

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
| 4 | Silence inter-phrases ≤ 150 ms | ❌ **Échec mesuré, diagnostiqué avant conclusion architecturale (6 vérifications, y compris NNAPI et XNNPACK recompilés et réellement testés)** — meilleure config mesurée : ~25 s (RTF ~4,7×, CPU+4 threads) ; NNAPI réellement actif mesuré *plus lent* (~5,4×) ; XNNPACK réellement actif mesuré *plus lent* (~5,2×) ; toujours très loin de 150 ms ; tous les leviers de configuration épuisés, **ADR-022 accepté — Kokoro retenu malgré le budget non tenu, alternatives Piper/VITS écartées (licence ou qualité)** | §10.1-10.2, sections « Diagnostic de la latence Kokoro », « NNAPI recompilé », « XNNPACK recompilé », ADR-022 |
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
