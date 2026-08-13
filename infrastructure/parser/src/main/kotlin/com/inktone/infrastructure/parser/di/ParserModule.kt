package com.inktone.infrastructure.parser.di

import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.EpubResourceResolver
import com.inktone.domain.service.FixedPageRenderer
import com.inktone.domain.service.PublicationParser
import com.inktone.infrastructure.parser.CompositePublicationParser
import com.inktone.infrastructure.parser.EpubChapterParser
import com.inktone.infrastructure.parser.JsoupChapterParser
import com.inktone.infrastructure.parser.PdfPageRendererImpl
import com.inktone.infrastructure.parser.ReadiumResourceResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie les contrats de domaine aux implémentations dans infrastructure/parser.
 *
 * - [PublicationParser] → [CompositePublicationParser] (EPUB/TXT/PDF)
 * - [ChapterParser] → [EpubChapterParser] (parsing paresseux EPUB)
 * - [JsoupChapterParser] fourni comme singleton (utilisé par EpubChapterParser)
 * - [FixedPageRenderer] → [PdfPageRendererImpl] (rendu bitmap PDF)
 *
 * [EpubResourceResolver] est lié séparément, dans [ViewModelScopedParserModule]
 * ci-dessous — jamais `@Singleton` (voir son KDoc).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {
    @Binds
    @Singleton
    abstract fun bindPublicationParser(impl: CompositePublicationParser): PublicationParser

    @Binds
    @Singleton
    abstract fun bindChapterParser(impl: EpubChapterParser): ChapterParser

    @Binds
    @Singleton
    abstract fun bindFixedPageRenderer(impl: PdfPageRendererImpl): FixedPageRenderer

    companion object {
        @Provides
        @Singleton
        fun provideJsoupChapterParser(): JsoupChapterParser = JsoupChapterParser()
    }
}

/**
 * [EpubResourceResolver] scopé au ViewModel (plutôt qu'à
 * [SingletonComponent]) : chaque `ReaderViewModel` reçoit sa propre
 * instance, jamais partagée entre deux lecteurs ouverts en parallèle.
 * Une instance `@Singleton` fermerait ici la publication d'un autre
 * lecteur au moindre chevauchement d'écran (back-stack, navigation
 * rapide) — la `Publication` Readium elle-même reste partagée via
 * [com.inktone.infrastructure.parser.ReadiumPublicationRegistry] (K2),
 * seule l'instance de résolution est isolée par lecteur.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class ViewModelScopedParserModule {
    @Binds
    @ViewModelScoped
    abstract fun bindEpubResourceResolver(impl: ReadiumResourceResolver): EpubResourceResolver
}
