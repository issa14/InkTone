package com.inktone.infrastructure.media.di

import com.inktone.domain.service.AudioPlayer
import com.inktone.infrastructure.media.GaplessAudioPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine [AudioPlayer] (Lot 15, ADR-025) au lecteur
 * gapless `AudioTrack` `MODE_STREAM` ([GaplessAudioPlayer]). La couche
 * présentation ne consomme que le contrat — jamais l'implémentation
 * (sens des dépendances Blueprint §12.4).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindAudioPlayer(impl: GaplessAudioPlayer): AudioPlayer
}
