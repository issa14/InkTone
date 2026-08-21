package com.inktone.feature.reader.di

import com.inktone.domain.service.PlaybackSession
import com.inktone.feature.reader.PlaybackOrchestrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Expose [PlaybackOrchestrator] (implémentation de la session TTS, P1) sous
 * son contrat domaine [PlaybackSession], consommé par la notification média
 * (`infrastructure/media`) sans que ce module ne dépende de `feature/reader` :
 * la liaison Hilt traverse la frontière à l'assemblage (`:app`), jamais au
 * niveau des dépendances de compilation (sens des dépendances, Blueprint §12.4).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackSessionModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackSession(impl: PlaybackOrchestrator): PlaybackSession
}
