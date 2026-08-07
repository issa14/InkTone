package com.inktone.domain.usecase

import com.inktone.domain.model.LibraryItemType
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository

/** Lot 4, tâche 4.6 — supprime un [com.inktone.domain.model.LibraryItem], quelle que soit son entité d'origine. */
class DeleteLibraryItemUseCase(
    private val annotationRepository: AnnotationRepository,
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(type: LibraryItemType, id: String) {
        when (type) {
            LibraryItemType.BOOKMARK -> bookmarkRepository.delete(id)
            LibraryItemType.HIGHLIGHT, LibraryItemType.NOTE -> annotationRepository.delete(id)
        }
    }
}
