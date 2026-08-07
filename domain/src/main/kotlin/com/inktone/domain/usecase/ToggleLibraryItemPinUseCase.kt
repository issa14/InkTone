package com.inktone.domain.usecase

import com.inktone.domain.model.LibraryItemType
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository

/** Lot 4, tâche 4.3 — même patron que [TogglePinUseCase], mais réparti sur les deux entités que fusionne [com.inktone.domain.model.LibraryItem]. */
class ToggleLibraryItemPinUseCase(
    private val annotationRepository: AnnotationRepository,
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(type: LibraryItemType, id: String, isPinned: Boolean) {
        when (type) {
            LibraryItemType.BOOKMARK -> bookmarkRepository.setPinned(id, isPinned)
            LibraryItemType.HIGHLIGHT, LibraryItemType.NOTE -> annotationRepository.setPinned(id, isPinned)
        }
    }
}
