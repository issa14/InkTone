package com.inktone.data.di

import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Fournit les Use Cases du domaine a constructeur simple (Tache 1.8 :
 * pas de @Inject dans domain/, qui reste pur Kotlin sans annotation de
 * framework de DI). Ajoutes au fil des phases qui les consomment
 * reellement (Tache 3.5 : UpdateReadingStateUseCase ; Tache 3.7 :
 * GetReadingStateUseCase pour verifier la reprise K3 ; Tache 5.5 :
 * GetVoiceProfilesUseCase pour le selecteur de voix de PlayerScreen),
 * pas par anticipation.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideUpdateReadingStateUseCase(
        readingStateRepository: ReadingStateRepository,
    ): UpdateReadingStateUseCase = UpdateReadingStateUseCase(readingStateRepository)

    @Provides
    fun provideGetReadingStateUseCase(
        readingStateRepository: ReadingStateRepository,
    ): GetReadingStateUseCase = GetReadingStateUseCase(readingStateRepository)

    @Provides
    fun provideGetVoiceProfilesUseCase(
        voiceProfileRepository: VoiceProfileRepository,
    ): GetVoiceProfilesUseCase = GetVoiceProfilesUseCase(voiceProfileRepository)
}
