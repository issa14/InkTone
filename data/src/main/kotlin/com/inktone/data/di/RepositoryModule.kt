package com.inktone.data.di

import com.inktone.data.repository.RoomAnnotationRepository
import com.inktone.data.repository.RoomBookmarkRepository
import com.inktone.data.repository.RoomLibraryItemRepository
import com.inktone.data.repository.RoomPreferencesRepository
import com.inktone.data.repository.RoomPronunciationRuleRepository
import com.inktone.data.repository.RoomPublicationRepository
import com.inktone.data.repository.RoomReadingSessionRepository
import com.inktone.data.repository.RoomReadingStateRepository
import com.inktone.data.repository.RoomVoiceProfileRepository
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.LibraryItemRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.VoiceProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPublicationRepository(impl: RoomPublicationRepository): PublicationRepository
    @Binds @Singleton abstract fun bindReadingStateRepository(impl: RoomReadingStateRepository): ReadingStateRepository
    @Binds @Singleton abstract fun bindReadingSessionRepository(impl: RoomReadingSessionRepository): ReadingSessionRepository
    @Binds @Singleton abstract fun bindBookmarkRepository(impl: RoomBookmarkRepository): BookmarkRepository
    @Binds @Singleton abstract fun bindAnnotationRepository(impl: RoomAnnotationRepository): AnnotationRepository
    @Binds @Singleton abstract fun bindVoiceProfileRepository(impl: RoomVoiceProfileRepository): VoiceProfileRepository
    @Binds @Singleton abstract fun bindPreferencesRepository(impl: RoomPreferencesRepository): PreferencesRepository
    @Binds @Singleton abstract fun bindPronunciationRuleRepository(impl: RoomPronunciationRuleRepository): PronunciationRuleRepository
    @Binds @Singleton abstract fun bindLibraryItemRepository(impl: RoomLibraryItemRepository): LibraryItemRepository
}
