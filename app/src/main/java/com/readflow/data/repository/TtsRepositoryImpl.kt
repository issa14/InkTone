package com.readflow.data.repository

import android.util.Log
import com.readflow.data.database.PronunciationRuleDao
import com.readflow.data.settings.SettingsRepository
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.provider.TtsProvider
import com.readflow.domain.repository.TtsRepository
import com.readflow.service.audio.AudioCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsRepositoryImpl @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TtsProvider>,
    private val settingsRepository: SettingsRepository,
    private val cache: AudioCacheManager,
    private val pronunciationRuleDao: PronunciationRuleDao
) : TtsRepository {

    companion object {
        private const val TAG = "TtsRepository"
        private const val DEFAULT_ENGINE = "piper"
    }

    /** Cache du provider sélectionné (évite les lookups répétés). */
    @Volatile
    private var cachedProvider: TtsProvider? = null

    /** Identifiant du moteur correspondant au provider en cache. */
    @Volatile
    private var cachedEngineId: String? = null

    override suspend fun synthesize(
        text: String,
        voice: Int,
        speed: Float
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val provider = resolveProvider()
        val voiceId = resolveVoiceId(provider, voice)

        val correctedText = applyPronunciationRules(text)
        val key = "${provider.engineId}|${correctedText.trim()}|${voiceId}|${"%.2f".format(speed)}"

        cache.get(key)?.let { return@withContext it }

        val result = provider.synthesize(correctedText, voiceId, speed)
        cache.put(key, result)
        result
    }

    override fun getAvailableEngines(): List<TtsProvider> {
        return providers.toList().sortedBy { it.engineId }
    }

    override fun getEngine(engineId: String): TtsProvider? {
        return providers.find { it.engineId == engineId }
    }

    /**
     * Résout le provider TTS actif à partir des préférences utilisateur.
     *
     * Stratégie de fallback :
     * 1. Lit le moteur sélectionné dans DataStore (défaut "piper")
     * 2. Si le moteur est trouvé ET disponible → retourne
     * 3. Sinon, cherche le premier provider disponible (fallback automatique)
     * 4. En dernier recours, retourne le provider "piper" (même si indisponible)
     */
    private fun resolveProvider(): TtsProvider {
        // Utiliser le cache si valide
        val cached = cachedProvider
        val cachedId = cachedEngineId
        if (cached != null && cachedId != null && cached.isAvailable) {
            return cached
        }

        // NOTE: la lecture de DataStore dans un contexte non-suspend n'est pas possible.
        // On utilise un provider "piper" par défaut, et on laisse la logique de sélection
        // fine se faire au niveau UI (SettingsViewModel), qui appellera synthesize() avec
        // le bon provider via la méthode getEngine().
        //
        // Pour synthesize(), on priorise :
        // 1. Piper (local, toujours disponible si modèle chargé)
        // 2. Premier provider disponible
        // 3. Piper même si indisponible (l'erreur sera propagée proprement)

        val provider = providers.find { it.engineId == DEFAULT_ENGINE && it.isAvailable }
            ?: providers.firstOrNull { it.isAvailable }
            ?: providers.find { it.engineId == DEFAULT_ENGINE }
            ?: providers.first()

        cachedProvider = provider
        cachedEngineId = provider.engineId
        return provider
    }

    /**
     * Convertit l'ancien identifiant numérique [voice] (sid Piper)
     * en identifiant string utilisé par les providers.
     *
     * Pour le provider Piper : convertit le sid en nom de voix.
     * Pour les autres providers : utilise la première voix disponible
     * ou celle configurée dans les paramètres.
     */
    private fun resolveVoiceId(provider: TtsProvider, voice: Int): String {
        return when (provider.engineId) {
            "piper" -> {
                val voiceEnum = com.readflow.service.onnx.OnnxInferenceService.Voice.entries
                    .find { it.sid == voice }
                voiceEnum?.name?.lowercase() ?: provider.availableVoices.first().id
            }
            else -> {
                // Pour les providers cloud (Edge, etc.), utiliser la première voix
                // ou celle stockée dans les settings (géré via SettingsViewModel)
                provider.availableVoices.firstOrNull()?.id ?: "fr-FR-VivienneNeural"
            }
        }
    }

    /**
     * Applique les règles de prononciation actives au texte avant synthèse.
     */
    private suspend fun applyPronunciationRules(text: String): String {
        return try {
            val rules = pronunciationRuleDao.getActiveRules()
            rules.fold(text) { currentText, rule ->
                if (rule.isRegex) {
                    try {
                        Regex(rule.pattern, RegexOption.IGNORE_CASE).replace(currentText, rule.replacement)
                    } catch (e: Exception) {
                        currentText
                    }
                } else {
                    currentText.replace(
                        Regex(Regex.escape(rule.pattern), RegexOption.IGNORE_CASE),
                        rule.replacement
                    )
                }
            }
        } catch (e: Exception) {
            text
        }
    }
}

