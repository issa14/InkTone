package com.inktone.infrastructure.tts.di

import com.inktone.domain.service.TtsEngine
import com.inktone.infrastructure.tts.AndroidNativeTtsEngine
import com.inktone.infrastructure.tts.FallbackTtsEngine
import com.inktone.infrastructure.tts.SherpaOnnxTtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine TtsEngine (Tache 1.7) a FallbackTtsEngine
 * (Tache 5.8), qui essaie le Palier 2 (Sherpa-ONNX) et bascule
 * automatiquement vers le Palier 1 (Android natif) en cas d'echec -
 * decision de selection actee par ADR-021 ("detection au runtime"),
 * plus le binding direct au seul Palier 1 de la Phase 3. Les deux
 * moteurs concrets sont qualifies (@Palier1/@Palier2) pour que
 * FallbackTtsEngine puisse les distinguer dans le graphe Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: FallbackTtsEngine): TtsEngine

    @Binds
    @Palier2
    abstract fun bindPalier2(impl: SherpaOnnxTtsEngine): TtsEngine

    @Binds
    @Palier1
    abstract fun bindPalier1(impl: AndroidNativeTtsEngine): TtsEngine
}
