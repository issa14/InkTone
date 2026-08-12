package com.inktone.infrastructure.parser.di

import com.inktone.domain.service.FixedPageRenderer
import com.inktone.domain.service.PublicationParser
import com.inktone.infrastructure.parser.CompositePublicationParser
import com.inktone.infrastructure.parser.PdfPageRendererImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine PublicationParser (Tâche 1.7) à
 * CompositePublicationParser, qui sélectionne le bon parser par format
 * (Readium pour EPUB, TxtPublicationParser pour TXT, PdfPublicationParser
 * pour PDF — Lot 12). Jamais lié directement à ReadiumPublicationParser,
 * contrairement à ce que la Phase 3 faisait implicitement (un seul
 * format alors supporté).
 *
 * FixedPageRenderer (Lot 12, tâche 12.7, Palier 2) : contrat de rendu
 * bitmap distinct du parsing (décision actée 14 du plan) — même module
 * d'implémentation (PDFium déjà présent ici), pas de nouveau module
 * `infrastructure/`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {
    @Binds
    @Singleton
    abstract fun bindPublicationParser(impl: CompositePublicationParser): PublicationParser

    @Binds
    @Singleton
    abstract fun bindFixedPageRenderer(impl: PdfPageRendererImpl): FixedPageRenderer
}
