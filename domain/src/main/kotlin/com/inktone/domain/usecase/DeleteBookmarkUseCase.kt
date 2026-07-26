package com.inktone.domain.usecase

import com.inktone.domain.repository.BookmarkRepository

class DeleteBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(id: String) {
        bookmarkRepository.delete(id)
    }
}
