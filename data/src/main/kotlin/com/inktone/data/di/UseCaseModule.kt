package com.inktone.data.di

import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.ExportLibraryUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
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
    fun provideImportPublicationUseCase(
        publicationParser: PublicationParser,
        publicationRepository: PublicationRepository,
        fileStorageService: FileStorageService,
    ): ImportPublicationUseCase =
        ImportPublicationUseCase(publicationParser, publicationRepository, fileStorageService)

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
}
