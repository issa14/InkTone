package com.inktone.domain.usecase

import com.inktone.domain.repository.BookmarkRepository

/**
 * Correctif Lot 21 — `ReaderViewModel.saveBookmarkNote` écrivait
 * directement via `BookmarkRepository.updateNote`, seule écriture de
 * signet à contourner le patron use case (`CreateBookmarkUseCase`,
 * `DeleteBookmarkUseCase`). Même forme que ces deux-là.
 */
class UpdateBookmarkNoteUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(id: String, note: String?) {
        bookmarkRepository.updateNote(id, note)
    }
}
