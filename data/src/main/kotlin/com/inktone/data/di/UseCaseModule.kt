package com.inktone.data.di

import android.content.Context
import com.inktone.data.export.ExportStatisticsUseCase
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncNowService
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ImportSessionStore
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.SearchService
import com.inktone.domain.service.SyncOperationTracker
import com.inktone.domain.service.OpdsCredentialsStore
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.domain.service.StatisticsExportService
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.ApplyAccessibilityPresetUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteAnnotationUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.DeleteCustomThemeUseCase
import com.inktone.domain.usecase.DeleteLibraryItemUseCase
import com.inktone.domain.usecase.ExportLibraryUseCase
import com.inktone.domain.usecase.GetCurrentBookUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetStatisticsUseCase
import com.inktone.domain.usecase.GetCatalogsUseCase
import com.inktone.domain.usecase.AddCatalogUseCase
import com.inktone.domain.usecase.BrowseOpdsFeedUseCase
import com.inktone.domain.usecase.RemoveCatalogUseCase
import com.inktone.domain.usecase.SearchOpdsFeedUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ObserveSyncUiStateUseCase
import com.inktone.domain.usecase.ResolvePositionConflictUseCase
import com.inktone.domain.usecase.SynchronizeNowUseCase
import com.inktone.domain.usecase.SearchPublicationUseCase
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.ToggleLibraryItemPinUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Fournit les Use Cases du domaine a constructeur simple (Tache 1.8 :
 * pas de @Inject dans domain/, qui reste pur Kotlin sans annotation de
 * framework de DI). Ajoutes au fil des phases qui les consomment
 * reellement (Tache 3.5 : UpdateReadingStateUseCase ; Tache 3.7 :
 * GetReadingStateUseCase pour verifier la reprise K3 ; Tache 5.5 :
 * GetVoiceProfilesUseCase pour le selecteur de voix de PlayerScreen ;
 * Tache 6.2 : ImportPublicationUseCase, consomme par ImportWorker ;
 * Tache 6.7 : ExportLibraryUseCase ; Tache 6.6 : ToggleFavoriteUseCase,
 * consomme par LibraryViewModel ; Tache 7.1 : AddAnnotationUseCase ;
 * Tache 7.2 : CreateBookmarkUseCase/DeleteBookmarkUseCase, consommes par
 * ReaderViewModel),
 * pas par anticipation.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideImportSessionStore(): ImportSessionStore = ImportSessionStore()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

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

    @Provides
    fun provideObserveSyncUiStateUseCase(
        syncAccountRepository: SyncAccountRepository,
        syncOperationTracker: SyncOperationTracker,
    ): ObserveSyncUiStateUseCase = ObserveSyncUiStateUseCase(syncAccountRepository, syncOperationTracker)

    @Provides
    fun provideSynchronizeNowUseCase(
        syncNowService: SyncNowService,
    ): SynchronizeNowUseCase = SynchronizeNowUseCase(syncNowService)

    @Provides
    fun provideResolvePositionConflictUseCase(
        readingStateRepository: ReadingStateRepository,
        conflictQueueRepository: ConflictQueueRepository,
    ): ResolvePositionConflictUseCase = ResolvePositionConflictUseCase(readingStateRepository, conflictQueueRepository)

    @Provides
    fun provideImportPublicationUseCase(
        publicationParser: PublicationParser,
        publicationRepository: PublicationRepository,
        fileStorageService: FileStorageService,
        searchService: SearchService,
        chapterParser: ChapterParser,
    ): ImportPublicationUseCase =
        ImportPublicationUseCase(publicationParser, publicationRepository, fileStorageService, searchService, chapterParser)

    @Provides
    fun provideExportLibraryUseCase(
        publicationRepository: PublicationRepository,
        fileStorageService: FileStorageService,
    ): ExportLibraryUseCase = ExportLibraryUseCase(publicationRepository, fileStorageService)

    @Provides
    fun provideToggleFavoriteUseCase(
        publicationRepository: PublicationRepository,
    ): ToggleFavoriteUseCase = ToggleFavoriteUseCase(publicationRepository)

    @Provides
    fun provideTogglePinUseCase(
        publicationRepository: PublicationRepository,
    ): TogglePinUseCase = TogglePinUseCase(publicationRepository)

    @Provides
    fun provideDeletePublicationUseCase(
        publicationRepository: PublicationRepository,
    ): DeletePublicationUseCase = DeletePublicationUseCase(publicationRepository)

    @Provides
    fun provideAddAnnotationUseCase(
        annotationRepository: AnnotationRepository,
    ): AddAnnotationUseCase = AddAnnotationUseCase(annotationRepository)

    @Provides
    fun provideCreateBookmarkUseCase(
        bookmarkRepository: BookmarkRepository,
    ): CreateBookmarkUseCase = CreateBookmarkUseCase(bookmarkRepository)

    @Provides
    fun provideDeleteBookmarkUseCase(
        bookmarkRepository: BookmarkRepository,
    ): DeleteBookmarkUseCase = DeleteBookmarkUseCase(bookmarkRepository)

    @Provides
    fun provideDeleteAnnotationUseCase(
        annotationRepository: AnnotationRepository,
    ): DeleteAnnotationUseCase = DeleteAnnotationUseCase(annotationRepository)

    @Provides
    fun provideToggleLibraryItemPinUseCase(
        annotationRepository: AnnotationRepository,
        bookmarkRepository: BookmarkRepository,
    ): ToggleLibraryItemPinUseCase = ToggleLibraryItemPinUseCase(annotationRepository, bookmarkRepository)

    @Provides
    fun provideDeleteLibraryItemUseCase(
        annotationRepository: AnnotationRepository,
        bookmarkRepository: BookmarkRepository,
    ): DeleteLibraryItemUseCase = DeleteLibraryItemUseCase(annotationRepository, bookmarkRepository)

    @Provides
    fun provideSearchPublicationUseCase(
        searchService: SearchService,
    ): SearchPublicationUseCase = SearchPublicationUseCase(searchService)

    @Provides
    fun provideApplyAccessibilityPresetUseCase(
        preferencesRepository: PreferencesRepository,
    ): ApplyAccessibilityPresetUseCase = ApplyAccessibilityPresetUseCase(preferencesRepository)

    @Provides
    fun provideGetStatisticsUseCase(
        readingSessionRepository: ReadingSessionRepository,
        publicationRepository: PublicationRepository,
        clock: Clock,
    ): GetStatisticsUseCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository, clock)

    @Provides
    fun provideGetCurrentBookUseCase(
        readingSessionRepository: ReadingSessionRepository,
        publicationRepository: PublicationRepository,
        readingStateRepository: ReadingStateRepository,
    ): GetCurrentBookUseCase = GetCurrentBookUseCase(readingSessionRepository, publicationRepository, readingStateRepository)

    @Provides
    fun provideDeleteCustomThemeUseCase(
        themeRepository: ThemeRepository,
        preferencesRepository: PreferencesRepository,
        readingStateRepository: ReadingStateRepository,
    ): DeleteCustomThemeUseCase = DeleteCustomThemeUseCase(themeRepository, preferencesRepository, readingStateRepository)

    @Provides
    @Singleton
    fun provideStatisticsExportService(
        readingSessionDao: ReadingSessionDao,
        @ApplicationContext context: Context,
    ): StatisticsExportService = ExportStatisticsUseCase(readingSessionDao, context)

    @Provides
    fun provideGetCatalogsUseCase(
        catalogRepository: OpdsCatalogRepository,
    ): GetCatalogsUseCase = GetCatalogsUseCase(catalogRepository)

    @Provides
    fun provideAddCatalogUseCase(
        catalogRepository: OpdsCatalogRepository,
        credentialsStore: OpdsCredentialsStore,
    ): AddCatalogUseCase = AddCatalogUseCase(catalogRepository, credentialsStore)

    @Provides
    fun provideRemoveCatalogUseCase(
        catalogRepository: OpdsCatalogRepository,
        credentialsStore: OpdsCredentialsStore,
    ): RemoveCatalogUseCase = RemoveCatalogUseCase(catalogRepository, credentialsStore)

    @Provides
    @Singleton
    fun provideBrowseOpdsFeedUseCase(
        httpClient: OpdsHttpClient,
        parser: OpdsFeedParser,
        catalogRepository: OpdsCatalogRepository,
    ): BrowseOpdsFeedUseCase = BrowseOpdsFeedUseCase(httpClient, parser, catalogRepository)

    @Provides
    fun provideSearchOpdsFeedUseCase(
        browse: BrowseOpdsFeedUseCase,
    ): SearchOpdsFeedUseCase = SearchOpdsFeedUseCase(browse)
}
