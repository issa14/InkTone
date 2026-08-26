package com.inktone.data.di

import android.content.Context
import com.inktone.data.preanalysis.FilePreAnalysisStore
import com.inktone.data.repository.DeviceIdentityRepositoryImpl
import com.inktone.data.repository.InMemoryOpdsDownloadObserver
import com.inktone.data.repository.InMemorySyncOperationTracker
import com.inktone.data.repository.RoomAnnotationRepository
import com.inktone.data.repository.RoomBookmarkRepository
import com.inktone.data.repository.RoomLibraryItemRepository
import com.inktone.data.repository.RoomOpdsCatalogRepository
import com.inktone.data.repository.RoomPreferencesRepository
import com.inktone.data.repository.RoomPronunciationRuleRepository
import com.inktone.data.repository.RemoteDeviceFleetRepository
import com.inktone.data.repository.RemoteSyncActivityLogRepository
import com.inktone.data.repository.RoomConflictQueueRepository
import com.inktone.data.repository.RoomPublicationRepository
import com.inktone.data.repository.RoomReadingSessionRepository
import com.inktone.data.repository.RoomReadingStateRepository
import com.inktone.data.repository.RoomSyncAccountRepository
import com.inktone.data.repository.RoomThemeRepository
import com.inktone.data.repository.RoomVoiceProfileRepository
import com.inktone.data.sync.SyncNowManager
import com.inktone.data.pdfcache.RenderedPageCacheImpl
import com.inktone.data.ttscache.TtsSegmentCacheImpl
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.LibraryItemRepository
import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.service.OpdsDownloadObserver
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.service.RenderedPageCache
import com.inktone.domain.service.SyncNowService
import com.inktone.domain.service.SyncOperationTracker
import com.inktone.domain.service.TtsSegmentCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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
    @Binds @Singleton abstract fun bindThemeRepository(impl: RoomThemeRepository): ThemeRepository
    @Binds @Singleton abstract fun bindSyncAccountRepository(impl: RoomSyncAccountRepository): SyncAccountRepository
    @Binds @Singleton abstract fun bindDeviceIdentityRepository(impl: DeviceIdentityRepositoryImpl): DeviceIdentityRepository
    @Binds @Singleton abstract fun bindSyncOperationTracker(impl: InMemorySyncOperationTracker): SyncOperationTracker
    @Binds @Singleton abstract fun bindSyncFleetRepository(impl: RemoteDeviceFleetRepository): SyncFleetRepository
    @Binds @Singleton abstract fun bindSyncActivityLogRepository(impl: RemoteSyncActivityLogRepository): SyncActivityLogRepository
    @Binds @Singleton abstract fun bindSyncNowService(impl: SyncNowManager): SyncNowService
    @Binds @Singleton abstract fun bindConflictQueueRepository(impl: RoomConflictQueueRepository): ConflictQueueRepository
    @Binds @Singleton abstract fun bindOpdsDownloadObserver(impl: InMemoryOpdsDownloadObserver): OpdsDownloadObserver
    @Binds @Singleton abstract fun bindOpdsCatalogRepository(impl: RoomOpdsCatalogRepository): OpdsCatalogRepository
    @Binds @Singleton abstract fun bindPreAnalysisStore(impl: FilePreAnalysisStore): PreAnalysisStore
    @Binds @Singleton abstract fun bindTtsSegmentCache(impl: TtsSegmentCacheImpl): TtsSegmentCache
    @Binds @Singleton abstract fun bindRenderedPageCache(impl: RenderedPageCacheImpl): RenderedPageCache

    companion object {
        @Provides
        @Singleton
        fun provideCacheDir(@ApplicationContext context: Context): File = context.cacheDir
    }
}
