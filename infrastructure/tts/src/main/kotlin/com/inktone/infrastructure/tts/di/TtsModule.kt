package com.inktone.infrastructure.tts.di

import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.service.PronunciationRuleApplier
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.VoiceModelDownloadService
import com.inktone.infrastructure.tts.AndroidNativeTtsEngine
import com.inktone.infrastructure.tts.EdgeTtsEngine
import com.inktone.infrastructure.tts.FallbackTtsEngine
import com.inktone.infrastructure.tts.SelectiveTtsEngine
import com.inktone.infrastructure.tts.SherpaOnnxTtsEngine
import com.inktone.infrastructure.tts.SherpaOnnxVoiceModelDownloadService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine TtsEngine (Tache 1.7) a SelectiveTtsEngine
 * (Lot 14, ADR-024) : facade qui route selon `VoiceProfile.engine` entre
 * l'adaptateur Edge TTS (cloud, optionnel) et la chaine offline
 * FallbackTtsEngine (Palier 2 Sherpa-ONNX -> Palier 1 Android natif,
 * ADR-021). Les trois moteurs concrets sont qualifies pour etre
 * distinguables dans le graphe Hilt : @EdgeTtsEngine/@OfflineTtsEngine
 * pour la facade, @Palier1/@Palier2 pour FallbackTtsEngine.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SelectiveTtsEngine): TtsEngine

    @Binds
    @EdgeEngine
    abstract fun bindEdgeTtsEngine(impl: EdgeTtsEngine): TtsEngine

    @Binds
    @OfflineTtsEngine
    abstract fun bindOfflineTtsEngine(impl: FallbackTtsEngine): TtsEngine

    @Binds
    @Palier2
    abstract fun bindPalier2(impl: SherpaOnnxTtsEngine): TtsEngine

    @Binds
    @Palier1
    abstract fun bindPalier1(impl: AndroidNativeTtsEngine): TtsEngine

    @Binds
    @Singleton
    abstract fun bindVoiceModelDownloadService(impl: SherpaOnnxVoiceModelDownloadService): VoiceModelDownloadService

    companion object {
        @Provides
        @Singleton
        fun providePronunciationRuleApplier(
            ruleRepository: PronunciationRuleRepository,
        ): PronunciationRuleApplier = PronunciationRuleApplier(ruleRepository)
    }
}
