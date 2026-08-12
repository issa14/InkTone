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
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie les contrats de domaine aux implémentations dans infrastructure/parser.
 *
 * - [PublicationParser] → [CompositePublicationParser] (EPUB/TXT/PDF)
 * - [ChapterParser] → [EpubChapterParser] (parsing paresseux EPUB)
 * - [JsoupChapterParser] fourni comme singleton (utilisé par EpubChapterParser)
 * - [FixedPageRenderer] → [PdfPageRendererImpl] (rendu bitmap PDF)
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
    abstract fun bindEpubResourceResolver(impl: ReadiumResourceResolver): EpubResourceResolver

    @Binds
    @Singleton
    abstract fun bindFixedPageRenderer(impl: PdfPageRendererImpl): FixedPageRenderer

    companion object {
        @Provides
        @Singleton
        fun provideJsoupChapterParser(): JsoupChapterParser = JsoupChapterParser()
    }
}
