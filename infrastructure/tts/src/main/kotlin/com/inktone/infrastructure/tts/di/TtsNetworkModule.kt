package com.inktone.infrastructure.tts.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Fournit l'`OkHttpClient` dédié à Edge TTS (Lot 14). Séparé de
 * `SyncNetworkModule` (Lot 11) et `OpdsNetworkModule` (Lot 13) : timeouts
 * propres à la synthèse (connexion 10 s, lecture 15 s — une synthèse
 * phrase par phrase est courte), et qualifié par `@EdgeTts` pour éviter
 * un conflit de binding Hilt sur `OkHttpClient` non qualifié.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsNetworkModule {
    companion object {
        @Provides
        @Singleton
        @EdgeTts
        fun provideEdgeTtsOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
