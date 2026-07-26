package com.inktone.infrastructure.tts.di

import com.inktone.domain.service.TtsEngine
import com.inktone.infrastructure.tts.AndroidNativeTtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine TtsEngine (Tache 1.7) a l'adaptateur Palier 1
 * (ADR-021). Un seul moteur actif pour l'instant : la selection entre
 * Palier 1 et Palier 2 (multi-binding qualifie) est une decision de la
 * Phase 5, pas de cette tache.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: AndroidNativeTtsEngine): TtsEngine
}
